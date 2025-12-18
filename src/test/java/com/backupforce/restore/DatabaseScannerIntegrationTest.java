package com.backupforce.restore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DatabaseScanner using Testcontainers.
 * Tests PostgreSQL and SQL Server database connections and backup table detection.
 * 
 * Note: These tests require Docker to be running.
 * Tests are automatically skipped if Docker is not available.
 */
@Testcontainers
@DisplayName("DatabaseScanner Integration Tests")
@EnabledIf("isDockerAvailable")
class DatabaseScannerIntegrationTest {
    
    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-latest")
        .acceptLicense();
    
    // ==================== PostgreSQL Tests ====================
    
    @Nested
    @DisplayName("PostgreSQL Integration")
    class PostgreSQLTests {
        
        @BeforeEach
        void setupPostgresData() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                // Drop tables if they exist (clean slate)
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS account CASCADE");
                    stmt.execute("DROP TABLE IF EXISTS contact CASCADE");
                    stmt.execute("DROP TABLE IF EXISTS opportunity CASCADE");
                    stmt.execute("DROP TABLE IF EXISTS non_sf_table CASCADE");
                }
                
                // Create Salesforce-like backup tables
                try (Statement stmt = conn.createStatement()) {
                    // Account table with SF fields
                    stmt.execute("CREATE TABLE account (" +
                        "id VARCHAR(18) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "createddate TIMESTAMP, " +
                        "lastmodifieddate TIMESTAMP, " +
                        "createdbyid VARCHAR(18), " +
                        "lastmodifiedbyid VARCHAR(18), " +
                        "ownerid VARCHAR(18), " +
                        "isdeleted BOOLEAN, " +
                        "billingcity VARCHAR(255))");
                    
                    // Contact table with SF fields
                    stmt.execute("CREATE TABLE contact (" +
                        "id VARCHAR(18) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "firstname VARCHAR(100), " +
                        "lastname VARCHAR(100), " +
                        "accountid VARCHAR(18), " +
                        "createddate TIMESTAMP, " +
                        "lastmodifieddate TIMESTAMP, " +
                        "createdbyid VARCHAR(18), " +
                        "isdeleted BOOLEAN)");
                    
                    // Opportunity table
                    stmt.execute("CREATE TABLE opportunity (" +
                        "id VARCHAR(18) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "accountid VARCHAR(18), " +
                        "stagename VARCHAR(100), " +
                        "createddate TIMESTAMP, " +
                        "lastmodifieddate TIMESTAMP, " +
                        "systemmodstamp TIMESTAMP, " +
                        "isdeleted BOOLEAN)");
                    
                    // Non-Salesforce table (should be ignored)
                    stmt.execute("CREATE TABLE non_sf_table (" +
                        "pk INT PRIMARY KEY, " +
                        "data VARCHAR(255), " +
                        "created TIMESTAMP)");
                }
                
