package com.backupforce.sink.dialect;

import com.backupforce.sink.JdbcDatabaseSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snowflake-specific SQL dialect
 */
public class SnowflakeDialect implements JdbcDatabaseSink.DatabaseDialect {
    private static final Logger logger = LoggerFactory.getLogger(SnowflakeDialect.class);
    
    @Override
    public String sanitizeTableName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "SF_" + sanitized;
        }
        return sanitized.toUpperCase();
    }
    
    @Override
    public String sanitizeColumnName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "COL_" + sanitized;
        }
        return sanitized.toUpperCase();
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
                return "VARCHAR(" + Math.min(length, 16777216) + ")";
            
            case "TEXTAREA":
            case "LONGTEXTAREA":
                return "VARCHAR(16777216)";
            
            case "INT":
                return "NUMBER(10, 0)";
            
            case "DOUBLE":
            case "CURRENCY":
            case "PERCENT":
                return "NUMBER(18, 2)";
            
            case "BOOLEAN":
                return "BOOLEAN";
            
            case "DATE":
                return "DATE";
            
            case "DATETIME":
                return "TIMESTAMP_NTZ";
            
            case "TIME":
                return "TIME";
            
            case "BASE64":
                return "BINARY";
            
            case "ID":
                return "VARCHAR(18)";
            
            case "ADDRESS":
            case "LOCATION":
                return null; // Skip compound types
            
            default:
                logger.debug("Unknown Salesforce type: {}, using VARCHAR", sfType);
                return "VARCHAR(16777216)";
        }
    }
    
    @Override
    public String getTimestampType() {
        return "TIMESTAMP_NTZ";
    }
    
    @Override
    public String getCurrentTimestamp() {
        return "CURRENT_TIMESTAMP()";
    }
    
    @Override
    public String getVarcharType(int length) {
        return "VARCHAR(" + length + ")";
    }
    
    @Override
    public int getOptimalBatchSize() {
        return 10000;
    }
}
