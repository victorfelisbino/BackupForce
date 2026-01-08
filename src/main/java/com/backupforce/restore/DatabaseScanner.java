package com.backupforce.restore;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.config.SSLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Scans database connections for backup tables that can be restored to Salesforce.
 * Supports Snowflake, PostgreSQL, and SQL Server.
 */
public class DatabaseScanner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseScanner.class);
    
    /**
     * Represents a table found in the database that may contain backup data
     */
    public static class BackupTable {
        private final String tableName;
        private final String objectName; // Salesforce object name (derived from table name)
        private final long recordCount;
        private final String lastModified;
        private final String schema;
        
        public BackupTable(String tableName, String objectName, long recordCount, String lastModified, String schema) {
            this.tableName = tableName;
            this.objectName = objectName;
            this.recordCount = recordCount;
            this.lastModified = lastModified;
            this.schema = schema;
        }
        
        public String getTableName() { return tableName; }
        public String getObjectName() { return objectName; }
        public long getRecordCount() { return recordCount; }
        public String getLastModified() { return lastModified; }
        public String getSchema() { return schema; }
    }
    
    private final SavedConnection connection;
    private Consumer<String> logConsumer;
    
    public DatabaseScanner(SavedConnection connection) {
        this.connection = connection;
    }
    
    public void setLogConsumer(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
    }
    
    private void log(String message) {
        logger.info(message);
        if (logConsumer != null) {
            logConsumer.accept(message);
        }
    }
    
    /**
     * Scans the database for tables that appear to be Salesforce backup tables.
     * Looks for tables with common Salesforce fields (Id, Name, CreatedDate, etc.)
     */
    public List<BackupTable> scanForBackupTables() throws SQLException {
        String jdbcUrl = buildJdbcUrl();
        String username = connection.getUsername();
        String password = ConnectionManager.getInstance().getDecryptedPassword(connection);
        
        log("Connecting to " + connection.getType() + " database: " + connection.getDatabase());
        log("JDBC URL: " + jdbcUrl.replaceAll("password=[^&]*", "password=****"));
        
        List<BackupTable> tables = new ArrayList<>();
        
        // Set a connection timeout (60 seconds)
        DriverManager.setLoginTimeout(60);
        
        log("Opening JDBC connection...");
        
        // Build connection properties
        java.util.Properties props = new java.util.Properties();
        if ("Snowflake".equals(connection.getType())) {
            // For Snowflake with externalbrowser, only need username
            if (username != null) props.put("user", username);
            // Critical: Disable SSL validation for Snowflake JDBC internal HttpClient
            props.put("insecure_mode", "true");
            props.put("tracing", "OFF");
        } else {
            // For other databases, use username/password
            if (username != null) props.put("user", username);
            if (password != null) props.put("password", password);
        }
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            log("✓ JDBC connection established successfully");
            log("Setting query timeout to 30 seconds...");
            
            String schema = connection.getSchema();
            
            switch (connection.getType()) {
                case "Snowflake":
                    tables = scanSnowflake(conn, schema);
                    break;
                case "PostgreSQL":
                    tables = scanPostgreSQL(conn, schema);
                    break;
                case "SQL Server":
                    tables = scanSqlServer(conn, schema);
                    break;
                default:
                    throw new SQLException("Unsupported database type: " + connection.getType());
            }
        } catch (SQLException e) {
            log("ERROR: " + e.getMessage());
            throw e;
        }
        
        log("Found " + tables.size() + " backup tables");
        return tables;
    }
    
    private List<BackupTable> scanSnowflake(Connection conn, String schema) throws SQLException {
        List<BackupTable> tables = new ArrayList<>();
        
        log("Scanning Snowflake schema: " + (schema != null && !schema.isEmpty() ? schema : "PUBLIC"));
        
        // Query INFORMATION_SCHEMA for tables in the specified schema
        // Use LIKE patterns to pre-filter for likely Salesforce tables (speeds up significantly)
        String query = "SELECT TABLE_NAME, ROW_COUNT, LAST_ALTERED " +
            "FROM INFORMATION_SCHEMA.TABLES " +
            "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
            "AND (TABLE_NAME LIKE 'SF_%' OR TABLE_NAME LIKE 'SALESFORCE_%' OR TABLE_NAME LIKE '%__C' " +
            "OR TABLE_NAME IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CASE', 'USER', 'PROFILE')) " +
            "ORDER BY TABLE_NAME";
        
        log("Preparing query with Salesforce table name filters...");
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setQueryTimeout(30); // 30 second timeout for query execution
            stmt.setString(1, schema != null && !schema.isEmpty() ? schema.toUpperCase() : "PUBLIC");
            
            log("Executing query...");
            try (ResultSet rs = stmt.executeQuery()) {
                log("✓ Query executed, processing results...");
                int totalTables = 0;
                
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    long rowCount = rs.getLong("ROW_COUNT");
                    Timestamp lastAltered = rs.getTimestamp("LAST_ALTERED");
                    totalTables++;
                    
                    log("Processing table: " + tableName);
                    
                    // Since we've already filtered by Salesforce-like names in the query,
                    // we can trust these are backup tables without expensive column checks
                    String objectName = tableNameToObjectName(tableName);
                    String lastModified = lastAltered != null ? 
                        lastAltered.toLocalDateTime().toString().substring(0, 16).replace('T', ' ') : 
                        "Unknown";
                    
                    tables.add(new BackupTable(tableName, objectName, rowCount, lastModified, schema));
                    log("✓ Added backup table: " + tableName + " (" + rowCount + " records)");
                }
                
                log("Finished processing all " + totalTables + " tables in schema");
            }
        }
        
        return tables;
    }
    
    private List<BackupTable> scanPostgreSQL(Connection conn, String schema) throws SQLException {
        List<BackupTable> tables = new ArrayList<>();
        String targetSchema = schema != null && !schema.isEmpty() ? schema : "public";
        
        // Get tables from information_schema
        String query = "SELECT t.table_name, " +
            "(SELECT reltuples::bigint FROM pg_class WHERE relname = t.table_name) as row_count " +
            "FROM information_schema.tables t " +
            "WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' " +
            "ORDER BY t.table_name";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, targetSchema);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    long rowCount = rs.getLong("row_count");
                    
                    // Check if this looks like a Salesforce backup table
                    if (isSalesforceBackupTable(conn, targetSchema, tableName)) {
                        String objectName = tableNameToObjectName(tableName);
                        
                        // Get last modified from pg_stat_user_tables
                        String lastModified = getPostgresLastModified(conn, targetSchema, tableName);
                        
                        tables.add(new BackupTable(tableName, objectName, rowCount, lastModified, targetSchema));
                        log("Found backup table: " + tableName + " (" + rowCount + " records)");
                    }
                }
            }
        }
        
        return tables;
    }
    
    private String getPostgresLastModified(Connection conn, String schema, String tableName) {
        try {
            String query = "SELECT greatest(last_vacuum, last_autovacuum, last_analyze, last_autoanalyze) as last_modified " +
                "FROM pg_stat_user_tables WHERE schemaname = ? AND relname = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, schema);
                stmt.setString(2, tableName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp ts = rs.getTimestamp("last_modified");
                        if (ts != null) {
                            return ts.toLocalDateTime().toString().substring(0, 16).replace('T', ' ');
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("Could not get last modified time for {}.{}", schema, tableName);
        }
        return "Unknown";
    }
    
    private List<BackupTable> scanSqlServer(Connection conn, String schema) throws SQLException {
        List<BackupTable> tables = new ArrayList<>();
        String targetSchema = schema != null && !schema.isEmpty() ? schema : "dbo";
        
        String query = "SELECT t.TABLE_NAME, p.rows as row_count, o.modify_date " +
            "FROM INFORMATION_SCHEMA.TABLES t " +
            "INNER JOIN sys.objects o ON o.name = t.TABLE_NAME " +
            "INNER JOIN sys.partitions p ON p.object_id = o.object_id AND p.index_id IN (0, 1) " +
            "WHERE t.TABLE_SCHEMA = ? AND t.TABLE_TYPE = 'BASE TABLE' " +
            "ORDER BY t.TABLE_NAME";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, targetSchema);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    long rowCount = rs.getLong("row_count");
                    Timestamp modifyDate = rs.getTimestamp("modify_date");
                    
                    // Check if this looks like a Salesforce backup table
                    if (isSalesforceBackupTable(conn, targetSchema, tableName)) {
                        String objectName = tableNameToObjectName(tableName);
                        String lastModified = modifyDate != null ?
                            modifyDate.toLocalDateTime().toString().substring(0, 16).replace('T', ' ') :
                            "Unknown";
                        
                        tables.add(new BackupTable(tableName, objectName, rowCount, lastModified, targetSchema));
                        log("Found backup table: " + tableName + " (" + rowCount + " records)");
                    }
                }
            }
        }
        
        return tables;
    }
    
    /**
     * Checks if a table appears to be a Salesforce backup table by looking for
     * common Salesforce fields like Id, Name, CreatedDate, etc.
     */
    private boolean isSalesforceBackupTable(Connection conn, String schema, String tableName) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            
            // Get column names for this table
            Map<String, Boolean> columns = new HashMap<>();
            
            try (ResultSet rs = meta.getColumns(null, schema, tableName, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME").toUpperCase();
                    columns.put(columnName, true);
                }
            }
            
            // Check for common Salesforce fields
            // Must have "ID" column to be considered a Salesforce backup
            if (!columns.containsKey("ID")) {
                return false;
            }
            
            // Should have at least one other common Salesforce field
            int sfFieldCount = 0;
            String[] commonFields = {"NAME", "CREATEDDATE", "LASTMODIFIEDDATE", "CREATEDBYID", 
                                      "LASTMODIFIEDBYID", "SYSTEMMODSTAMP", "ISDELETED", "OWNERID"};
            
            for (String field : commonFields) {
                if (columns.containsKey(field)) {
                    sfFieldCount++;
                }
            }
            
            // Consider it a Salesforce table if it has ID + at least 2 other common fields
            return sfFieldCount >= 2;
            
        } catch (SQLException e) {
            logger.debug("Error checking if {} is a Salesforce table: {}", tableName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Converts a database table name back to a Salesforce object name.
     * Handles common naming conventions from backup tools.
     */
    private String tableNameToObjectName(String tableName) {
        // Remove common prefixes
        String name = tableName;
        if (name.startsWith("SF_")) {
            name = name.substring(3);
        } else if (name.startsWith("SALESFORCE_")) {
            name = name.substring(11);
        }
        
        // Handle custom object suffix
        if (name.endsWith("__C")) {
            name = name.substring(0, name.length() - 3) + "__c";
        }
        
        // Convert SCREAMING_SNAKE_CASE to CamelCase for standard objects
        if (!name.contains("__")) {
            name = snakeToCamelCase(name);
        }
        
        return name;
    }
    
    private String snakeToCamelCase(String snakeCase) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Reads data from a backup table for restoration.
     * Returns a map of column names to values for each row.
     */
    public List<Map<String, Object>> readTableData(String tableName, int limit) throws SQLException {
        String jdbcUrl = buildJdbcUrl();
        String username = connection.getUsername();
        String password = ConnectionManager.getInstance().getDecryptedPassword(connection);
        
        List<Map<String, Object>> records = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            String schema = connection.getSchema();
            String qualifiedName = getQualifiedTableName(schema, tableName);
            
            String query = "SELECT * FROM " + qualifiedName;
            if (limit > 0) {
                switch (connection.getType()) {
                    case "Snowflake":
                    case "PostgreSQL":
                        query += " LIMIT " + limit;
                        break;
                    case "SQL Server":
                        query = "SELECT TOP " + limit + " * FROM " + qualifiedName;
                        break;
                }
            }
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnName(i);
                        Object value = rs.getObject(i);
                        record.put(columnName, value);
                    }
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    /**
     * Gets the column names from a backup table.
     */
    public List<String> getTableColumns(String tableName) throws SQLException {
        String jdbcUrl = buildJdbcUrl();
        String username = connection.getUsername();
        String password = ConnectionManager.getInstance().getDecryptedPassword(connection);
        
        List<String> columns = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            String schema = connection.getSchema();
            
            try (ResultSet rs = meta.getColumns(null, schema, tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        
        return columns;
    }
    
    private String getQualifiedTableName(String schema, String tableName) {
        if (schema != null && !schema.isEmpty()) {
            switch (connection.getType()) {
                case "Snowflake":
                    return "\"" + schema.toUpperCase() + "\".\"" + tableName.toUpperCase() + "\"";
                case "PostgreSQL":
                    return "\"" + schema + "\".\"" + tableName + "\"";
                case "SQL Server":
                    return "[" + schema + "].[" + tableName + "]";
            }
        }
        return tableName;
    }
    
    private String buildJdbcUrl() {
        switch (connection.getType()) {
            case "Snowflake":
                String account = connection.getAccount();
                String warehouse = connection.getWarehouse();
                String database = connection.getDatabase();
                String schema = connection.getSchema();
                if (connection.isUseSso()) {
                    return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s&authenticator=externalbrowser",
                        account, warehouse, database, schema);
                } else {
                    return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s",
                        account, warehouse, database, schema);
                }
                
            case "SQL Server":
                String server = connection.getHost();
                String db = connection.getDatabase();
                return String.format("jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true",
                    server, db);
                
            case "PostgreSQL":
                String host = connection.getHost();
                String port = connection.getPort() != null ? connection.getPort() : "5432";
                String pgDb = connection.getDatabase();
                return String.format("jdbc:postgresql://%s:%s/%s", host, port, pgDb);
                
            default:
                throw new IllegalArgumentException("Unsupported database type: " + connection.getType());
        }
    }
}
