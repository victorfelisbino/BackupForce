package com.backupforce.scheduler;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * Represents a scheduled backup configuration.
 * Supports daily, weekly, and monthly schedules with specific time settings.
 */
public class BackupSchedule {
    
    public enum Frequency {
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly");
        
        private final String displayName;
        
        Frequency(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private String id;
    private String name;
    private boolean enabled;
    
    // Salesforce connection info
    private String salesforceUsername;
    private String salesforceInstanceUrl;
    private boolean useSandbox;
    
    // Database target (optional)
    private String databaseConnectionId; // Reference to saved connection
    private boolean exportToDatabase;
    
    // Schedule settings
    private Frequency frequency;
    private LocalTime scheduledTime;
    private Set<DayOfWeek> daysOfWeek; // For weekly schedules
    private int dayOfMonth; // For monthly schedules (1-31, 0 = last day)
    private String timeZone;
    
    // Backup settings
    private List<String> selectedObjects;
    private String outputFolder;
    private boolean incremental;
    private boolean compress;
    private boolean preserveRelationships;
    private boolean includeRelatedRecords;
    private int recordLimit; // 0 = no limit
    
    // Notification settings
    private boolean notifyOnSuccess;
    private boolean notifyOnFailure;
    private String notificationEmail;
    
    // Tracking
    private long lastRunTime;
    private String lastRunStatus;
    private long nextRunTime;
    private long createdAt;
    private long modifiedAt;
    
    public BackupSchedule() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.frequency = Frequency.DAILY;
        this.scheduledTime = LocalTime.of(2, 0); // Default: 2:00 AM
        this.daysOfWeek = new HashSet<>();
        this.daysOfWeek.add(DayOfWeek.MONDAY); // Default for weekly
        this.dayOfMonth = 1; // Default for monthly
        this.timeZone = TimeZone.getDefault().getID();
        this.selectedObjects = new ArrayList<>();
        this.incremental = true; // Default to incremental
        this.compress = false;
        this.preserveRelationships = true;
        this.includeRelatedRecords = false;
        this.recordLimit = 0;
        this.notifyOnFailure = true;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public BackupSchedule(String name) {
        this();
        this.name = name;
    }
    
    // ==================== Getters and Setters ====================
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name; 
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public String getSalesforceUsername() { return salesforceUsername; }
    public void setSalesforceUsername(String salesforceUsername) { 
        this.salesforceUsername = salesforceUsername;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public String getSalesforceInstanceUrl() { return salesforceInstanceUrl; }
    public void setSalesforceInstanceUrl(String salesforceInstanceUrl) { 
        this.salesforceInstanceUrl = salesforceInstanceUrl;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isUseSandbox() { return useSandbox; }
    public void setUseSandbox(boolean useSandbox) { 
        this.useSandbox = useSandbox;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public String getDatabaseConnectionId() { return databaseConnectionId; }
    public void setDatabaseConnectionId(String databaseConnectionId) { 
        this.databaseConnectionId = databaseConnectionId;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isExportToDatabase() { return exportToDatabase; }
    public void setExportToDatabase(boolean exportToDatabase) { 
        this.exportToDatabase = exportToDatabase;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { 
        this.frequency = frequency;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { 
        this.scheduledTime = scheduledTime;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public Set<DayOfWeek> getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(Set<DayOfWeek> daysOfWeek) { 
        this.daysOfWeek = daysOfWeek;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public int getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(int dayOfMonth) { 
        this.dayOfMonth = dayOfMonth;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { 
        this.timeZone = timeZone;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public List<String> getSelectedObjects() { return selectedObjects; }
    public void setSelectedObjects(List<String> selectedObjects) { 
        this.selectedObjects = selectedObjects;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public String getOutputFolder() { return outputFolder; }
    public void setOutputFolder(String outputFolder) { 
        this.outputFolder = outputFolder;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isIncremental() { return incremental; }
    public void setIncremental(boolean incremental) { 
        this.incremental = incremental;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isCompress() { return compress; }
    public void setCompress(boolean compress) { 
        this.compress = compress;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isPreserveRelationships() { return preserveRelationships; }
    public void setPreserveRelationships(boolean preserveRelationships) { 
        this.preserveRelationships = preserveRelationships;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isIncludeRelatedRecords() { return includeRelatedRecords; }
    public void setIncludeRelatedRecords(boolean includeRelatedRecords) { 
        this.includeRelatedRecords = includeRelatedRecords;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public int getRecordLimit() { return recordLimit; }
    public void setRecordLimit(int recordLimit) { 
        this.recordLimit = recordLimit;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isNotifyOnSuccess() { return notifyOnSuccess; }
    public void setNotifyOnSuccess(boolean notifyOnSuccess) { 
        this.notifyOnSuccess = notifyOnSuccess;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public boolean isNotifyOnFailure() { return notifyOnFailure; }
    public void setNotifyOnFailure(boolean notifyOnFailure) { 
        this.notifyOnFailure = notifyOnFailure;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public String getNotificationEmail() { return notificationEmail; }
    public void setNotificationEmail(String notificationEmail) { 
        this.notificationEmail = notificationEmail;
        this.modifiedAt = System.currentTimeMillis();
    }
    
    public long getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(long lastRunTime) { this.lastRunTime = lastRunTime; }
    
    public String getLastRunStatus() { return lastRunStatus; }
    public void setLastRunStatus(String lastRunStatus) { this.lastRunStatus = lastRunStatus; }
    
    public long getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(long nextRunTime) { this.nextRunTime = nextRunTime; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get a human-readable description of the schedule.
     */
    public String getScheduleDescription() {
        StringBuilder sb = new StringBuilder();
        
        switch (frequency) {
            case DAILY:
                sb.append("Daily at ");
                break;
            case WEEKLY:
                sb.append("Weekly on ");
                if (!daysOfWeek.isEmpty()) {
                    List<String> days = daysOfWeek.stream()
                        .sorted()
                        .map(d -> d.toString().substring(0, 3))
                        .collect(java.util.stream.Collectors.toList());
                    sb.append(String.join(", ", days));
                }
                sb.append(" at ");
                break;
            case MONTHLY:
                sb.append("Monthly on day ");
                sb.append(dayOfMonth == 0 ? "last" : dayOfMonth);
                sb.append(" at ");
                break;
        }
        
        sb.append(scheduledTime.toString());
        return sb.toString();
    }
    
    /**
     * Get a short status string.
     */
    public String getStatusString() {
        if (!enabled) {
            return "Disabled";
        }
        if (lastRunStatus != null) {
            return lastRunStatus;
        }
        return "Pending";
    }
    
    @Override
    public String toString() {
        return String.format("BackupSchedule{name='%s', frequency=%s, time=%s, enabled=%s}", 
            name, frequency, scheduledTime, enabled);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackupSchedule that = (BackupSchedule) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
