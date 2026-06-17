package com.nutomic.syncthingandroid.http;

import android.annotation.SuppressLint;
import android.util.Log;

import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/*
 * TrustManager checking against the local Syncthing instance's https public key.
 *
 * Based on http://stackoverflow.com/questions/16719959#16759793
 *
 * The local Syncthing instance ships a self-signed certificate by default, which is verified by
 * pinning against the public key stored in https-cert.pem. A user may instead replace the HTTPS
 * certificate with one signed by a CA they trust at the Android OS level (see
 * https://github.com/researchxxl/syncthing-android/issues/222); for that case we fall back to the
 * OS trust store when the self-signed pin does not match.
 *
 * Security scope: this trust manager is only ever wired into the loopback-pinned connection to the
 * local Syncthing instance (see ApiRequest#forceLoopbackHost). Because that connection cannot leave
 * the device, falling back to the OS trust store — which trusts user-installed CAs — does not open a
 * network MITM surface. Do not reuse this trust manager for any routable/remote connection.
 */
class SyncthingTrustManager implements X509TrustManager {

    private static final String TAG = "SyncthingTrustManager";

    private final File mHttpsCertPath;

    SyncthingTrustManager(File httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    @Override
    @SuppressLint("TrustAllX509TrustManager")
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
    }

    /**
     * Verifies certs against the public key of the local syncthing instance (self-signed pin).
     * If that fails, falls back to the Android OS trust store, which validates CA-signed
     * certificates against the system and user-installed certificate authorities.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] certs,
                                   String authType) throws CertificateException {
        try {
            verifyAgainstPinnedCert(certs);
        } catch (CertificateException pinFailure) {
            // The presented certificate is not the pinned self-signed certificate. This is expected
            // when the user replaced the HTTPS certificate with a CA-signed one, so fall back to the
            // Android OS trust store instead of failing the connection outright. Logged at debug
            // level to avoid spamming logcat with the wrapped BAD_SIGNATURE on every request.
            Log.d(TAG, "Pinned certificate did not match, trying Android OS trust store.");
            X509TrustManager osTrustManager = Util.getOsTrustManager();
            if (osTrustManager == null) {
                throw pinFailure;
            }
            osTrustManager.checkServerTrusted(certs, authType);
        }
    }

    /**
     * Verifies that every presented certificate is signed by the public key of the certificate
     * pinned in {@link #mHttpsCertPath} (the certificate the local syncthing instance generated).
     */
    private void verifyAgainstPinnedCert(X509Certificate[] certs) throws CertificateException {
        InputStream is = null;
        try {
            is = new FileInputStream(mHttpsCertPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate) cf.generateCertificate(is);
            for (X509Certificate cert : certs) {
                cert.verify(ca.getPublicKey());
            }
        } catch (FileNotFoundException | NoSuchAlgorithmException | InvalidKeyException |
                NoSuchProviderException | SignatureException e) {
            throw new CertificateException("Untrusted Certificate!", e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }
    }

    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
