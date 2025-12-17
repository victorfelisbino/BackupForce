package com.backupforce.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 Web Server Flow for Salesforce using PKCE
 * Uses the Salesforce CLI's pre-registered Connected App (works on ALL orgs!)
 * No need to create a Connected App in each org
 */
public class SalesforceOAuthServer {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceOAuthServer.class);
    
    // Using Salesforce CLI's official Connected App - works on ALL Salesforce orgs!
    // This is the same app used by 'sf org login web' command
    private static final String CLIENT_ID = "PlatformCLI";
    // Try multiple ports in case the default is blocked by firewall/antivirus
    private static final int[] FALLBACK_PORTS = {1717, 8888, 3000, 8080, 9090};
    
    private HttpServer server;
    private CompletableFuture<OAuthResult> resultFuture;
    private String codeVerifier;  // PKCE code verifier
    private String loginUrl;  // Store login URL for token exchange
    private int activePort = -1;  // Port that successfully started
    private String activeRedirectUri = null;  // Redirect URI for the active port
    
    public static class OAuthResult {
        public final String accessToken;
        public final String instanceUrl;
        public final String refreshToken;
        public final String error;
        
        public OAuthResult(String accessToken, String instanceUrl, String refreshToken) {
            this.accessToken = accessToken;
            this.instanceUrl = instanceUrl;
            this.refreshToken = refreshToken;
            this.error = null;
        }
        
        public OAuthResult(String error) {
            this.accessToken = null;
            this.instanceUrl = null;
            this.refreshToken = null;
            this.error = error;
        }
        
        public boolean isSuccess() {
            return error == null && accessToken != null;
        }
    }
    
    /**
     * Start OAuth flow - opens browser and waits for callback
     */
    public OAuthResult authenticate(String loginUrl) throws Exception {
        // Generate PKCE code verifier and challenge
        codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        logger.info("Generated PKCE code verifier and challenge");
        logger.info("Using Salesforce CLI Connected App (works on all orgs)");
        
        // Store login URL for token exchange
        this.loginUrl = loginUrl;
        
        resultFuture = new CompletableFuture<>();
        
        // Try to start local HTTP server on available port
        Exception lastException = null;
        for (int port : FALLBACK_PORTS) {
            try {
                logger.info("Attempting to start OAuth server on port {}...", port);
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                server.createContext("/OauthRedirect", new OAuthCallbackHandler());
                server.setExecutor(null);
                server.start();
                
                activePort = port;
                activeRedirectUri = "http://localhost:" + port + "/OauthRedirect";
                logger.info("✅ OAuth server started successfully on port {}", port);
                break; // Success!
                
            } catch (IOException e) {
                logger.warn("⚠️ Port {} is unavailable: {}", port, e.getMessage());
                lastException = e;
                server = null;
            }
        }
        
        // If all ports failed, show helpful error message
        if (server == null) {
            String errorMsg = "Cannot start OAuth server - all ports are blocked.\n\n" +
                             "Tried ports: 1717, 8888, 3000, 8080, 9090\n\n" +
                             "Possible solutions:\n" +
                             "1. Check your firewall settings\n" +
                             "2. Temporarily disable antivirus\n" +
                             "3. Close applications using these ports\n" +
                             "4. Run as administrator\n\n" +
                             "Error: " + (lastException != null ? lastException.getMessage() : "Unknown");
            logger.error(errorMsg);
            return new OAuthResult(errorMsg);
        }
        
        try {
            // Build authorization URL with PKCE challenge
            // prompt=login select_account forces account selection screen
            String authUrl = String.format(
                "%s/services/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s&scope=api%%20refresh_token%%20web&code_challenge=%s&code_challenge_method=S256&prompt=login%%20select_account",
                loginUrl,
                CLIENT_ID,
                activeRedirectUri,
                codeChallenge
            );
            
            logger.info("Opening browser for Salesforce login...");
            logger.info("Redirect URI: {}", activeRedirectUri);
            
            // Open browser
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(new URI(authUrl));
                    logger.info("✅ Browser opened successfully");
                } catch (Exception e) {
                    logger.error("❌ Failed to open browser: {}", e.getMessage());
                    return new OAuthResult("Cannot open browser automatically.\n\n" +
                                          "Please manually navigate to:\n" + authUrl + "\n\n" +
                                          "Error: " + e.getMessage());
                }
            } else {
                String errorMsg = "Desktop not supported - cannot open browser automatically.\n\n" +
                                 "Please manually copy this URL to your browser:\n\n" + authUrl;
                logger.error(errorMsg);
                return new OAuthResult(errorMsg);
            }
            
            // Wait for callback (with 5 minute timeout)
            logger.info("Waiting for OAuth callback on port {}...", activePort);
            OAuthResult result = resultFuture.get(5, TimeUnit.MINUTES);
            
            if (result.isSuccess()) {
                logger.info("✅ OAuth flow completed successfully");
            } else {
                logger.error("❌ OAuth flow failed: {}", result.error);
            }
            return result;
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("❌ OAuth timeout - no response received within 5 minutes");
            return new OAuthResult("Authentication timeout.\n\n" +
                                  "The OAuth callback was not received within 5 minutes.\n" +
                                  "Please try again and complete the login faster.");
        } catch (Exception e) {
            logger.error("❌ OAuth authentication failed", e);
            return new OAuthResult("Authentication failed: " + e.getMessage());
        } finally {
            // Stop server
            if (server != null) {
                server.stop(0);
                logger.info("OAuth server stopped (port {})", activePort);
            }
        }
    }
    
    /**
     * Handler for OAuth callback from Salesforce
     */
    private class OAuthCallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                
                if (params.containsKey("error")) {
                    // Error from Salesforce
                    String error = params.get("error");
                    String errorDescription = params.getOrDefault("error_description", "Unknown error");
                    logger.error("OAuth error: {} - {}", error, errorDescription);
                    
                    sendResponse(exchange, 400, "Authentication failed: " + errorDescription);
                    resultFuture.complete(new OAuthResult(errorDescription));
                    return;
                }
                
                if (!params.containsKey("code")) {
                    sendResponse(exchange, 400, "Missing authorization code");
                    resultFuture.complete(new OAuthResult("Missing authorization code"));
                    return;
                }
                
                String authCode = params.get("code");
                logger.info("Received authorization code");
                
                // Exchange code for access token
                OAuthResult result = exchangeCodeForToken(authCode);
                
                if (result.isSuccess()) {
                    sendResponse(exchange, 200, 
                        "<html><body>" +
                        "<h1>Success!</h1>" +
                        "<p>You have successfully authenticated with Salesforce.</p>" +
                        "<p>You can close this window and return to BackupForce.</p>" +
                        "</body></html>");
                } else {
                    sendResponse(exchange, 400, 
                        "<html><body>" +
                        "<h1>Error</h1>" +
                        "<p>" + result.error + "</p>" +
                        "</body></html>");
                }
                
                resultFuture.complete(result);
                
            } catch (Exception e) {
                logger.error("Error handling OAuth callback", e);
                sendResponse(exchange, 500, "Internal server error");
                resultFuture.complete(new OAuthResult("Error: " + e.getMessage()));
            }
        }
    }
    
    /**
     * Exchange authorization code for access token
     */
    private OAuthResult exchangeCodeForToken(String authCode) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Use the same endpoint as authorization (sandbox or production)
            String tokenEndpoint = loginUrl + "/services/oauth2/token";
            logger.info("Token endpoint: {}", tokenEndpoint);
            
            HttpPost post = new HttpPost(tokenEndpoint);
            
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "authorization_code"));
            params.add(new BasicNameValuePair("code", authCode));
            params.add(new BasicNameValuePair("client_id", CLIENT_ID));
            params.add(new BasicNameValuePair("code_verifier", codeVerifier));
            params.add(new BasicNameValuePair("redirect_uri", activeRedirectUri));  // Use active port
            
            post.setEntity(new UrlEncodedFormEntity(params));
            
            try (ClassicHttpResponse response = httpClient.executeOpen(null, post, null)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() != 200) {
                    logger.error("Token exchange failed: {}", responseBody);
                    return new OAuthResult("Token exchange failed: " + responseBody);
                }
                
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                
                String accessToken = json.get("access_token").getAsString();
                String instanceUrl = json.get("instance_url").getAsString();
                String refreshToken = json.has("refresh_token") ? 
                    json.get("refresh_token").getAsString() : null;
                
                logger.info("Successfully obtained access token");
                logger.info("Instance URL: {}", instanceUrl);
                
                return new OAuthResult(accessToken, instanceUrl, refreshToken);
            }
            
        } catch (Exception e) {
            logger.error("Error exchanging code for token", e);
            return new OAuthResult("Token exchange error: " + e.getMessage());
        }
    }
    
    /**
     * Parse query string into map
     */
    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                result.put(key, value);
            }
        }
        return result;
    }
    
    /**
     * Send HTTP response
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * Generate a cryptographically random code verifier for PKCE
     * Must be 43-128 characters from [A-Z][a-z][0-9]-._~
     */
    private String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Generate SHA-256 code challenge from verifier for PKCE
     * code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))
     */
    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
