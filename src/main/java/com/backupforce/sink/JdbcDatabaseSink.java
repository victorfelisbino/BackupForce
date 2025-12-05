package com.backupforce.sink;

import com.sforce.soap.partner.Field;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic JDBC database sink - works with any JDBC-compliant database
 * Supports Snowflake, SQL Server, PostgreSQL, MySQL, Oracle, etc.
 */
public class JdbcDatabaseSink implements DataSink {
    private static final Logger logger = LoggerFactory.getLogger(JdbcDatabaseSink.class);
    
    private final String jdbcUrl;
    private final Properties connectionProperties;
    private final DatabaseDialect dialect;
    private final String displayName;
    private Connection connection;
    
    public JdbcDatabaseSink(String jdbcUrl, Properties connectionProperties, 
                           DatabaseDialect dialect, String displayName) {
        this.jdbcUrl = jdbcUrl;
        this.connectionProperties = connectionProperties;
        this.dialect = dialect;
        this.displayName = displayName;
    }
    
    @Override
    public void connect() throws Exception {
        logger.info("Connecting to database: {}", displayName);
        connection = DriverManager.getConnection(jdbcUrl, connectionProperties);
        logger.info("Successfully connected to database");
    }
    
    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Disconnected from database");
            } catch (SQLException e) {
                logger.error("Error disconnecting from database", e);
            }
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            connect();
            disconnect();
            return true;
        } catch (Exception e) {
            logger.error("Connection test failed", e);
            return false;
        }
    }
    
    @Override
    public void prepareSink(String objectName, Field[] fields) throws Exception {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        
        String tableName = dialect.sanitizeTableName(objectName);
        
        // Check if table exists
        if (tableExists(tableName)) {
            logger.info("Table {} already exists", tableName);
            return;
        }
        
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        
        for (Field field : fields) {
            String fieldName = dialect.sanitizeColumnName(field.getName());
            String fieldType = dialect.mapSalesforceType(field.getType().name(), field.getLength());
            
            // Skip compound fields or unmapped types
            if (fieldType == null) {
                continue;
            }
            
            columnDefs.add("  " + fieldName + " " + fieldType);
        }
        
        // Add metadata columns
        columnDefs.add("  " + dialect.sanitizeColumnName("BACKUP_TIMESTAMP") + " " + dialect.getTimestampType() + " DEFAULT " + dialect.getCurrentTimestamp());
        columnDefs.add("  " + dialect.sanitizeColumnName("BACKUP_ID") + " " + dialect.getVarcharType(50));
        
        createTableSQL.append(String.join(",\n", columnDefs));
        createTableSQL.append("\n)");
        
        logger.info("Creating table: {}", tableName);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL.toString());
            logger.info("Table {} created successfully", tableName);
        }
    }
    
    @Override
    public int writeData(String objectName, Reader csvReader, String backupId, 
                        ProgressCallback progressCallback) throws Exception {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        
        String tableName = dialect.sanitizeTableName(objectName);
        
        if (progressCallback != null) {
            progressCallback.update("Parsing CSV data...");
        }
        
        int recordCount = 0;
        
        try (CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvReader)) {
            List<String> headers = parser.getHeaderNames();
            
            if (headers.isEmpty()) {
                logger.warn("{}: No data to load", objectName);
                return 0;
            }
            
            logger.info("{}: CSV has {} columns", objectName, headers.size());
            
            // Check if this CSV has blob file paths
            int blobPathColumnIndex = -1;
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equals("BLOB_FILE_PATH")) {
                    blobPathColumnIndex = i;
                    logger.info("{}: Found BLOB_FILE_PATH column - will load blob data", objectName);
                    break;
                }
            }
            
            // Auto-create table if it doesn't exist (based on CSV headers)
            if (!tableExists(tableName)) {
                if (progressCallback != null) {
                    progressCallback.update("Creating table...");
                }
                createTableFromHeaders(tableName, headers, blobPathColumnIndex >= 0);
            }
            
            // Prepare INSERT statement
            String sanitizedHeaders = headers.stream()
                .map(dialect::sanitizeColumnName)
                .collect(Collectors.joining(", "));
            
            String placeholders = headers.stream()
                .map(h -> "?")
                .collect(Collectors.joining(", "));
            
            String insertSQL = String.format(
                "INSERT INTO %s (%s, %s, %s) VALUES (%s, ?, ?)",
                tableName, sanitizedHeaders, 
                dialect.sanitizeColumnName("BACKUP_ID"),
                dialect.sanitizeColumnName("BACKUP_TIMESTAMP"),
                placeholders
            );
            
            int batchSize = dialect.getOptimalBatchSize();
            
            if (progressCallback != null) {
                progressCallback.update("Inserting records...");
            }
            
            logger.info("{}: Starting batch insert with batch size {}", objectName, batchSize);
            
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
                
                for (CSVRecord record : parser) {
                    for (int i = 0; i < headers.size(); i++) {
                        String value = record.get(i);
                        
                        // Special handling for BLOB_FILE_PATH - load actual blob data
                        if (i == blobPathColumnIndex && value != null && !value.trim().isEmpty()) {
                            String blobData = loadBlobAsBase64(value);
                            pstmt.setString(i + 1, blobData != null ? blobData : value);
                        } else if (value == null || value.trim().isEmpty()) {
                            pstmt.setNull(i + 1, Types.VARCHAR);
                        } else {
                            pstmt.setString(i + 1, value);
                        }
                    }
                    pstmt.setString(headers.size() + 1, backupId);
                    pstmt.setTimestamp(headers.size() + 2, currentTimestamp);
                    pstmt.addBatch();
                    recordCount++;
                    
                    if (recordCount % batchSize == 0) {
                        pstmt.executeBatch();
                        if (progressCallback != null) {
                            progressCallback.update("Inserted " + recordCount + " records...");
                        }
                        logger.info("{}: Loaded {} records so far", objectName, recordCount);
                    }
                }
                
                // Execute remaining batch
                if (recordCount % batchSize != 0) {
                    pstmt.executeBatch();
                    logger.info("{}: Executed final batch, total records: {}", objectName, recordCount);
                }
                
                logger.info("{}: Successfully loaded {} records to database", objectName, recordCount);
            }
        }
        
        if (progressCallback != null) {
            progressCallback.update("Completed - " + recordCount + " records");
        }
        
        return recordCount;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String getType() {
        return "JDBC";
    }
    
    private void createTableFromHeaders(String tableName, List<String> headers) throws SQLException {
        createTableFromHeaders(tableName, headers, false);
    }
    
    private void createTableFromHeaders(String tableName, List<String> headers, boolean hasBlobData) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        
        // Add columns from CSV headers
        for (String header : headers) {
            String columnName = dialect.sanitizeColumnName(header);
            String columnType;
            
            // Special handling for BLOB_FILE_PATH - store as large VARCHAR for base64 data
            if (header.equals("BLOB_FILE_PATH") && hasBlobData) {
                columnType = dialect.getVarcharType(16777216); // 16MB max for base64 blob data
                logger.info("{}: BLOB_FILE_PATH column will store base64-encoded blob data", tableName);
            } else {
                // Use large VARCHAR for all other fields since we don't have Salesforce metadata
                columnType = dialect.getVarcharType(16777216); // 16MB max
            }
            
            columnDefs.add("  " + columnName + " " + columnType);
        }
        
        // Add metadata columns
        columnDefs.add("  " + dialect.sanitizeColumnName("BACKUP_ID") + " " + dialect.getVarcharType(50));
        columnDefs.add("  " + dialect.sanitizeColumnName("BACKUP_TIMESTAMP") + " " + dialect.getTimestampType());
        
        createTableSQL.append(String.join(",\n", columnDefs));
        createTableSQL.append("\n)");
        
        logger.info("Creating table from CSV headers: {}", tableName);
        logger.debug("CREATE TABLE SQL: {}", createTableSQL.toString());
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL.toString());
            logger.info("Table {} created successfully with {} columns", tableName, headers.size());
        }
    }
    
    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String schema = connectionProperties.getProperty("schema");
        String database = connectionProperties.getProperty("db");
        
        try (ResultSet rs = meta.getTables(database, schema, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
    
    /**
     * Get the last backup timestamp for incremental backups
     * Returns null if table doesn't exist or has no data
     */
    public java.sql.Timestamp getLastBackupTimestamp(String tableName) throws SQLException {
        if (!tableExists(tableName)) {
            return null;
        }
        
        String timestampCol = dialect.sanitizeColumnName("BACKUP_TIMESTAMP");
        String query = String.format("SELECT MAX(%s) as LAST_BACKUP FROM %s", timestampCol, tableName);
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getTimestamp("LAST_BACKUP");
            }
        }
        return null;
    }
    
    /**
     * Check if CSV has LastModifiedDate column for delta support
     */
    private boolean hasLastModifiedDate(List<String> headers) {
        return headers.stream()
            .anyMatch(h -> h.equalsIgnoreCase("LastModifiedDate"));
    }
    
    /**
     * Load blob file and encode as base64 for database storage
     * Returns null if file cannot be loaded
     */
    private String loadBlobAsBase64(String blobPath) {
        if (blobPath == null || blobPath.trim().isEmpty()) {
            return null;
        }
        
        try {
            // blobPath format: "ObjectName_blobs/RecordId.bin"
            // We need to find this relative to the backup output folder
            
            // Try multiple resolution strategies:
            java.nio.file.Path filePath = null;
            
            // Strategy 1: Check if it's already an absolute path
            java.nio.file.Path testPath = java.nio.file.Paths.get(blobPath);
            if (testPath.isAbsolute() && java.nio.file.Files.exists(testPath)) {
                filePath = testPath;
            }
            
            // Strategy 2: Relative to current working directory
            if (filePath == null) {
                testPath = java.nio.file.Paths.get(System.getProperty("user.dir"), blobPath);
                if (java.nio.file.Files.exists(testPath)) {
                    filePath = testPath;
                }
            }
            
            // Strategy 3: Check common backup locations
            if (filePath == null) {
                String[] commonDirs = {"E:\\Staging Backup", "C:\\Backups", "."};
                for (String dir : commonDirs) {
                    testPath = java.nio.file.Paths.get(dir, blobPath);
                    if (java.nio.file.Files.exists(testPath)) {
                        filePath = testPath;
                        break;
                    }
                }
            }
            
            if (filePath != null && java.nio.file.Files.exists(filePath)) {
                byte[] blobBytes = java.nio.file.Files.readAllBytes(filePath);
                String base64 = java.util.Base64.getEncoder().encodeToString(blobBytes);
                
                // Log only for larger files to avoid spam
                if (blobBytes.length > 10000) {
                    logger.debug("Loaded blob from {} ({} KB -> {} KB base64)", 
                        blobPath, blobBytes.length / 1024, base64.length() / 1024);
                }
                
                return base64;
            } else {
                logger.warn("Blob file not found: {} (tried multiple locations)", blobPath);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to load blob from {}: {}", blobPath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Database-specific dialect for SQL generation and type mapping
     */
    public interface DatabaseDialect {
        String sanitizeTableName(String name);
        String sanitizeColumnName(String name);
        String mapSalesforceType(String sfType, int length);
        String getTimestampType();
        String getCurrentTimestamp();
        String getVarcharType(int length);
        int getOptimalBatchSize();
    }
}
