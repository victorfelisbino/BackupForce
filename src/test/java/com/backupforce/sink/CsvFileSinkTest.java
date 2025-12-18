package com.backupforce.sink;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvFileSink.
 * Tests CSV file output, directory creation, and connection handling.
 */
@DisplayName("CsvFileSink Tests")
class CsvFileSinkTest {
    
    @TempDir
    Path tempDir;
    
    private CsvFileSink sink;
    
    @BeforeEach
    void setUp() {
        sink = new CsvFileSink(tempDir.toString());
    }
    
    @Test
    @DisplayName("Constructor sets output directory")
    void testConstructor() {
        CsvFileSink csvSink = new CsvFileSink("/custom/path");
        assertTrue(csvSink.getDisplayName().contains("/custom/path"));
    }
    
    @Test
    @DisplayName("getType returns CSV")
    void testGetType() {
        assertEquals("CSV", sink.getType());
    }
    
    @Test
    @DisplayName("getDisplayName includes output path")
    void testGetDisplayName() {
        String displayName = sink.getDisplayName();
        assertTrue(displayName.startsWith("CSV Files ("));
        assertTrue(displayName.contains(tempDir.toString()));
    }
    
    @Test
    @DisplayName("connect creates output directory if not exists")
    void testConnectCreatesDirectory() throws Exception {
        Path newDir = tempDir.resolve("new_subdir");
        CsvFileSink newSink = new CsvFileSink(newDir.toString());
        
        assertFalse(Files.exists(newDir));
        
        newSink.connect();
        
        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));
    }
    
    @Test
    @DisplayName("connect succeeds if directory already exists")
    void testConnectExistingDirectory() throws Exception {
        assertDoesNotThrow(() -> sink.connect());
    }
    
    @Test
    @DisplayName("disconnect does nothing (no-op)")
    void testDisconnect() {
        assertDoesNotThrow(() -> sink.disconnect());
    }
    
    @Test
    @DisplayName("testConnection returns true for writable directory")
    void testTestConnectionSuccess() {
        assertTrue(sink.testConnection());
    }
    
    @Test
    @DisplayName("testConnection creates directory if not exists")
    void testTestConnectionCreatesDirectory() {
        Path newPath = tempDir.resolve("test_dir");
        CsvFileSink newSink = new CsvFileSink(newPath.toString());
        
        assertTrue(newSink.testConnection());
        assertTrue(Files.exists(newPath));
    }
    
    @Test
    @DisplayName("prepareSink does nothing (no-op for CSV)")
    void testPrepareSink() {
        assertDoesNotThrow(() -> sink.prepareSink("Account", null));
    }
    
    @Test
    @DisplayName("writeData writes CSV content to file")
    void testWriteDataBasic() throws Exception {
        String csvContent = "Id,Name,CreatedDate\n001ABC,Test Account,2024-01-15\n002DEF,Another Account,2024-01-16\n";
        Reader reader = new StringReader(csvContent);
        
        int recordCount = sink.writeData("Account", reader, "backup-123", null);
        
        Path outputFile = tempDir.resolve("Account.csv");
        assertTrue(Files.exists(outputFile));
        
        String fileContent = Files.readString(outputFile);
        assertTrue(fileContent.contains("Id,Name,CreatedDate"));
        assertTrue(fileContent.contains("Test Account"));
    }
    
    @Test
    @DisplayName("writeData with progress callback")
    void testWriteDataWithProgressCallback() throws Exception {
        String csvContent = "Id,Name\n001,Test\n";
        Reader reader = new StringReader(csvContent);
        
        final StringBuilder progressMessages = new StringBuilder();
        DataSink.ProgressCallback callback = msg -> progressMessages.append(msg).append("|");
        
        sink.writeData("Contact", reader, "backup-456", callback);
        
        String messages = progressMessages.toString();
        assertTrue(messages.contains("Writing to file"));
    }
    
    @Test
    @DisplayName("writeData creates file with object name")
    void testWriteDataCreatesCorrectFileName() throws Exception {
        String csvContent = "Id\n001\n";
        
        sink.writeData("CustomObject__c", new StringReader(csvContent), "backup-789", null);
        
        Path outputFile = tempDir.resolve("CustomObject__c.csv");
        assertTrue(Files.exists(outputFile), "File should be named after the object");
    }
    
    @Test
    @DisplayName("writeData handles empty CSV")
    void testWriteDataEmptyCsv() throws Exception {
        String csvContent = "Id,Name\n";  // Header only
        
        int recordCount = sink.writeData("EmptyObject", new StringReader(csvContent), "backup-000", null);
        
        Path outputFile = tempDir.resolve("EmptyObject.csv");
        assertTrue(Files.exists(outputFile));
    }
    
    @Test
    @DisplayName("Multiple objects create multiple files")
    void testMultipleObjects() throws Exception {
        sink.writeData("Account", new StringReader("Id\n001\n"), "backup-1", null);
        sink.writeData("Contact", new StringReader("Id\n002\n"), "backup-1", null);
        sink.writeData("Opportunity", new StringReader("Id\n003\n"), "backup-1", null);
        
        assertTrue(Files.exists(tempDir.resolve("Account.csv")));
        assertTrue(Files.exists(tempDir.resolve("Contact.csv")));
        assertTrue(Files.exists(tempDir.resolve("Opportunity.csv")));
    }
}
