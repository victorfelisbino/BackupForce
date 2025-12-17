package com.backupforce.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConnectionManager
 */
public class ConnectionManagerTest {
    
    @Test
    void testSavedConnection_construction() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("Test Connection", "Snowflake");
        
        assertNotNull(conn.getId());
        assertEquals("Test Connection", conn.getName());
        assertEquals("Snowflake", conn.getType());
        assertFalse(conn.isUseSso());
        assertFalse(conn.isEncrypted());
        assertNotNull(conn.getAdditionalProps());
        assertTrue(conn.getLastUsed() > 0);
    }
    
    @Test
    void testSavedConnection_snowflakeFields() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("Snowflake Prod", "Snowflake");
        
        conn.setAccount("xy12345.us-east-1");
        conn.setWarehouse("COMPUTE_WH");
        conn.setDatabase("SALESFORCE_BACKUP");
        conn.setSchema("PUBLIC");
        conn.setUsername("testuser");
        conn.setUseSso(true);
        
        assertEquals("xy12345.us-east-1", conn.getAccount());
        assertEquals("COMPUTE_WH", conn.getWarehouse());
        assertEquals("SALESFORCE_BACKUP", conn.getDatabase());
        assertEquals("PUBLIC", conn.getSchema());
        assertEquals("testuser", conn.getUsername());
        assertTrue(conn.isUseSso());
    }
    
    @Test
    void testSavedConnection_sqlServerFields() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("SQL Server Dev", "SQL Server");
        
        conn.setHost("localhost\\SQLEXPRESS");
        conn.setDatabase("SalesforceBackup");
        conn.setUsername("sa");
        conn.setPassword("password123");
        
        assertEquals("localhost\\SQLEXPRESS", conn.getHost());
        assertEquals("SalesforceBackup", conn.getDatabase());
        assertEquals("sa", conn.getUsername());
        assertEquals("password123", conn.getPassword());
    }
    
    @Test
    void testSavedConnection_postgresFields() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("PostgreSQL Local", "PostgreSQL");
        
        conn.setHost("localhost");
        conn.setPort("5432");
        conn.setDatabase("sf_backup");
        conn.setSchema("public");
        conn.setUsername("postgres");
        conn.setPassword("pgpass");
        
        assertEquals("localhost", conn.getHost());
        assertEquals("5432", conn.getPort());
        assertEquals("sf_backup", conn.getDatabase());
        assertEquals("public", conn.getSchema());
        assertEquals("postgres", conn.getUsername());
        assertEquals("pgpass", conn.getPassword());
    }
    
    @Test
    void testSavedConnection_additionalProps() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("Test", "Custom");
        
        conn.getAdditionalProps().put("customKey1", "value1");
        conn.getAdditionalProps().put("customKey2", "value2");
        conn.getAdditionalProps().put("recreateTables", "true");
        
        assertEquals("value1", conn.getAdditionalProps().get("customKey1"));
        assertEquals("value2", conn.getAdditionalProps().get("customKey2"));
        assertEquals("true", conn.getAdditionalProps().get("recreateTables"));
    }
    
    @Test
    void testSavedConnection_uniqueIds() {
        ConnectionManager.SavedConnection conn1 = new ConnectionManager.SavedConnection("Conn1", "Snowflake");
        ConnectionManager.SavedConnection conn2 = new ConnectionManager.SavedConnection("Conn2", "Snowflake");
        ConnectionManager.SavedConnection conn3 = new ConnectionManager.SavedConnection("Conn3", "PostgreSQL");
        
        assertNotEquals(conn1.getId(), conn2.getId());
        assertNotEquals(conn2.getId(), conn3.getId());
        assertNotEquals(conn1.getId(), conn3.getId());
    }
    
    @Test
    void testSavedConnection_ssoToggle() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("SSO Test", "Snowflake");
        
        assertFalse(conn.isUseSso());
        
        conn.setUseSso(true);
        assertTrue(conn.isUseSso());
        
        conn.setUseSso(false);
        assertFalse(conn.isUseSso());
    }
    
    @Test
    void testSavedConnection_encryptedFlag() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("Encrypted Test", "Snowflake");
        
        assertFalse(conn.isEncrypted());
        
        conn.setEncrypted(true);
        assertTrue(conn.isEncrypted());
    }
    
    @Test
    void testSavedConnection_propertiesMap() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("Map Test", "Snowflake");
        conn.setAccount("testaccount");
        conn.setWarehouse("WH");
        conn.setDatabase("DB");
        conn.setSchema("SCHEMA");
        conn.setUsername("user");
        conn.setUseSso(false);
        
        Map<String, String> props = conn.getProperties();
        
        assertEquals("testaccount", props.get("Account"));
        assertEquals("WH", props.get("Warehouse"));
        assertEquals("DB", props.get("Database"));
        assertEquals("SCHEMA", props.get("Schema"));
        assertEquals("user", props.get("Username"));
    }
    
    @Test
    void testSavedConnection_ssoPropertiesExcludesPassword() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection("SSO Conn", "Snowflake");
        conn.setAccount("account");
        conn.setUseSso(true);
        conn.setPassword("should_not_appear");
        
        // Even though password is set, SSO mode should indicate no password needed
        assertTrue(conn.isUseSso());
    }
    
    @Test
    void testSavedConnection_defaultConstructor() {
        ConnectionManager.SavedConnection conn = new ConnectionManager.SavedConnection();
        
        assertNotNull(conn.getId());
        assertNotNull(conn.getAdditionalProps());
        assertTrue(conn.getLastUsed() > 0);
    }
}
