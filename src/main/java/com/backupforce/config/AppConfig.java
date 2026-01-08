package com.backupforce.config;

/**
 * Application-wide configuration constants.
 * Centralized config to avoid magic numbers scattered across the codebase.
 */
public class AppConfig {
    
    // ============================================
    // Query & Data Loading Limits
    // ============================================
    
    /**
     * Default max number of search results to display in UI tables.
     * Used by RestoreWizardController for initial record queries.
     */
    public static final int MAX_SEARCH_RESULTS = 200;
    
    /**
     * Maximum number of related records to load per table when resolving relationships.
     * Prevents memory issues when following deep relationship chains.
     */
    public static final int MAX_RECORDS_PER_TABLE = 1000;
    
    /**
     * Maximum number of records to load when "Load All" is enabled.
     * This is a safety limit to prevent OOM errors.
     */
    public static final int MAX_LOAD_ALL_LIMIT = 50000;
    
    // ============================================
    // Bulk API Settings
    // ============================================
    
    /**
     * Batch size for Bulk API 2.0 operations.
     * Salesforce recommends 200 records per batch for optimal performance.
     */
    public static final int BULK_API_BATCH_SIZE = 200;
    
    /**
     * Maximum number of retry attempts for failed Bulk API operations.
     */
    public static final int BULK_API_MAX_RETRIES = 3;
    
    /**
     * Delay in milliseconds between Bulk API retry attempts.
     */
    public static final long BULK_API_RETRY_DELAY_MS = 2000;
    
    // ============================================
    // OAuth & Authentication
    // ============================================
    
    /**
     * Default OAuth flow timeout in seconds.
     */
    public static final int OAUTH_TIMEOUT_SECONDS = 180; // 3 minutes
    
    /**
     * Refresh token expiry threshold in days.
     * Tokens older than this will be discarded and require re-authentication.
     */
    public static final int REFRESH_TOKEN_MAX_AGE_DAYS = 90;
    
    // ============================================
    // UI & Display Settings
    // ============================================
    
    /**
     * Maximum length of text to display in preview tables before truncating.
     */
    public static final int MAX_PREVIEW_TEXT_LENGTH = 100;
    
    /**
     * Maximum number of validation errors to show in error dialogs.
     */
    public static final int MAX_VALIDATION_ERRORS_SHOWN = 50;
    
    // ============================================
    // File & Directory Settings
    // ============================================
    
    /**
     * Name of the backup manifest file.
     */
    public static final String MANIFEST_FILENAME = "backup_manifest.json";
    
    /**
     * Config directory name in user home.
     */
    public static final String CONFIG_DIR_NAME = ".backupforce";
    
    private AppConfig() {
        // Utility class - no instantiation
    }
}
