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
    private boolean recreateTables = false;  // Default: incremental mode
    private boolean skipMatchingCounts = false;  // Default: don't skip based on count matching
    private final boolean externalConnection;  // If true, don't close the connection on disconnect
    
    public JdbcDatabaseSink(String jdbcUrl, Properties connectionProperties, 
                           DatabaseDialect dialect, String displayName) {
        this.jdbcUrl = jdbcUrl;
        this.connectionProperties = connectionProperties;
        this.dialect = dialect;
        this.displayName = displayName;
        this.externalConnection = false;
    }
    
    /**
     * Create a JdbcDatabaseSink with a pre-existing connection (e.g., from SSO cache).
     * The connection will NOT be closed when disconnect() is called.
     */
    public JdbcDatabaseSink(Connection existingConnection, DatabaseDialect dialect, String displayName) {
        this.jdbcUrl = null;
        this.connectionProperties = null;
        this.dialect = dialect;
        this.displayName = displayName;
        this.connection = existingConnection;
        this.externalConnection = true;
        logger.info("Using existing connection for database sink: {}", displayName);
    }
    
    @Override
    public void setRecreateTables(boolean recreate) {
        this.recreateTables = recreate;
        logger.info("Recreate tables mode: {}", recreate ? "ENABLED (full reload)" : "DISABLED (incremental)");
    }
    
    /**
     * Returns whether recreate tables mode is enabled
     */
    public boolean isRecreateTables() {
        return this.recreateTables;
    }
    
    /**
     * Set whether to skip tables if record count matches Salesforce
     */
    public void setSkipMatchingCounts(boolean skip) {
        this.skipMatchingCounts = skip;
        logger.info("Skip matching counts mode: {}", skip ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Returns whether skip matching counts mode is enabled
     */
    public boolean isSkipMatchingCounts() {
        return this.skipMatchingCounts;
    }
    
    @Override
    public void dropTable(String objectName) throws Exception {
        ensureConnection();
        
        String tableName = dialect.sanitizeTableName(objectName);
        
        if (tableExists(tableName)) {
            String dropSQL = "DROP TABLE " + tableName;
            logger.info("Dropping table: {}", tableName);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(dropSQL);
                logger.info("Table {} dropped successfully", tableName);
            }
        } else {
            logger.info("Table {} does not exist, nothing to drop", tableName);
        }
    }
    
    /**
     * Ensure connection is valid, reconnecting if necessary.
     * This prevents issues with stale connections during long-running backups.
     */
    private void ensureConnection() throws Exception {
        if (connection == null) {
            connect();
            return;
        }
        
        try {
            // Check if connection is closed or invalid
            if (connection.isClosed()) {
                logger.warn("Connection is closed, reconnecting...");
                connect();
                return;
            }
            
            // Validate connection with 10 second timeout
            if (!connection.isValid(10)) {
                logger.warn("Connection is no longer valid, reconnecting...");
                if (!externalConnection) {
                    try { connection.close(); } catch (Exception ignored) {}
                }
                connection = null;
                connect();
            }
        } catch (SQLException e) {
            logger.warn("Connection validation failed: {}, reconnecting...", e.getMessage());
            if (!externalConnection) {
                try { connection.close(); } catch (Exception ignored) {}
            }
            connection = null;
            connect();
        }
    }
    
    @Override
    public void connect() throws Exception {
        // If we already have an external connection, just verify it's valid
        if (externalConnection && connection != null) {
            try {
                if (connection.isValid(5)) {
                    logger.info("Using existing connection for: {}", displayName);
                    return;
                }
            } catch (SQLException e) {
                logger.warn("External connection validation failed: {}", e.getMessage());
            }
            throw new Exception("External connection is no longer valid");
        }
        
        logger.info("Connecting to database: {}", displayName);
        connection = DriverManager.getConnection(jdbcUrl, connectionProperties);
        logger.info("Successfully connected to database");
    }
    
    @Override
    public void disconnect() {
        if (connection != null && !externalConnection) {
            // Only close if we own the connection (not external)
            try {
                connection.close();
                logger.info("Disconnected from database");
            } catch (SQLException e) {
                logger.error("Error disconnecting from database", e);
            }
        } else if (externalConnection) {
            logger.info("Keeping external connection open for: {}", displayName);
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
        ensureConnection();
        
        String tableName = dialect.sanitizeTableName(objectName);
        
        // Drop table first if recreate mode is enabled
        if (recreateTables && tableExists(tableName)) {
            logger.info("Recreate mode enabled - dropping existing table: {}", tableName);
            dropTable(objectName);
        }
        
        // Check if table exists (after potential drop)
        if (tableExists(tableName)) {
            logger.info("Table {} already exists (incremental mode)", tableName);
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
        
        // Log connection details for debugging
        try {
            logger.info("Connection catalog: {}, schema: {}", connection.getCatalog(), connection.getSchema());
        } catch (SQLException e) {
            logger.debug("Could not get catalog/schema info: {}", e.getMessage());
        }
        
        try (Statement stmt = connection.createStatement()) {
            logger.info("Executing CREATE TABLE SQL: {}", createTableSQL.toString().substring(0, Math.min(200, createTableSQL.length())));
            stmt.execute(createTableSQL.toString());
            logger.info("Table {} created successfully", tableName);
            
            // Explicitly commit if not in autocommit mode
            if (!connection.getAutoCommit()) {
                connection.commit();
                logger.info("CREATE TABLE committed");
            }
        }
    }
    
    @Override
    public int writeData(String objectName, Reader csvReader, String backupId, 
                        ProgressCallback progressCallback) throws Exception {
        ensureConnection();
        
        // Log connection details
        try {
            logger.info("writeData for {}: catalog={}, schema={}, autoCommit={}", 
                objectName, connection.getCatalog(), connection.getSchema(), connection.getAutoCommit());
        } catch (SQLException e) {
            logger.debug("Could not get connection details: {}", e.getMessage());
        }
        
        String tableName = dialect.sanitizeTableName(objectName);
        
        if (progressCallback != null) {
            progressCallback.update("Parsing CSV data...");
        }
        
        int recordCount = 0;
        
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(csvReader)) {
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
            
            // Drop table first if recreate mode is enabled
            if (recreateTables && tableExists(tableName)) {
                logger.info("{}: Recreate mode - dropping existing table", objectName);
                if (progressCallback != null) {
                    progressCallback.update("Dropping existing table...");
                }
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("DROP TABLE " + tableName);
                    logger.info("{}: Table dropped successfully", objectName);
                }
            }
            
            // Auto-create table if it doesn't exist (based on CSV headers)
            boolean hasBlobData = blobPathColumnIndex >= 0;
            if (!tableExists(tableName)) {
                if (progressCallback != null) {
                    progressCallback.update("Creating table...");
                }
                createTableFromHeaders(tableName, headers, hasBlobData);
            } else if (hasBlobData) {
                // Table exists - check if BLOB_DATA column exists, if not add it
                if (!columnExists(tableName, "BLOB_DATA")) {
                    logger.info("{}: Adding BLOB_DATA column to existing table", objectName);
                    addBlobDataColumn(tableName);
                }
            }
            
            // Check if BLOB_DATA column actually exists in the table (for INSERT statement building)
            boolean tableBlobDataColumn = hasBlobData && columnExists(tableName, "BLOB_DATA");
            
            // Prepare INSERT statement
            // If we have blob data AND the table has the BLOB_DATA column, include it in INSERT
            String sanitizedHeaders = headers.stream()
                .map(dialect::sanitizeColumnName)
                .collect(Collectors.joining(", "));
            
            String placeholders = headers.stream()
                .map(h -> "?")
                .collect(Collectors.joining(", "));
            
            String insertSQL;
            if (tableBlobDataColumn) {
                // Add BLOB_DATA column to the insert
                insertSQL = String.format(
                    "INSERT INTO %s (%s, %s, %s, %s) VALUES (%s, ?, ?, ?)",
                    tableName, sanitizedHeaders,
                    dialect.sanitizeColumnName("BLOB_DATA"),
                    dialect.sanitizeColumnName("BACKUP_ID"),
                    dialect.sanitizeColumnName("BACKUP_TIMESTAMP"),
                    placeholders
                );
                logger.info("{}: INSERT will include BLOB_DATA column", objectName);
            } else {
                insertSQL = String.format(
                    "INSERT INTO %s (%s, %s, %s) VALUES (%s, ?, ?)",
                    tableName, sanitizedHeaders, 
                    dialect.sanitizeColumnName("BACKUP_ID"),
                    dialect.sanitizeColumnName("BACKUP_TIMESTAMP"),
                    placeholders
                );
            }
            
            int batchSize = dialect.getOptimalBatchSize();
            
            if (progressCallback != null) {
                progressCallback.update("Inserting records...");
            }
            
            logger.info("{}: Starting batch insert with batch size {}", objectName, batchSize);
            
            // Use final variable for lambda capture
            final boolean insertBlobData = tableBlobDataColumn;
            
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
                
                for (CSVRecord record : parser) {
                    byte[] blobBytes = null;
                    
                    for (int i = 0; i < headers.size(); i++) {
                        String value = record.get(i);
                        
                        // For BLOB_FILE_PATH column, keep the path as-is but also load the blob for BLOB_DATA
                        if (i == blobPathColumnIndex && value != null && !value.trim().isEmpty()) {
                            // Store the file path in BLOB_FILE_PATH column
                            pstmt.setString(i + 1, value);
                            // Load blob bytes for BLOB_DATA column (only if we're inserting blob data)
                            if (insertBlobData) {
                                blobBytes = loadBlobAsBytes(value);
                            }
                        } else if (value == null || value.trim().isEmpty()) {
                            pstmt.setNull(i + 1, Types.VARCHAR);
                        } else {
                            pstmt.setString(i + 1, value);
                        }
                    }
                    
                    // Set BLOB_DATA, BACKUP_ID, and BACKUP_TIMESTAMP
                    int paramIndex = headers.size() + 1;
                    if (insertBlobData) {
                        if (blobBytes != null) {
                            pstmt.setBytes(paramIndex, blobBytes);
                        } else {
                            pstmt.setNull(paramIndex, Types.BINARY);
                        }
                        paramIndex++;
                    }
                    pstmt.setString(paramIndex, backupId);
                    pstmt.setTimestamp(paramIndex + 1, currentTimestamp);
                    
                    pstmt.addBatch();
                    recordCount++;
                    
                    if (recordCount % batchSize == 0) {
                        int[] results = pstmt.executeBatch();
                        int batchSuccess = countSuccessfulInserts(results);
                        if (batchSuccess < batchSize) {
                            logger.warn("{}: Batch had {} failures out of {} records", objectName, batchSize - batchSuccess, batchSize);
                        }
                        if (progressCallback != null) {
                            progressCallback.update("Inserted " + recordCount + " records...");
                        }
                        logger.info("{}: Loaded {} records so far", objectName, recordCount);
                    }
                }
                
                // Execute remaining batch
                int remaining = recordCount % batchSize;
                if (remaining != 0) {
                    int[] results = pstmt.executeBatch();
                    int batchSuccess = countSuccessfulInserts(results);
                    if (batchSuccess < remaining) {
                        logger.warn("{}: Final batch had {} failures out of {} records", objectName, remaining - batchSuccess, remaining);
                    }
                    logger.info("{}: Executed final batch, total records: {}", objectName, recordCount);
                }
                
                logger.info("{}: Successfully loaded {} records to database", objectName, recordCount);
                
                // Explicitly commit to ensure data is persisted
                if (!connection.getAutoCommit()) {
                    connection.commit();
                    logger.info("{}: Transaction committed", objectName);
                }
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
            
            // Special handling for BLOB_FILE_PATH - use BINARY type for actual blob storage
            if (header.equals("BLOB_FILE_PATH") && hasBlobData) {
                // Store actual binary data in a separate column
                columnType = dialect.getVarcharType(4096); // Keep file path for reference
                columnDefs.add("  " + columnName + " " + columnType);
                // Add BLOB_DATA column for actual binary content
                String blobDataType = dialect.getBinaryType();
                columnDefs.add("  " + dialect.sanitizeColumnName("BLOB_DATA") + " " + blobDataType);
                logger.info("{}: Added BLOB_DATA column ({}) for binary blob storage", tableName, blobDataType);
            } else {
                // Use large VARCHAR for all other fields since we don't have Salesforce metadata
                columnType = dialect.getVarcharType(16777216); // 16MB max
                columnDefs.add("  " + columnName + " " + columnType);
            }
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
        String upperTableName = tableName.toUpperCase();
        
        // Get schema - try multiple sources
        String schema = null;
        if (connectionProperties != null) {
            schema = connectionProperties.getProperty("schema");
        }
        if (schema == null || schema.isEmpty()) {
            schema = connection.getSchema();
        }
        // If still null, try querying Snowflake directly for current schema
        if (schema == null || schema.isEmpty()) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT CURRENT_SCHEMA()")) {
                if (rs.next()) {
                    schema = rs.getString(1);
                    logger.info("Got current schema from Snowflake: {}", schema);
                }
            } catch (SQLException e) {
                logger.warn("Could not get current schema: {}", e.getMessage());
            }
        }
        String upperSchema = schema != null ? schema.toUpperCase() : null;
        
        logger.info("tableExists check: schema={}, table={}", upperSchema, upperTableName);
        
        // First try: query table without schema prefix (relies on USE SCHEMA context)
        String query = String.format("SELECT 1 FROM %s LIMIT 1", upperTableName);
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            logger.info("Table {} EXISTS (query succeeded)", upperTableName);
            return true;
        } catch (SQLException e) {
            String msg = e.getMessage().toLowerCase();
            logger.info("Query '{}' failed: {}", query, e.getMessage());
            
            // If "does not exist" error, table doesn't exist
            if (msg.contains("does not exist") || msg.contains("not found") || 
                msg.contains("invalid object") || msg.contains("unknown table")) {
                logger.info("Table {} confirmed NOT EXIST", upperTableName);
                return false;
            }
            // Some other error - continue to try with schema prefix
        }
        
        // Second try: with fully qualified name if we have a schema
        if (upperSchema != null) {
            String qualifiedTable = upperSchema + "." + upperTableName;
            query = String.format("SELECT 1 FROM %s LIMIT 1", qualifiedTable);
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                logger.info("Table {} EXISTS (found with schema prefix)", qualifiedTable);
                return true;
            } catch (SQLException e) {
                String msg = e.getMessage().toLowerCase();
                logger.info("Query '{}' failed: {}", query, e.getMessage());
                
                if (msg.contains("does not exist") || msg.contains("not found") || 
                    msg.contains("invalid object") || msg.contains("unknown table")) {
                    logger.info("Table {} confirmed NOT EXIST", qualifiedTable);
                    return false;
                }
                // Other error - table might exist but we can't query it
                // Assume it exists to avoid trying to create it
                logger.warn("Cannot determine if table {} exists, assuming it DOES exist: {}", 
                    qualifiedTable, e.getMessage());
                return true;
            }
        }
        
        logger.info("Table {} - assuming does not exist", upperTableName);
        return false;
    }
    
    /**
     * Check if a specific column exists in a table
     */
    private boolean columnExists(String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        // Get schema/database from connectionProperties if available, otherwise from connection
        String schema = null;
        String database = null;
        if (connectionProperties != null) {
            schema = connectionProperties.getProperty("schema");
            database = connectionProperties.getProperty("db");
        }
        if (schema == null) {
            schema = connection.getSchema();
        }
        if (database == null) {
            database = connection.getCatalog();
        }
        String sanitizedColumnName = dialect.sanitizeColumnName(columnName);
        
        try (ResultSet rs = meta.getColumns(database, schema, tableName, sanitizedColumnName)) {
            return rs.next();
        }
    }
    
    /**
     * Add BLOB_DATA column to an existing table
     */
    private void addBlobDataColumn(String tableName) throws SQLException {
        String blobDataType = dialect.getBinaryType();
        String columnName = dialect.sanitizeColumnName("BLOB_DATA");
        String alterSQL = String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, blobDataType);
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(alterSQL);
            logger.info("Added {} column to table {}", columnName, tableName);
        } catch (SQLException e) {
            // Column might already exist with different case or the syntax might differ
            logger.warn("Could not add BLOB_DATA column to {}: {}", tableName, e.getMessage());
        }
    }
    
    /**
     * Check if a table exists in the database.
     * @param objectName The Salesforce object name
     * @return true if table exists, false otherwise
     */
    public boolean doesTableExist(String objectName) {
        try {
            ensureConnection();
            String tableName = dialect.sanitizeTableName(objectName);
            return tableExists(tableName);
        } catch (Exception e) {
            logger.warn("Error checking if table exists for {}: {}", objectName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the unique record count of a table (by ID column).
     * This handles multiple backup runs where the same record may exist multiple times.
     * @param objectName The Salesforce object name
     * @return The number of unique IDs in the table, or -1 if table doesn't exist or error
     */
    public long getTableRowCount(String objectName) {
        try {
            ensureConnection();
            String tableName = dialect.sanitizeTableName(objectName);
            String upperTableName = tableName.toUpperCase();
            
            logger.info("Checking unique record count for {} (full path: {})", objectName, getFullTablePath(objectName));
            
            if (!tableExists(tableName)) {
                logger.info("Table {} does not exist, returning -1", upperTableName);
                return -1;
            }
            
            // Count DISTINCT IDs to handle multiple backup runs with duplicates
            // First check if ID column exists
            String idColumn = dialect.sanitizeColumnName("ID");
            String query;
            
            // Try to use DISTINCT ID count (handles duplicates from multiple backups)
            try {
                query = String.format("SELECT COUNT(DISTINCT %s) as ROW_COUNT FROM %s", idColumn, upperTableName);
                logger.info("Executing distinct count query: {}", query);
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    if (rs.next()) {
                        long count = rs.getLong("ROW_COUNT");
                        logger.info("Table {} has {} unique records (by ID)", upperTableName, count);
                        return count;
                    }
                }
            } catch (SQLException idEx) {
                // ID column might not exist, fall back to regular count
                logger.info("Could not count by ID column, falling back to total row count: {}", idEx.getMessage());
            }
            
            // Fallback: regular count (for tables without ID column)
            query = String.format("SELECT COUNT(*) as ROW_COUNT FROM %s", upperTableName);
            logger.info("Executing total count query: {}", query);
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    long count = rs.getLong("ROW_COUNT");
                    logger.info("Table {} has {} total rows", upperTableName, count);
                    return count;
                }
            }
            return -1;
        } catch (Exception e) {
            logger.warn("Error getting row count for {}: {}", objectName, e.getMessage());
            return -1;
        }
    }
    
    /**
     * Get the full qualified table path (database.schema.table) for display purposes.
     * @param objectName The Salesforce object name
     * @return The full table path like "DATABASE.SCHEMA.TABLENAME"
     */
    public String getFullTablePath(String objectName) {
        try {
            String tableName = dialect.sanitizeTableName(objectName).toUpperCase();
            String schema = null;
            String database = null;
            
            if (connectionProperties != null) {
                schema = connectionProperties.getProperty("schema");
                database = connectionProperties.getProperty("db");
            }
            if (schema == null || schema.isEmpty()) {
                try {
                    schema = connection.getSchema();
                } catch (Exception e) {
                    schema = "?";
                }
            }
            if (database == null || database.isEmpty()) {
                try {
                    database = connection.getCatalog();
                } catch (Exception e) {
                    database = "?";
                }
            }
            
            return String.format("%s.%s.%s", 
                database != null ? database.toUpperCase() : "?",
                schema != null ? schema.toUpperCase() : "?",
                tableName);
        } catch (Exception e) {
            return objectName.toUpperCase();
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
     * Load blob file as raw bytes for database storage
     * Returns null if file cannot be loaded
     */
    private byte[] loadBlobAsBytes(String blobPath) {
        if (blobPath == null || blobPath.trim().isEmpty()) {
            return null;
        }
        
        java.nio.file.Path filePath = resolveBlobPath(blobPath);
        
        if (filePath != null && java.nio.file.Files.exists(filePath)) {
            try {
                byte[] blobBytes = java.nio.file.Files.readAllBytes(filePath);
                
                // Log only for larger files to avoid spam
                if (blobBytes.length > 100000) {
                    logger.debug("Loaded blob from {} ({} KB)", blobPath, blobBytes.length / 1024);
                }
                
                return blobBytes;
            } catch (Exception e) {
                logger.error("Failed to read blob file {}: {}", blobPath, e.getMessage());
                return null;
            }
        } else {
            logger.warn("Blob file not found: {} (tried multiple locations)", blobPath);
            return null;
        }
    }
    
    /**
     * Resolve blob file path using multiple strategies
     */
    private java.nio.file.Path resolveBlobPath(String blobPath) {
        if (blobPath == null || blobPath.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Strategy 1: Check if it's already an absolute path
            java.nio.file.Path testPath = java.nio.file.Paths.get(blobPath);
            if (testPath.isAbsolute() && java.nio.file.Files.exists(testPath)) {
                return testPath;
            }
            
            // Strategy 2: Relative to current working directory
            testPath = java.nio.file.Paths.get(System.getProperty("user.dir"), blobPath);
            if (java.nio.file.Files.exists(testPath)) {
                return testPath;
            }
            
            // Strategy 3: Check common backup locations
            String[] commonDirs = {"E:\\Staging Backup", "C:\\Backups", "."};
            for (String dir : commonDirs) {
                testPath = java.nio.file.Paths.get(dir, blobPath);
                if (java.nio.file.Files.exists(testPath)) {
                    return testPath;
                }
            }
        } catch (Exception e) {
            logger.error("Error resolving blob path {}: {}", blobPath, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Load blob file and encode as base64 for database storage
     * Returns null if file cannot be loaded
     */
    private String loadBlobAsBase64(String blobPath) {
        byte[] blobBytes = loadBlobAsBytes(blobPath);
        if (blobBytes == null) {
            return null;
        }
        
        String base64 = java.util.Base64.getEncoder().encodeToString(blobBytes);
        
        // Log only for larger files to avoid spam
        if (blobBytes.length > 100000) {
            logger.debug("Encoded blob from {} ({} KB -> {} KB base64)", 
                blobPath, blobBytes.length / 1024, base64.length() / 1024);
        }
        
        return base64;
    }
    
    /**
     * Count successful inserts from batch execution results
     * Handles various JDBC driver return codes
     */
    private int countSuccessfulInserts(int[] results) {
        int success = 0;
        for (int result : results) {
            // SUCCESS_NO_INFO (-2) means success but row count unknown
            // Positive numbers mean rows affected
            // Statement.EXECUTE_FAILED (-3) means failure
            if (result >= 0 || result == java.sql.Statement.SUCCESS_NO_INFO) {
                success++;
            }
        }
        return success;
    }

    /**
     * Get list of all tables in the current schema that match Salesforce backup pattern.
     * This is used for verification to find what was actually backed up.
     * @return List of table names (Salesforce object names format)
     */
    public List<String> listBackedUpTables() {
        List<String> tables = new ArrayList<>();
        try {
            ensureConnection();
            
            String schema = null;
            String database = null;
            
            if (connectionProperties != null) {
                schema = connectionProperties.getProperty("schema");
                database = connectionProperties.getProperty("db");
            }
            if (schema == null || schema.isEmpty()) {
                schema = connection.getSchema();
            }
            if (database == null || database.isEmpty()) {
                database = connection.getCatalog();
            }
            
            logger.info("Listing tables in schema: {}.{}", database, schema);
            
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(database, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null && !tableName.startsWith("_") && !tableName.startsWith("SYS")) {
                        tables.add(tableName);
                    }
                }
            }
            
            logger.info("Found {} tables in schema", tables.size());
        } catch (Exception e) {
            logger.error("Error listing tables: {}", e.getMessage());
        }
        return tables;
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
        
        /**
         * Get the binary/blob data type for this database
         * Used for storing binary file content
         */
        default String getBinaryType() {
            return "BLOB";
        }
    }
}
