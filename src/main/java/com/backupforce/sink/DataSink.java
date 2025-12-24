package com.backupforce.sink;

import com.sforce.soap.partner.Field;

import java.io.Reader;

/**
 * Interface for different backup destinations (databases, cloud storage, files, etc.)
 */
public interface DataSink {
    
    /**
     * Establish connection to the sink
     */
    void connect() throws Exception;
    
    /**
     * Close connection to the sink
     */
    void disconnect();
    
    /**
     * Test if connection can be established
     */
    boolean testConnection();
    
    /**
     * Prepare the sink for receiving data (e.g., create table, create bucket, create folder)
     * 
     * @param objectName Salesforce object name
     * @param fields Salesforce field metadata
     */
    void prepareSink(String objectName, Field[] fields) throws Exception;
    
    /**
     * Write data from CSV stream to the sink
     * 
     * @param objectName Salesforce object name
     * @param csvReader CSV data reader
     * @param backupId Unique backup identifier
     * @param progressCallback Progress callback
     * @return Number of records written
     */
    int writeData(String objectName, Reader csvReader, String backupId, 
                  ProgressCallback progressCallback) throws Exception;
    
    /**
     * Get display name for this sink
     */
    String getDisplayName();
    
    /**
     * Get sink type identifier
     */
    String getType();
    
    /**
     * Set whether to recreate tables (drop and create fresh)
     * Default is false (incremental/append mode)
     * 
     * @param recreate true to drop tables before creating, false for incremental
     */
    default void setRecreateTables(boolean recreate) {
        // Default: no-op for sinks that don't support this
    }
    
    /**
     * Set whether to skip tables if record count matches Salesforce
     * Default is false
     * 
     * @param skip true to skip tables with matching counts
     */
    default void setSkipMatchingCounts(boolean skip) {
        // Default: no-op for sinks that don't support this
    }
    
    /**
     * Drop existing table if it exists
     * 
     * @param objectName Salesforce object name
     */
    default void dropTable(String objectName) throws Exception {
        // Default: no-op for sinks that don't support this
    }
    
    @FunctionalInterface
    interface ProgressCallback {
        void update(String status);
    }
}
