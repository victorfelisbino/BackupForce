package com.backupforce.sink.dialect;

import com.backupforce.sink.JdbcDatabaseSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL Server-specific SQL dialect
 */
public class SqlServerDialect implements JdbcDatabaseSink.DatabaseDialect {
    private static final Logger logger = LoggerFactory.getLogger(SqlServerDialect.class);
    
    @Override
    public String sanitizeTableName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "SF_" + sanitized;
        }
        return "[" + sanitized + "]";
    }
    
    @Override
    public String sanitizeColumnName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "COL_" + sanitized;
        }
        return "[" + sanitized + "]";
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
                return "NVARCHAR(" + Math.min(length, 4000) + ")";
            
            case "TEXTAREA":
            case "LONGTEXTAREA":
                return "NVARCHAR(MAX)";
            
            case "INT":
                return "INT";
            
            case "DOUBLE":
            case "CURRENCY":
            case "PERCENT":
                return "DECIMAL(18, 2)";
            
            case "BOOLEAN":
                return "BIT";
            
            case "DATE":
                return "DATE";
            
            case "DATETIME":
                return "DATETIME2";
            
            case "TIME":
                return "TIME";
            
            case "BASE64":
                return "VARBINARY(MAX)";
            
            case "ID":
                return "NVARCHAR(18)";
            
            case "ADDRESS":
            case "LOCATION":
                return null;
            
            default:
                logger.debug("Unknown Salesforce type: {}, using NVARCHAR(MAX)", sfType);
                return "NVARCHAR(MAX)";
        }
    }
    
    @Override
    public String getTimestampType() {
        return "DATETIME2";
    }
    
    @Override
    public String getCurrentTimestamp() {
        return "GETDATE()";
    }
    
    @Override
    public String getVarcharType(int length) {
        return "NVARCHAR(" + length + ")";
    }
    
    @Override
    public int getOptimalBatchSize() {
        return 1000; // SQL Server performs well with smaller batches
    }
}
