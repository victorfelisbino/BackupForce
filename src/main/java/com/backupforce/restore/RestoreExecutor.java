package com.backupforce.restore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Executes data restoration to Salesforce using Bulk API 2.0.
 * Supports insert, update, and upsert operations.
 */
public class RestoreExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(RestoreExecutor.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;
    private final RelationshipManager relationshipManager;
    private final RelationshipResolver relationshipResolver;
    
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Consumer<RestoreProgress> progressCallback;
    private Consumer<String> logCallback;
    
    public enum RestoreMode {
        INSERT,
        UPSERT,
        UPDATE
    }
    
    public RestoreExecutor(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
        this.relationshipManager = new RelationshipManager(instanceUrl, accessToken, apiVersion);
        this.relationshipResolver = new RelationshipResolver(instanceUrl, accessToken, apiVersion);
    }
    
    public void setProgressCallback(Consumer<RestoreProgress> callback) {
        this.progressCallback = callback;
    }
    
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
        this.relationshipResolver.setLogCallback(callback);
    }
    
    public void cancel() {
        cancelled.set(true);
    }
    
    /**
     * Restores data from a CSV file to Salesforce
     */
    public RestoreResult restoreFromCsv(String objectName, Path csvPath, RestoreMode mode, 
                                         RestoreOptions options) throws IOException, ParseException {
        
        log("Starting restore for " + objectName + " in " + mode + " mode");
        RestoreResult result = new RestoreResult(objectName);
        
        // Read and parse CSV
        List<Map<String, String>> records = readCsvRecords(csvPath);
        if (records.isEmpty()) {
            log(objectName + ": No records to restore");
            return result;
        }
        
        log(objectName + ": Read " + records.size() + " records from CSV");
        result.setTotalRecords(records.size());
        
        // Resolve relationships if enabled
        if (options.isResolveRelationships()) {
            log(objectName + ": Resolving relationship references...");
            records = relationshipResolver.resolveRelationships(objectName, records);
        }
        
        // Get object metadata for field validation
        RelationshipManager.ObjectMetadata metadata = relationshipManager.describeObject(objectName);
        
        // Determine external ID field for upsert
        String externalIdField = null;
        if (mode == RestoreMode.UPSERT) {
            externalIdField = determineExternalIdField(metadata, options);
            if (externalIdField == null) {
                throw new IOException("No external ID field found for upsert on " + objectName);
            }
            log(objectName + ": Using external ID field '" + externalIdField + "' for upsert");
        }
        
        // Process records in batches
        int batchSize = options.getBatchSize();
        int totalBatches = (int) Math.ceil((double) records.size() / batchSize);
        
        for (int batchNum = 0; batchNum < totalBatches && !cancelled.get(); batchNum++) {
            int start = batchNum * batchSize;
            int end = Math.min(start + batchSize, records.size());
            List<Map<String, String>> batch = records.subList(start, end);
            
            log(String.format("%s: Processing batch %d/%d (%d records)", 
                objectName, batchNum + 1, totalBatches, batch.size()));
            
            try {
                BatchResult batchResult = processBatch(objectName, batch, mode, externalIdField, options);
                result.addBatchResult(batchResult);
                
                updateProgress(objectName, end, records.size(), result);
                
                if (!batchResult.isSuccess() && options.isStopOnError()) {
                    log(objectName + ": Stopping due to errors");
                    break;
                }
                
            } catch (Exception e) {
                logger.error("Error processing batch for " + objectName, e);
                log(objectName + ": Batch error - " + e.getMessage());
                result.addError("Batch " + (batchNum + 1) + ": " + e.getMessage());
                
                if (options.isStopOnError()) {
                    break;
                }
            }
        }
        
        result.setCompleted(!cancelled.get());
        log(String.format("%s: Restore completed. Success: %d, Failed: %d", 
            objectName, result.getSuccessCount(), result.getFailureCount()));
        
        return result;
    }
    
    private BatchResult processBatch(String objectName, List<Map<String, String>> records,
                                      RestoreMode mode, String externalIdField,
                                      RestoreOptions options) throws IOException, ParseException {
        
        BatchResult result = new BatchResult();
        
        // Clean records - remove Id column for insert, remove system fields
        List<Map<String, String>> cleanedRecords = cleanRecordsForRestore(records, mode, objectName);
        
        switch (mode) {
            case INSERT:
                return processInsertBatch(objectName, cleanedRecords, options);
            case UPDATE:
                return processUpdateBatch(objectName, cleanedRecords, options);
            case UPSERT:
                return processUpsertBatch(objectName, cleanedRecords, externalIdField, options);
            default:
                throw new IllegalArgumentException("Unknown restore mode: " + mode);
        }
    }
    
    private BatchResult processInsertBatch(String objectName, List<Map<String, String>> records,
                                            RestoreOptions options) throws IOException, ParseException {
        BatchResult result = new BatchResult();
        
        // Use Composite API for small batches, Bulk API for large ones
        if (records.size() <= 200) {
            return processWithCompositeApi(objectName, records, "POST", null);
        } else {
            return processWithBulkApi(objectName, records, "insert", null);
        }
    }
    
    private BatchResult processUpdateBatch(String objectName, List<Map<String, String>> records,
                                            RestoreOptions options) throws IOException, ParseException {
        // Records must have Id field for update
        for (Map<String, String> record : records) {
            if (!record.containsKey("Id") || record.get("Id").isEmpty()) {
                BatchResult result = new BatchResult();
                result.addError("Update requires Id field for all records");
                return result;
            }
        }
        
        if (records.size() <= 200) {
            return processWithCompositeApi(objectName, records, "PATCH", null);
        } else {
            return processWithBulkApi(objectName, records, "update", null);
        }
    }
    
    private BatchResult processUpsertBatch(String objectName, List<Map<String, String>> records,
                                            String externalIdField, RestoreOptions options) 
                                            throws IOException, ParseException {
        
        // Validate external ID field exists in records
        for (Map<String, String> record : records) {
            if (!record.containsKey(externalIdField) || record.get(externalIdField).isEmpty()) {
                BatchResult result = new BatchResult();
                result.addError("Upsert requires external ID field '" + externalIdField + "' for all records");
                return result;
            }
        }
        
        return processWithBulkApi(objectName, records, "upsert", externalIdField);
    }
    
    private BatchResult processWithCompositeApi(String objectName, List<Map<String, String>> records,
                                                 String method, String externalIdField) 
                                                 throws IOException, ParseException {
        BatchResult result = new BatchResult();
        
        String url = instanceUrl + "/services/data/v" + apiVersion + "/composite/sobjects";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("allOrNone", false);
        
        JsonArray recordsArray = new JsonArray();
        for (Map<String, String> record : records) {
            JsonObject recordObj = new JsonObject();
            recordObj.addProperty("attributes", "");
            
            JsonObject attributes = new JsonObject();
            attributes.addProperty("type", objectName);
            recordObj.add("attributes", attributes);
            
            for (Map.Entry<String, String> entry : record.entrySet()) {
                String value = entry.getValue();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    recordObj.addProperty(entry.getKey(), value);
                }
            }
            recordsArray.add(recordObj);
        }
        requestBody.add("records", recordsArray);
        
        HttpPost post = new HttpPost(url);
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(gson.toJson(requestBody), ContentType.APPLICATION_JSON));
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, post, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                logger.warn("Composite API error: {}", responseBody);
                result.addError("API Error: " + responseBody);
                return result;
            }
            
            JsonArray results = JsonParser.parseString(responseBody).getAsJsonArray();
            for (int i = 0; i < results.size(); i++) {
                JsonObject res = results.get(i).getAsJsonObject();
                boolean success = res.get("success").getAsBoolean();
                
                if (success) {
                    result.incrementSuccess();
                    String newId = res.has("id") ? res.get("id").getAsString() : "";
                    result.addCreatedId(newId);
                } else {
                    result.incrementFailure();
                    if (res.has("errors")) {
                        JsonArray errors = res.getAsJsonArray("errors");
                        for (int j = 0; j < errors.size(); j++) {
                            JsonObject error = errors.get(j).getAsJsonObject();
                            String message = error.has("message") ? error.get("message").getAsString() : "Unknown error";
                            result.addError("Record " + (i + 1) + ": " + message);
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    private BatchResult processWithBulkApi(String objectName, List<Map<String, String>> records,
                                            String operation, String externalIdField) 
                                            throws IOException, ParseException {
        BatchResult result = new BatchResult();
        
        // Step 1: Create Bulk Job
        String jobId = createBulkJob(objectName, operation, externalIdField);
        log("Created Bulk API job: " + jobId);
        
        try {
            // Step 2: Upload data
            uploadBulkData(jobId, records);
            log("Uploaded " + records.size() + " records to job");
            
            // Step 3: Close job to start processing
            closeBulkJob(jobId);
            log("Closed job, waiting for processing...");
            
            // Step 4: Wait for completion and get results
            waitForJobCompletion(jobId);
            
            // Step 5: Get results
            result = getBulkJobResults(jobId);
            
        } finally {
            // Cleanup - abort job if still running
            try {
                abortBulkJobIfNeeded(jobId);
            } catch (Exception e) {
                logger.warn("Error cleaning up bulk job", e);
            }
        }
        
        return result;
    }
    
    private String createBulkJob(String objectName, String operation, String externalIdField) 
            throws IOException, ParseException {
        
        String url = instanceUrl + "/services/data/v" + apiVersion + "/jobs/ingest";
        
        JsonObject body = new JsonObject();
        body.addProperty("object", objectName);
        body.addProperty("operation", operation);
        body.addProperty("contentType", "CSV");
        body.addProperty("lineEnding", "LF");
        
        if (externalIdField != null && operation.equals("upsert")) {
            body.addProperty("externalIdFieldName", externalIdField);
        }
        
        HttpPost post = new HttpPost(url);
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(gson.toJson(body), ContentType.APPLICATION_JSON));
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, post, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                throw new IOException("Failed to create bulk job: " + responseBody);
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.get("id").getAsString();
        }
    }
    
    private void uploadBulkData(String jobId, List<Map<String, String>> records) throws IOException, ParseException {
        String url = instanceUrl + "/services/data/v" + apiVersion + "/jobs/ingest/" + jobId + "/batches";
        
        // Convert records to CSV
        StringBuilder csv = new StringBuilder();
        
        // Get all unique field names
        Set<String> allFields = new LinkedHashSet<>();
        for (Map<String, String> record : records) {
            allFields.addAll(record.keySet());
        }
        
        // Write header
        csv.append(String.join(",", allFields)).append("\n");
        
        // Write data rows
        for (Map<String, String> record : records) {
            List<String> values = new ArrayList<>();
            for (String field : allFields) {
                String value = record.getOrDefault(field, "");
                values.add(escapeCsvValue(value));
            }
            csv.append(String.join(",", values)).append("\n");
        }
        
        HttpPatch put = new HttpPatch(url);
        put.setHeader("Authorization", "Bearer " + accessToken);
        put.setHeader("Content-Type", "text/csv");
        put.setEntity(new StringEntity(csv.toString(), ContentType.create("text/csv", "UTF-8")));
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, put, null)) {
            if (response.getCode() >= 400) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Failed to upload bulk data: " + responseBody);
            }
        }
    }
    
    private void closeBulkJob(String jobId) throws IOException, ParseException {
        updateJobState(jobId, "UploadComplete");
    }
    
    private void abortBulkJobIfNeeded(String jobId) throws IOException, ParseException {
        try {
            String state = getJobState(jobId);
            if ("Open".equals(state) || "UploadComplete".equals(state)) {
                updateJobState(jobId, "Aborted");
            }
        } catch (Exception e) {
            // Ignore errors during cleanup
        }
    }
    
    private void updateJobState(String jobId, String state) throws IOException, ParseException {
        String url = instanceUrl + "/services/data/v" + apiVersion + "/jobs/ingest/" + jobId;
        
        JsonObject body = new JsonObject();
        body.addProperty("state", state);
        
        HttpPatch patch = new HttpPatch(url);
        patch.setHeader("Authorization", "Bearer " + accessToken);
        patch.setHeader("Content-Type", "application/json");
        patch.setEntity(new StringEntity(gson.toJson(body), ContentType.APPLICATION_JSON));
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, patch, null)) {
            if (response.getCode() >= 400) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Failed to update job state: " + responseBody);
            }
        }
    }
    
    private String getJobState(String jobId) throws IOException, ParseException {
        String url = instanceUrl + "/services/data/v" + apiVersion + "/jobs/ingest/" + jobId;
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.get("state").getAsString();
        }
    }
    
    private void waitForJobCompletion(String jobId) throws IOException, ParseException {
        int maxWaitSeconds = 300; // 5 minutes max
        int waitedSeconds = 0;
        
        while (waitedSeconds < maxWaitSeconds && !cancelled.get()) {
            String state = getJobState(jobId);
            
            if ("JobComplete".equals(state)) {
                log("Bulk job completed successfully");
                return;
            } else if ("Failed".equals(state) || "Aborted".equals(state)) {
                throw new IOException("Bulk job failed with state: " + state);
            }
            
            try {
                Thread.sleep(2000); // Check every 2 seconds
                waitedSeconds += 2;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Job wait interrupted");
            }
        }
        
        if (cancelled.get()) {
            throw new IOException("Job cancelled by user");
        }
        
        throw new IOException("Job timed out after " + maxWaitSeconds + " seconds");
    }
    
    private BatchResult getBulkJobResults(String jobId) throws IOException, ParseException {
        BatchResult result = new BatchResult();
        
        // Get job info for counts
        String url = instanceUrl + "/services/data/v" + apiVersion + "/jobs/ingest/" + jobId;
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            
            int processed = json.has("numberRecordsProcessed") ? 
                json.get("numberRecordsProcessed").getAsInt() : 0;
            int failed = json.has("numberRecordsFailed") ? 
                json.get("numberRecordsFailed").getAsInt() : 0;
            
            result.setSuccessCount(processed - failed);
            result.setFailureCount(failed);
        }
        
        // Get failed records if any
        if (result.getFailureCount() > 0) {
            String failedUrl = url + "/failedResults";
            HttpGet failedGet = new HttpGet(failedUrl);
            failedGet.setHeader("Authorization", "Bearer " + accessToken);
            
            try (ClassicHttpResponse response = httpClient.executeOpen(null, failedGet, null)) {
                String csv = EntityUtils.toString(response.getEntity());
                // Parse CSV errors
                String[] lines = csv.split("\n");
                for (int i = 1; i < lines.length && i < 20; i++) { // Limit error messages
                    result.addError(lines[i]);
                }
            }
        }
        
        return result;
    }
    
    private List<Map<String, String>> cleanRecordsForRestore(List<Map<String, String>> records,
                                                              RestoreMode mode, String objectName) {
        List<Map<String, String>> cleaned = new ArrayList<>();
        
        // System fields that should not be inserted
        Set<String> systemFields = Set.of(
            "CreatedDate", "CreatedById", "LastModifiedDate", "LastModifiedById",
            "SystemModstamp", "IsDeleted", "LastActivityDate", "LastViewedDate",
            "LastReferencedDate", "attributes"
        );
        
        // Fields starting with underscore are enrichment fields, skip them
        for (Map<String, String> record : records) {
            Map<String, String> cleanRecord = new LinkedHashMap<>();
            
            for (Map.Entry<String, String> entry : record.entrySet()) {
                String field = entry.getKey();
                String value = entry.getValue();
                
                // Skip system fields
                if (systemFields.contains(field)) {
                    continue;
                }
                
                // Skip enrichment fields (start with _ref_)
                if (field.startsWith("_ref_")) {
                    continue;
                }
                
                // For insert mode, skip Id field
                if (mode == RestoreMode.INSERT && field.equals("Id")) {
                    continue;
                }
                
                // Skip empty values
                if (value == null || value.isEmpty() || value.equals("null")) {
                    continue;
                }
                
                cleanRecord.put(field, value);
            }
            
            cleaned.add(cleanRecord);
        }
        
        return cleaned;
    }
    
    private String determineExternalIdField(RelationshipManager.ObjectMetadata metadata, 
                                             RestoreOptions options) {
        // Use specified external ID if provided
        if (options.getExternalIdField() != null && !options.getExternalIdField().isEmpty()) {
            return options.getExternalIdField();
        }
        
        // Use first available external ID field
        if (!metadata.getExternalIdFields().isEmpty()) {
            return metadata.getExternalIdFields().get(0).getName();
        }
        
        return null;
    }
    
    private List<Map<String, String>> readCsvRecords(Path csvPath) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return records;
            }
            
            String[] headers = parseCsvLine(headerLine);
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                Map<String, String> record = new LinkedHashMap<>();
                
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    record.put(headers[i], values[i]);
                }
                
                records.add(record);
            }
        }
        
        return records;
    }
    
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        
        return values.toArray(new String[0]);
    }
    
    private String escapeCsvValue(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private void updateProgress(String objectName, int processed, int total, RestoreResult result) {
        if (progressCallback != null) {
            RestoreProgress progress = new RestoreProgress();
            progress.setCurrentObject(objectName);
            progress.setProcessedRecords(processed);
            progress.setTotalRecords(total);
            progress.setSuccessCount(result.getSuccessCount());
            progress.setFailureCount(result.getFailureCount());
            progress.setPercentComplete((double) processed / total * 100);
            progressCallback.accept(progress);
        }
    }
    
    private void log(String message) {
        logger.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
    
    public void close() {
        try {
            httpClient.close();
            relationshipManager.close();
        } catch (IOException e) {
            logger.warn("Error closing resources", e);
        }
    }
    
    // ==================== Result Classes ====================
    
    public static class RestoreResult {
        private final String objectName;
        private int totalRecords;
        private int successCount;
        private int failureCount;
        private boolean completed;
        private final List<String> errors = new ArrayList<>();
        private final List<String> createdIds = new ArrayList<>();
        
        public RestoreResult(String objectName) {
            this.objectName = objectName;
        }
        
        public void addBatchResult(BatchResult batch) {
            this.successCount += batch.getSuccessCount();
            this.failureCount += batch.getFailureCount();
            this.errors.addAll(batch.getErrors());
            this.createdIds.addAll(batch.getCreatedIds());
        }
        
        public void addError(String error) { errors.add(error); }
        
        public String getObjectName() { return objectName; }
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int total) { this.totalRecords = total; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public List<String> getErrors() { return errors; }
        public List<String> getCreatedIds() { return createdIds; }
    }
    
    public static class BatchResult {
        private int successCount;
        private int failureCount;
        private final List<String> errors = new ArrayList<>();
        private final List<String> createdIds = new ArrayList<>();
        
        public void incrementSuccess() { successCount++; }
        public void incrementFailure() { failureCount++; }
        public void addError(String error) { errors.add(error); }
        public void addCreatedId(String id) { createdIds.add(id); }
        
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int count) { this.successCount = count; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int count) { this.failureCount = count; }
        public List<String> getErrors() { return errors; }
        public List<String> getCreatedIds() { return createdIds; }
        public boolean isSuccess() { return failureCount == 0; }
    }
    
    public static class RestoreProgress {
        private String currentObject;
        private int processedRecords;
        private int totalRecords;
        private int successCount;
        private int failureCount;
        private double percentComplete;
        
        public String getCurrentObject() { return currentObject; }
        public void setCurrentObject(String obj) { this.currentObject = obj; }
        public int getProcessedRecords() { return processedRecords; }
        public void setProcessedRecords(int count) { this.processedRecords = count; }
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int count) { this.totalRecords = count; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int count) { this.successCount = count; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int count) { this.failureCount = count; }
        public double getPercentComplete() { return percentComplete; }
        public void setPercentComplete(double percent) { this.percentComplete = percent; }
    }
    
    public static class RestoreOptions {
        private int batchSize = 200;
        private boolean stopOnError = false;
        private boolean validateBeforeRestore = true;
        private boolean resolveRelationships = true;
        private boolean preserveIds = false;
        private String externalIdField;
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int size) { this.batchSize = size; }
        public boolean isStopOnError() { return stopOnError; }
        public void setStopOnError(boolean stop) { this.stopOnError = stop; }
        public boolean isValidateBeforeRestore() { return validateBeforeRestore; }
        public void setValidateBeforeRestore(boolean validate) { this.validateBeforeRestore = validate; }
        public boolean isResolveRelationships() { return resolveRelationships; }
        public void setResolveRelationships(boolean resolve) { this.resolveRelationships = resolve; }
        public boolean isPreserveIds() { return preserveIds; }
        public void setPreserveIds(boolean preserve) { this.preserveIds = preserve; }
        public String getExternalIdField() { return externalIdField; }
        public void setExternalIdField(String field) { this.externalIdField = field; }
    }
}
