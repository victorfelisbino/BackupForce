package com.backupforce.bulkv2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BulkV2Client blob handling functionality
 */
public class BulkV2ClientBlobTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testGetExtensionFromContentType() throws Exception {
        // Use reflection to test private method
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        // Test via reflection
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("getExtensionFromContentType", String.class);
        method.setAccessible(true);
        
        // Test common MIME types
        assertEquals("pdf", method.invoke(client, "application/pdf"));
        assertEquals("docx", method.invoke(client, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("xlsx", method.invoke(client, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals("jpg", method.invoke(client, "image/jpeg"));
        assertEquals("png", method.invoke(client, "image/png"));
        assertEquals("txt", method.invoke(client, "text/plain"));
        assertEquals("json", method.invoke(client, "application/json"));
        assertEquals("bin", method.invoke(client, (String) null));
        assertEquals("bin", method.invoke(client, "application/octet-stream")); // Unknown should try to parse
        
        client.close();
    }
    
    @Test
    void testSanitizeFileName() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("sanitizeFileName", String.class);
        method.setAccessible(true);
        
        // Test invalid characters are replaced
        assertEquals("file_name", method.invoke(client, "file:name"));
        assertEquals("file_name", method.invoke(client, "file/name"));
        assertEquals("file_name", method.invoke(client, "file\\name"));
        assertEquals("file_name", method.invoke(client, "file*name"));
        assertEquals("file_name", method.invoke(client, "file?name"));
        assertEquals("file_name", method.invoke(client, "file\"name"));
        assertEquals("file_name", method.invoke(client, "file<name"));
        assertEquals("file_name", method.invoke(client, "file>name"));
        assertEquals("file_name", method.invoke(client, "file|name"));
        
        // Test null handling
        assertEquals("unknown", method.invoke(client, (String) null));
        
        // Test length limiting (max 100 chars)
        String longName = "a".repeat(150);
        String result = (String) method.invoke(client, longName);
        assertEquals(100, result.length());
        
        client.close();
    }
    
    @Test
    void testBuildBlobQuery_ContentVersion() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("buildBlobQuery", String.class, String.class);
        method.setAccessible(true);
        
        String query = (String) method.invoke(client, "ContentVersion", "VersionData");
        
        assertTrue(query.contains("SELECT Id"));
        assertTrue(query.contains("Title"));
        assertTrue(query.contains("FileExtension"));
        assertTrue(query.contains("ContentDocumentId"));
        assertTrue(query.contains("VersionNumber"));
        assertTrue(query.contains("FROM ContentVersion"));
        assertTrue(query.contains("IsLatest = true"));
        // Should use ContentSize > 0 instead of VersionData != null
        assertTrue(query.contains("ContentSize > 0"));
        assertFalse(query.contains("VersionData != null"));
        
        client.close();
    }
    
    @Test
    void testBuildBlobQuery_Attachment() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("buildBlobQuery", String.class, String.class);
        method.setAccessible(true);
        
        String query = (String) method.invoke(client, "Attachment", "Body");
        
        assertTrue(query.contains("SELECT Id"));
        assertTrue(query.contains("Name"));
        assertTrue(query.contains("ParentId"));
        assertTrue(query.contains("ContentType"));
        assertTrue(query.contains("BodyLength"));
        assertTrue(query.contains("FROM Attachment"));
        // Should use BodyLength > 0 instead of Body != null (Body cannot be filtered)
        assertTrue(query.contains("BodyLength > 0"));
        assertFalse(query.contains("Body != null"));
        
        client.close();
    }
    
    @Test
    void testBuildBlobQuery_Document() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("buildBlobQuery", String.class, String.class);
        method.setAccessible(true);
        
        String query = (String) method.invoke(client, "Document", "Body");
        
        assertTrue(query.contains("SELECT Id"));
        assertTrue(query.contains("Name"));
        assertTrue(query.contains("DeveloperName"));
        assertTrue(query.contains("Type"));
        assertTrue(query.contains("ContentType"));
        assertTrue(query.contains("FROM Document"));
        // Should use BodyLength > 0 instead of Body != null
        assertTrue(query.contains("BodyLength > 0"));
        assertFalse(query.contains("Body != null"));
        
        client.close();
    }
    
    @Test
    void testBuildBlobQuery_StaticResource() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("buildBlobQuery", String.class, String.class);
        method.setAccessible(true);
        
        String query = (String) method.invoke(client, "StaticResource", "Body");
        
        assertTrue(query.contains("SELECT Id"));
        assertTrue(query.contains("Name"));
        assertTrue(query.contains("ContentType"));
        assertTrue(query.contains("BodyLength"));
        assertTrue(query.contains("FROM StaticResource"));
        // Should use BodyLength > 0 instead of Body != null
        assertTrue(query.contains("BodyLength > 0"));
        assertFalse(query.contains("Body != null"));
        
        client.close();
    }
    
    @Test
    void testGetBlobDownloadUrl() throws Exception {
        BulkV2Client client = new BulkV2Client("https://myorg.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("getBlobDownloadUrl", String.class, String.class, String.class);
        method.setAccessible(true);
        
        // Test Attachment URL
        String attachmentUrl = (String) method.invoke(client, "Attachment", "00P123456789", "Body");
        assertEquals("https://myorg.salesforce.com/services/data/v59.0/sobjects/Attachment/00P123456789/Body", attachmentUrl);
        
        // Test ContentVersion URL
        String cvUrl = (String) method.invoke(client, "ContentVersion", "068123456789", "VersionData");
        assertEquals("https://myorg.salesforce.com/services/data/v59.0/sobjects/ContentVersion/068123456789/VersionData", cvUrl);
        
        // Test Document URL
        String docUrl = (String) method.invoke(client, "Document", "015123456789", "Body");
        assertEquals("https://myorg.salesforce.com/services/data/v59.0/sobjects/Document/015123456789/Body", docUrl);
        
        // Test StaticResource URL
        String srUrl = (String) method.invoke(client, "StaticResource", "081123456789", "Body");
        assertEquals("https://myorg.salesforce.com/services/data/v59.0/sobjects/StaticResource/081123456789/Body", srUrl);
        
        client.close();
    }
    
    @Test
    void testBuildBlobFileName_ContentVersion() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("buildBlobFileName", String.class, com.google.gson.JsonObject.class);
        method.setAccessible(true);
        
        // Create a mock record
        com.google.gson.JsonObject record = new com.google.gson.JsonObject();
        record.addProperty("Id", "068ABCDEFGHIJKLMNO");
        record.addProperty("Title", "SalesReport");
        record.addProperty("FileExtension", "pdf");
        record.addProperty("VersionNumber", "2");
        
        String fileName = (String) method.invoke(client, "ContentVersion", record);
        
        assertEquals("SalesReport_v2_068ABCDEFGHIJKLMNO.pdf", fileName);
        
        client.close();
    }
    
    @Test
    void testBuildBlobFileName_Attachment() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod("buildBlobFileName", String.class, com.google.gson.JsonObject.class);
        method.setAccessible(true);
        
        // Create a mock record with extension in name
        com.google.gson.JsonObject record = new com.google.gson.JsonObject();
        record.addProperty("Id", "00PABCDEFGHIJKLMNO");
        record.addProperty("Name", "Contract.docx");
        record.addProperty("ContentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        
        String fileName = (String) method.invoke(client, "Attachment", record);
        
        assertEquals("Contract_00PABCDEFGHIJKLMNO.docx", fileName);
        
        client.close();
    }
}
