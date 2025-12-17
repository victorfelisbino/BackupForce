package com.backupforce.sink;

import com.backupforce.sink.dialect.SnowflakeDialect;
import com.backupforce.sink.dialect.PostgresDialect;
import com.backupforce.sink.dialect.SqlServerDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JdbcDatabaseSink blob handling functionality
 */
public class JdbcDatabaseSinkBlobTest {
    
    @TempDir
    Path tempDir;
    
    // Test dialect implementations
    
    @Test
    void testSnowflakeDialect_getBinaryType() {
        SnowflakeDialect dialect = new SnowflakeDialect();
        String binaryType = dialect.getBinaryType();
        
        assertEquals("BINARY(8388608)", binaryType);
    }
    
    @Test
    void testPostgresDialect_getBinaryType() {
        PostgresDialect dialect = new PostgresDialect();
        String binaryType = dialect.getBinaryType();
        
        assertEquals("BYTEA", binaryType);
    }
    
    @Test
    void testSqlServerDialect_getBinaryType() {
        SqlServerDialect dialect = new SqlServerDialect();
        String binaryType = dialect.getBinaryType();
        
        assertEquals("VARBINARY(MAX)", binaryType);
    }
    
    @Test
    void testSnowflakeDialect_mapBase64Type() {
        SnowflakeDialect dialect = new SnowflakeDialect();
        String base64Type = dialect.mapSalesforceType("BASE64", 0);
        
        assertEquals("BINARY", base64Type);
    }
    
    @Test
    void testPostgresDialect_mapBase64Type() {
        PostgresDialect dialect = new PostgresDialect();
        String base64Type = dialect.mapSalesforceType("BASE64", 0);
        
        assertEquals("BYTEA", base64Type);
    }
    
    @Test
    void testSqlServerDialect_mapBase64Type() {
        SqlServerDialect dialect = new SqlServerDialect();
        String base64Type = dialect.mapSalesforceType("BASE64", 0);
        
        assertEquals("VARBINARY(MAX)", base64Type);
    }
    
    @Test
    void testSnowflakeDialect_sanitizeTableName() {
        SnowflakeDialect dialect = new SnowflakeDialect();
        
        assertEquals("CONTENTVERSION", dialect.sanitizeTableName("ContentVersion"));
        assertEquals("ATTACHMENT", dialect.sanitizeTableName("Attachment"));
        // Custom objects starting with __ get the underscores preserved but uppercased
        assertEquals("__CUSTOMOBJECT__C", dialect.sanitizeTableName("__CustomObject__c"));
    }
    
    @Test
    void testSnowflakeDialect_sanitizeColumnName() {
        SnowflakeDialect dialect = new SnowflakeDialect();
        
        assertEquals("BLOB_FILE_PATH", dialect.sanitizeColumnName("BLOB_FILE_PATH"));
        assertEquals("BLOB_DATA", dialect.sanitizeColumnName("BLOB_DATA"));
        assertEquals("ID", dialect.sanitizeColumnName("Id"));
    }
    
    @Test
    void testLoadBlobAsBytes() throws Exception {
        // Create a temp blob file
        Path blobFile = tempDir.resolve("test_blob.bin");
        byte[] testData = "Hello, this is test blob data!".getBytes();
        Files.write(blobFile, testData);
        
        // Create a minimal JdbcDatabaseSink just to test the loadBlobAsBytes method
        // We'll use reflection since the method is private
        SnowflakeDialect dialect = new SnowflakeDialect();
        java.util.Properties props = new java.util.Properties();
        JdbcDatabaseSink sink = new JdbcDatabaseSink(
            "jdbc:snowflake://test.snowflakecomputing.com", 
            props, 
            dialect, 
            "Test Snowflake"
        );
        
        // Use reflection to access private method
        java.lang.reflect.Method method = JdbcDatabaseSink.class.getDeclaredMethod("loadBlobAsBytes", String.class);
        method.setAccessible(true);
        
        // Test with absolute path
        byte[] result = (byte[]) method.invoke(sink, blobFile.toAbsolutePath().toString());
        
        assertNotNull(result);
        assertArrayEquals(testData, result);
    }
    
