package com.backupforce.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * Manages saved database connections with encryption and session caching
 */
public class ConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    private static final String PREFS_NODE = "com.backupforce.connections";
    private static final String CONNECTIONS_FILE = "connections.json";
    private static final String KEY_ALIAS = "encryption_key";
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    
    private static ConnectionManager instance;
    private final Gson gson;
    private final Path configDir;
    private List<SavedConnection> connections;
    private String lastUsedConnectionId;
    
    // Session cache for database connections (keyed by connection ID)
    private final Map<String, CachedSession> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * Cached database session with timestamp
     */
    public static class CachedSession {
        private final Connection connection;
        private final long createdAt;
        private final String connectionId;
        
        public CachedSession(String connectionId, Connection connection) {
            this.connectionId = connectionId;
            this.connection = connection;
            this.createdAt = System.currentTimeMillis();
        }
        
        public Connection getConnection() { return connection; }
        public long getCreatedAt() { return createdAt; }
        public String getConnectionId() { return connectionId; }
        
        public boolean isValid() {
            try {
                return connection != null && !connection.isClosed() && 
                       (System.currentTimeMillis() - createdAt) < SESSION_TIMEOUT_MS;
            } catch (SQLException e) {
                return false;
            }
        }
        
        public void close() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {}
            }
        }
    }
    
    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }
    
    private ConnectionManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Get config directory in user home
        String userHome = System.getProperty("user.home");
        this.configDir = Paths.get(userHome, ".backupforce");
        
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
        
        loadConnections();
        loadLastUsed();
    }
    
    public List<SavedConnection> getConnections() {
        return new ArrayList<>(connections);
    }
    
    public SavedConnection getConnection(String id) {
        return connections.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    public SavedConnection getLastUsedConnection() {
        if (lastUsedConnectionId != null) {
            return getConnection(lastUsedConnectionId);
        }
        return connections.isEmpty() ? null : connections.get(0);
    }
    
    public void saveConnection(SavedConnection connection) {
        // Encrypt password
        if (connection.getPassword() != null && !connection.getPassword().isEmpty()) {
            connection.setPassword(encrypt(connection.getPassword()));
            connection.setEncrypted(true);
        }
        
        // Remove existing connection with same ID
        connections.removeIf(c -> c.getId().equals(connection.getId()));
        
        // Add new connection
        connections.add(connection);
        
        // Save to file
        saveConnections();
    }
    
    /**
     * Create and save a new connection from name, type, and properties map
     */
    public SavedConnection saveConnection(String name, String type, Map<String, String> props) {
        SavedConnection conn = new SavedConnection(name, type);
        applyPropertiesToConnection(conn, props);
        saveConnection(conn);
        return conn;
    }
    
    /**
     * Update an existing connection
     */
    public void updateConnection(String id, String name, String type, Map<String, String> props) {
        SavedConnection existing = getConnection(id);
        if (existing != null) {
            existing.setName(name);
            existing.setType(type);
            existing.setEncrypted(false); // Reset encryption flag so password gets re-encrypted
            applyPropertiesToConnection(existing, props);
            saveConnection(existing);
        }
    }
    
    private void applyPropertiesToConnection(SavedConnection conn, Map<String, String> props) {
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            switch (key) {
                case "Account": conn.setAccount(value); break;
                case "Warehouse": conn.setWarehouse(value); break;
                case "Database": conn.setDatabase(value); break;
                case "Schema": conn.setSchema(value); break;
                case "Username": conn.setUsername(value); break;
                case "Password": conn.setPassword(value); break;
                case "Host": conn.setHost(value); break;
                case "Port": conn.setPort(value); break;
                case "Server": conn.setHost(value); break; // SQL Server uses "Server" but stores as host
                case "useSSO": conn.setUseSso(Boolean.parseBoolean(value)); break;
                default: conn.getAdditionalProps().put(key, value); break;
            }
        }
    }
    
    public void deleteConnection(String id) {
        connections.removeIf(c -> c.getId().equals(id));
        saveConnections();
        
        if (id.equals(lastUsedConnectionId)) {
            lastUsedConnectionId = null;
            saveLastUsed();
        }
    }
    
    public void setLastUsedConnection(String id) {
        this.lastUsedConnectionId = id;
        saveLastUsed();
    }
    
    public String getDecryptedPassword(SavedConnection connection) {
        if (connection.isEncrypted() && connection.getPassword() != null) {
            return decrypt(connection.getPassword());
        }
        return connection.getPassword();
    }
    
    // ============================================
    // Session Cache Management
    // ============================================
    
    /**
     * Get a cached session for a connection, or null if no valid session exists
     */
    public CachedSession getCachedSession(String connectionId) {
        CachedSession session = sessionCache.get(connectionId);
        if (session != null && session.isValid()) {
            logger.debug("Using cached session for connection: {}", connectionId);
            return session;
        }
        // Remove invalid session
        if (session != null) {
            logger.debug("Cached session expired or invalid for connection: {}", connectionId);
            session.close();
            sessionCache.remove(connectionId);
        }
        return null;
    }
    
    /**
     * Cache a database session for a connection
     */
    public void cacheSession(String connectionId, Connection connection) {
        // Close any existing session first
        CachedSession existing = sessionCache.get(connectionId);
        if (existing != null) {
            existing.close();
        }
        
        CachedSession session = new CachedSession(connectionId, connection);
        sessionCache.put(connectionId, session);
        logger.info("Cached database session for connection: {}", connectionId);
    }
    
    /**
     * Check if a valid cached session exists for a connection
     */
    public boolean hasCachedSession(String connectionId) {
        CachedSession session = sessionCache.get(connectionId);
        return session != null && session.isValid();
    }
    
    /**
     * Invalidate and close a cached session
     */
    public void invalidateSession(String connectionId) {
        CachedSession session = sessionCache.remove(connectionId);
        if (session != null) {
            session.close();
            logger.info("Invalidated cached session for connection: {}", connectionId);
        }
    }
    
    /**
     * Close all cached sessions (call on application shutdown)
     */
    public void closeAllSessions() {
        for (CachedSession session : sessionCache.values()) {
            session.close();
        }
        sessionCache.clear();
        logger.info("Closed all cached database sessions");
    }
    
    private void loadConnections() {
        connections = new ArrayList<>();
        
        Path connectionsFile = configDir.resolve(CONNECTIONS_FILE);
        if (!Files.exists(connectionsFile)) {
            return;
        }
        
        try {
            String json = Files.readString(connectionsFile);
            Type listType = new TypeToken<List<SavedConnection>>(){}.getType();
            List<SavedConnection> loaded = gson.fromJson(json, listType);
            if (loaded != null) {
                connections = loaded;
            }
        } catch (Exception e) {
            logger.error("Failed to load connections", e);
        }
    }
    
    private void saveConnections() {
        try {
            String json = gson.toJson(connections);
            Path connectionsFile = configDir.resolve(CONNECTIONS_FILE);
            Files.writeString(connectionsFile, json);
        } catch (Exception e) {
            logger.error("Failed to save connections", e);
        }
    }
    
    private void loadLastUsed() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        lastUsedConnectionId = prefs.get("lastUsedConnectionId", null);
    }
    
    private void saveLastUsed() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        if (lastUsedConnectionId != null) {
            prefs.put("lastUsedConnectionId", lastUsedConnectionId);
        } else {
            prefs.remove("lastUsedConnectionId");
        }
    }
    
    private String encrypt(String plainText) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            return plainText; // Fallback to plain text
        }
    }
    
    private String decrypt(String encryptedText) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            return encryptedText; // Fallback to encrypted text
        }
    }
    
    private SecretKey getOrCreateKey() throws Exception {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        String encodedKey = prefs.get(KEY_ALIAS, null);
        
        if (encodedKey == null) {
            // Generate new key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            SecretKey key = keyGen.generateKey();
            
            // Save key
            encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            prefs.put(KEY_ALIAS, encodedKey);
            
            return key;
        } else {
            // Load existing key
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
        }
    }
    
    public static class SavedConnection {
        private String id;
        private String name;
        private String type; // "Snowflake", "PostgreSQL", etc.
        private String account;
        private String warehouse;
        private String database;
        private String schema;
        private String username;
        private String password;
        private String host;
        private String port;
        private boolean useSso;
        private boolean encrypted;
        private long lastUsed;
        private Map<String, String> additionalProps;
        
        public SavedConnection() {
            this.id = UUID.randomUUID().toString();
            this.lastUsed = System.currentTimeMillis();
            this.additionalProps = new HashMap<>();
        }
        
        public SavedConnection(String name, String type) {
            this();
            this.name = name;
            this.type = type;
        }
        
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder(name);
            if (type != null) {
                sb.append(" (").append(type).append(")");
            }
            if (database != null && !database.isEmpty()) {
                sb.append(" - ").append(database);
            }
            if (schema != null && !schema.isEmpty()) {
                sb.append(".").append(schema);
            }
            return sb.toString();
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getAccount() { return account; }
        public void setAccount(String account) { this.account = account; }
        
        public String getWarehouse() { return warehouse; }
        public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
        
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        
        public boolean isUseSso() { return useSso; }
        public void setUseSso(boolean useSso) { this.useSso = useSso; }
        
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        
        public long getLastUsed() { return lastUsed; }
        public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }
        
        public Map<String, String> getAdditionalProps() { return additionalProps; }
        public void setAdditionalProps(Map<String, String> additionalProps) { 
            this.additionalProps = additionalProps; 
        }
        
        /**
         * Get all properties as a map (for form population)
         */
        public Map<String, String> getProperties() {
            Map<String, String> props = new HashMap<>();
            if (account != null) props.put("Account", account);
            if (warehouse != null) props.put("Warehouse", warehouse);
            if (database != null) props.put("Database", database);
            if (schema != null) props.put("Schema", schema);
            if (username != null) props.put("Username", username);
            if (password != null) props.put("Password", password);
            if (host != null) {
                if ("SQL Server".equals(type)) {
                    props.put("Server", host);
                } else {
                    props.put("Host", host);
                }
            }
            if (port != null) props.put("Port", port);
            props.putAll(additionalProps);
            return props;
        }
        
        @Override
        public String toString() {
            return getDisplayName();
        }
    }
}
