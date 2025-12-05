package com.backupforce.sink;

import com.backupforce.sink.dialect.PostgresDialect;
import com.backupforce.sink.dialect.SnowflakeDialect;
import com.backupforce.sink.dialect.SqlServerDialect;

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
     * Create a generic JDBC sink with custom dialect
     */
    public static DataSink createGenericJdbcSink(String jdbcUrl, Properties properties, 
                                                JdbcDatabaseSink.DatabaseDialect dialect,
                                                String displayName) {
        return new JdbcDatabaseSink(jdbcUrl, properties, dialect, displayName);
    }
}
