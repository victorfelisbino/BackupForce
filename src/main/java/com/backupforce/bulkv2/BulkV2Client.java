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

    public void queryObject(String objectName, String outputFolder) throws IOException, InterruptedException, ParseException {
        logger.info("Starting Bulk API v2 query for: {}", objectName);
        
        // Step 1: Create query job
        String jobId = createQueryJob(objectName);
        logger.info("{}: Job created with ID: {}", objectName, jobId);
        
        // Step 2: Poll for job completion
        waitForJobCompletion(jobId, objectName);
        
        // Step 3: Download results
        downloadResults(jobId, objectName, outputFolder);
        
        logger.info("{}: Query completed successfully", objectName);
    }

    private String createQueryJob(String objectName) throws IOException, ParseException {
        // First, get all field names for this object
        String fields = getObjectFields(objectName);
        
        String url = String.format("%s/services/data/v%s/jobs/query", instanceUrl, apiVersion);
        
        JsonObject jobRequest = new JsonObject();
        jobRequest.addProperty("operation", "query");
        jobRequest.addProperty("query", "SELECT " + fields + " FROM " + objectName);
        
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
                    logger.warn("{}: Object not supported by Bulk API - skipping", objectName);
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
            
            for (int i = 0; i < fields.size(); i++) {
                JsonObject field = fields.get(i).getAsJsonObject();
                String fieldName = field.get("name").getAsString();
                String fieldType = field.has("type") ? field.get("type").getAsString() : "";
                
                // Skip compound fields that can't be queried in Bulk API
                if (fieldType.equals("address") || fieldType.equals("location")) {
                    logger.debug("{}: Skipping compound field: {}", objectName, fieldName);
                    continue;
                }
                
                if (fieldNames.length() > 0) {
                    fieldNames.append(", ");
                }
                fieldNames.append(fieldName);
            }
            
            if (fieldNames.length() == 0) {
                throw new IOException("No queryable fields found for object");
            }
            
            return fieldNames.toString();
        }
    }

    private void waitForJobCompletion(String jobId, String objectName) throws IOException, InterruptedException, ParseException {
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

    public void close() throws IOException {
        httpClient.close();
    }
}
