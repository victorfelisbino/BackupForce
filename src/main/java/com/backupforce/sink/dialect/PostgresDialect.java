package com.backupforce.sink.dialect;

import com.backupforce.sink.JdbcDatabaseSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL-specific SQL dialect
 */
public class PostgresDialect implements JdbcDatabaseSink.DatabaseDialect {
    private static final Logger logger = LoggerFactory.getLogger(PostgresDialect.class);
    
    @Override
    public String sanitizeTableName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "sf_" + sanitized;
        }
        return sanitized.toLowerCase();
    }
    
    @Override
    public String sanitizeColumnName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "col_" + sanitized;
        }
        return sanitized.toLowerCase();
    }
    
    @Override
    public String mapSalesforceType(String sfType, int length) {
        switch (sfType.toUpperCase()) {
            case "STRING":
            case "PICKLIST":
            case "MULTIPICKLIST":
            case "COMBOBOX":
            case "REFERENCE":
            case "PHONE":
            case "EMAIL":
            case "URL":
            case "ENCRYPTEDSTRING":
            case "ID":
                return "VARCHAR(" + length + ")";
            
            case "TEXTAREA":
            case "LONGTEXTAREA":
                return "TEXT";
            
            case "INT":
                return "INTEGER";
            
            case "DOUBLE":
            case "CURRENCY":
            case "PERCENT":
                return "NUMERIC(18, 2)";
            
            case "BOOLEAN":
                return "BOOLEAN";
            
            case "DATE":
                return "DATE";
            
            case "DATETIME":
                return "TIMESTAMP";
            
            case "TIME":
                return "TIME";
            
            case "BASE64":
                return "BYTEA";
            
            case "ADDRESS":
            case "LOCATION":
                return null;
            
            default:
                logger.debug("Unknown Salesforce type: {}, using TEXT", sfType);
                return "TEXT";
        }
    }
    
    @Override
    public String getTimestampType() {
        return "TIMESTAMP";
    }
    
    @Override
    public String getCurrentTimestamp() {
        return "CURRENT_TIMESTAMP";
    }
    
    @Override
    public String getVarcharType(int length) {
        return "VARCHAR(" + length + ")";
    }
    
    @Override
    public String getBinaryType() {
        // PostgreSQL BYTEA type for binary data
        return "BYTEA";
    }
    
    @Override
    public int getOptimalBatchSize() {
        return 5000;
    }
}
