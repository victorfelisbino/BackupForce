package com.backupforce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Utility class for SSL configuration.
 * Provides methods to bypass SSL certificate validation for development/testing.
 */
public class SSLHelper {
    private static final Logger logger = LoggerFactory.getLogger(SSLHelper.class);
    private static boolean sslBypassEnabled = false;

    /**
     * Disables SSL certificate validation globally.
     * WARNING: This should only be used in development/testing environments.
     * In production, proper SSL certificates should be installed.
     */
    public static void disableSSLValidation() {
        if (sslBypassEnabled) {
            logger.debug("SSL validation bypass already enabled");
            return;
        }

        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all client certificates
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all server certificates
                    }
                }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting hostname verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // Install the all-trusting hostname verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            sslBypassEnabled = true;
            logger.warn("⚠️  SSL certificate validation has been disabled globally");
            logger.warn("⚠️  This is insecure and should only be used in development/testing");
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.error("Failed to disable SSL validation", e);
        }
    }

    /**
     * Re-enables SSL certificate validation by restoring default settings.
     * Note: This may not fully restore the original state in all cases.
     */
    public static void enableSSLValidation() {
        if (!sslBypassEnabled) {
            logger.debug("SSL validation bypass not currently enabled");
            return;
        }

        try {
            // Restore default SSL context
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, null, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Restore default hostname verifier
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
                return HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
            });

            sslBypassEnabled = false;
            logger.info("SSL certificate validation has been re-enabled");
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.error("Failed to re-enable SSL validation", e);
        }
    }

    /**
     * Checks if SSL validation bypass is currently enabled.
     */
    public static boolean isSSLBypassEnabled() {
        return sslBypassEnabled;
    }
}
