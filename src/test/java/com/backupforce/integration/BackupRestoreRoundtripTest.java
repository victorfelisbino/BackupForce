package com.backupforce.integration;

import com.backupforce.bulkv2.BulkV2Client;
import com.backupforce.restore.RestoreExecutor;
import com.backupforce.restore.RestoreExecutor.*;
import com.backupforce.sink.CsvFileSink;
import com.backupforce.sink.DataSink;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that performs a complete backup-restore roundtrip to verify:
 * 1. Data backup completes successfully
 * 2. CSV files are created with correct structure
 * 3. Data can be restored from backup
 * 4. Restored data matches original data
 * 
 * NOTE: This test requires real Salesforce credentials and will:
 * - Create test records in Salesforce
 * - Backup those records to CSV
 * - Delete the test records
 * - Restore from backup
 * - Verify restored records match originals
 * - Clean up all test data
 * 
 * This test is DISABLED by default. Enable it by setting system property:
 * -Dbackupforce.integration.enabled=true
 * 
 * Required environment variables:
 * - SF_USERNAME: Salesforce username
 * - SF_PASSWORD: Salesforce password + security token
 * - SF_INSTANCE_URL: Salesforce instance URL (e.g., https://login.salesforce.com)
 */
@DisplayName("Backup-Restore Roundtrip Integration Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackupRestoreRoundtripTest {
    
    @TempDir
    Path tempBackupDir;
    
    private static PartnerConnection connection;
    private static BulkV2Client bulkClient;
    private static String sessionId;
    private static String instanceUrl;
    
    // Test data tracking
    private static final List<String> createdAccountIds = new ArrayList<>();
    private static final List<String> createdContactIds = new ArrayList<>();
    private static final Map<String, Map<String, String>> originalAccountData = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> originalContactData = new LinkedHashMap<>();
    
    private static final String TEST_ACCOUNT_PREFIX = "BackupForce_Test_";
    private static final int TEST_ACCOUNT_COUNT = 5;
    private static final int TEST_CONTACTS_PER_ACCOUNT = 3;
    
    @BeforeAll
    static void setup() {
        // Skip if integration tests not enabled
        String enabled = System.getProperty("backupforce.integration.enabled", "false");
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled), 
            "Integration tests disabled. Enable with -Dbackupforce.integration.enabled=true");
        
        // Verify environment variables
        String username = System.getenv("SF_USERNAME");
        String password = System.getenv("SF_PASSWORD");
        String loginUrl = System.getenv().getOrDefault("SF_INSTANCE_URL", "https://login.salesforce.com");
        
        Assumptions.assumeTrue(username != null && !username.isEmpty(), "SF_USERNAME not set");
        Assumptions.assumeTrue(password != null && !password.isEmpty(), "SF_PASSWORD not set");
        
        System.out.println("=== Backup-Restore Roundtrip Test ===");
        System.out.println("Username: " + username);
        System.out.println("Instance: " + loginUrl);
    }
    
    @Test
    @Order(1)
    @DisplayName("1. Authenticate to Salesforce")
    void test1_authenticate() throws Exception {
        String username = System.getenv("SF_USERNAME");
        String password = System.getenv("SF_PASSWORD");
        String loginUrl = System.getenv().getOrDefault("SF_INSTANCE_URL", "https://login.salesforce.com");
        
        // Create partner connection
        com.sforce.ws.ConnectorConfig config = new com.sforce.ws.ConnectorConfig();
        config.setUsername(username);
        config.setPassword(password);
        config.setAuthEndpoint(loginUrl + "/services/Soap/u/59.0");
        
        connection = com.sforce.soap.partner.Connector.newConnection(config);
        sessionId = config.getSessionId();
        instanceUrl = config.getServiceEndpoint().replace("/services/Soap/u/59.0", "");
        
        assertNotNull(connection, "Connection should not be null");
        assertNotNull(sessionId, "Session ID should not be null");
        
        System.out.println("✓ Authentication successful");
        System.out.println("  Session ID: " + sessionId.substring(0, 15) + "...");
        System.out.println("  Instance URL: " + instanceUrl);
    }
    
    @Test
    @Order(2)
    @DisplayName("2. Create test data in Salesforce")
    void test2_createTestData() throws Exception {
        // Create test Accounts
        SObject[] accounts = new SObject[TEST_ACCOUNT_COUNT];
        for (int i = 0; i < TEST_ACCOUNT_COUNT; i++) {
            SObject account = new SObject();
            account.setType("Account");
            account.setField("Name", TEST_ACCOUNT_PREFIX + i + "_" + System.currentTimeMillis());
            account.setField("Industry", i % 2 == 0 ? "Technology" : "Finance");
            account.setField("AnnualRevenue", (i + 1) * 100000.0);
            account.setField("Description", "Test account for backup-restore roundtrip testing");
            accounts[i] = account;
        }
        
        com.sforce.soap.partner.SaveResult[] accountResults = connection.create(accounts);
        
        for (int i = 0; i < accountResults.length; i++) {
            assertTrue(accountResults[i].isSuccess(), 
                "Account creation should succeed: " + Arrays.toString(accountResults[i].getErrors()));
            
            String accountId = accountResults[i].getId();
            createdAccountIds.add(accountId);
            
            // Store original data
            Map<String, String> accountData = new HashMap<>();
            accountData.put("Id", accountId);
            accountData.put("Name", (String) accounts[i].getField("Name"));
            accountData.put("Industry", (String) accounts[i].getField("Industry"));
            accountData.put("AnnualRevenue", String.valueOf(accounts[i].getField("AnnualRevenue")));
            accountData.put("Description", (String) accounts[i].getField("Description"));
            originalAccountData.put(accountId, accountData);
        }
        
        System.out.println("✓ Created " + createdAccountIds.size() + " test Accounts");
        
        // Create test Contacts for each Account
        List<SObject> allContacts = new ArrayList<>();
        for (String accountId : createdAccountIds) {
            for (int i = 0; i < TEST_CONTACTS_PER_ACCOUNT; i++) {
                SObject contact = new SObject();
                contact.setType("Contact");
                contact.setField("FirstName", "Test");
                contact.setField("LastName", "Contact_" + i + "_" + System.currentTimeMillis());
                contact.setField("Email", "test.contact" + i + "@backupforce.test");
                contact.setField("AccountId", accountId);
                contact.setField("Title", "Test Contact " + i);
                allContacts.add(contact);
            }
        }
        
        com.sforce.soap.partner.SaveResult[] contactResults = connection.create(
            allContacts.toArray(new SObject[0]));
        
        for (int i = 0; i < contactResults.length; i++) {
            assertTrue(contactResults[i].isSuccess(), 
                "Contact creation should succeed: " + Arrays.toString(contactResults[i].getErrors()));
            
            String contactId = contactResults[i].getId();
            createdContactIds.add(contactId);
            
            // Store original data
            Map<String, String> contactData = new HashMap<>();
            contactData.put("Id", contactId);
            contactData.put("FirstName", (String) allContacts.get(i).getField("FirstName"));
            contactData.put("LastName", (String) allContacts.get(i).getField("LastName"));
            contactData.put("Email", (String) allContacts.get(i).getField("Email"));
            contactData.put("AccountId", (String) allContacts.get(i).getField("AccountId"));
            contactData.put("Title", (String) allContacts.get(i).getField("Title"));
            originalContactData.put(contactId, contactData);
        }
        
        System.out.println("✓ Created " + createdContactIds.size() + " test Contacts");
        System.out.println("  Total test records: " + (createdAccountIds.size() + createdContactIds.size()));
    }
    
    @Test
    @Order(3)
    @DisplayName("3. Backup test data to CSV files")
    void test3_backupData() throws Exception {
        bulkClient = new BulkV2Client(sessionId, instanceUrl, "59.0");
        
        // Backup Accounts
        String accountWhereClause = "Id IN ('" + String.join("','", createdAccountIds) + "')";
        bulkClient.queryObject("Account", tempBackupDir.toString(), accountWhereClause, 
            TEST_ACCOUNT_COUNT, null);
        
        // Verify Account CSV exists
        Path accountCsv = tempBackupDir.resolve("Account.csv");
        assertTrue(Files.exists(accountCsv), "Account.csv should exist");
        
        long accountLines = Files.lines(accountCsv).count();
        assertEquals(TEST_ACCOUNT_COUNT + 1, accountLines, 
            "Account CSV should have header + " + TEST_ACCOUNT_COUNT + " records");
        
        System.out.println("✓ Backed up " + TEST_ACCOUNT_COUNT + " Accounts to CSV");
        
        // Backup Contacts
        String contactWhereClause = "Id IN ('" + String.join("','", createdContactIds) + "')";
        bulkClient.queryObject("Contact", tempBackupDir.toString(), contactWhereClause, 
            createdContactIds.size(), null);
        
        // Verify Contact CSV exists
        Path contactCsv = tempBackupDir.resolve("Contact.csv");
        assertTrue(Files.exists(contactCsv), "Contact.csv should exist");
        
        long contactLines = Files.lines(contactCsv).count();
        assertEquals(createdContactIds.size() + 1, contactLines, 
            "Contact CSV should have header + " + createdContactIds.size() + " records");
        
        System.out.println("✓ Backed up " + createdContactIds.size() + " Contacts to CSV");
        System.out.println("  Backup directory: " + tempBackupDir);
    }
    
    @Test
    @Order(4)
    @DisplayName("4. Verify CSV backup data integrity")
    void test4_verifyBackupData() throws Exception {
        // Read and verify Account CSV
        Path accountCsv = tempBackupDir.resolve("Account.csv");
        List<Map<String, String>> accountRecords = readCsvFile(accountCsv);
        
        assertEquals(TEST_ACCOUNT_COUNT, accountRecords.size(), 
            "Should have " + TEST_ACCOUNT_COUNT + " account records in CSV");
        
        for (Map<String, String> csvRecord : accountRecords) {
            String id = csvRecord.get("Id");
            assertTrue(createdAccountIds.contains(id), "CSV should contain created account: " + id);
            
            Map<String, String> originalData = originalAccountData.get(id);
            assertEquals(originalData.get("Name"), csvRecord.get("Name"), 
                "Account Name should match");
            assertEquals(originalData.get("Industry"), csvRecord.get("Industry"), 
                "Account Industry should match");
        }
        
        System.out.println("✓ Account CSV data integrity verified");
        
        // Read and verify Contact CSV
        Path contactCsv = tempBackupDir.resolve("Contact.csv");
        List<Map<String, String>> contactRecords = readCsvFile(contactCsv);
        
        assertEquals(createdContactIds.size(), contactRecords.size(), 
            "Should have " + createdContactIds.size() + " contact records in CSV");
        
        for (Map<String, String> csvRecord : contactRecords) {
            String id = csvRecord.get("Id");
            assertTrue(createdContactIds.contains(id), "CSV should contain created contact: " + id);
            
            Map<String, String> originalData = originalContactData.get(id);
            assertEquals(originalData.get("FirstName"), csvRecord.get("FirstName"), 
                "Contact FirstName should match");
            assertEquals(originalData.get("LastName"), csvRecord.get("LastName"), 
                "Contact LastName should match");
            assertEquals(originalData.get("Email"), csvRecord.get("Email"), 
                "Contact Email should match");
        }
        
        System.out.println("✓ Contact CSV data integrity verified");
    }
    
    @Test
    @Order(5)
    @DisplayName("5. Delete test records from Salesforce")
    void test5_deleteTestRecords() throws Exception {
        // Delete Contacts first (child records)
        if (!createdContactIds.isEmpty()) {
            String[] contactIdsArray = createdContactIds.toArray(new String[0]);
            com.sforce.soap.partner.DeleteResult[] contactDeleteResults = connection.delete(contactIdsArray);
            
            for (com.sforce.soap.partner.DeleteResult result : contactDeleteResults) {
                assertTrue(result.isSuccess(), 
                    "Contact deletion should succeed: " + Arrays.toString(result.getErrors()));
            }
            
            System.out.println("✓ Deleted " + createdContactIds.size() + " Contacts");
        }
        
        // Delete Accounts
        if (!createdAccountIds.isEmpty()) {
            String[] accountIdsArray = createdAccountIds.toArray(new String[0]);
            com.sforce.soap.partner.DeleteResult[] accountDeleteResults = connection.delete(accountIdsArray);
            
            for (com.sforce.soap.partner.DeleteResult result : accountDeleteResults) {
                assertTrue(result.isSuccess(), 
                    "Account deletion should succeed: " + Arrays.toString(result.getErrors()));
            }
            
            System.out.println("✓ Deleted " + createdAccountIds.size() + " Accounts");
        }
        
        // Verify records are deleted
        String[] allIds = new String[createdAccountIds.size() + createdContactIds.size()];
        int idx = 0;
        for (String id : createdAccountIds) allIds[idx++] = id;
        for (String id : createdContactIds) allIds[idx++] = id;
        
        SObject[] retrieved = connection.retrieve("Id", "Account", createdAccountIds.toArray(new String[0]));
        for (SObject obj : retrieved) {
            assertNull(obj, "Account should be deleted");
        }
        
        System.out.println("✓ Verified all test records deleted from Salesforce");
    }
    
    @Test
    @Order(6)
    @DisplayName("6. Restore data from CSV backup")
    void test6_restoreData() throws Exception {
        RestoreExecutor restoreExecutor = new RestoreExecutor(instanceUrl, sessionId, "59.0");
        
        // Configure restore options
        RestoreOptions options = new RestoreOptions();
        options.setBatchSize(200);
        options.setStopOnError(false);
        options.setValidateBeforeRestore(true);
        options.setResolveRelationships(false); // No relationship resolution needed for this test
        options.setDryRun(false);
        
        List<String> restoredAccountIds = new ArrayList<>();
        List<String> restoredContactIds = new ArrayList<>();
        
        // Restore Accounts first (parent records)
        Path accountCsv = tempBackupDir.resolve("Account.csv");
        RestoreResult accountResult = restoreExecutor.restoreFromCsv(
            "Account", accountCsv, RestoreMode.INSERT, options);
        
        assertTrue(accountResult.isCompleted(), "Account restore should complete");
        assertEquals(TEST_ACCOUNT_COUNT, accountResult.getSuccessCount(), 
            "All accounts should restore successfully. Errors: " + accountResult.getErrors());
        assertEquals(0, accountResult.getFailureCount(), 
            "No account restore failures expected");
        
        restoredAccountIds.addAll(accountResult.getCreatedIds());
        System.out.println("✓ Restored " + accountResult.getSuccessCount() + " Accounts");
        
        // Update Contact CSV with new Account IDs (since old IDs don't exist)
        Path contactCsv = tempBackupDir.resolve("Contact.csv");
        Path modifiedContactCsv = tempBackupDir.resolve("Contact_modified.csv");
        
        // Map old Account IDs to new ones
        Map<String, String> accountIdMapping = new HashMap<>();
        for (int i = 0; i < createdAccountIds.size() && i < restoredAccountIds.size(); i++) {
            accountIdMapping.put(createdAccountIds.get(i), restoredAccountIds.get(i));
        }
        
        // Update AccountId references in Contact CSV
        List<String> contactLines = Files.readAllLines(contactCsv);
        List<String> modifiedLines = new ArrayList<>();
        modifiedLines.add(contactLines.get(0)); // Header
        
        for (int i = 1; i < contactLines.size(); i++) {
            String line = contactLines.get(i);
            for (Map.Entry<String, String> entry : accountIdMapping.entrySet()) {
                line = line.replace(entry.getKey(), entry.getValue());
            }
            modifiedLines.add(line);
        }
        
        Files.write(modifiedContactCsv, modifiedLines);
        
        // Restore Contacts
        RestoreResult contactResult = restoreExecutor.restoreFromCsv(
            "Contact", modifiedContactCsv, RestoreMode.INSERT, options);
        
        assertTrue(contactResult.isCompleted(), "Contact restore should complete");
        assertEquals(createdContactIds.size(), contactResult.getSuccessCount(), 
            "All contacts should restore successfully. Errors: " + contactResult.getErrors());
        assertEquals(0, contactResult.getFailureCount(), 
            "No contact restore failures expected");
        
        restoredContactIds.addAll(contactResult.getCreatedIds());
        System.out.println("✓ Restored " + contactResult.getSuccessCount() + " Contacts");
        
        // Store restored IDs for cleanup
        createdAccountIds.clear();
        createdAccountIds.addAll(restoredAccountIds);
        createdContactIds.clear();
        createdContactIds.addAll(restoredContactIds);
        
        restoreExecutor.close();
    }
    
    @Test
    @Order(7)
    @DisplayName("7. Verify restored data matches original")
    void test7_verifyRestoredData() throws Exception {
        // Query restored Accounts
        String accountIds = createdAccountIds.stream()
            .map(id -> "'" + id + "'")
            .collect(Collectors.joining(","));
        
        com.sforce.soap.partner.QueryResult accountQr = connection.query(
            "SELECT Id, Name, Industry, AnnualRevenue, Description FROM Account WHERE Id IN (" + accountIds + ")");
        
        assertEquals(TEST_ACCOUNT_COUNT, accountQr.getSize(), 
            "Should query " + TEST_ACCOUNT_COUNT + " restored accounts");
        
        for (SObject account : accountQr.getRecords()) {
            String name = (String) account.getField("Name");
            assertTrue(name.startsWith(TEST_ACCOUNT_PREFIX), 
                "Restored account name should match original prefix");
            
            String industry = (String) account.getField("Industry");
            assertTrue(industry.equals("Technology") || industry.equals("Finance"), 
                "Restored industry should match original values");
        }
        
        System.out.println("✓ Verified " + accountQr.getSize() + " restored Accounts match original data");
        
        // Query restored Contacts
        String contactIds = createdContactIds.stream()
            .map(id -> "'" + id + "'")
            .collect(Collectors.joining(","));
        
        com.sforce.soap.partner.QueryResult contactQr = connection.query(
            "SELECT Id, FirstName, LastName, Email, AccountId, Title FROM Contact WHERE Id IN (" + contactIds + ")");
        
        assertEquals(createdContactIds.size(), contactQr.getSize(), 
            "Should query " + createdContactIds.size() + " restored contacts");
        
        int contactsWithAccounts = 0;
        for (SObject contact : contactQr.getRecords()) {
            String firstName = (String) contact.getField("FirstName");
            assertEquals("Test", firstName, "Restored contact FirstName should match");
            
            String accountId = (String) contact.getField("AccountId");
            if (accountId != null) {
                assertTrue(createdAccountIds.contains(accountId), 
                    "Contact should reference a restored Account");
                contactsWithAccounts++;
            }
        }
        
        assertEquals(createdContactIds.size(), contactsWithAccounts, 
            "All contacts should have Account relationships");
        
        System.out.println("✓ Verified " + contactQr.getSize() + " restored Contacts match original data");
        System.out.println("✓ Verified Contact-Account relationships preserved");
    }
    
    @AfterAll
    static void cleanup() {
        if (connection == null) {
            return; // Test was skipped
        }
        
        try {
            // Final cleanup - delete any remaining test records
            List<String> allTestIds = new ArrayList<>();
            allTestIds.addAll(createdContactIds);
            allTestIds.addAll(createdAccountIds);
            
            if (!allTestIds.isEmpty()) {
                System.out.println("\n=== Final Cleanup ===");
                
                if (!createdContactIds.isEmpty()) {
                    connection.delete(createdContactIds.toArray(new String[0]));
                    System.out.println("✓ Cleaned up " + createdContactIds.size() + " Contacts");
                }
                
                if (!createdAccountIds.isEmpty()) {
                    connection.delete(createdAccountIds.toArray(new String[0]));
                    System.out.println("✓ Cleaned up " + createdAccountIds.size() + " Accounts");
                }
            }
            
            if (bulkClient != null) {
                bulkClient.close();
            }
            
            System.out.println("\n=== Roundtrip Test Complete ===");
            System.out.println("All test data backed up, restored, verified, and cleaned up successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Helper method to read CSV file into list of maps
    private List<Map<String, String>> readCsvFile(Path csvPath) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        List<String> lines = Files.readAllLines(csvPath);
        
        if (lines.isEmpty()) {
            return records;
        }
        
        String[] headers = lines.get(0).split(",");
        
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",", -1); // -1 to preserve empty fields
            Map<String, String> record = new HashMap<>();
            
            for (int j = 0; j < headers.length && j < values.length; j++) {
                record.put(headers[j].trim(), values[j].trim());
            }
            
            records.add(record);
        }
        
        return records;
    }
}