    @Test
    void testLoadBlobAsBytes_FileNotFound() throws Exception {
        SnowflakeDialect dialect = new SnowflakeDialect();
        java.util.Properties props = new java.util.Properties();
        JdbcDatabaseSink sink = new JdbcDatabaseSink(
            "jdbc:snowflake://test.snowflakecomputing.com", 
            props, 
            dialect, 
            "Test Snowflake"
        );
        
        java.lang.reflect.Method method = JdbcDatabaseSink.class.getDeclaredMethod("loadBlobAsBytes", String.class);
        method.setAccessible(true);
        
        // Test with non-existent file
        byte[] result = (byte[]) method.invoke(sink, "/nonexistent/path/file.bin");
        
        assertNull(result);
    }
    
    @Test
    void testLoadBlobAsBytes_NullPath() throws Exception {
        SnowflakeDialect dialect = new SnowflakeDialect();
        java.util.Properties props = new java.util.Properties();
        JdbcDatabaseSink sink = new JdbcDatabaseSink(
            "jdbc:snowflake://test.snowflakecomputing.com", 
            props, 
            dialect, 
            "Test Snowflake"
        );
        
        java.lang.reflect.Method method = JdbcDatabaseSink.class.getDeclaredMethod("loadBlobAsBytes", String.class);
        method.setAccessible(true);
        
        // Test with null
        byte[] result = (byte[]) method.invoke(sink, (String) null);
        assertNull(result);
        
        // Test with empty string
        result = (byte[]) method.invoke(sink, "");
        assertNull(result);
        
        // Test with whitespace
        result = (byte[]) method.invoke(sink, "   ");
        assertNull(result);
    }
    
    @Test
    void testLoadBlobAsBase64() throws Exception {
        // Create a temp blob file
        Path blobFile = tempDir.resolve("test_blob.txt");
        String testContent = "Hello World";
        Files.writeString(blobFile, testContent);
        
        SnowflakeDialect dialect = new SnowflakeDialect();
        java.util.Properties props = new java.util.Properties();
        JdbcDatabaseSink sink = new JdbcDatabaseSink(
            "jdbc:snowflake://test.snowflakecomputing.com", 
            props, 
            dialect, 
            "Test Snowflake"
        );
        
        java.lang.reflect.Method method = JdbcDatabaseSink.class.getDeclaredMethod("loadBlobAsBase64", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(sink, blobFile.toAbsolutePath().toString());
        
        assertNotNull(result);
        // Decode and verify
        String decoded = new String(java.util.Base64.getDecoder().decode(result));
        assertEquals(testContent, decoded);
    }
    
    @Test
    void testCreateTableFromHeaders_WithBlobData() throws Exception {
        // This is more of an integration test - we'd need a real database connection
        // For now, we just verify the dialect provides the right types
        SnowflakeDialect dialect = new SnowflakeDialect();
        
        // Verify BLOB_DATA would use BINARY type
        String binaryType = dialect.getBinaryType();
        assertNotNull(binaryType);
        assertTrue(binaryType.contains("BINARY"));
        
        // Verify BLOB_FILE_PATH would use VARCHAR
        String varcharType = dialect.getVarcharType(4096);
        assertEquals("VARCHAR(4096)", varcharType);
    }
    
    @Test
    void testDialectsHaveConsistentInterface() {
        // Verify all dialects implement the required methods
        JdbcDatabaseSink.DatabaseDialect[] dialects = {
            new SnowflakeDialect(),
            new PostgresDialect(),
            new SqlServerDialect()
        };
        
        for (JdbcDatabaseSink.DatabaseDialect dialect : dialects) {
            assertNotNull(dialect.sanitizeTableName("TestTable"));
            assertNotNull(dialect.sanitizeColumnName("TestColumn"));
            assertNotNull(dialect.getTimestampType());
            assertNotNull(dialect.getCurrentTimestamp());
            assertNotNull(dialect.getVarcharType(100));
            assertNotNull(dialect.getBinaryType());
            assertTrue(dialect.getOptimalBatchSize() > 0);
        }
    }
}
