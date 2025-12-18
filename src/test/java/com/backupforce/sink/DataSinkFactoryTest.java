package com.backupforce.sink;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataSinkFactory.
 * Tests factory methods for creating various data sink implementations.
 */
@DisplayName("DataSinkFactory Tests")
class DataSinkFactoryTest {
    
    @Test
    @DisplayName("DatabaseType enum has correct display names")
    void testDatabaseTypeDisplayNames() {
        assertEquals("Snowflake", DataSinkFactory.DatabaseType.SNOWFLAKE.getDisplayName());
        assertEquals("SQL Server", DataSinkFactory.DatabaseType.SQL_SERVER.getDisplayName());
        assertEquals("PostgreSQL", DataSinkFactory.DatabaseType.POSTGRESQL.getDisplayName());
        assertEquals("MySQL", DataSinkFactory.DatabaseType.MYSQL.getDisplayName());
        assertEquals("Oracle", DataSinkFactory.DatabaseType.ORACLE.getDisplayName());
    }
    
    @Test
    @DisplayName("createCsvFileSink creates CsvFileSink with correct directory")
    void testCreateCsvFileSink() {
        DataSink sink = DataSinkFactory.createCsvFileSink("/output/path");
        
        assertNotNull(sink);
        assertTrue(sink instanceof CsvFileSink);
        assertEquals("CSV", sink.getType());
        assertTrue(sink.getDisplayName().contains("/output/path"));
    }
    
    @Test
    @DisplayName("createSnowflakeSink creates JdbcDatabaseSink with password auth")
    void testCreateSnowflakeSinkWithPassword() {
        DataSink sink = DataSinkFactory.createSnowflakeSink(
            "xy12345",
            "COMPUTE_WH",
            "SALESFORCE_DB",
            "PUBLIC",
            "testuser",
            "testpass"
        );
        
        assertNotNull(sink);
        assertTrue(sink instanceof JdbcDatabaseSink);
        assertTrue(sink.getDisplayName().contains("Snowflake"));
        assertTrue(sink.getDisplayName().contains("SALESFORCE_DB"));
    }
    
    @Test
    @DisplayName("createSnowflakeSink with null password uses SSO")
    void testCreateSnowflakeSinkWithNullPassword() {
        DataSink sink = DataSinkFactory.createSnowflakeSink(
            "xy12345",
            "COMPUTE_WH",
            "SALESFORCE_DB",
            "PUBLIC",
            "ssouser",
            null  // SSO mode
        );
        
        assertNotNull(sink);
        assertTrue(sink instanceof JdbcDatabaseSink);
    }
    
    @Test
    @DisplayName("createSnowflakeSink with empty password uses SSO")
    void testCreateSnowflakeSinkWithEmptyPassword() {
        DataSink sink = DataSinkFactory.createSnowflakeSink(
            "xy12345",
            "COMPUTE_WH",
            "SALESFORCE_DB",
            "PUBLIC",
            "ssouser",
            "   "  // Empty/whitespace password triggers SSO
        );
        
        assertNotNull(sink);
        assertTrue(sink instanceof JdbcDatabaseSink);
    }
    
    @Test
    @DisplayName("createSnowflakeSinkWithSSO creates sink with externalbrowser auth")
    void testCreateSnowflakeSinkWithSSO() {
        DataSink sink = DataSinkFactory.createSnowflakeSinkWithSSO(
            "xy12345",
            "COMPUTE_WH",
            "SALESFORCE_DB",
            "PUBLIC"
        );
        
        assertNotNull(sink);
        assertTrue(sink instanceof JdbcDatabaseSink);
        assertTrue(sink.getDisplayName().contains("SSO"));
    }
    
    @Test
    @DisplayName("createSqlServerSink creates JdbcDatabaseSink")
    void testCreateSqlServerSink() {
        DataSink sink = DataSinkFactory.createSqlServerSink(
            "localhost\\SQLEXPRESS",
            "SalesforceBackup",
            "sa",
            "password123"
        );
        
        assertNotNull(sink);
        assertTrue(sink instanceof JdbcDatabaseSink);
        assertTrue(sink.getDisplayName().contains("SQL Server"));
    }
    
    @Test
    @DisplayName("createPostgresSink creates JdbcDatabaseSink")
    void testCreatePostgresSink() {
        DataSink sink = DataSinkFactory.createPostgresSink(
            "localhost",
            5432,
            "sf_backup",
            "public",
            "postgres",
            "pgpass"
        );
        
        assertNotNull(sink);
        assertTrue(sink instanceof JdbcDatabaseSink);
        assertTrue(sink.getDisplayName().contains("PostgreSQL"));
    }
    
    @Test
    @DisplayName("DatabaseType valueOf works for all types")
    void testDatabaseTypeValueOf() {
        assertEquals(DataSinkFactory.DatabaseType.SNOWFLAKE, 
            DataSinkFactory.DatabaseType.valueOf("SNOWFLAKE"));
        assertEquals(DataSinkFactory.DatabaseType.SQL_SERVER, 
            DataSinkFactory.DatabaseType.valueOf("SQL_SERVER"));
        assertEquals(DataSinkFactory.DatabaseType.POSTGRESQL, 
            DataSinkFactory.DatabaseType.valueOf("POSTGRESQL"));
        assertEquals(DataSinkFactory.DatabaseType.MYSQL, 
            DataSinkFactory.DatabaseType.valueOf("MYSQL"));
        assertEquals(DataSinkFactory.DatabaseType.ORACLE, 
            DataSinkFactory.DatabaseType.valueOf("ORACLE"));
    }
    
    @Test
    @DisplayName("DatabaseType values() returns all types")
    void testDatabaseTypeValues() {
        DataSinkFactory.DatabaseType[] types = DataSinkFactory.DatabaseType.values();
        assertEquals(5, types.length);
    }
}
