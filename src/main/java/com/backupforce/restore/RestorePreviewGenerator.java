package com.backupforce.restore;

import com.backupforce.restore.RestoreExecutor.RestoreOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates preview summaries before restore execution.
 * Analyzes objects, estimates API calls, time, and identifies potential issues.
 */
public class RestorePreviewGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(RestorePreviewGenerator.class);
    
    // API call estimation constants
    private static final int BULK_API_BATCH_SIZE = 200;
    private static final int COMPOSITE_API_MAX_RECORDS = 200;
    private static final int RECORDS_PER_SECOND_ESTIMATE = 50; // Conservative estimate
    
    // File size thresholds (in bytes)
    private static final long LARGE_FILE_THRESHOLD = 50 * 1024 * 1024; // 50 MB
    private static final long HUGE_FILE_THRESHOLD = 500 * 1024 * 1024; // 500 MB
    
    /**
     * Main preview for entire restore operation
     */
    public static class RestorePreview {
        private long totalRecords;
        private long totalFileSize;
        private int estimatedApiCalls;
        private int estimatedTimeMinutes;
        private List<ObjectPreview> objectPreviews;
        private List<String> globalWarnings;
        
        public RestorePreview() {
            this.objectPreviews = new ArrayList<>();
            this.globalWarnings = new ArrayList<>();
        }
        
        // Getters and setters
        public long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
        
        public long getTotalFileSize() { return totalFileSize; }
        public void setTotalFileSize(long totalFileSize) { this.totalFileSize = totalFileSize; }
        
        public int getEstimatedApiCalls() { return estimatedApiCalls; }
        public void setEstimatedApiCalls(int estimatedApiCalls) { this.estimatedApiCalls = estimatedApiCalls; }
        
        public int getEstimatedTimeMinutes() { return estimatedTimeMinutes; }
        public void setEstimatedTimeMinutes(int estimatedTimeMinutes) { this.estimatedTimeMinutes = estimatedTimeMinutes; }
        
        public List<ObjectPreview> getObjectPreviews() { return objectPreviews; }
        public void setObjectPreviews(List<ObjectPreview> objectPreviews) { this.objectPreviews = objectPreviews; }
        
        public List<String> getGlobalWarnings() { return globalWarnings; }
        public void setGlobalWarnings(List<String> globalWarnings) { this.globalWarnings = globalWarnings; }
        
        public void addObjectPreview(ObjectPreview preview) {
            this.objectPreviews.add(preview);
        }
        
        public void addGlobalWarning(String warning) {
            this.globalWarnings.add(warning);
        }
    }
    
    /**
     * Preview for individual object
     */
    public static class ObjectPreview {
        private String objectName;
        private long recordCount;
        private long fileSize;
        private String statusIcon; // ✅, ⚠️, ❌
        private List<String> warnings;
        private List<String> errors;
        private int estimatedApiCalls;
        private int estimatedTimeSeconds;
        
        public ObjectPreview(String objectName) {
            this.objectName = objectName;
            this.warnings = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.statusIcon = "✅";
        }
        
        // Getters and setters
        public String getObjectName() { return objectName; }
        public void setObjectName(String objectName) { this.objectName = objectName; }
        
        public long getRecordCount() { return recordCount; }
        public void setRecordCount(long recordCount) { this.recordCount = recordCount; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public String getStatusIcon() { return statusIcon; }
        public void setStatusIcon(String statusIcon) { this.statusIcon = statusIcon; }
        
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        
        public int getEstimatedApiCalls() { return estimatedApiCalls; }
        public void setEstimatedApiCalls(int estimatedApiCalls) { this.estimatedApiCalls = estimatedApiCalls; }
        
        public int getEstimatedTimeSeconds() { return estimatedTimeSeconds; }
        public void setEstimatedTimeSeconds(int estimatedTimeSeconds) { this.estimatedTimeSeconds = estimatedTimeSeconds; }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
            if (this.statusIcon.equals("✅")) {
                this.statusIcon = "⚠️";
            }
        }
        
        public void addError(String error) {
            this.errors.add(error);
            this.statusIcon = "❌";
        }
    }
    
    /**
     * Generate preview for a list of restore objects
     */
    public static RestorePreview generatePreview(List<?> objects, RestoreOptions options) {
        RestorePreview preview = new RestorePreview();
        
        long totalRecords = 0;
        long totalFileSize = 0;
        int totalApiCalls = 0;
        int totalTimeSeconds = 0;
        
        for (Object obj : objects) {
            ObjectPreview objPreview = analyzeObject(obj, options);
            preview.addObjectPreview(objPreview);
            
            totalRecords += objPreview.getRecordCount();
            totalFileSize += objPreview.getFileSize();
            totalApiCalls += objPreview.getEstimatedApiCalls();
            totalTimeSeconds += objPreview.getEstimatedTimeSeconds();
        }
        
        preview.setTotalRecords(totalRecords);
        preview.setTotalFileSize(totalFileSize);
        preview.setEstimatedApiCalls(totalApiCalls);
        preview.setEstimatedTimeMinutes((totalTimeSeconds + 59) / 60); // Round up
        
        // Add global warnings
        if (totalRecords > 100000) {
            preview.addGlobalWarning("Large restore: " + String.format("%,d", totalRecords) + 
                " records may take several hours");
        }
        
        if (totalApiCalls > 5000) {
            preview.addGlobalWarning("High API usage: " + String.format("%,d", totalApiCalls) + 
                " API calls - check your org limits");
        }
        
        if (totalFileSize > HUGE_FILE_THRESHOLD) {
            preview.addGlobalWarning("Very large data size: " + formatSize(totalFileSize) + 
                " - ensure sufficient memory");
        }
        
        return preview;
    }
    
    /**
     * Analyze a single restore object
     */
    private static ObjectPreview analyzeObject(Object obj, RestoreOptions options) {
        // Use reflection to get object properties
        String objectName = getObjectName(obj);
        ObjectPreview preview = new ObjectPreview(objectName);
        
        try {
            long recordCount = getRecordCount(obj);
            preview.setRecordCount(recordCount);
            
            long fileSize = getFileSize(obj);
            preview.setFileSize(fileSize);
            
            // Estimate API calls
            int batchSize = options != null ? options.getBatchSize() : BULK_API_BATCH_SIZE;
            int apiCalls = estimateApiCalls(recordCount, batchSize);
            preview.setEstimatedApiCalls(apiCalls);
            
            // Estimate time
            int timeSeconds = estimateTime(recordCount);
            preview.setEstimatedTimeSeconds(timeSeconds);
            
            // Check for warnings
            detectWarnings(preview, recordCount, fileSize);
            
        } catch (Exception e) {
            logger.error("Error analyzing object {}: {}", objectName, e.getMessage());
            preview.addError("Failed to analyze: " + e.getMessage());
        }
        
        return preview;
    }
    
    /**
     * Extract object name from restore object using reflection
     */
    private static String getObjectName(Object obj) {
        try {
            return (String) obj.getClass().getMethod("getName").invoke(obj);
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Extract record count from restore object using reflection
     */
    private static long getRecordCount(Object obj) {
        try {
            Object count = obj.getClass().getMethod("getRecordCount").invoke(obj);
            if (count instanceof Number) {
                return ((Number) count).longValue();
            }
        } catch (Exception e) {
            logger.debug("Could not get record count: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get file size from restore object
     */
    private static long getFileSize(Object obj) {
        try {
            // Try to get file path
            String filePath = (String) obj.getClass().getMethod("getFilePath").invoke(obj);
            if (filePath != null) {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    return Files.size(path);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get file size: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Estimate API calls needed for restore
     */
    public static int estimateApiCalls(long recordCount, int batchSize) {
        if (recordCount == 0) return 0;
        
        // Bulk API: 1 job creation + 1 per batch + 1 job status check
        int batches = (int) Math.ceil((double) recordCount / batchSize);
        return 1 + batches + 1;
    }
    
    /**
     * Estimate time for restore (in seconds)
     */
    public static int estimateTime(long recordCount) {
        if (recordCount == 0) return 0;
        
        // Conservative estimate: 50 records per second
        int seconds = (int) (recordCount / RECORDS_PER_SECOND_ESTIMATE);
        
        // Minimum 5 seconds, maximum 2 hours
        return Math.min(Math.max(seconds, 5), 7200);
    }
    
    /**
     * Detect potential warnings for an object
     */
    private static void detectWarnings(ObjectPreview preview, long recordCount, long fileSize) {
        // Large record count
        if (recordCount > 100000) {
            preview.addWarning("Large dataset: " + String.format("%,d", recordCount) + 
                " records may take a long time");
        } else if (recordCount > 50000) {
            preview.addWarning("Medium dataset: " + String.format("%,d", recordCount) + 
                " records - allow extra time");
        }
        
        // Large file size
        if (fileSize > HUGE_FILE_THRESHOLD) {
            preview.addWarning("Very large file: " + formatSize(fileSize) + 
                " - may require additional memory");
        } else if (fileSize > LARGE_FILE_THRESHOLD) {
            preview.addWarning("Large file: " + formatSize(fileSize) + 
                " - processing may be slower");
        }
        
        // Low record count (potential issue)
        if (recordCount == 0) {
            preview.addWarning("No records found - verify data source");
        }
    }
    
    /**
     * Format file size in human-readable format
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
