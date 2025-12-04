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
        
        CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvReader);
        List<String> headers = parser.getHeaderNames();
        
        if (headers.isEmpty()) {
            logger.warn("{}: No data to load", objectName);
            return 0;
        }
        
        // Auto-create table if it doesn't exist (based on CSV headers)
        if (!tableExists(tableName)) {
            if (progressCallback != null) {
                progressCallback.update("Creating table...");
            }
            createTableFromHeaders(tableName, headers);
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
        
        int recordCount = 0;
        int batchSize = dialect.getOptimalBatchSize();
        
        if (progressCallback != null) {
            progressCallback.update("Inserting records...");
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
            
            for (CSVRecord record : parser) {
                for (int i = 0; i < headers.size(); i++) {
                    String value = record.get(i);
                    if (value == null || value.trim().isEmpty()) {
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
                    logger.debug("{}: Loaded {} records", objectName, recordCount);
                }
            }
            
            // Execute remaining batch
            if (recordCount % batchSize != 0) {
                pstmt.executeBatch();
            }
            
            logger.info("{}: Successfully loaded {} records", objectName, recordCount);
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
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        
        // Add columns from CSV headers - use generous VARCHAR sizes since we don't have type info
        for (String header : headers) {
            String columnName = dialect.sanitizeColumnName(header);
            // Use large VARCHAR for all fields since we don't have Salesforce metadata
            String columnType = dialect.getVarcharType(16777216); // 16MB max
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
