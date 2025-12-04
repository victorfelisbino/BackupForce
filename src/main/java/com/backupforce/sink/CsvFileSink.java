package com.backupforce.sink;

import com.sforce.soap.partner.Field;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CSV file-based data sink - writes backup data to CSV files
 */
public class CsvFileSink implements DataSink {
    private static final Logger logger = LoggerFactory.getLogger(CsvFileSink.class);
    
    private final String outputDirectory;
    
    public CsvFileSink(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
    
    @Override
    public void connect() throws Exception {
        // Ensure output directory exists
        Path path = Paths.get(outputDirectory);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            logger.info("Created output directory: {}", outputDirectory);
        }
    }
    
    @Override
    public void disconnect() {
        // No cleanup needed for file system
    }
    
    @Override
    public boolean testConnection() {
        try {
            Path path = Paths.get(outputDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            // Test write permission
            Path testFile = path.resolve(".test");
            Files.write(testFile, "test".getBytes());
            Files.deleteIfExists(testFile);
            return true;
        } catch (Exception e) {
            logger.error("Cannot write to output directory", e);
            return false;
        }
    }
    
    @Override
    public void prepareSink(String objectName, Field[] fields) throws Exception {
        // No preparation needed for CSV files
    }
    
    @Override
    public int writeData(String objectName, Reader csvReader, String backupId, 
                        ProgressCallback progressCallback) throws Exception {
        if (progressCallback != null) {
            progressCallback.update("Writing to file...");
        }
        
        Path outputPath = Paths.get(outputDirectory, objectName + ".csv");
        int recordCount = 0;
        
        try (BufferedReader reader = new BufferedReader(csvReader);
             BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                if (recordCount > 0) { // Skip header
                    recordCount++;
                }
                if (line.isEmpty() || recordCount == 0) {
                    recordCount = 1;
                }
            }
        }
        
        long fileSize = Files.size(outputPath);
        logger.info("{}: Downloaded {} bytes to {}", objectName, fileSize, outputPath);
        
        if (progressCallback != null) {
            progressCallback.update("Completed - " + (recordCount - 1) + " records");
        }
        
        return recordCount - 1; // Subtract header row
    }
    
    @Override
    public String getDisplayName() {
        return "CSV Files (" + outputDirectory + ")";
    }
    
    @Override
    public String getType() {
        return "CSV";
    }
}
