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
    
    @FunctionalInterface
    interface ProgressCallback {
        void update(String status);
    }
}
