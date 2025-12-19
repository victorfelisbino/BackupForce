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
    
    // ==================== ApiLimits Tests ====================
    
    @Test
    void testApiLimits_getDailyApiPercentUsed() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.dailyApiRequestsUsed = 5000;
        limits.dailyApiRequestsMax = 100000;
        
        assertEquals(5.0, limits.getDailyApiPercentUsed(), 0.01);
    }
    
    @Test
    void testApiLimits_getDailyApiPercentUsed_ZeroMax() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.dailyApiRequestsUsed = 5000;
        limits.dailyApiRequestsMax = 0;
        
        assertEquals(0.0, limits.getDailyApiPercentUsed(), 0.01);
    }
    
    @Test
    void testApiLimits_getBulkApiPercentUsed() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.bulkApiJobsUsed = 50;
        limits.bulkApiJobsMax = 1000;
        
        assertEquals(5.0, limits.getBulkApiPercentUsed(), 0.01);
    }
    
    @Test
    void testApiLimits_getBulkApiPercentUsed_ZeroMax() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.bulkApiJobsUsed = 50;
        limits.bulkApiJobsMax = 0;
        
        assertEquals(0.0, limits.getBulkApiPercentUsed(), 0.01);
    }
    
    @Test
    void testApiLimits_getFormattedDailyApi() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.dailyApiRequestsUsed = 5000;
        limits.dailyApiRequestsMax = 100000;
        
        String formatted = limits.getFormattedDailyApi();
        
        assertTrue(formatted.contains("5,000"));
        assertTrue(formatted.contains("100,000"));
        assertTrue(formatted.contains("5.0%"));
    }
    
    @Test
    void testApiLimits_getFormattedBulkApi() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.bulkApiJobsUsed = 50;
        limits.bulkApiJobsMax = 1000;
        
        String formatted = limits.getFormattedBulkApi();
        
        assertTrue(formatted.contains("50"));
        assertTrue(formatted.contains("1,000"));
        assertTrue(formatted.contains("jobs"));
    }
    
    @Test
    void testApiLimits_AllFields() {
        BulkV2Client.ApiLimits limits = new BulkV2Client.ApiLimits();
        limits.dailyApiRequestsUsed = 10000;
        limits.dailyApiRequestsMax = 1000000;
        limits.bulkApiJobsUsed = 100;
        limits.bulkApiJobsMax = 5000;
        limits.bulkApiStorageUsedMB = 500;
        limits.bulkApiStorageMaxMB = 10000;
        
        assertEquals(10000, limits.dailyApiRequestsUsed);
        assertEquals(1000000, limits.dailyApiRequestsMax);
        assertEquals(100, limits.bulkApiJobsUsed);
        assertEquals(5000, limits.bulkApiJobsMax);
        assertEquals(500, limits.bulkApiStorageUsedMB);
        assertEquals(10000, limits.bulkApiStorageMaxMB);
    }
    
    @Test
    void testGetJsonString() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod(
            "getJsonString", com.google.gson.JsonObject.class, String.class, String.class);
        method.setAccessible(true);
        
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("name", "Test");
        obj.add("nullField", com.google.gson.JsonNull.INSTANCE);
        
        // Test existing field
        String name = (String) method.invoke(client, obj, "name", "default");
        assertEquals("Test", name);
        
        // Test missing field
        String missing = (String) method.invoke(client, obj, "nonexistent", "default");
        assertEquals("default", missing);
        
        // Test null field
        String nullValue = (String) method.invoke(client, obj, "nullField", "default");
        assertEquals("default", nullValue);
        
        client.close();
    }
    
    @Test
    void testClientClose() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        // Should not throw
        assertDoesNotThrow(() -> client.close());
    }
    
    @Test
    void testBuildBlobFileName_Document() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod(
            "buildBlobFileName", String.class, com.google.gson.JsonObject.class);
        method.setAccessible(true);
        
        com.google.gson.JsonObject record = new com.google.gson.JsonObject();
        record.addProperty("Id", "015ABCDEFGHIJKLMNO");
        record.addProperty("Name", "Logo");
        record.addProperty("Type", "png");
        record.addProperty("ContentType", "image/png");
        
        String fileName = (String) method.invoke(client, "Document", record);
        
        assertTrue(fileName.contains("015ABCDEFGHIJKLMNO"));
        assertTrue(fileName.endsWith(".png"));
        
        client.close();
    }
    
    @Test
    void testBuildBlobFileName_StaticResource() throws Exception {
        BulkV2Client client = new BulkV2Client("https://test.salesforce.com", "faketoken", "59.0");
        
        java.lang.reflect.Method method = BulkV2Client.class.getDeclaredMethod(
            "buildBlobFileName", String.class, com.google.gson.JsonObject.class);
        method.setAccessible(true);
        
        com.google.gson.JsonObject record = new com.google.gson.JsonObject();
        record.addProperty("Id", "081ABCDEFGHIJKLMNO");
        record.addProperty("Name", "StyleBundle");
        record.addProperty("ContentType", "application/zip");
        
        String fileName = (String) method.invoke(client, "StaticResource", record);
        
        assertTrue(fileName.contains("081ABCDEFGHIJKLMNO"));
        assertTrue(fileName.contains("StyleBundle"));
        
        client.close();
    }
}
