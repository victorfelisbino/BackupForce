package com.backupforce.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages backup history for tracking runs and enabling incremental backups.
 * Stores history in a JSON file in the user's app data directory.
 */
public class BackupHistory {
    private static final Logger logger = LoggerFactory.getLogger(BackupHistory.class);
    private static final String HISTORY_FILE = "backup_history.json";
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    private static BackupHistory instance;
    private final Path historyFile;
    private List<BackupRun> history;
    
    /**
     * Represents a single backup run
     */
    public static class BackupRun {
        private String id;
        private String orgId;
        private String orgName;
        private String username;
        private String startTime;
        private String endTime;
        private String status; // "RUNNING", "COMPLETED", "FAILED", "CANCELLED"
        private String backupType; // "FULL", "INCREMENTAL"
        private String destination; // "CSV", "Snowflake", "PostgreSQL", "SQL Server"
        private String outputPath;
        private int totalObjects;
        private int completedObjects;
        private int failedObjects;
        private long totalRecords;
        private long totalBytes;
        private List<ObjectBackupResult> objectResults;
        private String errorMessage;
        
        public BackupRun() {
            this.id = UUID.randomUUID().toString();
            this.objectResults = new ArrayList<>();
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }
        
        public String getOrgName() { return orgName; }
        public void setOrgName(String orgName) { this.orgName = orgName; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getBackupType() { return backupType; }
        public void setBackupType(String backupType) { this.backupType = backupType; }
        
        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }
        
        public String getOutputPath() { return outputPath; }
        public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
        
        public int getTotalObjects() { return totalObjects; }
        public void setTotalObjects(int totalObjects) { this.totalObjects = totalObjects; }
        
        public int getCompletedObjects() { return completedObjects; }
        public void setCompletedObjects(int completedObjects) { this.completedObjects = completedObjects; }
        
        public int getFailedObjects() { return failedObjects; }
        public void setFailedObjects(int failedObjects) { this.failedObjects = failedObjects; }
        
