package com.nutomic.syncthingandroid.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.DateFormat

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
     */
    fun validate(certSlot: ByteArray, keySlot: ByteArray): ValidationResult {
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
            trustCheck(chain, leaf),
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

    private fun trustCheck(chain: List<X509Certificate>, leaf: X509Certificate): CheckResult {
        // Self-signed pin path — mirrors SyncthingTrustManager.verifyAgainstPinnedCert.
        if (isSelfSigned(leaf)) {
            return CheckResult(Check.TRUST, Status.PASS, "Self-signed — the app will pin this certificate.")
        }
        val tm = Util.getOsTrustManager()
            ?: return CheckResult(Check.TRUST, Status.FAIL, "The Android trust store is unavailable.")
        return try {
            tm.checkServerTrusted(chain.toTypedArray(), authType(leaf))
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

    private fun keyCheck(keyBytes: ByteArray, leaf: X509Certificate): CheckResult {
        val text = String(keyBytes, Charsets.US_ASCII)
        val privateKey: PrivateKey? = try {
            when {
                text.contains("BEGIN PRIVATE KEY") -> loadPkcs8(keyBytes, leaf.publicKey.algorithm)
                text.contains("BEGIN ENCRYPTED PRIVATE KEY") ->
                    return CheckResult(Check.KEY, Status.WARN, "The private key is encrypted — it will be verified when applied.")
                text.contains("BEGIN RSA PRIVATE KEY") || text.contains("BEGIN EC PRIVATE KEY") ->
                    return CheckResult(Check.KEY, Status.WARN, "Legacy key format — it will be verified when applied.")
                else -> null
            }
        } catch (e: Exception) {
            return CheckResult(Check.KEY, Status.WARN, "Could not read the key locally — it will be verified when applied.")
        }
        if (privateKey == null) {
            return CheckResult(Check.KEY, Status.WARN, "Unrecognized key format — it will be verified when applied.")
        }
        return if (keyMatchesCert(privateKey, leaf))
            CheckResult(Check.KEY, Status.PASS, "The private key matches the certificate.")
        else
            CheckResult(Check.KEY, Status.FAIL, "The private key does NOT match the certificate.")
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

    private fun authType(leaf: X509Certificate): String =
        when (leaf.publicKey.algorithm.uppercase()) {
            "EC", "ECDSA" -> "EC"
            else -> leaf.publicKey.algorithm
        }

    private fun loadPkcs8(pem: ByteArray, certKeyAlgorithm: String): PrivateKey {
        val der = pemToDer(pem, "PRIVATE KEY")
        val alg = if (certKeyAlgorithm.equals("EC", true) || certKeyAlgorithm.equals("ECDSA", true))
            "EC" else certKeyAlgorithm
        return KeyFactory.getInstance(alg).generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun keyMatchesCert(privateKey: PrivateKey, leaf: X509Certificate): Boolean = try {
        val sigAlg = when (privateKey.algorithm.uppercase()) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            else -> return false
        }
        val signer = Signature.getInstance(sigAlg)
        signer.initSign(privateKey)
        signer.update(NONCE)
        val sig = signer.sign()
        val verifier = Signature.getInstance(sigAlg)
        verifier.initVerify(leaf.publicKey)
        verifier.update(NONCE)
        verifier.verify(sig)
    } catch (e: Exception) {
        false
    }

    private fun pemToDer(pem: ByteArray, kind: String): ByteArray {
        val text = String(pem, Charsets.US_ASCII)
        val begin = "-----BEGIN $kind-----"
        val end = "-----END $kind-----"
        val b = text.indexOf(begin)
        val e = text.indexOf(end)
        require(b >= 0 && e > b) { "PEM block not found" }
        val body = text.substring(b + begin.length, e).replace("\\s".toRegex(), "")
        return Base64.decode(body, Base64.DEFAULT)
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
