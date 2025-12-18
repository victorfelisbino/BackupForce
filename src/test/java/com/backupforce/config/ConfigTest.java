package com.backupforce.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Config class.
 * Tests configuration file loading and property retrieval.
 */
@DisplayName("Config Tests")
class ConfigTest {
    
    @TempDir
    Path tempDir;
    
    private Path createConfigFile(String content) throws IOException {
        Path configFile = tempDir.resolve("test-config.properties");
        Files.writeString(configFile, content);
        return configFile;
    }
    
    @Test
    @DisplayName("Config loads properties from file")
    void testLoadProperties() throws IOException {
        String content = "sf.username=testuser@example.com\n" +
                "sf.password=testpassword\n" +
                "sf.serverurl=https://login.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("testuser@example.com", config.getUsername());
        assertEquals("testpassword", config.getPassword());
        assertEquals("https://login.salesforce.com", config.getServerUrl());
    }
    
    @Test
    @DisplayName("Config throws for missing config file")
    void testMissingConfigFile() {
        assertThrows(IOException.class, () -> 
            new Config("/nonexistent/path/config.properties"));
    }
    
    @Test
    @DisplayName("Config throws for missing required property")
    void testMissingRequiredProperty() throws IOException {
        String content = "sf.password=testpassword\n" +
                "sf.serverurl=https://login.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertThrows(RuntimeException.class, () -> config.getUsername());
    }
    
    @Test
    @DisplayName("Config throws for empty required property")
    void testEmptyRequiredProperty() throws IOException {
        String content = "sf.username=\n" +
                "sf.password=testpassword\n" +
                "sf.serverurl=https://login.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertThrows(RuntimeException.class, () -> config.getUsername());
    }
    
    @Test
    @DisplayName("getOutputFolder returns default when not specified")
    void testOutputFolderDefault() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("C:/backupForce2/backup", config.getOutputFolder());
    }
    
    @Test
    @DisplayName("getOutputFolder returns custom value when specified")
    void testOutputFolderCustom() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n" +
                "outputFolder=/custom/output/path\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("/custom/output/path", config.getOutputFolder());
    }
    
    @Test
    @DisplayName("getBackupObjects returns * by default")
    void testBackupObjectsDefault() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("*", config.getBackupObjects());
    }
    
    @Test
    @DisplayName("getBackupObjects returns custom list")
    void testBackupObjectsCustom() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n" +
                "backup.objects=Account,Contact,Opportunity\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("Account,Contact,Opportunity", config.getBackupObjects());
    }
    
    @Test
    @DisplayName("getBackupObjectsExclude returns empty set by default")
    void testBackupObjectsExcludeDefault() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertTrue(config.getBackupObjectsExclude().isEmpty());
    }
    
    @Test
    @DisplayName("getBackupObjectsExclude parses comma-separated list")
    void testBackupObjectsExcludeList() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n" +
                "backup.objects.exclude=Task,Event,FeedItem\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        Set<String> excludes = config.getBackupObjectsExclude();
        
        assertEquals(3, excludes.size());
        assertTrue(excludes.contains("task"));  // lowercase
        assertTrue(excludes.contains("event"));
        assertTrue(excludes.contains("feeditem"));
    }
    
    @Test
    @DisplayName("getConnectionTimeout returns default value")
    void testConnectionTimeoutDefault() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals(60, config.getConnectionTimeout());
    }
    
    @Test
    @DisplayName("getConnectionTimeout returns custom value")
    void testConnectionTimeoutCustom() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n" +
                "http.connectionTimeoutSecs=120\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals(120, config.getConnectionTimeout());
    }
    
    @Test
    @DisplayName("getReadTimeout returns default value")
    void testReadTimeoutDefault() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals(540, config.getReadTimeout());
    }
    
    @Test
    @DisplayName("getReadTimeout returns custom value")
    void testReadTimeoutCustom() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n" +
                "http.readTimeoutSecs=300\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals(300, config.getReadTimeout());
    }
    
    @Test
    @DisplayName("getApiVersion returns current version")
    void testApiVersion() throws IOException {
        String content = "sf.username=user\n" +
                "sf.password=pass\n" +
                "sf.serverurl=https://test.salesforce.com\n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("62.0", config.getApiVersion());
    }
    
    @Test
    @DisplayName("Property values are trimmed")
    void testPropertyTrimming() throws IOException {
        String content = "sf.username=  user@test.com  \n" +
                "sf.password=  pass123  \n" +
                "sf.serverurl=  https://test.salesforce.com  \n";
        Path configFile = createConfigFile(content);
        
        Config config = new Config(configFile.toString());
        
        assertEquals("user@test.com", config.getUsername());
        assertEquals("pass123", config.getPassword());
        assertEquals("https://test.salesforce.com", config.getServerUrl());
    }
}