        public long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
        
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public List<ObjectBackupResult> getObjectResults() { return objectResults; }
        public void setObjectResults(List<ObjectBackupResult> objectResults) { this.objectResults = objectResults; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        /**
         * Get formatted duration
         */
        public String getDuration() {
            if (startTime == null || endTime == null) return "";
            try {
                LocalDateTime start = LocalDateTime.parse(startTime);
                LocalDateTime end = LocalDateTime.parse(endTime);
                long seconds = java.time.Duration.between(start, end).getSeconds();
                if (seconds < 60) return seconds + "s";
                if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
                return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
            } catch (Exception e) {
                return "";
            }
        }
        
        /**
         * Get formatted total size
         */
        public String getFormattedSize() {
            if (totalBytes < 1024) return totalBytes + " B";
            if (totalBytes < 1024 * 1024) return String.format("%.1f KB", totalBytes / 1024.0);
            if (totalBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalBytes / (1024.0 * 1024));
            return String.format("%.2f GB", totalBytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * Represents the backup result for a single object
     */
    public static class ObjectBackupResult {
        private String objectName;
        private String status; // "COMPLETED", "FAILED", "SKIPPED"
        private long recordCount;
        private long byteCount;
        private String lastModifiedDate; // For incremental backup tracking
        private String errorMessage;
        private long durationMs;
        
        public ObjectBackupResult() {}
        
        public ObjectBackupResult(String objectName) {
            this.objectName = objectName;
        }
        
        // Getters and setters
        public String getObjectName() { return objectName; }
        public void setObjectName(String objectName) { this.objectName = objectName; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public long getRecordCount() { return recordCount; }
        public void setRecordCount(long recordCount) { this.recordCount = recordCount; }
        
        public long getByteCount() { return byteCount; }
        public void setByteCount(long byteCount) { this.byteCount = byteCount; }
        
        public String getLastModifiedDate() { return lastModifiedDate; }
        public void setLastModifiedDate(String lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }
    
    private BackupHistory() {
        // Store in user's app data directory
        String appData = System.getProperty("user.home") + "/.backupforce";
        try {
            Files.createDirectories(Paths.get(appData));
        } catch (IOException e) {
            logger.error("Failed to create app data directory", e);
        }
        this.historyFile = Paths.get(appData, HISTORY_FILE);
        loadHistory();
    }
    
    public static synchronized BackupHistory getInstance() {
        if (instance == null) {
            instance = new BackupHistory();
        }
        return instance;
    }
    
    /**
     * Load history from JSON file
     */
    private void loadHistory() {
        if (Files.exists(historyFile)) {
            try {
                String json = Files.readString(historyFile);
                Type listType = new TypeToken<ArrayList<BackupRun>>(){}.getType();
                history = gson.fromJson(json, listType);
                if (history == null) {
                    history = new ArrayList<>();
                }
                logger.info("Loaded {} backup history entries", history.size());
            } catch (Exception e) {
                logger.error("Failed to load backup history", e);
                history = new ArrayList<>();
            }
        } else {
            history = new ArrayList<>();
        }
    }
    
    /**
     * Save history to JSON file
     */
    private void saveHistory() {
        try {
            String json = gson.toJson(history);
            Files.writeString(historyFile, json);
            logger.debug("Saved {} backup history entries", history.size());
        } catch (IOException e) {
            logger.error("Failed to save backup history", e);
        }
    }
    
    /**
     * Start a new backup run
     */
    public BackupRun startBackup(String username, String backupType, String destination, String outputPath, int totalObjects) {
        BackupRun run = new BackupRun();
        run.setUsername(username);
        run.setStartTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        run.setStatus("RUNNING");
        run.setBackupType(backupType);
        run.setDestination(destination);
        run.setOutputPath(outputPath);
        run.setTotalObjects(totalObjects);
        
        history.add(0, run); // Add to beginning (most recent first)
        saveHistory();
        
        logger.info("Started backup run: {} ({})", run.getId(), backupType);
        return run;
    }
    
    /**
     * Update a running backup
     */
    public void updateBackup(BackupRun run) {
        saveHistory();
    }
    
    /**
     * Complete a backup run
     */
    public void completeBackup(BackupRun run, boolean success) {
        run.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        run.setStatus(success ? "COMPLETED" : "FAILED");
        
        // Calculate totals from object results
        long totalRecords = 0;
        long totalBytes = 0;
        int completed = 0;
        int failed = 0;
        
        for (ObjectBackupResult result : run.getObjectResults()) {
            if ("COMPLETED".equals(result.getStatus())) {
                completed++;
                totalRecords += result.getRecordCount();
                totalBytes += result.getByteCount();
            } else if ("FAILED".equals(result.getStatus())) {
                failed++;
            }
        }
        
        run.setCompletedObjects(completed);
        run.setFailedObjects(failed);
        run.setTotalRecords(totalRecords);
        run.setTotalBytes(totalBytes);
        
        saveHistory();
        logger.info("Completed backup run: {} - {} objects, {} records, {}", 
                run.getId(), completed, totalRecords, run.getFormattedSize());
    }
    
    /**
     * Cancel a running backup
     */
    public void cancelBackup(BackupRun run) {
        run.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        run.setStatus("CANCELLED");
        saveHistory();
        logger.info("Cancelled backup run: {}", run.getId());
    }
    
    /**
     * Get all backup history
     */
    public List<BackupRun> getHistory() {
        return new ArrayList<>(history);
    }
    
    /**
     * Get history for a specific user/org
     */
    public List<BackupRun> getHistoryForUser(String username) {
        return history.stream()
                .filter(r -> username.equals(r.getUsername()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get the last successful backup for an object (for incremental backups)
     */
    public Optional<ObjectBackupResult> getLastSuccessfulBackup(String username, String objectName) {
        return history.stream()
                .filter(r -> username.equals(r.getUsername()))
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .flatMap(r -> r.getObjectResults().stream())
                .filter(o -> objectName.equals(o.getObjectName()))
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .filter(o -> o.getLastModifiedDate() != null)
                .findFirst();
    }
    
    /**
     * Get the last backup run for a user
     */
    public Optional<BackupRun> getLastBackup(String username) {
        return history.stream()
                .filter(r -> username.equals(r.getUsername()))
                .findFirst();
    }
    
    /**
     * Get count of successful backups
     */
    public int getSuccessfulBackupCount(String username) {
        return (int) history.stream()
                .filter(r -> username.equals(r.getUsername()))
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .count();
    }
    
    /**
     * Clear old history entries (keep last N entries)
     */
    public void pruneHistory(int keepCount) {
        if (history.size() > keepCount) {
            history = new ArrayList<>(history.subList(0, keepCount));
            saveHistory();
            logger.info("Pruned backup history to {} entries", keepCount);
        }
    }
    
    /**
     * Delete a specific backup run from history
     */
    public void deleteBackupRun(String runId) {
        history.removeIf(r -> runId.equals(r.getId()));
        saveHistory();
    }
}
