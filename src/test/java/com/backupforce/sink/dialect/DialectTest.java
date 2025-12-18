package com.backupforce.sink.dialect;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for database dialect implementations.
 * Tests type mapping, name sanitization, and SQL generation.
 */
@DisplayName("Database Dialect Tests")
class DialectTest {
    
    // ============================================
    // Snowflake Dialect Tests
    // ============================================
    
    @Nested
    @DisplayName("Snowflake Dialect")
    class SnowflakeDialectTests {
        
        private SnowflakeDialect dialect;
        
        @BeforeEach
        void setUp() {
            dialect = new SnowflakeDialect();
        }
        
        @Test
        @DisplayName("getBinaryType returns BINARY with size")
        void testGetBinaryType() {
            assertEquals("BINARY(8388608)", dialect.getBinaryType());
        }
        
        @Test
        @DisplayName("mapSalesforceType maps BASE64 to BINARY")
        void testMapBase64Type() {
            assertEquals("BINARY", dialect.mapSalesforceType("BASE64", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps STRING types correctly")
        void testMapStringTypes() {
            assertEquals("VARCHAR(255)", dialect.mapSalesforceType("STRING", 255));
            assertEquals("VARCHAR(4000)", dialect.mapSalesforceType("STRING", 4000));
            assertEquals("VARCHAR(16777216)", dialect.mapSalesforceType("TEXTAREA", 0));
            assertEquals("VARCHAR(16777216)", dialect.mapSalesforceType("LONGTEXTAREA", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps numeric types correctly")
        void testMapNumericTypes() {
            assertEquals("NUMBER(18, 2)", dialect.mapSalesforceType("DOUBLE", 0));
            assertEquals("NUMBER(18, 2)", dialect.mapSalesforceType("CURRENCY", 0));
            assertEquals("NUMBER(18, 2)", dialect.mapSalesforceType("PERCENT", 0));
            assertEquals("NUMBER(10, 0)", dialect.mapSalesforceType("INT", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps date/time types correctly")
        void testMapDateTypes() {
            assertEquals("DATE", dialect.mapSalesforceType("DATE", 0));
            assertEquals("TIMESTAMP_NTZ", dialect.mapSalesforceType("DATETIME", 0));
            assertEquals("TIME", dialect.mapSalesforceType("TIME", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps boolean correctly")
        void testMapBooleanType() {
            assertEquals("BOOLEAN", dialect.mapSalesforceType("BOOLEAN", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps ID to VARCHAR(18)")
        void testMapIdType() {
            assertEquals("VARCHAR(18)", dialect.mapSalesforceType("ID", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps REFERENCE to VARCHAR with length")
        void testMapReferenceType() {
            assertEquals("VARCHAR(18)", dialect.mapSalesforceType("REFERENCE", 18));
        }
        
        @Test
        @DisplayName("mapSalesforceType returns VARCHAR for unknown types")
        void testMapUnknownType() {
            assertEquals("VARCHAR(16777216)", dialect.mapSalesforceType("UNKNOWN_TYPE", 0));
        }
        
        @Test
        @DisplayName("sanitizeTableName uppercases names")
        void testSanitizeTableName() {
            assertEquals("ACCOUNT", dialect.sanitizeTableName("Account"));
            assertEquals("CONTENTVERSION", dialect.sanitizeTableName("ContentVersion"));
            assertEquals("CUSTOMOBJECT__C", dialect.sanitizeTableName("CustomObject__c"));
        }
        
        @Test
        @DisplayName("sanitizeColumnName uppercases names")
        void testSanitizeColumnName() {
            assertEquals("ID", dialect.sanitizeColumnName("Id"));
            assertEquals("FIRSTNAME", dialect.sanitizeColumnName("FirstName"));
            assertEquals("CUSTOM_FIELD__C", dialect.sanitizeColumnName("Custom_Field__c"));
        }
        
        @Test
        @DisplayName("getTimestampType returns TIMESTAMP_NTZ")
        void testGetTimestampType() {
            assertEquals("TIMESTAMP_NTZ", dialect.getTimestampType());
        }
        
        @Test
        @DisplayName("getCurrentTimestamp returns CURRENT_TIMESTAMP()")
        void testGetCurrentTimestamp() {
            assertEquals("CURRENT_TIMESTAMP()", dialect.getCurrentTimestamp());
        }
        
        @Test
        @DisplayName("getOptimalBatchSize returns 1000")
        void testGetOptimalBatchSize() {
            assertEquals(1000, dialect.getOptimalBatchSize());
        }
        
        @Test
        @DisplayName("getVarcharType returns VARCHAR with length")
        void testGetVarcharType() {
            assertEquals("VARCHAR(100)", dialect.getVarcharType(100));
            assertEquals("VARCHAR(255)", dialect.getVarcharType(255));
        }
    }
    
    // ============================================
    // PostgreSQL Dialect Tests
    // ============================================
    
    @Nested
    @DisplayName("PostgreSQL Dialect")
    class PostgresDialectTests {
        
        private PostgresDialect dialect;
        
        @BeforeEach
        void setUp() {
            dialect = new PostgresDialect();
        }
        
        @Test
        @DisplayName("getBinaryType returns BYTEA")
        void testGetBinaryType() {
            assertEquals("BYTEA", dialect.getBinaryType());
        }
        
        @Test
        @DisplayName("mapSalesforceType maps BASE64 to BYTEA")
        void testMapBase64Type() {
            assertEquals("BYTEA", dialect.mapSalesforceType("BASE64", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps STRING types correctly")
        void testMapStringTypes() {
            assertEquals("VARCHAR(255)", dialect.mapSalesforceType("STRING", 255));
            assertEquals("TEXT", dialect.mapSalesforceType("TEXTAREA", 0));
            assertEquals("TEXT", dialect.mapSalesforceType("LONGTEXTAREA", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps numeric types correctly")
        void testMapNumericTypes() {
            assertEquals("NUMERIC(18, 2)", dialect.mapSalesforceType("DOUBLE", 0));
            assertEquals("NUMERIC(18, 2)", dialect.mapSalesforceType("CURRENCY", 0));
            assertEquals("NUMERIC(18, 2)", dialect.mapSalesforceType("PERCENT", 0));
            assertEquals("INTEGER", dialect.mapSalesforceType("INT", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps date/time types correctly")
        void testMapDateTypes() {
            assertEquals("DATE", dialect.mapSalesforceType("DATE", 0));
            assertEquals("TIMESTAMP", dialect.mapSalesforceType("DATETIME", 0));
            assertEquals("TIME", dialect.mapSalesforceType("TIME", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps boolean correctly")
        void testMapBooleanType() {
            assertEquals("BOOLEAN", dialect.mapSalesforceType("BOOLEAN", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps ID to VARCHAR with length")
        void testMapIdType() {
            assertEquals("VARCHAR(18)", dialect.mapSalesforceType("ID", 18));
        }
        
        @Test
        @DisplayName("sanitizeTableName lowercases names")
        void testSanitizeTableName() {
            assertEquals("account", dialect.sanitizeTableName("Account"));
            assertEquals("contentversion", dialect.sanitizeTableName("ContentVersion"));
        }
        
        @Test
        @DisplayName("sanitizeColumnName lowercases names")
        void testSanitizeColumnName() {
            assertEquals("id", dialect.sanitizeColumnName("Id"));
            assertEquals("firstname", dialect.sanitizeColumnName("FirstName"));
        }
        
        @Test
        @DisplayName("mapSalesforceType returns TEXT for unknown types")
        void testMapUnknownType() {
            assertEquals("TEXT", dialect.mapSalesforceType("UNKNOWN_TYPE", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType returns null for compound types")
        void testMapCompoundTypes() {
            assertNull(dialect.mapSalesforceType("ADDRESS", 0));
            assertNull(dialect.mapSalesforceType("LOCATION", 0));
        }
    }
    
    // ============================================
    // SQL Server Dialect Tests
    // ============================================
    
    @Nested
    @DisplayName("SQL Server Dialect")
    class SqlServerDialectTests {
        
        private SqlServerDialect dialect;
        
        @BeforeEach
        void setUp() {
            dialect = new SqlServerDialect();
        }
        
        @Test
        @DisplayName("getBinaryType returns VARBINARY(MAX)")
        void testGetBinaryType() {
            assertEquals("VARBINARY(MAX)", dialect.getBinaryType());
        }
        
        @Test
        @DisplayName("mapSalesforceType maps BASE64 to VARBINARY(MAX)")
        void testMapBase64Type() {
            assertEquals("VARBINARY(MAX)", dialect.mapSalesforceType("BASE64", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps STRING types correctly")
        void testMapStringTypes() {
            assertEquals("NVARCHAR(255)", dialect.mapSalesforceType("STRING", 255));
            assertEquals("NVARCHAR(MAX)", dialect.mapSalesforceType("TEXTAREA", 0));
            assertEquals("NVARCHAR(MAX)", dialect.mapSalesforceType("LONGTEXTAREA", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps numeric types correctly")
        void testMapNumericTypes() {
            assertEquals("DECIMAL(18, 2)", dialect.mapSalesforceType("DOUBLE", 0));
            assertEquals("DECIMAL(18, 2)", dialect.mapSalesforceType("CURRENCY", 0));
            assertEquals("DECIMAL(18, 2)", dialect.mapSalesforceType("PERCENT", 0));
            assertEquals("INT", dialect.mapSalesforceType("INT", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps date/time types correctly")
        void testMapDateTypes() {
            assertEquals("DATE", dialect.mapSalesforceType("DATE", 0));
            assertEquals("DATETIME2", dialect.mapSalesforceType("DATETIME", 0));
            assertEquals("TIME", dialect.mapSalesforceType("TIME", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps boolean correctly")
        void testMapBooleanType() {
            assertEquals("BIT", dialect.mapSalesforceType("BOOLEAN", 0));
        }
        
        @Test
        @DisplayName("mapSalesforceType maps ID to NVARCHAR(18)")
        void testMapIdType() {
            assertEquals("NVARCHAR(18)", dialect.mapSalesforceType("ID", 0));
        }
        
        @Test
        @DisplayName("sanitizeTableName uses square brackets")
        void testSanitizeTableName() {
            assertEquals("[Account]", dialect.sanitizeTableName("Account"));
            assertEquals("[ContentVersion]", dialect.sanitizeTableName("ContentVersion"));
        }
        
        @Test
        @DisplayName("sanitizeColumnName uses square brackets")
        void testSanitizeColumnName() {
            assertEquals("[Id]", dialect.sanitizeColumnName("Id"));
            assertEquals("[FirstName]", dialect.sanitizeColumnName("FirstName"));
        }
        
        @Test
        @DisplayName("mapSalesforceType returns null for compound types")
        void testMapCompoundTypes() {
            assertNull(dialect.mapSalesforceType("ADDRESS", 0));
            assertNull(dialect.mapSalesforceType("LOCATION", 0));
        }
    }
}
