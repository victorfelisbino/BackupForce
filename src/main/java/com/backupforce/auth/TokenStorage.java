package com.backupforce.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Securely stores OAuth refresh tokens for silent re-authentication.
 * Tokens are stored per login URL (production/sandbox) and username.
 */
public class TokenStorage {
    private static final Logger logger = LoggerFactory.getLogger(TokenStorage.class);
    private static final String TOKENS_FILENAME = "oauth_tokens.json";
    
    private static TokenStorage instance;
    
    private final Path configDir;
    private final Path tokensFile;
    private final Gson gson;
    private Map<String, StoredToken> tokens;
    
    public static synchronized TokenStorage getInstance() {
        if (instance == null) {
            instance = new TokenStorage();
        }
        return instance;
    }
    
    private TokenStorage() {
        this.configDir = Paths.get(System.getProperty("user.home"), ".backupforce");
        this.tokensFile = configDir.resolve(TOKENS_FILENAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.tokens = new HashMap<>();
        
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
        
        loadTokens();
    }
    
    /**
     * Store a refresh token for later silent authentication.
     * @param loginUrl The Salesforce login URL (production or sandbox)
     * @param username The Salesforce username
     * @param refreshToken The OAuth refresh token
     * @param instanceUrl The Salesforce instance URL
     */
    public void storeToken(String loginUrl, String username, String refreshToken, String instanceUrl) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            logger.debug("No refresh token to store for {}", username);
            return;
        }
        
        String key = createKey(loginUrl, username);
        StoredToken token = new StoredToken();
        token.refreshToken = refreshToken;
        token.instanceUrl = instanceUrl;
        token.loginUrl = loginUrl;
        token.username = username;
        token.storedAt = System.currentTimeMillis();
        
        tokens.put(key, token);
        saveTokens();
        
        logger.info("Stored refresh token for {} ({})", username, loginUrl.contains("test") ? "sandbox" : "production");
    }
    
    /**
     * Get a stored refresh token.
     * @param loginUrl The Salesforce login URL
     * @param username The username (optional - if null, returns any token for this loginUrl)
     * @return The stored token or null if not found
     */
    public StoredToken getToken(String loginUrl, String username) {
        if (username != null) {
            String key = createKey(loginUrl, username);
            return tokens.get(key);
        }
        
        // Find any token for this login URL
        for (Map.Entry<String, StoredToken> entry : tokens.entrySet()) {
            if (entry.getKey().startsWith(loginUrl + "|")) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Get all stored tokens for a login URL (production or sandbox).
     */
    public Map<String, StoredToken> getTokensForLoginUrl(String loginUrl) {
        Map<String, StoredToken> result = new HashMap<>();
        for (Map.Entry<String, StoredToken> entry : tokens.entrySet()) {
            if (entry.getValue().loginUrl.equals(loginUrl)) {
                result.put(entry.getValue().username, entry.getValue());
            }
        }
        return result;
    }
    
    /**
     * Remove a stored token (e.g., when logout is requested or token is revoked).
     */
    public void removeToken(String loginUrl, String username) {
        String key = createKey(loginUrl, username);
        if (tokens.remove(key) != null) {
            saveTokens();
            logger.info("Removed stored token for {}", username);
        }
    }
    
    /**
     * Clear all stored tokens.
     */
    public void clearAll() {
        tokens.clear();
        saveTokens();
        logger.info("Cleared all stored OAuth tokens");
    }
    
    private String createKey(String loginUrl, String username) {
        return loginUrl + "|" + (username != null ? username.toLowerCase() : "unknown");
    }
    
    private void loadTokens() {
        if (!Files.exists(tokensFile)) {
            return;
        }
        
        try {
            String json = Files.readString(tokensFile);
            Type type = new TypeToken<Map<String, StoredToken>>(){}.getType();
            Map<String, StoredToken> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                tokens = loaded;
                logger.info("Loaded {} stored OAuth tokens", tokens.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load OAuth tokens", e);
            tokens = new HashMap<>();
        }
    }
    
    private void saveTokens() {
        try {
            String json = gson.toJson(tokens);
            Files.writeString(tokensFile, json);
        } catch (IOException e) {
            logger.error("Failed to save OAuth tokens", e);
        }
    }
    
    /**
     * Stored OAuth token data.
     */
    public static class StoredToken {
        public String refreshToken;
        public String instanceUrl;
        public String loginUrl;
        public String username;
        public long storedAt;
        
        public boolean isExpired() {
            // Refresh tokens don't expire in Salesforce unless revoked
            // But we could add a staleness check (e.g., 90 days)
            long ninetyDays = 90L * 24 * 60 * 60 * 1000;
            return System.currentTimeMillis() - storedAt > ninetyDays;
        }
    }
}
