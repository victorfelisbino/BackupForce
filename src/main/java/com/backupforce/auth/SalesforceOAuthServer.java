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
    
    // Client ID loaded from oauth.properties, falls back to Salesforce CLI's app
    private static final String CLIENT_ID = loadClientId();
    // Try multiple ports in case the default is blocked by firewall/antivirus
    private static final int[] FALLBACK_PORTS = {1717, 8888, 3000, 8080, 9090};
    
    /**
     * Load OAuth client ID from properties file.
     * Falls back to "PlatformCLI" (Salesforce CLI's app) if not configured.
     */
    private static String loadClientId() {
        String clientId = "PlatformCLI"; // Default fallback
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.InputStream is = SalesforceOAuthServer.class.getResourceAsStream("/oauth.properties");
            if (is != null) {
                props.load(is);
                is.close();
                String configuredId = props.getProperty("oauth.client_id");
                if (configuredId != null && !configuredId.isBlank() && !configuredId.equals("YOUR_CLIENT_ID_HERE")) {
                    clientId = configuredId.trim();
                    logger.info("Loaded custom OAuth client ID from oauth.properties");
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load oauth.properties, using default client ID: {}", e.getMessage());
        }
        return clientId;
    }
    
    // Default timeout (can be changed via setTimeout)
    private static final int DEFAULT_TIMEOUT_SECONDS = 180; // 3 minutes
    
    private HttpServer server;
    private CompletableFuture<OAuthResult> resultFuture;
    private String codeVerifier;  // PKCE code verifier
    private String oauthState;    // State parameter to prevent CSRF and stale code reuse
    private String loginUrl;  // Store login URL for token exchange
    private int activePort = -1;  // Port that successfully started
    private String activeRedirectUri = null;  // Redirect URI for the active port
    private volatile boolean cancelled = false;  // Flag to track cancellation
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    
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
        
        public boolean isCancelled() {
            return "CANCELLED".equals(error);
        }
    }
    
    /**
     * Cancel the ongoing OAuth flow
     */
    public void cancel() {
        cancelled = true;
        if (resultFuture != null && !resultFuture.isDone()) {
            resultFuture.complete(new OAuthResult("CANCELLED"));
        }
        stopServer();
    }
    
    /**
     * Stop the OAuth server
     */
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            logger.info("OAuth server stopped");
            server = null;
        }
    }
    
    /**
     * Set timeout in seconds (default: 180 = 3 minutes)
     */
    public void setTimeout(int seconds) {
        this.timeoutSeconds = seconds;
    }
    
    /**
     * Get the current timeout in seconds
     */
    public int getTimeout() {
        return timeoutSeconds;
    }
    
    /**
     * Start OAuth flow - opens browser and waits for callback
     */
    public OAuthResult authenticate(String loginUrl) throws Exception {
        // Generate PKCE code verifier and challenge
        codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        // Generate unique state to prevent CSRF and stale authorization code reuse
        oauthState = generateCodeVerifier();  // Reuse the secure random generator
        logger.info("Generated PKCE code verifier, challenge, and state");
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
            // Build authorization URL with PKCE challenge and state parameter
            // prompt=login select_account forces account selection screen
            // state parameter prevents CSRF and ensures callback matches this request
            String authUrl = String.format(
                "%s/services/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s&scope=api%%20refresh_token%%20web&code_challenge=%s&code_challenge_method=S256&state=%s&prompt=login%%20select_account",
                loginUrl,
                CLIENT_ID,
                activeRedirectUri,
                codeChallenge,
                oauthState
            );
            
            logger.info("Opening browser for Salesforce login...");
            logger.info("Redirect URI: {}", activeRedirectUri);
            
            // Open browser - try multiple methods for reliability
            boolean browserOpened = openBrowser(authUrl);
            if (!browserOpened) {
                return new OAuthResult("Cannot open browser automatically.\n\n" +
                                      "Please manually navigate to:\n" + authUrl);
            }
            
            // Wait for callback (with configurable timeout)
            logger.info("Waiting for OAuth callback on port {} (timeout: {}s)...", activePort, timeoutSeconds);
            OAuthResult result = resultFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            
            if (cancelled) {
                logger.info("OAuth flow was cancelled by user");
                return new OAuthResult("CANCELLED");
            }
            
            if (result.isSuccess()) {
                logger.info("✅ OAuth flow completed successfully");
            } else if (result.isCancelled()) {
                logger.info("OAuth flow cancelled");
            } else {
                logger.error("❌ OAuth flow failed: {}", result.error);
            }
            return result;
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("❌ OAuth timeout - no response received within {} seconds", timeoutSeconds);
            return new OAuthResult("Authentication timeout.\n\n" +
                                  "No response received within " + (timeoutSeconds / 60) + " minutes.\n" +
                                  "Please try again and complete the login in the browser.");
        } catch (java.util.concurrent.CancellationException e) {
            logger.info("OAuth flow was cancelled");
            return new OAuthResult("CANCELLED");
        } catch (Exception e) {
            if (cancelled) {
                return new OAuthResult("CANCELLED");
            }
            logger.error("❌ OAuth authentication failed", e);
            return new OAuthResult("Authentication failed: " + e.getMessage());
        } finally {
            // Stop server
            stopServer();
        }
    }
    
    /**
     * Open browser using multiple fallback methods for reliability.
     * Some methods work better in certain environments (packaged app vs JAR).
     */
    private boolean openBrowser(String url) {
        String os = System.getProperty("os.name").toLowerCase();
        
        // Method 1: Try Desktop.browse() first - most reliable for URLs with special chars
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                logger.info("Trying Desktop.browse() to open browser...");
                Desktop.getDesktop().browse(new URI(url));
                logger.info("✅ Browser opened via Desktop.browse()");
                return true;
            } catch (Exception e) {
                logger.warn("Desktop.browse() failed: {}", e.getMessage(), e);
            }
        }
        
        // Method 2: Try Windows rundll32 - handles URLs with & characters properly
        if (os.contains("win")) {
            try {
                logger.info("Trying rundll32 to open browser...");
                ProcessBuilder pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                pb.start();
                Thread.sleep(500);
                logger.info("✅ Browser opened via rundll32");
                return true;
            } catch (Exception e) {
                logger.warn("rundll32 failed: {}", e.getMessage(), e);
            }
        }
        
        // Method 3: Try PowerShell - properly escapes URLs
        if (os.contains("win")) {
            try {
                logger.info("Trying PowerShell Start-Process to open browser...");
                // Use -ArgumentList to properly pass URL with special characters
                ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", 
                    "Start-Process", "\"" + url.replace("\"", "`\"") + "\"");
                pb.start();
                Thread.sleep(500);
                logger.info("✅ Browser opened via PowerShell");
                return true;
            } catch (Exception e) {
                logger.warn("PowerShell Start-Process failed: {}", e.getMessage(), e);
            }
        }
        
        // Method 4: Try xdg-open for Linux
        if (os.contains("nix") || os.contains("nux")) {
            try {
                new ProcessBuilder("xdg-open", url).start();
                logger.info("✅ Browser opened via xdg-open");
                return true;
            } catch (Exception e) {
                logger.warn("xdg-open failed: {}", e.getMessage());
            }
        }
        
        // Method 5: Try 'open' for macOS
        if (os.contains("mac")) {
            try {
                new ProcessBuilder("open", url).start();
                logger.info("✅ Browser opened via 'open' command");
                return true;
            } catch (Exception e) {
                logger.warn("macOS 'open' failed: {}", e.getMessage());
            }
        }
        
        logger.error("❌ All browser opening methods failed");
        return false;
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
                
                // Validate state parameter to prevent CSRF and stale code reuse
                String receivedState = params.get("state");
                if (oauthState != null && !oauthState.equals(receivedState)) {
                    logger.error("State mismatch - possible stale authorization code from previous attempt");
                    sendResponse(exchange, 400, 
                        "<html><body>" +
                        "<h1>Session Expired</h1>" +
                        "<p>This authorization link is from a previous login attempt.</p>" +
                        "<p>Please close this tab and try logging in again from BackupForce.</p>" +
                        "</body></html>");
                    resultFuture.complete(new OAuthResult("Authorization session expired. Please try again."));
                    return;
                }
                
                String authCode = params.get("code");
                logger.info("Received authorization code with valid state");
                
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
    
    /**
     * Static method to refresh an access token using a stored refresh token.
     * This allows silent authentication without opening a browser.
     * 
     * @param loginUrl The Salesforce login URL (production or sandbox)
     * @param refreshToken The stored refresh token
     * @return OAuthResult with new access token, or error if refresh failed
     */
    public static OAuthResult refreshAccessToken(String loginUrl, String refreshToken) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String tokenEndpoint = loginUrl + "/services/oauth2/token";
            logger.info("Attempting silent token refresh...");
            
            HttpPost post = new HttpPost(tokenEndpoint);
            
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "refresh_token"));
            params.add(new BasicNameValuePair("refresh_token", refreshToken));
            params.add(new BasicNameValuePair("client_id", CLIENT_ID));
            
            post.setEntity(new UrlEncodedFormEntity(params));
            
            try (ClassicHttpResponse response = httpClient.executeOpen(null, post, null)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() != 200) {
                    logger.warn("Token refresh failed: {}", responseBody);
                    return new OAuthResult("Token refresh failed (session may have expired)");
                }
                
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                
                String accessToken = json.get("access_token").getAsString();
                String instanceUrl = json.get("instance_url").getAsString();
                
                logger.info("✅ Successfully refreshed access token silently");
                
                // Return result with the SAME refresh token (it's still valid)
                return new OAuthResult(accessToken, instanceUrl, refreshToken);
            }
            
        } catch (Exception e) {
            logger.error("Error refreshing token", e);
            return new OAuthResult("Token refresh error: " + e.getMessage());
        }
    }
    
    /**
     * Enhanced authentication that tries silent refresh first (if a refresh token exists),
     * then falls back to browser-based OAuth if needed.
     * 
     * @param loginUrl The Salesforce login URL
     * @param trySilentFirst Whether to attempt silent refresh before opening browser
     * @return OAuthResult
     */
    public OAuthResult authenticateWithSilentRefresh(String loginUrl, boolean trySilentFirst) throws Exception {
        if (trySilentFirst) {
            // Check for stored refresh token
            TokenStorage.StoredToken stored = TokenStorage.getInstance().getToken(loginUrl, null);
            
            if (stored != null && stored.refreshToken != null) {
                // Check token age (90 days = Salesforce default expiry)
                long ageMs = System.currentTimeMillis() - stored.storedAt;
                long ageDays = TimeUnit.MILLISECONDS.toDays(ageMs);
                
                if (ageDays < 90) {
                    logger.info("Found stored refresh token (age: {} days), attempting silent refresh...", ageDays);
                    
                    OAuthResult result = refreshAccessToken(loginUrl, stored.refreshToken);
                    if (result.isSuccess()) {
                        logger.info("✅ Silent authentication successful!");
                        return result;
                    } else {
                        logger.warn("Silent refresh failed, will open browser: {}", result.error);
                        // Remove invalid token
                        TokenStorage.getInstance().removeToken(loginUrl, stored.username);
                    }
                } else {
                    logger.info("Stored token is too old ({} days), will request new login", ageDays);
                    TokenStorage.getInstance().removeToken(loginUrl, stored.username);
                }
            } else {
                logger.info("No stored refresh token found, will open browser");
            }
        }
        
        // Fall back to normal browser-based authentication
        return authenticate(loginUrl);
    }
}