                // Insert test data
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO account (id, name, createddate, isdeleted) VALUES " +
                        "('001000000000001', 'Acme Corp', CURRENT_TIMESTAMP, false)");
                    stmt.execute("INSERT INTO account (id, name, createddate, isdeleted) VALUES " +
                        "('001000000000002', 'Test Inc', CURRENT_TIMESTAMP, false)");
                    
                    stmt.execute("INSERT INTO contact (id, name, accountid, createddate, isdeleted) VALUES " +
                        "('003000000000001', 'John Doe', '001000000000001', CURRENT_TIMESTAMP, false)");
                    
                    stmt.execute("INSERT INTO opportunity (id, name, accountid, stagename, createddate, isdeleted) VALUES " +
                        "('006000000000001', 'Big Deal', '001000000000001', 'Closed Won', CURRENT_TIMESTAMP, false)");
                    
                    stmt.execute("INSERT INTO non_sf_table (pk, data) VALUES (1, 'test data')");
                }
                
                // Analyze tables to update row counts
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ANALYZE account");
                    stmt.execute("ANALYZE contact");
                    stmt.execute("ANALYZE opportunity");
                }
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Connection successful")
        void testPostgresConnection() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                assertTrue(conn.isValid(5));
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Detect Salesforce backup tables")
        void testDetectSfTables() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                List<String> sfTables = detectSalesforceTables(conn, "public");
                
                assertTrue(sfTables.contains("account"), "Should detect account table");
                assertTrue(sfTables.contains("contact"), "Should detect contact table");
                assertTrue(sfTables.contains("opportunity"), "Should detect opportunity table");
                assertFalse(sfTables.contains("non_sf_table"), "Should NOT detect non-SF table");
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Get record counts")
        void testRecordCounts() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                long accountCount = getTableRowCount(conn, "account");
                long contactCount = getTableRowCount(conn, "contact");
                
                assertEquals(2, accountCount, "Account should have 2 records");
                assertEquals(1, contactCount, "Contact should have 1 record");
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Read backup data correctly")
        void testReadBackupData() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                List<Map<String, Object>> records = readTableData(conn, "account");
                
                assertEquals(2, records.size());
                
                Map<String, Object> acme = records.stream()
                    .filter(r -> "Acme Corp".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();
                
                assertEquals("001000000000001", acme.get("id"));
                assertEquals(false, acme.get("isdeleted"));
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Verify relationship data preserved")
        void testRelationshipDataPreserved() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                // Query contact with account lookup
                String query = "SELECT c.id, c.name, c.accountid, a.name as account_name " +
                    "FROM contact c JOIN account a ON c.accountid = a.id";
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    
                    assertTrue(rs.next());
                    assertEquals("003000000000001", rs.getString("id"));
                    assertEquals("John Doe", rs.getString("name"));
                    assertEquals("001000000000001", rs.getString("accountid"));
                    assertEquals("Acme Corp", rs.getString("account_name"));
                }
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Handles empty tables")
        void testEmptyTables() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                // Create empty SF table
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS empty_sf_table");
                    stmt.execute("CREATE TABLE empty_sf_table (" +
                        "id VARCHAR(18) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "createddate TIMESTAMP, " +
                        "lastmodifieddate TIMESTAMP, " +
                        "createdbyid VARCHAR(18))");
                    stmt.execute("ANALYZE empty_sf_table");
                }
                
                // Should still detect as SF table
                List<String> sfTables = detectSalesforceTables(conn, "public");
                assertTrue(sfTables.contains("empty_sf_table"));
            }
        }
        
        @Test
        @DisplayName("PostgreSQL: Custom schema support")
        void testCustomSchema() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                
                // Create custom schema
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP SCHEMA IF EXISTS backup_schema CASCADE");
                    stmt.execute("CREATE SCHEMA backup_schema");
                    stmt.execute("CREATE TABLE backup_schema.lead (" +
                        "id VARCHAR(18) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "createddate TIMESTAMP, " +
                        "lastmodifieddate TIMESTAMP, " +
                        "createdbyid VARCHAR(18))");
                }
                
                List<String> sfTables = detectSalesforceTables(conn, "backup_schema");
                assertTrue(sfTables.contains("lead"));
            }
        }
    }
    
    // ==================== SQL Server Tests ====================
    
    @Nested
    @DisplayName("SQL Server Integration")
    class SQLServerTests {
        
        @BeforeEach
        void setupSqlServerData() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                
                // Create Salesforce-like backup tables
                try (Statement stmt = conn.createStatement()) {
                    // Drop if exists
                    stmt.execute("IF OBJECT_ID('dbo.Account', 'U') IS NOT NULL DROP TABLE dbo.Account");
                    stmt.execute("IF OBJECT_ID('dbo.Contact', 'U') IS NOT NULL DROP TABLE dbo.Contact");
                    stmt.execute("IF OBJECT_ID('dbo.NonSfTable', 'U') IS NOT NULL DROP TABLE dbo.NonSfTable");
                    
                    // Account table
                    stmt.execute("CREATE TABLE dbo.Account (" +
                        "Id VARCHAR(18) PRIMARY KEY, " +
                        "Name NVARCHAR(255), " +
                        "CreatedDate DATETIME2, " +
                        "LastModifiedDate DATETIME2, " +
                        "CreatedById VARCHAR(18), " +
                        "LastModifiedById VARCHAR(18), " +
                        "OwnerId VARCHAR(18), " +
                        "IsDeleted BIT, " +
                        "BillingCity NVARCHAR(255))");
                    
                    // Contact table
                    stmt.execute("CREATE TABLE dbo.Contact (" +
                        "Id VARCHAR(18) PRIMARY KEY, " +
                        "Name NVARCHAR(255), " +
                        "AccountId VARCHAR(18), " +
                        "CreatedDate DATETIME2, " +
                        "LastModifiedDate DATETIME2, " +
                        "SystemModstamp DATETIME2, " +
                        "IsDeleted BIT)");
                    
                    // Non-SF table
                    stmt.execute("CREATE TABLE dbo.NonSfTable (" +
                        "PK INT PRIMARY KEY, " +
                        "Data NVARCHAR(255))");
                }
                
                // Insert test data
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO dbo.Account (Id, Name, CreatedDate, IsDeleted) VALUES " +
                        "('001000000000001', 'Acme Corp', GETDATE(), 0)");
                    stmt.execute("INSERT INTO dbo.Account (Id, Name, CreatedDate, IsDeleted) VALUES " +
                        "('001000000000002', 'Test Inc', GETDATE(), 0)");
                    stmt.execute("INSERT INTO dbo.Account (Id, Name, CreatedDate, IsDeleted) VALUES " +
                        "('001000000000003', 'Sample LLC', GETDATE(), 0)");
                    
                    stmt.execute("INSERT INTO dbo.Contact (Id, Name, AccountId, CreatedDate, IsDeleted) VALUES " +
                        "('003000000000001', 'John Doe', '001000000000001', GETDATE(), 0)");
                    stmt.execute("INSERT INTO dbo.Contact (Id, Name, AccountId, CreatedDate, IsDeleted) VALUES " +
                        "('003000000000002', 'Jane Smith', '001000000000002', GETDATE(), 0)");
                    
                    stmt.execute("INSERT INTO dbo.NonSfTable (PK, Data) VALUES (1, 'test')");
                }
            }
        }
        
        @Test
        @DisplayName("SQL Server: Connection successful")
        void testSqlServerConnection() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                assertTrue(conn.isValid(5));
            }
        }
        
        @Test
        @DisplayName("SQL Server: Detect Salesforce backup tables")
        void testDetectSfTables() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                
                List<String> sfTables = detectSalesforceTables(conn, "dbo");
                
                assertTrue(sfTables.contains("Account"), "Should detect Account table");
                assertTrue(sfTables.contains("Contact"), "Should detect Contact table");
                assertFalse(sfTables.contains("NonSfTable"), "Should NOT detect non-SF table");
            }
        }
        
        @Test
        @DisplayName("SQL Server: Get record counts")
        void testRecordCounts() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                
                long accountCount = getTableRowCount(conn, "Account");
                long contactCount = getTableRowCount(conn, "Contact");
                
                assertEquals(3, accountCount, "Account should have 3 records");
                assertEquals(2, contactCount, "Contact should have 2 records");
            }
        }
        
        @Test
        @DisplayName("SQL Server: Verify data integrity")
        void testDataIntegrity() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                
                // Verify Salesforce ID format preserved
                String query = "SELECT Id FROM dbo.Account WHERE Id LIKE '001%'";
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    
                    int count = 0;
                    while (rs.next()) {
                        String id = rs.getString("Id");
                        assertEquals(15, id.length(), "SF ID should be 15 or 18 chars");
                        assertTrue(id.startsWith("001"), "Account ID should start with 001");
                        count++;
                    }
                    assertEquals(3, count);
                }
            }
        }
        
        @Test
        @DisplayName("SQL Server: Query with joins")
        void testJoinQueries() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                
                String query = "SELECT c.Name as ContactName, a.Name as AccountName " +
                    "FROM dbo.Contact c " +
                    "INNER JOIN dbo.Account a ON c.AccountId = a.Id " +
                    "ORDER BY c.Name";
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    
                    List<String> contacts = new ArrayList<>();
                    while (rs.next()) {
                        contacts.add(rs.getString("ContactName") + " @ " + rs.getString("AccountName"));
                    }
                    
                    assertEquals(2, contacts.size());
                    assertTrue(contacts.contains("Jane Smith @ Test Inc"));
                    assertTrue(contacts.contains("John Doe @ Acme Corp"));
                }
            }
        }
        
        @Test
        @DisplayName("SQL Server: Unicode data preserved")
        void testUnicodeSupport() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword())) {
                
                // Insert unicode data
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO dbo.Account (Id, Name, CreatedDate, IsDeleted) VALUES " +
                        "('001000000000004', N'日本語テスト', GETDATE(), 0)");
                    stmt.execute("INSERT INTO dbo.Account (Id, Name, CreatedDate, IsDeleted) VALUES " +
                        "('001000000000005', N'Ñoño Español', GETDATE(), 0)");
                }
                
                // Read back unicode
                String query = "SELECT Name FROM dbo.Account WHERE Id IN ('001000000000004', '001000000000005')";
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    
                    Set<String> names = new HashSet<>();
                    while (rs.next()) {
                        names.add(rs.getString("Name"));
                    }
                    
                    assertTrue(names.contains("日本語テスト"), "Japanese text should be preserved");
                    assertTrue(names.contains("Ñoño Español"), "Spanish text should be preserved");
                }
            }
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Detects Salesforce backup tables by checking for common SF fields
     */
    private List<String> detectSalesforceTables(Connection conn, String schema) throws SQLException {
        List<String> sfTables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        
        // Get all tables in schema
        try (ResultSet tables = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                
                // Check for SF fields
                Map<String, Boolean> columns = new HashMap<>();
                try (ResultSet cols = meta.getColumns(null, schema, tableName, null)) {
                    while (cols.next()) {
                        columns.put(cols.getString("COLUMN_NAME").toUpperCase(), true);
                    }
                }
                
                // Must have ID column
                if (!columns.containsKey("ID")) {
                    continue;
                }
                
                // Count common SF fields
                String[] sfFields = {"NAME", "CREATEDDATE", "LASTMODIFIEDDATE", 
                    "CREATEDBYID", "LASTMODIFIEDBYID", "SYSTEMMODSTAMP", "ISDELETED", "OWNERID"};
                int sfFieldCount = 0;
                for (String field : sfFields) {
                    if (columns.containsKey(field)) {
                        sfFieldCount++;
                    }
                }
                
                // Consider SF table if ID + at least 2 common fields
                if (sfFieldCount >= 2) {
                    sfTables.add(tableName);
                }
            }
        }
        
        return sfTables;
    }
    
    /**
     * Gets row count for a table
     */
    private long getTableRowCount(Connection conn, String tableName) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            rs.next();
            return rs.getLong(1);
        }
    }
    
    /**
     * Reads all data from a table
     */
    private List<Map<String, Object>> readTableData(Connection conn, String tableName) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        String query = "SELECT * FROM " + tableName;
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnName(i).toLowerCase();
                    record.put(colName, rs.getObject(i));
                }
                records.add(record);
            }
        }
        
        return records;
    }
}
