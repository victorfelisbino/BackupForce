package com.backupforce.config;

import com.backupforce.config.ConnectionManager.SavedConnection;

/**
 * Helper utilities for JDBC operations.
 * Provides database-agnostic identifier quoting for SQL queries.
 */
public class JdbcHelper {
    
    /**
     * Build JDBC URL from SavedConnection.
     * 
     * @param conn The saved connection
     * @return The JDBC URL string
     */
    public static String buildJdbcUrl(SavedConnection conn) {
        String type = conn.getType();
        String host = conn.getHost();
        String portStr = conn.getPort();
        String database = conn.getDatabase();
        
        switch (type) {
            case "SQL Server":
                int sqlPort = portStr != null && !portStr.isEmpty() ? Integer.parseInt(portStr) : 1433;
                return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=true;trustServerCertificate=true", 
                    host, sqlPort, database);
                
            case "Snowflake":
                String schema = conn.getSchema();
                String warehouse = conn.getWarehouse();
                boolean useSso = conn.isUseSso();
                
                StringBuilder url = new StringBuilder(String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s",
                    host, warehouse != null ? warehouse : "COMPUTE_WH", database));
                
                if (schema != null && !schema.isEmpty()) {
                    url.append("&schema=").append(schema);
                }
                
                if (useSso) {
                    url.append("&authenticator=externalbrowser");
                }
                
                return url.toString();
                
            case "PostgreSQL":
                int pgPort = portStr != null && !portStr.isEmpty() ? Integer.parseInt(portStr) : 5432;
                return String.format("jdbc:postgresql://%s:%d/%s", host, pgPort, database);
                
            case "MySQL":
                int myPort = portStr != null && !portStr.isEmpty() ? Integer.parseInt(portStr) : 3306;
                return String.format("jdbc:mysql://%s:%d/%s", host, myPort, database);
                
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type);
        }
    }
    
    /**
     * Quote an identifier (table/column name) based on saved connection.
     * 
     * @param conn The saved connection
     * @param identifier The identifier to quote
     * @return The quoted identifier
     */
    public static String quoteIdentifier(SavedConnection conn, String identifier) {
        return quoteIdentifier(identifier, conn.getType());
    }
    
    /**
     * Quote an identifier (table/column name) based on database type.
     * 
     * @param identifier The identifier to quote
     * @param dbType The database type (e.g., "SQL Server", "Snowflake", "PostgreSQL")
     * @return The quoted identifier
     */
    public static String quoteIdentifier(String identifier, String dbType) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        
        // Remove any existing quotes first
        identifier = identifier.replace("\"", "").replace("[", "").replace("]", "").replace("`", "");
        
        switch (dbType) {
            case "SQL Server":
                // SQL Server uses square brackets
                return "[" + identifier + "]";
                
            case "MySQL":
                // MySQL uses backticks
                return "`" + identifier + "`";
                
            case "PostgreSQL":
            case "Snowflake":
            case "Oracle":
            default:
                // Standard SQL uses double quotes
                return "\"" + identifier + "\"";
        }
    }
    
    /**
     * Build a qualified table name with schema.
     * 
     * @param conn The saved connection
     * @param schema The schema name (can be null)
     * @param tableName The table name
     * @return The qualified table name (e.g., "schema"."table")
     */
    public static String getQualifiedTableName(SavedConnection conn, String schema, String tableName) {
        return getQualifiedTableName(schema, tableName, conn.getType());
    }
    
    /**
     * Build a qualified table name with schema.
     * 
     * @param schema The schema name (can be null)
     * @param tableName The table name
     * @param dbType The database type
     * @return The qualified table name (e.g., "schema"."table")
     */
    public static String getQualifiedTableName(String schema, String tableName, String dbType) {
        if (schema == null || schema.isEmpty()) {
            return quoteIdentifier(tableName, dbType);
        }
        return quoteIdentifier(schema, dbType) + "." + quoteIdentifier(tableName, dbType);
    }
    
    /**
     * Escape a string value for use in SQL queries.
     * This is a basic escaper - prepared statements are always preferred.
     * 
     * @param value The value to escape
     * @return The escaped value
     */
    public static String escapeString(String value) {
        if (value == null) {
            return "NULL";
        }
        // Escape single quotes by doubling them
        return "'" + value.replace("'", "''") + "'";
    }
    
    private JdbcHelper() {
        // Utility class - no instantiation
    }
}
