package com.backupforce;

import com.backupforce.auth.SalesforceAuth;
import com.backupforce.bulkv2.BulkV2Client;
import com.backupforce.config.Config;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupRunner {
    private static final Logger logger = LoggerFactory.getLogger(BackupRunner.class);

    public static void main(String[] args) {
        if (args.length == 0 || !args[0].equals("--config")) {
            System.err.println("Usage: java -jar backupforce-v2.jar --config <config-file>");
            System.exit(1);
        }

        String configFile = args[1];
        
        try {
            logger.info("BackupForce v2 - Using Bulk API v2");
            logger.info("Config file: {}", configFile);
            
            Config config = new Config(configFile);
            
            // Create output directory
            File outputDir = new File(config.getOutputFolder());
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Step 1: Authenticate using Partner API (SOAP) to get session
            logger.info("Authenticating to Salesforce...");
            ConnectorConfig partnerConfig = new ConnectorConfig();
            partnerConfig.setUsername(config.getUsername());
            partnerConfig.setPassword(config.getPassword());
            partnerConfig.setAuthEndpoint(config.getServerUrl() + "/services/Soap/u/" + config.getApiVersion());
            
            PartnerConnection connection = new PartnerConnection(partnerConfig);
            
            // Extract session info for REST API
            String sessionId = partnerConfig.getSessionId();
            String instanceUrl = partnerConfig.getServiceEndpoint();
            // Remove the SOAP endpoint part to get base instance URL
            instanceUrl = instanceUrl.substring(0, instanceUrl.indexOf("/services"));
            
            logger.info("Authenticated successfully");
            logger.info("Instance URL: {}", instanceUrl);
            
            // Step 2: Get list of all objects
            logger.info("Retrieving list of all objects...");
            DescribeGlobalResult dgr = connection.describeGlobal();
            DescribeGlobalSObjectResult[] sobjects = dgr.getSobjects();
            
            List<String> objectsToBackup = new ArrayList<>();
            Set<String> excludeSet = config.getBackupObjectsExclude();
            excludeSet.add("attachment"); // Always exclude attachments
            
            String backupObjects = config.getBackupObjects();
            
            for (DescribeGlobalSObjectResult sobject : sobjects) {
                String objectName = sobject.getName();
                
                // Skip excluded objects
                if (excludeSet.contains(objectName.toLowerCase())) {
                    continue;
                }
                
                // Skip non-queryable objects
                if (!sobject.isQueryable()) {
                    continue;
                }
                
                // If backup.objects=*, include all queryable objects
                if (backupObjects.equals("*")) {
                    objectsToBackup.add(objectName);
                } else {
                    // Check if object is in the list
                    String[] objectList = backupObjects.split(",");
                    for (String obj : objectList) {
                        if (obj.trim().equalsIgnoreCase(objectName)) {
                            objectsToBackup.add(objectName);
                            break;
                        }
                    }
                }
            }
            
            logger.info("Found {} objects to backup", objectsToBackup.size());
            
            // Step 3: Process objects using Bulk API v2
            BulkV2Client bulkClient = new BulkV2Client(instanceUrl, sessionId, config.getApiVersion());
            
            // Progress tracking
            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger successful = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            int totalObjects = objectsToBackup.size();
            long startTime = System.currentTimeMillis();
            
            // Process objects with thread pool for parallel execution
            int threadPoolSize = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
            
            for (String objectName : objectsToBackup) {
                executor.submit(() -> {
                    try {
                        bulkClient.queryObject(objectName, config.getOutputFolder());
                        successful.incrementAndGet();
                    } catch (Exception e) {
                        logger.error("Failed to backup {}: {}", objectName, e.getMessage());
                        failed.incrementAndGet();
                    } finally {
                        int completedCount = completed.incrementAndGet();
                        if (completedCount % 10 == 0 || completedCount == totalObjects) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double avgTimePerObject = (double) elapsed / completedCount;
                            long remaining = (long) ((totalObjects - completedCount) * avgTimePerObject);
                            logger.info("Progress: {}/{} objects ({} successful, {} failed) - ETA: {} seconds",
                                completedCount, totalObjects, successful.get(), failed.get(), remaining / 1000);
                        }
                    }
                });
            }
            
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
            
            bulkClient.close();
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("=".repeat(60));
            logger.info("Backup completed!");
            logger.info("Total objects: {}", totalObjects);
            logger.info("Successful: {}", successful.get());
            logger.info("Failed: {}", failed.get());
            logger.info("Total time: {} seconds", totalTime / 1000);
            logger.info("Output folder: {}", config.getOutputFolder());
            logger.info("=".repeat(60));
            
        } catch (Exception e) {
            logger.error("Backup failed", e);
            System.exit(1);
        }
    }
}
