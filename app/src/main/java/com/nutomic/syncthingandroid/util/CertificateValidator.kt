package com.nutomic.syncthingandroid.util

import com.google.common.io.BaseEncoding
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.SignatureException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.DateFormat
import javax.net.ssl.X509TrustManager

/**
 * Validates a user-supplied HTTPS certificate (+ private key) that is about to replace the local
 * Syncthing Web GUI certificate.
 *
 * The decisive check is [Check.TRUST]: it mirrors [com.nutomic.syncthingandroid.http.SyncthingTrustManager]
 * exactly (self-signed pin OR a chain that validates against the Android OS trust store via
 * [Util.getOsTrustManager]). If that passes, the app will be able to talk to Syncthing once the cert
 * is installed. The private-key match is best-effort: Syncthing's own `tls.LoadX509KeyPair` at the
 * post-replace restart is the authoritative arbiter, with automatic rollback on failure.
 */
object CertificateValidator {

    enum class Status { PASS, WARN, FAIL }

    /** The kinds of checks performed; the UI maps each to a localized title. */
    enum class Check { CHAIN, TRUST, VALIDITY, KEY }

    data class CheckResult(val check: Check, val status: Status, val detail: String? = null)

    data class CertInfo(
        val subject: String,
        val issuer: String,
        val notAfter: String,
        val selfSigned: Boolean,
    )

    class ValidationResult(
        /** Non-null when the files could not even be parsed into a cert + key pair. */
        val parseError: String?,
        val checks: List<CheckResult>,
        val canApply: Boolean,
        /** Normalized certificate PEM bytes to write (auto-corrected if the user swapped the pickers). */
        val certPem: ByteArray,
        /** Normalized private-key PEM bytes to write. */
        val keyPem: ByteArray,
        val info: CertInfo?,
    )

    private val NONCE = "syncthing-android-cert-key-match-probe".toByteArray()

    /**
     * Validates the two picked files. The arguments are passed in the order the user assigned them
     * (certificate slot, key slot) but are auto-corrected if swapped, based on their PEM block type.
     *
     * @param osTrustManager the trust store to validate CA-signed chains against; defaults to the
     * Android OS trust store. Injectable so [Check.TRUST] can be exercised in unit tests, which have
     * no "AndroidCAStore".
     */
    fun validate(
        certSlot: ByteArray,
        keySlot: ByteArray,
        osTrustManager: X509TrustManager? = Util.getOsTrustManager(),
    ): ValidationResult {
        val aIsCert = looksLikeCertificate(certSlot)
        val bIsCert = looksLikeCertificate(keySlot)
        val aIsKey = looksLikeKey(certSlot)
        val bIsKey = looksLikeKey(keySlot)

        val certBytes: ByteArray
        val keyBytes: ByteArray
        when {
            aIsCert && bIsKey -> { certBytes = certSlot; keyBytes = keySlot }
            bIsCert && aIsKey -> { certBytes = keySlot; keyBytes = certSlot } // swapped
            aIsCert && bIsCert -> return fail("Both files are certificates — one must be the private key.", certSlot, keySlot)
            aIsKey && bIsKey -> return fail("Both files are private keys — one must be the certificate.", certSlot, keySlot)
            !aIsCert && !bIsCert -> return fail("No PEM certificate found in the selected files.", certSlot, keySlot)
            aIsCert -> { certBytes = certSlot; keyBytes = keySlot }
            else -> { certBytes = keySlot; keyBytes = certSlot }
        }

        val chain: List<X509Certificate> = try {
            parseChain(certBytes)
        } catch (e: Exception) {
            return fail("Could not read the certificate: ${e.message}", certSlot, keySlot)
        }
        if (chain.isEmpty()) {
            return fail("The certificate file contains no certificates.", certSlot, keySlot)
        }

        val leaf = chain.first()
        val checks = listOf(
            chainCheck(chain),
            trustCheck(chain, leaf, osTrustManager),
            validityCheck(chain),
            keyCheck(keyBytes, leaf),
        )

        val trust = checks.first { it.check == Check.TRUST }
        val validity = checks.first { it.check == Check.VALIDITY }
        val key = checks.first { it.check == Check.KEY }
        val canApply = trust.status == Status.PASS &&
                validity.status == Status.PASS &&
                key.status != Status.FAIL

        return ValidationResult(null, checks, canApply, certBytes, keyBytes, certInfo(leaf))
    }

