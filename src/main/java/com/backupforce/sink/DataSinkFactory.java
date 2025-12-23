package com.backupforce.sink;

import com.backupforce.sink.dialect.PostgresDialect;
import com.backupforce.sink.dialect.SnowflakeDialect;
import com.backupforce.sink.dialect.SqlServerDialect;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Factory for creating appropriate DataSink implementations
 */
public class DataSinkFactory {
    
    public enum DatabaseType {
        SNOWFLAKE("Snowflake"),
        SQL_SERVER("SQL Server"),
        POSTGRESQL("PostgreSQL"),
        MYSQL("MySQL"),
        ORACLE("Oracle");
        
        private final String displayName;
        
        DatabaseType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Create a CSV file sink
     */
    public static DataSink createCsvFileSink(String outputDirectory) {
        return new CsvFileSink(outputDirectory);
    }
    
    /**
     * Create a Snowflake database sink
     */
    public static DataSink createSnowflakeSink(String account, String warehouse, 
                                              String database, String schema,
                                              String username, String password) {
        String jdbcUrl = String.format("jdbc:snowflake://%s.snowflakecomputing.com", account);
        
        Properties props = new Properties();
        
        // If password is null or empty, use SSO authentication
        if (password == null || password.trim().isEmpty()) {
            props.put("authenticator", "externalbrowser");
            if (username != null && !username.trim().isEmpty()) {
                props.put("user", username);
            }
        } else {
            // Standard username/password authentication
            if (username != null) props.put("user", username);
            props.put("password", password);
        }
        
        props.put("warehouse", warehouse);
        props.put("db", database);
        props.put("schema", schema);
        
        String displayName = String.format("Snowflake (%s.%s.%s)", database, schema, warehouse);
        
        return new JdbcDatabaseSink(jdbcUrl, props, new SnowflakeDialect(), displayName);
    }
    
    /**
     * Create a Snowflake database sink with an existing connection (e.g., from SSO cache).
     * This avoids re-authentication when a valid session already exists.
     * IMPORTANT: Sets USE DATABASE/SCHEMA/WAREHOUSE on the connection to ensure correct context.
     */
    public static DataSink createSnowflakeSinkWithExistingConnection(
            java.sql.Connection existingConnection, 
            String database, String schema, String warehouse) {
        String displayName = String.format("Snowflake (%s.%s.%s)", database, schema, warehouse);
        
        // CRITICAL: Set the database/schema/warehouse context on the existing connection
        // Without this, the connection might be pointing to a different database/schema
        try (java.sql.Statement stmt = existingConnection.createStatement()) {
            // Set a query timeout to prevent UI freeze if warehouse is resuming
            stmt.setQueryTimeout(30); // 30 second timeout
            
            LoggerFactory.getLogger(DataSinkFactory.class).info("Setting Snowflake context: database={}, schema={}, warehouse={}", 
                database, schema, warehouse);
            
            if (database != null && !database.isEmpty()) {
                stmt.execute(String.format("USE DATABASE \"%s\"", database));
                LoggerFactory.getLogger(DataSinkFactory.class).info("Set Snowflake database to: {}", database);
            }
            if (schema != null && !schema.isEmpty()) {
                stmt.execute(String.format("USE SCHEMA \"%s\"", schema));
                LoggerFactory.getLogger(DataSinkFactory.class).info("Set Snowflake schema to: {}", schema);
            }
            if (warehouse != null && !warehouse.isEmpty()) {
                stmt.execute(String.format("USE WAREHOUSE \"%s\"", warehouse));
                LoggerFactory.getLogger(DataSinkFactory.class).info("Set Snowflake warehouse to: {}", warehouse);
            }
            
            LoggerFactory.getLogger(DataSinkFactory.class).info("Snowflake context set successfully");
        } catch (java.sql.SQLException e) {
            LoggerFactory.getLogger(DataSinkFactory.class).error("Failed to set Snowflake context: {}", e.getMessage());
            throw new RuntimeException("Failed to set Snowflake database/schema/warehouse context: " + e.getMessage(), e);
        }
        
        return new JdbcDatabaseSink(existingConnection, new SnowflakeDialect(), displayName);
    }
    
    /**
     * Create a Snowflake database sink with SSO/External Browser authentication
     */
    public static DataSink createSnowflakeSinkWithSSO(String account, String warehouse, 
                                                      String database, String schema) {
        String jdbcUrl = String.format("jdbc:snowflake://%s.snowflakecomputing.com", account);
        
        Properties props = new Properties();
        props.put("authenticator", "externalbrowser");
        props.put("warehouse", warehouse);
        props.put("db", database);
        props.put("schema", schema);
        
        String displayName = String.format("Snowflake SSO (%s.%s.%s)", database, schema, warehouse);
        
        return new JdbcDatabaseSink(jdbcUrl, props, new SnowflakeDialect(), displayName);
    }
    
    /**
     * Create a SQL Server database sink
     */
    public static DataSink createSqlServerSink(String server, String database, 
                                              String username, String password) {
        String jdbcUrl = String.format("jdbc:sqlserver://%s;databaseName=%s", server, database);
        
        Properties props = new Properties();
        props.put("user", username);
        props.put("password", password);
        props.put("db", database);
        
        String displayName = String.format("SQL Server (%s.%s)", server, database);
        
        return new JdbcDatabaseSink(jdbcUrl, props, new SqlServerDialect(), displayName);
    }
    
    /**
     * Create a SQL Server database sink with an existing connection.
     */
    public static DataSink createSqlServerSinkWithExistingConnection(
            java.sql.Connection existingConnection, String server, String database) {
        String displayName = String.format("SQL Server (%s.%s)", server, database);
        return new JdbcDatabaseSink(existingConnection, new SqlServerDialect(), displayName);
    }
    
    /**
     * Create a PostgreSQL database sink
     */
    public static DataSink createPostgresSink(String host, int port, String database, 
                                             String schema, String username, String password) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        
        Properties props = new Properties();
        props.put("user", username);
        props.put("password", password);
        props.put("currentSchema", schema);
        props.put("schema", schema);
        props.put("db", database);
        
        String displayName = String.format("PostgreSQL (%s:%d/%s.%s)", host, port, database, schema);
        
        return new JdbcDatabaseSink(jdbcUrl, props, new PostgresDialect(), displayName);
    }
    
    /**
     * Create a PostgreSQL database sink with an existing connection.
     */
    public static DataSink createPostgresSinkWithExistingConnection(
            java.sql.Connection existingConnection, String host, int port, 
            String database, String schema) {
        String displayName = String.format("PostgreSQL (%s:%d/%s.%s)", host, port, database, schema);
        return new JdbcDatabaseSink(existingConnection, new PostgresDialect(), displayName);
    }
    
    /**
     * Create a generic JDBC sink with custom dialect
     */
    public static DataSink createGenericJdbcSink(String jdbcUrl, Properties properties, 
                                                JdbcDatabaseSink.DatabaseDialect dialect,
                                                String displayName) {
        return new JdbcDatabaseSink(jdbcUrl, properties, dialect, displayName);
    }
}
