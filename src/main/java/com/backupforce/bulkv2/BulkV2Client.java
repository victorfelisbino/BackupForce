package com.backupforce.bulkv2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class BulkV2Client {
    private static final Logger logger = LoggerFactory.getLogger(BulkV2Client.class);
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;

    public BulkV2Client(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
    }

    // Functional interface for progress updates
    @FunctionalInterface
    public interface ProgressCallback {
        void update(String status);
    }
    
    public void queryObject(String objectName, String outputFolder) throws IOException, InterruptedException, ParseException {
        queryObject(objectName, outputFolder, null, 0, null);
    }
    
    public void queryObject(String objectName, String outputFolder, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        queryObject(objectName, outputFolder, null, 0, progressCallback);
    }
    
    public void queryObject(String objectName, String outputFolder, String whereClause, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        queryObject(objectName, outputFolder, whereClause, 0, progressCallback);
    }
    
    /**
     * Query object with optional delta support and record limit
     * @param whereClause Optional WHERE clause for delta queries (e.g., "LastModifiedDate > 2024-01-01T00:00:00Z")
     * @param recordLimit Maximum number of records to retrieve (0 = no limit)
     */
    public void queryObject(String objectName, String outputFolder, String whereClause, int recordLimit, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        logger.info("Starting Bulk API v2 query for: {}", objectName);
        if (recordLimit > 0) {
            logger.info("{}: Record limit set to {}", objectName, recordLimit);
        }
        
        if (progressCallback != null) progressCallback.update("Creating job...");
        
        // Step 1: Create query job
        String jobId = createQueryJob(objectName, whereClause, recordLimit);
        logger.info("{}: Job created with ID: {}", objectName, jobId);
        
        if (progressCallback != null) progressCallback.update("Processing...");
        
        // Step 2: Poll for job completion
        waitForJobCompletion(jobId, objectName, progressCallback);
        
        if (progressCallback != null) progressCallback.update("Downloading...");
        
        // Step 3: Download results
        downloadResults(jobId, objectName, outputFolder);
        
        logger.info("{}: Query completed successfully", objectName);
    }

    private String createQueryJob(String objectName) throws IOException, ParseException {
        return createQueryJob(objectName, null, 0);
    }
    
    private String createQueryJob(String objectName, String whereClause) throws IOException, ParseException {
        return createQueryJob(objectName, whereClause, 0);
    }
    
    private String createQueryJob(String objectName, String whereClause, int recordLimit) throws IOException, ParseException {
        // First, get all field names for this object
        String fields = getObjectFields(objectName);
        
        String url = String.format("%s/services/data/v%s/jobs/query", instanceUrl, apiVersion);
        
        String soql = "SELECT " + fields + " FROM " + objectName;
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            soql += " WHERE " + whereClause;
        }
        if (recordLimit > 0) {
            soql += " LIMIT " + recordLimit;
        }
        
        JsonObject jobRequest = new JsonObject();
        jobRequest.addProperty("operation", "query");
        jobRequest.addProperty("query", soql);
        
        HttpPost post = new HttpPost(url);
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(jobRequest.toString(), ContentType.APPLICATION_JSON));
        
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                // Parse error details for better logging
                String errorMsg = responseBody;
                try {
                    JsonParser.parseString(responseBody);
                    // If it's JSON, use as-is, otherwise it's already a string
                } catch (Exception ignored) {
                }
                
                // Check for common unsupported object errors
                if (errorMsg.contains("not supported by the Bulk API") || 
                    errorMsg.contains("INVALIDENTITY") ||
                    errorMsg.contains("compound data not supported")) {
                    logger.debug("{}: Object not supported by Bulk API", objectName);
                } else {
                    logger.error("{}: Failed to create job: {}", objectName, errorMsg);
                }
                throw new IOException("Failed to create query job: " + errorMsg);
            }
            
            JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
            return responseJson.get("id").getAsString();
        }
    }
    
    private String getObjectFields(String objectName) throws IOException, ParseException {
        String url = String.format("%s/services/data/v%s/sobjects/%s/describe", instanceUrl, apiVersion, objectName);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (response.getCode() >= 400) {
                logger.error("Failed to describe object {}: {}", objectName, responseBody);
                throw new IOException("Failed to describe object: " + responseBody);
            }
            
            JsonArray fields = responseJson.getAsJsonArray("fields");
            StringBuilder fieldNames = new StringBuilder();
            int skippedCount = 0;
            boolean hasBlobFields = false;
            String idField = "Id";
            String blobField = null;
            
            for (int i = 0; i < fields.size(); i++) {
                JsonObject field = fields.get(i).getAsJsonObject();
                String fieldName = field.get("name").getAsString();
                String fieldType = field.has("type") ? field.get("type").getAsString() : "";
                
                // Skip compound fields that can't be queried in Bulk API
                if (fieldType.equals("address") || fieldType.equals("location")) {
                    logger.debug("{}: Skipping compound field: {}", objectName, fieldName);
                    skippedCount++;
                    continue;
                }
                
                // Track blob fields but skip them from the main query
                if (fieldType.equals("base64")) {
                    logger.debug("{}: Found blob field: {} (will download separately)", objectName, fieldName);
                    hasBlobFields = true;
                    blobField = fieldName;
                    skippedCount++;
                    continue;
                }
                
                if (fieldNames.length() > 0) {
                    fieldNames.append(", ");
                }
                fieldNames.append(fieldName);
            }
            
            if (skippedCount > 0) {
                logger.info("{}: Skipped {} unsupported field(s) (compound/blob types)", objectName, skippedCount);
            }
            
            if (hasBlobFields) {
                logger.info("{}: This object has blob field(s). Blob data will be downloaded separately.", objectName);
                // Store metadata for later blob download
                storeBlobMetadata(objectName, blobField);
            }
            
            if (fieldNames.length() == 0) {
                throw new IOException("No queryable fields found for object");
            }
            
            return fieldNames.toString();
        }
    }
    
    private void storeBlobMetadata(String objectName, String blobField) {
        // Store in a class variable for later use (would need to add instance variable)
        // For now, we'll handle this per-query
    }

    private void waitForJobCompletion(String jobId, String objectName, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        String url = String.format("%s/services/data/v%s/jobs/query/%s", instanceUrl, apiVersion, jobId);
        
        String state = "";
        int attempts = 0;
        int maxAttempts = 300; // 5 minutes max
        
        while (!state.equals("JobComplete") && !state.equals("Failed") && !state.equals("Aborted") && attempts < maxAttempts) {
            HttpGet get = new HttpGet(url);
            get.setHeader("Authorization", "Bearer " + accessToken);
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
                
                state = responseJson.get("state").getAsString();
                
                // Get progress information
                if (progressCallback != null && responseJson.has("numberRecordsProcessed")) {
                    int recordsProcessed = responseJson.get("numberRecordsProcessed").getAsInt();
                    if (recordsProcessed > 0) {
                        progressCallback.update(String.format("Processing (%,d records)...", recordsProcessed));
                    }
                }
                
                logger.debug("{}: Job state: {}", objectName, state);
                
                if (state.equals("Failed") || state.equals("Aborted")) {
                    String errorMessage = responseJson.has("errorMessage") ? 
                        responseJson.get("errorMessage").getAsString() : "Unknown error";
                    throw new IOException("Job failed: " + errorMessage);
                }
            }
            
            if (!state.equals("JobComplete")) {
                TimeUnit.SECONDS.sleep(1);
                attempts++;
            }
        }
        
        if (attempts >= maxAttempts) {
            throw new IOException("Job timed out after " + maxAttempts + " seconds");
        }
    }

    private void downloadResults(String jobId, String objectName, String outputFolder) throws IOException {
        String url = String.format("%s/services/data/v%s/jobs/query/%s/results", instanceUrl, apiVersion, jobId);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        get.setHeader("Accept", "text/csv");
        
        Path outputPath = Paths.get(outputFolder, objectName + ".csv");
        Files.createDirectories(outputPath.getParent());
        
        try (CloseableHttpResponse response = httpClient.execute(get);
             InputStream inputStream = response.getEntity().getContent();
             FileOutputStream outputStream = new FileOutputStream(outputPath.toFile())) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            logger.info("{}: Downloaded {} bytes to {}", objectName, totalBytes, outputPath);
        }
    }
    
    /**
     * Download blob content for objects with base64 fields
     * This uses the REST API to download individual blobs after the metadata CSV is created
     * Also appends blob file path column to CSV for reference
     * Handles pagination for large data sets
     */
    public int downloadBlobs(String objectName, String outputFolder, String blobFieldName, ProgressCallback progressCallback) throws IOException, ParseException {
        return downloadBlobs(objectName, outputFolder, blobFieldName, 0, progressCallback);
    }
    
    /**
     * Download blob content for objects with base64 fields with optional limit
     * @param recordLimit Maximum number of blobs to download (0 = no limit)
     */
    public int downloadBlobs(String objectName, String outputFolder, String blobFieldName, int recordLimit, ProgressCallback progressCallback) throws IOException, ParseException {
        logger.info("{}: Starting blob download for field '{}'", objectName, blobFieldName);
        if (recordLimit > 0) {
            logger.info("{}: Blob download limit set to {}", objectName, recordLimit);
        }
        
        // Create blobs subdirectory
        Path blobsDir = Paths.get(outputFolder, objectName + "_blobs");
        Files.createDirectories(blobsDir);
        
        // Read the CSV to get record IDs - only download blobs for records in the CSV
        Path csvPath = Paths.get(outputFolder, objectName + ".csv");
        if (!Files.exists(csvPath)) {
            logger.warn("{}: CSV file not found, skipping blob download", objectName);
            return 0;
        }
        
        // Extract record IDs from the CSV file
        java.util.Set<String> csvRecordIds = extractRecordIdsFromCsv(csvPath);
        if (csvRecordIds.isEmpty()) {
            logger.warn("{}: No records found in CSV, skipping blob download", objectName);
            return 0;
        }
        logger.info("{}: Found {} records in CSV to download blobs for", objectName, csvRecordIds.size());
        
        int downloadCount = 0;
        int failedCount = 0;
        java.util.Map<String, String> blobPaths = new java.util.HashMap<>();
        int totalRecords = csvRecordIds.size();
        
        if (progressCallback != null) {
            progressCallback.update(String.format("Downloading %,d blobs...", totalRecords));
        }
        
        // Download blobs only for records that exist in the CSV
        for (String recordId : csvRecordIds) {
            // Check if we've reached the limit
            if (recordLimit > 0 && downloadCount >= recordLimit) {
                logger.info("{}: Blob download limit reached ({} blobs)", objectName, recordLimit);
                break;
            }
            
            // Get record metadata for proper file naming
            JsonObject recordMeta = getRecordMetadata(objectName, recordId);
            String fileName = (recordMeta != null) 
                ? buildBlobFileName(objectName, recordMeta)
                : recordId + ".bin";
            
            // Download the blob using the record's blob field
            String blobUrl = getBlobDownloadUrl(objectName, recordId, blobFieldName);
            
            if (blobUrl != null) {
                Path blobFile = blobsDir.resolve(fileName);
                boolean success = downloadBlob(blobUrl, blobFile);
                
                if (success) {
                    blobPaths.put(recordId, blobFile.toAbsolutePath().toString());
                    downloadCount++;
                } else {
                    failedCount++;
                }
                
                int processed = downloadCount + failedCount;
                // Log progress every 100 files and update UI every 10 files
                if (processed % 100 == 0) {
                    double pct = totalRecords > 0 ? (processed * 100.0 / totalRecords) : 0;
                    logger.info("{}: Blob download progress: {}/{} ({:.1f}%)", 
                        objectName, processed, totalRecords, pct);
                }
                if (progressCallback != null && processed % 10 == 0) {
                    double pct = totalRecords > 0 ? (processed * 100.0 / totalRecords) : 0;
                    progressCallback.update(String.format("Downloading blobs: %,d/%,d (%.1f%%)", 
                        processed, totalRecords, pct));
                }
            }
        }
        
        // Update CSV to add blob file path column
        if (downloadCount > 0) {
            updateCsvWithBlobPaths(csvPath, blobPaths, blobFieldName);
        }
        
        if (failedCount > 0) {
            logger.warn("{}: Downloaded {} blobs, {} failed", objectName, downloadCount, failedCount);
        } else {
            logger.info("{}: Downloaded {} blob files to {}", objectName, downloadCount, blobsDir);
        }
        
        if (progressCallback != null) {
            progressCallback.update(String.format("Downloaded %,d blobs", downloadCount));
        }
        
        return downloadCount;
    }
    
    /**
     * Extract record IDs from a CSV file
     */
    private java.util.Set<String> extractRecordIdsFromCsv(Path csvPath) throws IOException {
        java.util.Set<String> recordIds = new java.util.LinkedHashSet<>();
        java.util.List<String> lines = Files.readAllLines(csvPath);
        
        if (lines.isEmpty()) return recordIds;
        
        // Find Id column index
        String header = lines.get(0);
        String[] headers = header.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        int idColumnIndex = -1;
        
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].replace("\"", "").trim().equalsIgnoreCase("Id")) {
                idColumnIndex = i;
                break;
            }
        }
        
        if (idColumnIndex == -1) {
            logger.warn("Could not find Id column in CSV");
            return recordIds;
        }
        
        // Extract IDs from data rows
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (values.length > idColumnIndex) {
                String id = values[idColumnIndex].replace("\"", "").trim();
                if (!id.isEmpty()) {
                    recordIds.add(id);
                }
            }
        }
        
        return recordIds;
    }
    
    /**
     * Get record metadata for proper blob file naming
     */
    private JsonObject getRecordMetadata(String objectName, String recordId) {
        try {
            String url = String.format("%s/services/data/v%s/sobjects/%s/%s", 
                instanceUrl, apiVersion, objectName, recordId);
            
            HttpGet get = new HttpGet(url);
            get.setHeader("Authorization", "Bearer " + accessToken);
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() >= 400) {
                    return null;
                }
                String responseBody = EntityUtils.toString(response.getEntity());
                return JsonParser.parseString(responseBody).getAsJsonObject();
            }
        } catch (Exception e) {
            logger.debug("Failed to get metadata for {}/{}: {}", objectName, recordId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Build SOQL query for blob download based on object type
     * Includes additional fields for proper file naming (Name, ContentType, FileExtension, etc.)
     * Note: We cannot filter on blob fields directly (Body, VersionData), so we use BodyLength/ContentSize > 0
     */
    private String buildBlobQuery(String objectName, String blobFieldName) {
        switch (objectName) {
            case "ContentVersion":
                // ContentVersion has rich metadata for file naming
                // Filter on ContentSize > 0 instead of VersionData != null
                return String.format(
                    "SELECT Id, Title, FileExtension, ContentDocumentId, VersionNumber, ContentSize " +
                    "FROM %s WHERE ContentSize > 0 AND IsLatest = true ORDER BY ContentDocumentId",
                    objectName);
            case "Attachment":
                // Attachments have Name and ContentType
                // Filter on BodyLength > 0 instead of Body != null (Body field cannot be filtered)
                return String.format(
                    "SELECT Id, Name, ParentId, ContentType, BodyLength " +
                    "FROM %s WHERE BodyLength > 0 ORDER BY ParentId",
                    objectName);
            case "Document":
                // Documents have Name, DeveloperName, Type and FolderId
                // Filter on BodyLength > 0 instead of Body != null
                return String.format(
                    "SELECT Id, Name, DeveloperName, FolderId, Type, ContentType, BodyLength " +
                    "FROM %s WHERE BodyLength > 0",
                    objectName);
            case "StaticResource":
                // Static resources have Name and ContentType
                // Filter on BodyLength > 0 instead of Body != null
                return String.format(
                    "SELECT Id, Name, ContentType, BodyLength, CacheControl " +
                    "FROM %s WHERE BodyLength > 0",
                    objectName);
            case "FeedAttachment":
                // Feed attachments - no blob field to filter on
                return String.format(
                    "SELECT Id, Title, Type, RecordId, FeedEntityId " +
                    "FROM %s",
                    objectName);
            default:
                // Generic query - just get all IDs, we can't reliably filter on blob fields
                // Most blob objects have BodyLength, so try that first
                return String.format(
                    "SELECT Id FROM %s",
                    objectName);
        }
    }
    
    /**
     * Build a proper file name for the blob based on object type and record data
     */
    private String buildBlobFileName(String objectName, JsonObject record) {
        String recordId = record.get("Id").getAsString();
        
        switch (objectName) {
            case "ContentVersion":
                String title = getJsonString(record, "Title", recordId);
                String fileExt = getJsonString(record, "FileExtension", "bin");
                String versionNum = getJsonString(record, "VersionNumber", "1");
                // Sanitize title for file system
                title = sanitizeFileName(title);
                return String.format("%s_v%s_%s.%s", title, versionNum, recordId, fileExt);
                
            case "Attachment":
                String attachName = getJsonString(record, "Name", recordId + ".bin");
                // Use original filename if it has an extension
                if (attachName.contains(".")) {
                    String baseName = attachName.substring(0, attachName.lastIndexOf('.'));
                    String ext = attachName.substring(attachName.lastIndexOf('.') + 1);
                    return String.format("%s_%s.%s", sanitizeFileName(baseName), recordId, ext);
                }
                // Try to get extension from ContentType
                String contentType = getJsonString(record, "ContentType", null);
                String ext = getExtensionFromContentType(contentType);
                return String.format("%s_%s.%s", sanitizeFileName(attachName), recordId, ext);
                
            case "Document":
                String docName = getJsonString(record, "Name", recordId);
                String docType = getJsonString(record, "Type", null);
                String docContentType = getJsonString(record, "ContentType", null);
                String docExt = (docType != null) ? docType.toLowerCase() : getExtensionFromContentType(docContentType);
                return String.format("%s_%s.%s", sanitizeFileName(docName), recordId, docExt);
                
            case "StaticResource":
                String resourceName = getJsonString(record, "Name", recordId);
                String resourceContentType = getJsonString(record, "ContentType", null);
                String resourceExt = getExtensionFromContentType(resourceContentType);
                return String.format("%s_%s.%s", sanitizeFileName(resourceName), recordId, resourceExt);
                
            default:
                return recordId + ".bin";
        }
    }
    
    private String getJsonString(JsonObject obj, String field, String defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * Sanitize file name for file system compatibility
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        // Remove or replace invalid characters
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        // Limit length
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        return sanitized;
    }
    
    /**
     * Get file extension from MIME content type
     */
    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) return "bin";
        
        // Common MIME type to extension mappings
        switch (contentType.toLowerCase()) {
            case "application/pdf": return "pdf";
            case "application/msword": return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
            case "application/vnd.ms-excel": return "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "xlsx";
            case "application/vnd.ms-powerpoint": return "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return "pptx";
            case "application/zip": return "zip";
            case "application/x-zip-compressed": return "zip";
            case "application/json": return "json";
            case "application/xml": return "xml";
            case "text/plain": return "txt";
            case "text/html": return "html";
            case "text/csv": return "csv";
            case "text/xml": return "xml";
            case "image/jpeg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/bmp": return "bmp";
            case "image/svg+xml": return "svg";
            case "image/webp": return "webp";
            case "image/tiff": return "tiff";
            case "audio/mpeg": return "mp3";
            case "audio/wav": return "wav";
            case "video/mp4": return "mp4";
            case "video/mpeg": return "mpeg";
            case "video/quicktime": return "mov";
            default:
                // Try to extract from content type (e.g., "image/png" -> "png")
                if (contentType.contains("/")) {
                    String subType = contentType.substring(contentType.indexOf('/') + 1);
                    // Remove any parameters (e.g., "text/plain; charset=utf-8")
                    if (subType.contains(";")) {
                        subType = subType.substring(0, subType.indexOf(';')).trim();
                    }
                    if (subType.matches("[a-zA-Z0-9]+")) {
                        return subType;
                    }
                }
                return "bin";
        }
    }
    
    private void updateCsvWithBlobPaths(Path csvPath, java.util.Map<String, String> blobPaths, String blobFieldName) throws IOException {
        // Read original CSV
        java.util.List<String> lines = Files.readAllLines(csvPath);
        if (lines.isEmpty()) return;
        
        // Create temp file for updated CSV
        Path tempPath = Paths.get(csvPath.toString() + ".tmp");
        
        try (BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
            // Process header - add BLOB_FILE_PATH column
            String header = lines.get(0);
            String[] headers = header.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1); // CSV-aware split
            int idColumnIndex = -1;
            
            // Find Id column
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].replace("\"", "").trim().equals("Id")) {
                    idColumnIndex = i;
                    break;
                }
            }
            
            if (idColumnIndex == -1) {
                logger.warn("Could not find Id column in CSV, skipping blob path update");
                return;
            }
            
            // Write updated header
            writer.write(header + ",BLOB_FILE_PATH\n");
            
            // Process data rows
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                
                if (values.length > idColumnIndex) {
                    String recordId = values[idColumnIndex].replace("\"", "").trim();
                    String blobPath = blobPaths.getOrDefault(recordId, "");
                    
                    writer.write(line + ",\"" + blobPath + "\"\n");
                } else {
                    writer.write(line + ",\n");
                }
            }
        }
        
        // Replace original CSV with updated version
        Files.delete(csvPath);
        Files.move(tempPath, csvPath);
        
        logger.info("Updated CSV with blob file paths for {} records", blobPaths.size());
    }
    
    private String getBlobDownloadUrl(String objectName, String recordId, String blobField) {
        // Different objects expose blobs differently
        switch (objectName) {
            case "Document":
                return String.format("%s/services/data/v%s/sobjects/Document/%s/Body", instanceUrl, apiVersion, recordId);
            case "Attachment":
                return String.format("%s/services/data/v%s/sobjects/Attachment/%s/Body", instanceUrl, apiVersion, recordId);
            case "ContentVersion":
                return String.format("%s/services/data/v%s/sobjects/ContentVersion/%s/VersionData", instanceUrl, apiVersion, recordId);
            case "StaticResource":
                return String.format("%s/services/data/v%s/sobjects/StaticResource/%s/Body", instanceUrl, apiVersion, recordId);
            default:
                // Generic approach - try using the blob field name
                return String.format("%s/services/data/v%s/sobjects/%s/%s/%s", 
                    instanceUrl, apiVersion, objectName, recordId, blobField);
        }
    }
    
    /**
     * Download a blob from the given URL to the output path
     * @return true if download was successful, false otherwise
     */
    private boolean downloadBlob(String url, Path outputPath) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            if (response.getCode() >= 400) {
                logger.warn("Failed to download blob from {}: HTTP {}", url, response.getCode());
                return false;
            }
            
            try (InputStream inputStream = response.getEntity().getContent();
                 FileOutputStream outputStream = new FileOutputStream(outputPath.toFile())) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                logger.debug("Downloaded {} bytes to {}", totalBytes, outputPath.getFileName());
                return true;
            }
        } catch (Exception e) {
            logger.warn("Exception downloading blob from {}: {}", url, e.getMessage());
            // Clean up partial file if it exists
            try {
                Files.deleteIfExists(outputPath);
            } catch (IOException ignored) {}
            return false;
        }
    }

    public void close() throws IOException {
        httpClient.close();
    }
}
