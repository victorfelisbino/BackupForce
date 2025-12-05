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
        queryObject(objectName, outputFolder, null, null);
    }
    
    public void queryObject(String objectName, String outputFolder, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        queryObject(objectName, outputFolder, null, progressCallback);
    }
    
    /**
     * Query object with optional delta support
     * @param whereClause Optional WHERE clause for delta queries (e.g., "LastModifiedDate > 2024-01-01T00:00:00Z")
     */
    public void queryObject(String objectName, String outputFolder, String whereClause, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        logger.info("Starting Bulk API v2 query for: {}", objectName);
        
        if (progressCallback != null) progressCallback.update("Creating job...");
        
        // Step 1: Create query job
        String jobId = createQueryJob(objectName, whereClause);
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
        return createQueryJob(objectName, null);
    }
    
    private String createQueryJob(String objectName, String whereClause) throws IOException, ParseException {
        // First, get all field names for this object
        String fields = getObjectFields(objectName);
        
        String url = String.format("%s/services/data/v%s/jobs/query", instanceUrl, apiVersion);
        
        String soql = "SELECT " + fields + " FROM " + objectName;
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            soql += " WHERE " + whereClause;
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
     */
    public int downloadBlobs(String objectName, String outputFolder, String blobFieldName, ProgressCallback progressCallback) throws IOException, ParseException {
        logger.info("{}: Starting blob download for field '{}'", objectName, blobFieldName);
        
        // Create blobs subdirectory
        Path blobsDir = Paths.get(outputFolder, objectName + "_blobs");
        Files.createDirectories(blobsDir);
        
        // Read the CSV to get record IDs
        Path csvPath = Paths.get(outputFolder, objectName + ".csv");
        if (!Files.exists(csvPath)) {
            logger.warn("{}: CSV file not found, skipping blob download", objectName);
            return 0;
        }
        
        // Query for blob field using REST API (small batches)
        String soql = String.format("SELECT Id, %s FROM %s WHERE %s != null LIMIT 2000", 
            blobFieldName, objectName, blobFieldName);
        String queryUrl = String.format("%s/services/data/v%s/query?q=%s", 
            instanceUrl, apiVersion, java.net.URLEncoder.encode(soql, "UTF-8"));
        
        HttpGet get = new HttpGet(queryUrl);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        int downloadCount = 0;
        java.util.Map<String, String> blobPaths = new java.util.HashMap<>();
        
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (response.getCode() >= 400) {
                logger.error("{}: Failed to query blobs: {}", objectName, responseBody);
                return 0;
            }
            
            JsonArray records = responseJson.getAsJsonArray("records");
            int totalRecords = records.size();
            
            logger.info("{}: Found {} records with blob data", objectName, totalRecords);
            
            for (int i = 0; i < records.size(); i++) {
                JsonObject record = records.get(i).getAsJsonObject();
                String recordId = record.get("Id").getAsString();
                
                // Download the blob using the record's blob field
                // Different objects have different blob access patterns
                String blobUrl = getBlobDownloadUrl(objectName, recordId, blobFieldName);
                
                if (blobUrl != null) {
                    String fileName = recordId + ".bin";
                    Path blobFile = blobsDir.resolve(fileName);
                    downloadBlob(blobUrl, blobFile);
                    // Store absolute path for easier database resolution
                    blobPaths.put(recordId, blobFile.toAbsolutePath().toString());
                    downloadCount++;
                    
                    if (progressCallback != null && downloadCount % 10 == 0) {
                        progressCallback.update(String.format("Downloaded %d/%d blobs...", downloadCount, totalRecords));
                    }
                }
            }
        }
        
        // Update CSV to add blob file path column
        if (downloadCount > 0) {
            updateCsvWithBlobPaths(csvPath, blobPaths, blobFieldName);
        }
        
        logger.info("{}: Downloaded {} blob files to {}", objectName, downloadCount, blobsDir);
        if (progressCallback != null) {
            progressCallback.update(String.format("Downloaded %d blobs", downloadCount));
        }
        
        return downloadCount;
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
            default:
                // Generic approach - try using the blob field name
                return String.format("%s/services/data/v%s/sobjects/%s/%s/%s", 
                    instanceUrl, apiVersion, objectName, recordId, blobField);
        }
    }
    
    private void downloadBlob(String url, Path outputPath) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (CloseableHttpResponse response = httpClient.execute(get);
             InputStream inputStream = response.getEntity().getContent();
             FileOutputStream outputStream = new FileOutputStream(outputPath.toFile())) {
            
            if (response.getCode() >= 400) {
                logger.warn("Failed to download blob from {}: HTTP {}", url, response.getCode());
                return;
            }
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    public void close() throws IOException {
        httpClient.close();
    }
}