    /** Parses the (currently installed) certificate file for display, or null if unreadable. */
    fun describe(certBytes: ByteArray): CertInfo? = try {
        parseChain(certBytes).firstOrNull()?.let { certInfo(it) }
    } catch (e: Exception) {
        null
    }

    /**
     * SHA-256 of the leaf certificate's DER encoding, or null if the PEM holds no readable
     * certificate.
     *
     * Used to tell whether the certificate on disk is still the one that was written: Syncthing
     * silently replaces an unloadable pair with a freshly generated self-signed certificate, so the
     * file having changed underneath us is the signal that the change did not take.
     */
    @JvmStatic
    fun leafFingerprint(certPem: ByteArray): String? = try {
        parseChain(certPem).firstOrNull()?.let { leaf ->
            MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    } catch (e: Exception) {
        null
    }

    // --- individual checks ---------------------------------------------------------------------

    private fun chainCheck(chain: List<X509Certificate>): CheckResult {
        if (chain.size == 1) {
            return if (isSelfSigned(chain[0]))
                CheckResult(Check.CHAIN, Status.PASS, "Self-signed certificate.")
            else
                CheckResult(
                    Check.CHAIN, Status.WARN,
                    "Only the leaf certificate is present — include the intermediate(s) so the chain is complete."
                )
        }
        for (i in 0 until chain.size - 1) {
            try {
                chain[i].verify(chain[i + 1].publicKey)
            } catch (e: Exception) {
                return CheckResult(
                    Check.CHAIN, Status.WARN,
                    "Certificates may be out of order or an intermediate is missing."
                )
            }
        }
        return CheckResult(Check.CHAIN, Status.PASS, "Full chain present (${chain.size} certificates).")
    }

    private fun trustCheck(
        chain: List<X509Certificate>,
        leaf: X509Certificate,
        osTrustManager: X509TrustManager?,
    ): CheckResult {
        // Self-signed pin path — mirrors SyncthingTrustManager.verifyAgainstPinnedCert.
        if (isSelfSigned(leaf)) {
            return CheckResult(Check.TRUST, Status.PASS, "Self-signed — the app will pin this certificate.")
        }
        val tm = osTrustManager
            ?: return CheckResult(Check.TRUST, Status.FAIL, "The Android trust store is unavailable.")
        return try {
            tm.checkServerTrusted(chain.toTypedArray(), TLS_AUTH_TYPE_UNKNOWN)
            CheckResult(
                Check.TRUST, Status.PASS,
                "Trusted by Android (chain validated; the hostname is intentionally not checked for the local connection)."
            )
        } catch (e: Exception) {
            CheckResult(
                Check.TRUST, Status.FAIL,
                "Not trusted by Android — install your root CA on the device and include the full chain."
            )
        }
    }

    private fun validityCheck(chain: List<X509Certificate>): CheckResult {
        for (c in chain) {
            try {
                c.checkValidity()
            } catch (e: CertificateExpiredException) {
                return CheckResult(Check.VALIDITY, Status.FAIL, "A certificate in the chain has expired.")
            } catch (e: CertificateNotYetValidException) {
                return CheckResult(Check.VALIDITY, Status.FAIL, "A certificate in the chain is not yet valid.")
            }
        }
        return CheckResult(
            Check.VALIDITY, Status.PASS,
            "Valid until ${DateFormat.getDateInstance().format(chain.first().notAfter)}."
        )
    }

    /**
     * Mirrors what Syncthing's `tls.LoadX509KeyPair` will do with the key, because the consequence of
     * getting it wrong is severe and silent: when the key pair fails to load, Syncthing does not
     * refuse to start — it generates a fresh self-signed certificate, overwriting the file we just
     * wrote (`lib/api/api.go` `getListener`). So anything Syncthing definitely cannot read has to be
     * a [Status.FAIL] here rather than an optimistic [Status.WARN].
     *
     * [Status.WARN] is reserved for "Syncthing can read this, but we cannot check it here" — a legacy
     * PKCS#1/SEC1 block, or a key of an algorithm this device's providers do not implement. Those stay
     * applyable, and the post-restart fingerprint check in
     * [com.nutomic.syncthingandroid.service.SyncthingService] is the backstop.
     */
    private fun keyCheck(keyBytes: ByteArray, leaf: X509Certificate): CheckResult {
        val text = String(keyBytes, Charsets.US_ASCII)

        // Two encrypted shapes: PKCS#8 ("ENCRYPTED PRIVATE KEY") and OpenSSL's traditional PEM, whose
        // block type is the ordinary "RSA PRIVATE KEY" and is only distinguishable by its DEK-Info
        // header. That check must therefore come before the legacy-format branch below.
        if (text.contains("BEGIN ENCRYPTED PRIVATE KEY") || text.contains("DEK-Info:")) {
            return CheckResult(
                Check.KEY, Status.FAIL,
                "The private key is encrypted. Syncthing cannot read encrypted keys — decrypt it first.",
            )
        }
        if (text.contains("BEGIN RSA PRIVATE KEY") || text.contains("BEGIN EC PRIVATE KEY")) {
            return CheckResult(
                Check.KEY, Status.WARN,
                "Legacy key format — Syncthing accepts it, but it cannot be checked against the certificate here.",
            )
        }
        if (!text.contains("BEGIN PRIVATE KEY")) {
            return CheckResult(Check.KEY, Status.FAIL, "No private key found in the selected file.")
        }

        val der = try {
            pemToDer(keyBytes, "PRIVATE KEY")
        } catch (e: Exception) {
            // The PEM envelope itself is broken, so Syncthing cannot read it either.
            return CheckResult(Check.KEY, Status.FAIL, "The private key could not be decoded.")
        }
        val privateKey = loadPkcs8(der)
            // Well-formed PKCS#8 that no installed provider understands — possibly a valid key of an
            // algorithm Syncthing supports and this device does not. Not ours to reject.
            ?: return CheckResult(
                Check.KEY, Status.WARN,
                "The key cannot be checked against the certificate on this device.",
            )

        return when (keyMatchesCert(privateKey, leaf)) {
            KeyMatch.MATCH ->
                CheckResult(Check.KEY, Status.PASS, "The private key matches the certificate.")
            KeyMatch.MISMATCH ->
                CheckResult(Check.KEY, Status.FAIL, "The private key does NOT match the certificate.")
            KeyMatch.UNKNOWN -> CheckResult(
                Check.KEY, Status.WARN,
                "The key cannot be checked against the certificate on this device.",
            )
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun parseChain(bytes: ByteArray): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificates(ByteArrayInputStream(bytes)).map { it as X509Certificate }
    }

    private fun certInfo(leaf: X509Certificate) = CertInfo(
        subject = shortName(leaf.subjectX500Principal.name),
        issuer = shortName(leaf.issuerX500Principal.name),
        notAfter = DateFormat.getDateInstance().format(leaf.notAfter),
        selfSigned = isSelfSigned(leaf),
    )

    private fun isSelfSigned(c: X509Certificate): Boolean = try {
        c.verify(c.publicKey)
        c.subjectX500Principal == c.issuerX500Principal
    } catch (e: Exception) {
        false
    }

    /**
     * `authType` names the TLS key exchange, which only exists during a handshake — here we are
     * pre-validating a certificate, so there is nothing to name. `"UNKNOWN"` asks the trust manager
     * for chain validation without tying it to a key-exchange method.
     *
     * Deriving it from the key algorithm instead (`"EC"`, `"RSA"`) happens to work on Android, whose
     * trust manager only requires the value to be non-empty, but it is not a valid TLS auth type:
     * a strict implementation rejects `"EC"` outright ("Unknown authType") and reads `"RSA"` as
     * static RSA key exchange, which then demands a `keyEncipherment` key usage a normal TLS server
     * certificate does not have. Either way the result is a spurious [Status.FAIL] here while the
     * real connection succeeds.
     */
    private const val TLS_AUTH_TYPE_UNKNOWN = "UNKNOWN"

    /**
     * Loads a PKCS#8 key by trying each algorithm this app cares about, the way Go's `parsePrivateKey`
     * tries each format. Deriving the algorithm from the *certificate* instead would misreport a
     * genuine key/certificate algorithm mismatch as an unreadable key.
     *
     * @return null when no installed provider accepts the (well-formed) key.
     */
    private fun loadPkcs8(der: ByteArray): PrivateKey? {
        val spec = PKCS8EncodedKeySpec(der)
        for (algorithm in listOf("RSA", "EC", "Ed25519")) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec)
            } catch (e: Exception) {
                // Wrong algorithm for this key, or unsupported on this device — try the next.
            }
        }
        return null
    }

    private enum class KeyMatch { MATCH, MISMATCH, UNKNOWN }

    /**
     * Signs a probe with the private key and verifies it with the certificate's public key.
     *
     * [KeyMatch.UNKNOWN] and [KeyMatch.MISMATCH] must stay distinct: conflating them meant a valid
     * Ed25519 pair — which Syncthing accepts — was reported as a mismatch and could not be applied,
     * because the signature algorithm was simply unknown to this function. Anything we cannot verify
     * is UNKNOWN, never a mismatch.
     */
    private fun keyMatchesCert(privateKey: PrivateKey, leaf: X509Certificate): KeyMatch {
        // A key and certificate of different algorithms can never belong together, and saying so here
        // avoids reporting the resulting InvalidKeyException as "unverifiable".
        if (canonicalAlgorithm(privateKey.algorithm) != canonicalAlgorithm(leaf.publicKey.algorithm)) {
            return KeyMatch.MISMATCH
        }
        val sigAlg = when (canonicalAlgorithm(privateKey.algorithm)) {
            "RSA" -> "SHA256withRSA"
            "EC" -> "SHA256withECDSA"
            // Signature support for Ed25519 only exists on newer Android versions; where it is
            // missing this falls through to UNKNOWN rather than a false mismatch.
            "ED25519" -> "Ed25519"
            else -> return KeyMatch.UNKNOWN
        }
        return try {
            val signer = Signature.getInstance(sigAlg)
            signer.initSign(privateKey)
            signer.update(NONCE)
            val sig = signer.sign()
            val verifier = Signature.getInstance(sigAlg)
            verifier.initVerify(leaf.publicKey)
            verifier.update(NONCE)
            if (verifier.verify(sig)) KeyMatch.MATCH else KeyMatch.MISMATCH
        } catch (e: SignatureException) {
            // Some providers reject a signature made by the wrong key by throwing rather than
            // returning false.
            KeyMatch.MISMATCH
        } catch (e: Exception) {
            KeyMatch.UNKNOWN
        }
    }

    private fun canonicalAlgorithm(algorithm: String): String =
        when (val upper = algorithm.uppercase()) {
            "ECDSA" -> "EC"
            "EDDSA" -> "ED25519"
            else -> upper
        }

    /**
     * Uses Guava rather than `android.util.Base64` deliberately: it keeps this class free of Android
     * APIs so it can be unit-tested on the JVM without Robolectric. `java.util.Base64` is not an
     * option — it needs API 26 and `min-sdk` is 23.
     */
    private fun pemToDer(pem: ByteArray, kind: String): ByteArray {
        val text = String(pem, Charsets.US_ASCII)
        val begin = "-----BEGIN $kind-----"
        val end = "-----END $kind-----"
        val b = text.indexOf(begin)
        val e = text.indexOf(end)
        require(b >= 0 && e > b) { "PEM block not found" }
        val body = text.substring(b + begin.length, e).replace("\\s".toRegex(), "")
        return BaseEncoding.base64().decode(body)
    }

    private fun looksLikeCertificate(bytes: ByteArray): Boolean =
        String(bytes, Charsets.US_ASCII).contains("-----BEGIN CERTIFICATE-----")

    private fun looksLikeKey(bytes: ByteArray): Boolean =
        String(bytes, Charsets.US_ASCII).contains("PRIVATE KEY-----")

    private fun shortName(dn: String): String =
        dn.split(",").map { it.trim() }.firstOrNull { it.startsWith("CN=", true) }?.substring(3) ?: dn

    private fun fail(message: String, certSlot: ByteArray, keySlot: ByteArray) =
        ValidationResult(message, emptyList(), false, certSlot, keySlot, null)
}
