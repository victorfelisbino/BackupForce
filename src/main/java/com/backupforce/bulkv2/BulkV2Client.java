package com.backupforce.bulkv2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BulkV2Client implements AutoCloseable {
    
    /**
     * Objects that have known Bulk API limitations and require special handling or should be skipped.
     * These objects may require filters, don't support queryMore, or have other restrictions.
     */
    private static final Set<String> KNOWN_PROBLEMATIC_OBJECTS = new HashSet<>(Arrays.asList(
        // Objects requiring specific filters
        "ActivityMetric",           // Requires specific object/record filter
        "ActivityMetricRollup",     // Requires specific object/record filter
        "ContentDocumentLink",      // Requires filter on ContentDocumentId or LinkedEntityId
        "ContentFolderItem",        // Requires filter on Id or ParentContentFolderId
        "ContentFolderMember",      // Requires filter on Id, ChildRecordId, or ParentContentFolderId
        "EventWhoRelation",         // Not supported by Bulk API
        "TaskWhoRelation",          // Not supported by Bulk API
        "TopicAssignment",          // Often has restrictions
        
        // Metadata objects requiring reified column filters
        "ApexTypeImplementor",      // Requires InterfaceName,IsConcrete filter
        "AppTabMember",             // Requires AppDefinitionId,TabDefinitionId filter
        "ColorDefinition",          // Requires TabDefinitionId,Context filter
        "FlowTestView",             // Requires FlowDefinitionViewId,DurableId filter
        "FlowVariableView",         // Requires FlowVersionViewId,DurableId filter
        "FlowVersionView",          // Requires FlowDefinitionViewId,DurableId filter
        
        // Objects that don't support queryMore/pagination
        "AuraDefinitionInfo",       // EXCEEDED_ID_LIMIT - needs LIMIT
        "DataType",                 // EXCEEDED_ID_LIMIT - needs LIMIT
        
        // External/special objects
        "DatacloudAddress",         // External object - transient queries not supported
        "DatacloudCompany",         // External object
        "DatacloudContact",         // External object
        "DatacloudDandBCompany",    // External object
        "FlexQueueItem",            // Requires queryType field expression
        
        // Objects with CSV serialization issues
        "EntityDefinition",         // Cannot serialize RecordTypesSupported in CSV
        "EntityParticle",           // Similar serialization issues
        "FieldDefinition",          // Similar serialization issues
        "RelationshipDomain",       // Similar serialization issues
        "RelationshipInfo",         // Similar serialization issues
        
        // Objects not supported by Bulk API
        "FieldSecurityClassification", // Not supported by Bulk API
        
        // Statistics objects
        "DataStatistics"            // Often fails during batch processing
    ));
    
    /**
     * Check if an object is known to have Bulk API issues
     */
    public static boolean isProblematicObject(String objectName) {
        return KNOWN_PROBLEMATIC_OBJECTS.contains(objectName);
    }
    
    /**
     * Objects that contain large binary data and may cause out-of-memory errors.
     * These require special handling (streaming, increased heap, or smaller batches).
     */
    private static final Set<String> LARGE_BINARY_OBJECTS = new HashSet<>(Arrays.asList(
        "Attachment",           // Binary file content
        "ContentVersion",       // Binary document content
        "Document",             // Binary document content
        "ContentBody",          // Large body content
        "StaticResource",       // Static resource files
        "EmailMessage",         // Can contain large attachments
        "FeedItem",            // Can contain large body content
        "EmailTemplate"        // Can contain large HTML body
    ));
    
    /**
     * Check if an object contains large binary data that may cause memory issues
     */
    public static boolean isLargeBinaryObject(String objectName) {
        return LARGE_BINARY_OBJECTS.contains(objectName);
    }
    
    /**
     * Get warning message for large binary objects
     */
    public static String getLargeBinaryObjectWarning(String objectName) {
        if (LARGE_BINARY_OBJECTS.contains(objectName)) {
            return objectName + " contains binary data and may require significant memory. Consider backing up with increased heap size (-Xmx4g).";
        }
        return null;
    }
    
    /**
     * Get warning message for a problematic object
     */
    public static String getProblematicObjectWarning(String objectName) {
        if (KNOWN_PROBLEMATIC_OBJECTS.contains(objectName)) {
            return objectName + " has known Bulk API limitations and may fail or return incomplete results.";
        }
        return null;
    }
    private static final Logger logger = LoggerFactory.getLogger(BulkV2Client.class);
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connectionManager;
    private volatile boolean clientShutdown = false;
    private volatile long reconnectVersion = 0; // Track reconnection attempts to avoid race conditions
    private final Object clientLock = new Object();

    public BulkV2Client(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        
        initializeHttpClient();
        
        logger.info("HTTP client initialized with extended timeouts for long-running backup operations");
    }
    
    /**
     * Initialize or reinitialize the HTTP client with connection pool settings.
     * This is called on startup and can be called again to recover from pool shutdown.
     */
    private void initializeHttpClient() {
        synchronized (clientLock) {
            logger.info("Initializing HTTP client (reconnect version {} -> {})", reconnectVersion, reconnectVersion + 1);
            
            // Close existing client if present
            if (httpClient != null) {
                try {
                    logger.debug("Closing old HTTP client...");
                    httpClient.close();
                } catch (Exception e) {
                    logger.debug("Error closing old HTTP client: {}", e.getMessage());
                }
                httpClient = null;
            }
            if (connectionManager != null) {
                try {
                    logger.debug("Closing old connection manager...");
                    connectionManager.close();
                } catch (Exception e) {
                    logger.debug("Error closing old connection manager: {}", e.getMessage());
                }
                connectionManager = null;
            }
            
            // Configure connection pool with long timeouts for backup operations
            this.connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofMinutes(5))
                    .setSocketTimeout(Timeout.ofMinutes(30))  // Long timeout for large downloads
                    .setTimeToLive(TimeValue.ofHours(2))      // Keep connections alive for 2 hours
                    .setValidateAfterInactivity(TimeValue.ofSeconds(30))  // Validate stale connections
                    .build())
                .setMaxConnTotal(20)      // Max total connections
                .setMaxConnPerRoute(10)   // Max connections per host
                .build();
            
            // Configure request timeouts
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMinutes(5))
                .setResponseTimeout(Timeout.ofMinutes(30))
                .build();
            
            this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(5))
                .build();
            
            this.clientShutdown = false;
            this.reconnectVersion++;
            logger.info("HTTP connection pool initialized/reinitialized (version {})", reconnectVersion);
        }
    }
    
    /**
     * Ensure HTTP client is available. If the connection pool was shut down
     * (e.g., due to OOM or other errors), recreate it.
     */
    private void ensureHttpClient() {
        if (clientShutdown) {
            synchronized (clientLock) {
                if (clientShutdown) {
                    logger.warn("HTTP connection pool was shut down, recreating...");
                    initializeHttpClient();
                }
            }
        }
    }
    
    /**
     * Force reconnection of the HTTP client. Call this after detecting
     * connection pool errors to ensure subsequent requests will work.
     * Thread-safe and uses version tracking to prevent redundant reconnections.
     */
    public void forceReconnect() {
        long versionBefore = reconnectVersion;
        logger.info("Force reconnection requested (current version: {})", versionBefore);
        synchronized (clientLock) {
            // Check if another thread already reconnected while we waited
            if (reconnectVersion == versionBefore) {
                logger.info("Forcing HTTP client reconnection (version {} -> {})", versionBefore, versionBefore + 1);
                clientShutdown = true;
                initializeHttpClient();
            } else {
                logger.info("Skipping force reconnect - another thread already reconnected (version {} -> {})", 
                    versionBefore, reconnectVersion);
            }
        }
    }
    
    /**
     * Mark the client as shutdown (called when we detect pool shutdown errors)
     */
    private void markClientShutdown() {
        clientShutdown = true;
    }
    
    /**
     * Check if an exception indicates the connection pool is shut down
     */
    private boolean isPoolShutdownError(Exception e) {
        String message = e.getMessage();
        if (message != null) {
            return message.contains("Connection pool shut down") ||
                   message.contains("Pool closed") ||
                   message.contains("shut down");
        }
        return false;
    }
    
    /**
     * Execute an HTTP request with automatic recovery from pool shutdown.
     * If the connection pool was shut down (e.g., due to OOM), recreate it and retry.
     * Uses version tracking to avoid race conditions when multiple threads detect shutdown.
     */
    private <T> T executeWithRecovery(HttpRequestExecutor<T> executor) throws IOException, ParseException {
        int maxRetries = 3;
        long versionAtStart = reconnectVersion;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ensureHttpClient();
                CloseableHttpClient currentClient;
                synchronized (clientLock) {
                    currentClient = httpClient;
                }
                return executor.execute(currentClient);
            } catch (IOException e) {
                if (isPoolShutdownError(e) && attempt < maxRetries) {
                    long currentVersion = reconnectVersion;
                    
                    // Only force reconnect if no other thread has already done so
                    if (currentVersion == versionAtStart) {
                        logger.warn("Connection pool shut down detected (version {}), recreating HTTP client (attempt {}/{})", 
                            currentVersion, attempt, maxRetries);
                        synchronized (clientLock) {
                            // Double-check: another thread may have reconnected while we waited for the lock
                            if (reconnectVersion == versionAtStart) {
                                clientShutdown = true;
                                initializeHttpClient();
                            } else {
                                logger.info("Another thread already reconnected (version {} -> {}), using existing pool", 
                                    versionAtStart, reconnectVersion);
                            }
                        }
                    } else {
                        logger.info("Pool already reconnected by another thread (version {} -> {}), retrying with new pool", 
                            versionAtStart, currentVersion);
                    }
                    
                    // Update our version reference for next iteration
                    versionAtStart = reconnectVersion;
                    
                    // Brief pause before retry to let reconnection stabilize
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting for reconnection", ie);
                    }
                    // retry on next iteration
                } else {
                    throw e;
                }
            }
        }
        throw new IOException("Failed to execute HTTP request after " + maxRetries + " attempts");
    }
    
    /**
     * Functional interface for HTTP request execution
     */
    @FunctionalInterface
    private interface HttpRequestExecutor<T> {
        T execute(CloseableHttpClient client) throws IOException, ParseException;
    }

    // Configure connection pool with long timeouts for backup operations
    // (initialization moved to initializeHttpClient method above)

    // Functional interface for progress updates
    @FunctionalInterface
    public interface ProgressCallback {
        void update(String status);
    }
    
    /**
     * Get the total record count for a Salesforce object using REST API.
     * @param objectName The Salesforce object name
     * @return The total number of records, or -1 if an error occurs
     */
    public int getRecordCount(String objectName) {
        try {
            String url = String.format("%s/services/data/v%s/query?q=%s",
                instanceUrl, apiVersion, 
                java.net.URLEncoder.encode("SELECT COUNT() FROM " + objectName, "UTF-8"));
            
            HttpGet request = new HttpGet(url);
            request.addHeader("Authorization", "Bearer " + accessToken);
            request.addHeader("Accept", "application/json");
            
            // Use a separate HttpClient for thread safety - don't share connection
            try (CloseableHttpClient countClient = HttpClients.createDefault()) {
                try (CloseableHttpResponse response = countClient.execute(request)) {
                    int statusCode = response.getCode();
                    String responseBody = EntityUtils.toString(response.getEntity());
                    
                    if (statusCode != 200) {
                        logger.warn("Failed to get count for {}: HTTP {} - {}", objectName, statusCode, responseBody);
                        return -1;
                    }
                    
                    // Parse JSON response to get totalSize
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    int count = json.get("totalSize").getAsInt();
                    logger.debug("Salesforce count for {}: {}", objectName, count);
                    return count;
                }
            }
        } catch (Exception e) {
            logger.warn("Error getting record count for {}: {}", objectName, e.getMessage());
            return -1;
        }
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
        queryObject(objectName, outputFolder, whereClause, recordLimit, null, progressCallback);
    }
    
    /**
     * Query object with optional delta support, record limit, and specific field selection
     * @param whereClause Optional WHERE clause for delta queries (e.g., "LastModifiedDate > 2024-01-01T00:00:00Z")
     * @param recordLimit Maximum number of records to retrieve (0 = no limit)
     * @param selectedFields Specific fields to query (null = all fields)
     */
    public void queryObject(String objectName, String outputFolder, String whereClause, int recordLimit, 
                           java.util.Set<String> selectedFields, ProgressCallback progressCallback) throws IOException, InterruptedException, ParseException {
        logger.info("Starting Bulk API v2 query for: {}", objectName);
        if (recordLimit > 0) {
            logger.info("{}: Record limit set to {}", objectName, recordLimit);
        }
        if (selectedFields != null) {
            logger.info("{}: Querying {} selected fields", objectName, selectedFields.size());
        }
        
        if (progressCallback != null) progressCallback.update("Creating job...");
        
        // Step 1: Create query job
        String jobId = createQueryJob(objectName, whereClause, recordLimit, selectedFields);
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
        return createQueryJob(objectName, null, 0, null);
    }
    
    private String createQueryJob(String objectName, String whereClause) throws IOException, ParseException {
        return createQueryJob(objectName, whereClause, 0, null);
    }
    
    private String createQueryJob(String objectName, String whereClause, int recordLimit) throws IOException, ParseException {
        return createQueryJob(objectName, whereClause, recordLimit, null);
    }
    
    private String createQueryJob(String objectName, String whereClause, int recordLimit, java.util.Set<String> selectedFields) throws IOException, ParseException {
        // Get field names - either from selection or by querying all fields
        String fields;
        if (selectedFields != null && !selectedFields.isEmpty()) {
            // Use the user-selected fields (ensure Id is always included)
            java.util.Set<String> fieldsToUse = new java.util.LinkedHashSet<>(selectedFields);
            fieldsToUse.add("Id"); // Always include Id
            fields = String.join(", ", fieldsToUse);
            logger.info("{}: Using {} selected fields", objectName, fieldsToUse.size());
        } else {
            // Get all field names for this object
            fields = getObjectFields(objectName);
        }
        
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
        
        return executeWithRecovery(client -> {
            try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
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
        });
    }
    
    private String getObjectFields(String objectName) throws IOException, ParseException {
        String url = String.format("%s/services/data/v%s/sobjects/%s/describe", instanceUrl, apiVersion, objectName);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        return executeWithRecovery(client -> {
            try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
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
        });
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
            
            String currentState = executeWithRecovery(client -> {
                try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
                    
                    String st = responseJson.get("state").getAsString();
                    
                    // Get progress information
                    if (progressCallback != null && responseJson.has("numberRecordsProcessed")) {
                        int recordsProcessed = responseJson.get("numberRecordsProcessed").getAsInt();
                        if (recordsProcessed > 0) {
                            progressCallback.update(String.format("Processing (%,d records)...", recordsProcessed));
                        }
                    }
                    
                    logger.debug("{}: Job state: {}", objectName, st);
                    
                    if (st.equals("Failed") || st.equals("Aborted")) {
                        String errorMessage = responseJson.has("errorMessage") ? 
                            responseJson.get("errorMessage").getAsString() : "Unknown error";
                        throw new IOException("Job failed: " + errorMessage);
                    }
                    return st;
                }
            });
            state = currentState;
            
            if (!state.equals("JobComplete")) {
                TimeUnit.SECONDS.sleep(1);
                attempts++;
            }
        }
        
        if (attempts >= maxAttempts) {
            throw new IOException("Job timed out after " + maxAttempts + " seconds");
        }
    }

    private void downloadResults(String jobId, String objectName, String outputFolder) throws IOException, ParseException {
        String baseUrl = String.format("%s/services/data/v%s/jobs/query/%s/results", instanceUrl, apiVersion, jobId);
        
        Path outputPath = Paths.get(outputFolder, objectName + ".csv");
        Files.createDirectories(outputPath.getParent());
        
        long totalBytes = 0;
        boolean isFirstChunk = true;
        String locator = null;
        int chunkCount = 0;
        
        // Bulk API v2 uses Sforce-Locator header for pagination
        // Keep fetching until we get all results
        do {
            String url = baseUrl;
            if (locator != null) {
                url = baseUrl + "?locator=" + locator;
            }
            
            HttpGet get = new HttpGet(url);
            get.setHeader("Authorization", "Bearer " + accessToken);
            get.setHeader("Accept", "text/csv");
            
            final boolean appendMode = !isFirstChunk;
            final String finalUrl = url;
            
            String[] result = executeWithRecovery(client -> {
                try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
                    // Check for locator in response header for next page
                    String nextLocator = null;
                    if (response.containsHeader("Sforce-Locator")) {
                        String locatorValue = response.getFirstHeader("Sforce-Locator").getValue();
                        // "null" string means no more data
                        if (locatorValue != null && !locatorValue.equals("null") && !locatorValue.isEmpty()) {
                            nextLocator = locatorValue;
                        }
                    }
                    
                    long bytesWritten = 0;
                    try (InputStream inputStream = response.getEntity().getContent();
                         FileOutputStream outputStream = new FileOutputStream(outputPath.toFile(), appendMode)) {
                        
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
                        java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outputStream));
                        
                        String line;
                        boolean firstLineOfChunk = true;
                        while ((line = reader.readLine()) != null) {
                            // Skip header line for subsequent chunks (avoid duplicate headers)
                            if (appendMode && firstLineOfChunk) {
                                firstLineOfChunk = false;
                                continue;
                            }
                            firstLineOfChunk = false;
                            
                            writer.write(line);
                            writer.newLine();
                            bytesWritten += line.length() + 1;
                        }
                        writer.flush();
                    }
                    
                    return new String[] { nextLocator, String.valueOf(bytesWritten) };
                }
            });
            
            locator = result[0];
            totalBytes += Long.parseLong(result[1]);
            chunkCount++;
            isFirstChunk = false;
        } while (locator != null);
        
        logger.info("{}: Downloaded {} bytes in {} chunk(s) to {}", objectName, totalBytes, chunkCount, outputPath);
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
        int skippedCount = 0;
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
                
                // Skip if blob file already exists (incremental backup support)
                if (Files.exists(blobFile) && Files.size(blobFile) > 0) {
                    blobPaths.put(recordId, blobFile.toAbsolutePath().toString());
                    skippedCount++;
                    int processed = downloadCount + skippedCount + failedCount;
                    if (progressCallback != null && processed % 10 == 0) {
                        double pct = totalRecords > 0 ? (processed * 100.0 / totalRecords) : 0;
                        progressCallback.update(String.format("Blobs: %,d/%,d (%.1f%%) - %d skipped", 
                            processed, totalRecords, pct, skippedCount));
                    }
                    continue;
                }
                
                boolean success = downloadBlob(blobUrl, blobFile);
                
                if (success) {
                    blobPaths.put(recordId, blobFile.toAbsolutePath().toString());
                    downloadCount++;
                } else {
                    failedCount++;
                }
                
                int processed = downloadCount + skippedCount + failedCount;
                // Log progress every 100 files and update UI every 10 files
                if (processed % 100 == 0) {
                    double pct = totalRecords > 0 ? (processed * 100.0 / totalRecords) : 0;
                    logger.info("{}: Blob download progress: {}/{} ({:.1f}%) - {} skipped", 
                        objectName, processed, totalRecords, pct, skippedCount);
                }
                if (progressCallback != null && processed % 10 == 0) {
                    double pct = totalRecords > 0 ? (processed * 100.0 / totalRecords) : 0;
                    progressCallback.update(String.format("Blobs: %,d/%,d (%.1f%%) - %d skipped", 
                        processed, totalRecords, pct, skippedCount));
                }
            }
        }
        
        // Update CSV to add blob file path column (include skipped files too)
        if (downloadCount > 0 || skippedCount > 0) {
            updateCsvWithBlobPaths(csvPath, blobPaths, blobFieldName);
        }
        
        if (skippedCount > 0) {
            logger.info("{}: Skipped {} existing blobs (incremental)", objectName, skippedCount);
        }
        if (failedCount > 0) {
            logger.warn("{}: Downloaded {} blobs, {} skipped, {} failed", objectName, downloadCount, skippedCount, failedCount);
        } else if (downloadCount > 0) {
            logger.info("{}: Downloaded {} blob files to {}", objectName, downloadCount, blobsDir);
        }
        
        if (progressCallback != null) {
            if (skippedCount > 0) {
                progressCallback.update(String.format("Blobs: %,d new, %,d skipped", downloadCount, skippedCount));
            } else {
                progressCallback.update(String.format("Downloaded %,d blobs", downloadCount));
            }
        }
        
        return downloadCount + skippedCount; // Return total processed (new + existing)
    }
    
    /**
     * Extract record IDs from a CSV file
     * Uses proper CSV parsing to handle multi-line fields and special characters
     */
    private java.util.Set<String> extractRecordIdsFromCsv(Path csvPath) throws IOException {
        java.util.Set<String> recordIds = new java.util.LinkedHashSet<>();
        
        try (java.io.BufferedReader reader = Files.newBufferedReader(csvPath, java.nio.charset.StandardCharsets.UTF_8)) {
            // Read and parse header line
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isEmpty()) return recordIds;
            
            String[] headers = parseCsvLine(headerLine);
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
            
            // Read data rows, handling multi-line CSV fields
            String line;
            StringBuilder currentRecord = new StringBuilder();
            boolean inQuotedField = false;
            
            while ((line = reader.readLine()) != null) {
                if (currentRecord.length() > 0) {
                    currentRecord.append("\n");
                }
                currentRecord.append(line);
                
                // Count quotes to determine if we're still inside a quoted field
                int quoteCount = 0;
                for (char c : currentRecord.toString().toCharArray()) {
                    if (c == '"') quoteCount++;
                }
                inQuotedField = (quoteCount % 2 != 0);
                
                if (!inQuotedField) {
                    // Complete record - parse it
                    String[] values = parseCsvLine(currentRecord.toString());
                    if (values.length > idColumnIndex) {
                        String id = values[idColumnIndex].replace("\"", "").trim();
                        // Validate it looks like a Salesforce ID (15 or 18 chars, alphanumeric)
                        if (!id.isEmpty() && id.length() >= 15 && id.length() <= 18 && id.matches("[a-zA-Z0-9]+")) {
                            recordIds.add(id);
                        }
                    }
                    currentRecord.setLength(0);
                }
            }
        }
        
        return recordIds;
    }
    
    /**
     * Parse a CSV line properly handling quoted fields with commas, quotes, and newlines
     */
    private String[] parseCsvLine(String line) {
        java.util.List<String> values = new java.util.ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    currentValue.append('"');
                    i++;
                } else {
                    // Toggle quote mode
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString());
                currentValue.setLength(0);
            } else {
                currentValue.append(c);
            }
        }
        values.add(currentValue.toString());
        
        return values.toArray(new String[0]);
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
            
            return executeWithRecovery(client -> {
                try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
                    if (response.getCode() >= 400) {
                        return null;
                    }
                    String responseBody = EntityUtils.toString(response.getEntity());
                    return JsonParser.parseString(responseBody).getAsJsonObject();
                }
            });
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
        
        try {
            return executeWithRecovery(client -> {
                try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
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
                }
            });
        } catch (Exception e) {
            logger.warn("Exception downloading blob from {}: {}", url, e.getMessage());
            // Clean up partial file if it exists
            try {
                Files.deleteIfExists(outputPath);
            } catch (IOException ignored) {}
            return false;
        }
    }
    
    /**
     * Get Salesforce API limits information
     * @return ApiLimits object with current usage and max values
     */
    public ApiLimits getApiLimits() throws IOException, ParseException {
        String url = instanceUrl + "/services/data/v" + apiVersion + "/limits";
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        get.setHeader("Accept", "application/json");
        
        return executeWithRecovery(client -> {
            try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    logger.warn("Failed to get API limits, status: {}", statusCode);
                    return null;
                }
                
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                
                ApiLimits limits = new ApiLimits();
                
                // Daily API Requests
                if (json.has("DailyApiRequests")) {
                    JsonObject dailyApi = json.getAsJsonObject("DailyApiRequests");
                    limits.dailyApiRequestsUsed = dailyApi.get("Remaining").getAsLong();
                    limits.dailyApiRequestsMax = dailyApi.get("Max").getAsLong();
                    // Salesforce returns "Remaining", so calculate used
                    limits.dailyApiRequestsUsed = limits.dailyApiRequestsMax - limits.dailyApiRequestsUsed;
                }
                
                // Daily Bulk API 2.0 Requests
                if (json.has("DailyBulkV2QueryFileStorageMB")) {
                    JsonObject bulkStorage = json.getAsJsonObject("DailyBulkV2QueryFileStorageMB");
                    limits.bulkApiStorageUsedMB = bulkStorage.get("Max").getAsLong() - bulkStorage.get("Remaining").getAsLong();
                    limits.bulkApiStorageMaxMB = bulkStorage.get("Max").getAsLong();
                }
                
                // Daily Bulk API Jobs
                if (json.has("DailyBulkV2QueryJobs")) {
                    JsonObject bulkJobs = json.getAsJsonObject("DailyBulkV2QueryJobs");
                    limits.bulkApiJobsUsed = bulkJobs.get("Max").getAsLong() - bulkJobs.get("Remaining").getAsLong();
                    limits.bulkApiJobsMax = bulkJobs.get("Max").getAsLong();
                }
                
                logger.debug("API Limits - Daily: {}/{}, Bulk Jobs: {}/{}", 
                    limits.dailyApiRequestsUsed, limits.dailyApiRequestsMax,
                    limits.bulkApiJobsUsed, limits.bulkApiJobsMax);
                
                return limits;
            }
        });
    }
    
    /**
     * Container for API limit information
     */
    public static class ApiLimits {
        public long dailyApiRequestsUsed;
        public long dailyApiRequestsMax;
        public long bulkApiJobsUsed;
        public long bulkApiJobsMax;
        public long bulkApiStorageUsedMB;
        public long bulkApiStorageMaxMB;
        
        public double getDailyApiPercentUsed() {
            return dailyApiRequestsMax > 0 ? (dailyApiRequestsUsed * 100.0 / dailyApiRequestsMax) : 0;
        }
        
        public double getBulkApiPercentUsed() {
            return bulkApiJobsMax > 0 ? (bulkApiJobsUsed * 100.0 / bulkApiJobsMax) : 0;
        }
        
        public String getFormattedDailyApi() {
            return String.format("%,d / %,d (%.1f%%)", 
                dailyApiRequestsUsed, dailyApiRequestsMax, getDailyApiPercentUsed());
        }
        
        public String getFormattedBulkApi() {
            return String.format("%,d / %,d jobs", bulkApiJobsUsed, bulkApiJobsMax);
        }
    }

    public void close() throws IOException {
        httpClient.close();
    }
}
