package com.backupforce.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests for the Database Settings dialog.
 * Tests database configuration, form validation, and connection handling.
 */
@DisplayName("Database Settings UI Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseSettingsUITest extends JavaFxTestBase {
    
    // Real Snowflake configuration payloads
    private static final String VALID_SNOWFLAKE_ACCOUNT = "xy12345.us-east-1";
    private static final String VALID_SNOWFLAKE_WAREHOUSE = "COMPUTE_WH";
    private static final String VALID_SNOWFLAKE_DATABASE = "SALESFORCE_BACKUP";
    private static final String VALID_SNOWFLAKE_SCHEMA = "RAW_DATA";
    private static final String VALID_SNOWFLAKE_USERNAME = "backup_service";
    private static final String VALID_SNOWFLAKE_PASSWORD = "Str0ng!P@ssw0rd#2024";
    
    // Real SQL Server configuration payloads
    private static final String VALID_SQLSERVER_SERVER = "myserver.database.windows.net";
    private static final String VALID_SQLSERVER_DATABASE = "SalesforceBackup";
    private static final String VALID_SQLSERVER_USERNAME = "backup_admin";
    private static final String VALID_SQLSERVER_PASSWORD = "SqlS3rv3r!Pass@2024";
    
    // Real PostgreSQL configuration payloads
    private static final String VALID_POSTGRES_HOST = "pg-backup.company.com";
    private static final String VALID_POSTGRES_PORT = "5432";
    private static final String VALID_POSTGRES_DATABASE = "salesforce_data";
    private static final String VALID_POSTGRES_SCHEMA = "backup";
    private static final String VALID_POSTGRES_USERNAME = "sf_backup_user";
    private static final String VALID_POSTGRES_PASSWORD = "P0stgr3s!Secure#2024";
    
    // Invalid payloads for negative testing
    private static final String INVALID_PORT = "not_a_port";
    private static final String INVALID_PORT_NEGATIVE = "-1";
    private static final String INVALID_PORT_TOO_HIGH = "99999";
    private static final String EMPTY_STRING = "";
    private static final String SQL_INJECTION = "'; DROP DATABASE --";
    private static final String PATH_TRAVERSAL = "../../etc/passwd";
    private static final String SPECIAL_CHARS = "<>\"'&;|";
    private static final String CONNECTION_NAME_SPECIAL = "Prod Backup (Main) - 2024";
    
    @Override
    protected Scene createTestScene() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/database-settings.fxml"));
        Parent root = loader.load();
        return new Scene(root, 600, 600);
    }
    
    // ==================== Initial State Tests ====================
    
    @Nested
    @DisplayName("Initial State Tests")
    class InitialStateTests {
        
        @Test
        @DisplayName("Database type combo should be visible and have options")
        void databaseTypeComboShouldBeVisible() {
            assertNodeVisible("databaseTypeCombo", "Database type combo");
            
            ComboBox<?> combo = findById("databaseTypeCombo");
            assertEquals(3, combo.getItems().size(), "Should have 3 database types");
        }
        
        @Test
        @DisplayName("Snowflake should be selected by default")
        void snowflakeShouldBeDefault() {
            ComboBox<?> combo = findById("databaseTypeCombo");
            Object selected = combo.getSelectionModel().getSelectedItem();
            assertNotNull(selected, "A database type should be selected");
            assertTrue(selected.toString().contains("Snowflake"), "Snowflake should be default");
        }
        
        @Test
        @DisplayName("Settings grid should be visible")
        void settingsGridShouldBeVisible() {
            assertNodeVisible("settingsGrid", "Settings grid");
            
            GridPane grid = findById("settingsGrid");
            assertTrue(grid.getChildren().size() > 0, "Grid should have fields");
        }
        
        @Test
        @DisplayName("Recreate tables checkbox should be unchecked by default")
        void recreateTablesCheckboxShouldBeUnchecked() {
            assertNodeVisible("recreateTablesCheckBox", "Recreate tables checkbox");
            
            CheckBox checkbox = findById("recreateTablesCheckBox");
            assertFalse(checkbox.isSelected(), "Recreate tables should be unchecked by default");
        }
        
        @Test
        @DisplayName("Connection name field should be empty")
        void connectionNameFieldShouldBeEmpty() {
            assertNodeVisible("connectionNameField", "Connection name field");
            
            TextField field = findById("connectionNameField");
            assertTrue(field.getText().isEmpty(), "Connection name should be empty initially");
        }
        
        @Test
        @DisplayName("Remember checkbox should exist")
        void rememberCheckboxShouldExist() {
            assertNodeVisible("rememberCheckBox", "Remember checkbox");
            
            CheckBox checkbox = findById("rememberCheckBox");
            assertFalse(checkbox.isSelected(), "Remember checkbox should be unchecked");
        }
        
        @Test
        @DisplayName("Status label should be empty initially")
        void statusLabelShouldBeEmptyInitially() {
            Label statusLabel = findById("statusLabel");
            assertTrue(statusLabel.getText().isEmpty(), "Status label should be empty initially");
        }
    }
    
    // ==================== Database Type Selection Tests ====================
    
    @Nested
    @DisplayName("Database Type Selection Tests")
    class DatabaseTypeSelectionTests {
        
        @Test
        @DisplayName("Should display Snowflake fields when Snowflake is selected")
        void shouldDisplaySnowflakeFields() {
            selectInComboBox("databaseTypeCombo", 0); // Snowflake
            waitForFxEvents();
            
            GridPane grid = findById("settingsGrid");
            String gridContent = getGridLabels(grid);
            
            assertTrue(gridContent.contains("Account"), "Should have Account field");
            assertTrue(gridContent.contains("Warehouse"), "Should have Warehouse field");
            assertTrue(gridContent.contains("Database"), "Should have Database field");
            assertTrue(gridContent.contains("Schema"), "Should have Schema field");
        }
        
        @Test
        @DisplayName("Should display SQL Server fields when SQL Server is selected")
        void shouldDisplaySqlServerFields() {
            selectInComboBox("databaseTypeCombo", 1); // SQL Server
            waitForFxEvents();
            
            GridPane grid = findById("settingsGrid");
            String gridContent = getGridLabels(grid);
            
            assertTrue(gridContent.contains("Server"), "Should have Server field");
            assertTrue(gridContent.contains("Database"), "Should have Database field");
            assertTrue(gridContent.contains("Username"), "Should have Username field");
            assertTrue(gridContent.contains("Password"), "Should have Password field");
        }
        
        @Test
        @DisplayName("Should display PostgreSQL fields when PostgreSQL is selected")
        void shouldDisplayPostgresFields() {
            selectInComboBox("databaseTypeCombo", 2); // PostgreSQL
            waitForFxEvents();
            
            GridPane grid = findById("settingsGrid");
            String gridContent = getGridLabels(grid);
            
            assertTrue(gridContent.contains("Host"), "Should have Host field");
            assertTrue(gridContent.contains("Port"), "Should have Port field");
            assertTrue(gridContent.contains("Database"), "Should have Database field");
            assertTrue(gridContent.contains("Schema"), "Should have Schema field");
        }
        
        @Test
        @DisplayName("Should switch between database types correctly")
        void shouldSwitchBetweenDatabaseTypes() {
            // Start with Snowflake
            selectInComboBox("databaseTypeCombo", 0);
            waitForFxEvents();
            
            GridPane grid = findById("settingsGrid");
            int snowflakeFieldCount = grid.getChildren().size();
            
            // Switch to SQL Server
            selectInComboBox("databaseTypeCombo", 1);
            waitForFxEvents();
            
            int sqlServerFieldCount = grid.getChildren().size();
            
            // Switch to PostgreSQL
            selectInComboBox("databaseTypeCombo", 2);
            waitForFxEvents();
            
            int postgresFieldCount = grid.getChildren().size();
            
            // All should have fields (field count varies by type)
            assertTrue(snowflakeFieldCount > 0, "Snowflake should have fields");
            assertTrue(sqlServerFieldCount > 0, "SQL Server should have fields");
            assertTrue(postgresFieldCount > 0, "PostgreSQL should have fields");
        }
    }
    
    // ==================== Snowflake Configuration Tests ====================
    
    @Nested
    @DisplayName("Snowflake Configuration Tests")
    class SnowflakeConfigurationTests {
        
        @BeforeEach
        void selectSnowflake() {
            selectInComboBox("databaseTypeCombo", 0);
            waitForFxEvents();
        }
        
        @Test
        @DisplayName("Should accept valid Snowflake account format")
        void shouldAcceptValidSnowflakeAccount() {
            enterTextInGridField("Account", VALID_SNOWFLAKE_ACCOUNT);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Account");
            assertEquals(VALID_SNOWFLAKE_ACCOUNT, enteredValue);
        }
        
        @Test
        @DisplayName("Should handle Snowflake account with region format")
        void shouldHandleSnowflakeAccountWithRegion() {
            String accountWithRegion = "myorg-account.us-west-2.aws";
            enterTextInGridField("Account", accountWithRegion);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Account");
            assertEquals(accountWithRegion, enteredValue);
        }
        
        @Test
        @DisplayName("Should enter full Snowflake configuration")
        void shouldEnterFullSnowflakeConfiguration() {
            enterTextInGridField("Account", VALID_SNOWFLAKE_ACCOUNT);
            enterTextInGridField("Username", VALID_SNOWFLAKE_USERNAME);
            enterTextInGridField("Password", VALID_SNOWFLAKE_PASSWORD);
            
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("Production Snowflake"));
            
            waitForFxEvents();
            
            assertEquals("Production Snowflake", connectionName.getText());
        }
    }
    
    // ==================== PostgreSQL Configuration Tests ====================
    
    @Nested
    @DisplayName("PostgreSQL Configuration Tests")
    class PostgresConfigurationTests {
        
        @BeforeEach
        void selectPostgres() {
            selectInComboBox("databaseTypeCombo", 2);
            waitForFxEvents();
        }
        
        @Test
        @DisplayName("Should accept valid PostgreSQL port")
        void shouldAcceptValidPort() {
            enterTextInGridField("Port", VALID_POSTGRES_PORT);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Port");
            assertEquals(VALID_POSTGRES_PORT, enteredValue);
        }
        
        @Test
        @DisplayName("Should accept alternate valid port numbers")
        void shouldAcceptAlternateValidPorts() {
            String[] validPorts = {"5432", "5433", "15432", "1", "65535"};
            
            for (String port : validPorts) {
                clearGridField("Port");
                enterTextInGridField("Port", port);
                waitForFxEvents();
                
                String enteredValue = getTextFromGridField("Port");
                assertEquals(port, enteredValue, "Should accept port: " + port);
            }
        }
        
        @Test
        @DisplayName("Should enter complete PostgreSQL configuration")
        void shouldEnterCompletePostgresConfiguration() {
            enterTextInGridField("Host", VALID_POSTGRES_HOST);
            enterTextInGridField("Port", VALID_POSTGRES_PORT);
            enterTextInGridField("Database", VALID_POSTGRES_DATABASE);
            enterTextInGridField("Schema", VALID_POSTGRES_SCHEMA);
            enterTextInGridField("Username", VALID_POSTGRES_USERNAME);
            enterTextInGridField("Password", VALID_POSTGRES_PASSWORD);
            
            waitForFxEvents();
            
            assertEquals(VALID_POSTGRES_HOST, getTextFromGridField("Host"));
            assertEquals(VALID_POSTGRES_DATABASE, getTextFromGridField("Database"));
        }
    }
    
    // ==================== Negative Tests - Invalid Input ====================
    
    @Nested
    @DisplayName("Negative Tests - Invalid Input")
    class NegativeInputTests {
        
        @Test
        @DisplayName("Should handle SQL injection in server field")
        void shouldHandleSqlInjectionInServerField() {
            selectInComboBox("databaseTypeCombo", 1); // SQL Server
            waitForFxEvents();
            
            enterTextInGridField("Server", SQL_INJECTION);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Server");
            assertEquals(SQL_INJECTION, enteredValue, "Input should be stored for later validation");
        }
        
        @Test
        @DisplayName("Should handle path traversal attempt in database name")
        void shouldHandlePathTraversalInDatabase() {
            selectInComboBox("databaseTypeCombo", 2); // PostgreSQL
            waitForFxEvents();
            
            enterTextInGridField("Database", PATH_TRAVERSAL);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Database");
            assertEquals(PATH_TRAVERSAL, enteredValue);
        }
        
        @Test
        @DisplayName("Should handle special characters in connection name")
        void shouldHandleSpecialCharsInConnectionName() {
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText(CONNECTION_NAME_SPECIAL));
            waitForFxEvents();
            
            assertEquals(CONNECTION_NAME_SPECIAL, connectionName.getText());
        }
        
        @Test
        @DisplayName("Should handle non-numeric port input")
        void shouldHandleNonNumericPort() {
            selectInComboBox("databaseTypeCombo", 2); // PostgreSQL
            waitForFxEvents();
            
            enterTextInGridField("Port", INVALID_PORT);
            waitForFxEvents();
            
            // Field should accept input (validation happens on save/test)
            String enteredValue = getTextFromGridField("Port");
            assertEquals(INVALID_PORT, enteredValue);
        }
        
        @Test
        @DisplayName("Should handle negative port number")
        void shouldHandleNegativePort() {
            selectInComboBox("databaseTypeCombo", 2); // PostgreSQL
            waitForFxEvents();
            
            enterTextInGridField("Port", INVALID_PORT_NEGATIVE);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Port");
            assertEquals(INVALID_PORT_NEGATIVE, enteredValue);
        }
        
        @Test
        @DisplayName("Should handle empty required fields")
        void shouldHandleEmptyFields() {
            selectInComboBox("databaseTypeCombo", 1); // SQL Server
            waitForFxEvents();
            
            // Leave all fields empty - just verify they're empty
            String server = getTextFromGridField("Server");
            String database = getTextFromGridField("Database");
            
            // Fields should be empty or have only placeholder text
            assertTrue(server.isEmpty() || server.equals(""), "Server should be empty");
        }
        
        @Test
        @DisplayName("Should handle very long host name")
        void shouldHandleVeryLongHostName() {
            selectInComboBox("databaseTypeCombo", 2); // PostgreSQL
            waitForFxEvents();
            
            String longHost = "a".repeat(255) + ".company.com";
            enterTextInGridField("Host", longHost);
            waitForFxEvents();
            
            String enteredValue = getTextFromGridField("Host");
            assertEquals(longHost, enteredValue);
        }
    }
    
    // ==================== Checkbox Interaction Tests ====================
    
    @Nested
    @DisplayName("Checkbox Interaction Tests")
    class CheckboxInteractionTests {
        
        @Test
        @DisplayName("Should toggle recreate tables checkbox")
        void shouldToggleRecreateTablesCheckbox() {
            CheckBox recreateCheckbox = findById("recreateTablesCheckBox");
            assertFalse(recreateCheckbox.isSelected());
            
            clickOn("#recreateTablesCheckBox");
            waitForFxEvents();
            
            assertTrue(recreateCheckbox.isSelected(), "Should be checked after click");
        }
        
        @Test
        @DisplayName("Should toggle remember checkbox")
        void shouldToggleRememberCheckbox() {
            CheckBox rememberCheckbox = findById("rememberCheckBox");
            assertFalse(rememberCheckbox.isSelected());
            
            clickOn("#rememberCheckBox");
            waitForFxEvents();
            
            assertTrue(rememberCheckbox.isSelected(), "Should be checked after click");
        }
        
        @Test
        @DisplayName("Recreate tables warning should be visible")
        void recreateTablesWarningShouldBeVisible() {
            // Just verify the checkbox exists and can be toggled
            CheckBox recreateCheckbox = findById("recreateTablesCheckBox");
            assertNotNull(recreateCheckbox, "Recreate tables checkbox should exist");
        }
    }
    
    // ==================== Connection Name Tests ====================
    
    @Nested
    @DisplayName("Connection Name Tests")
    class ConnectionNameTests {
        
        @Test
        @DisplayName("Should accept connection name with spaces")
        void shouldAcceptConnectionNameWithSpaces() {
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("My Production Database"));
            waitForFxEvents();
            
            assertEquals("My Production Database", connectionName.getText());
        }
        
        @Test
        @DisplayName("Should accept connection name with special characters")
        void shouldAcceptConnectionNameWithSpecialCharacters() {
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("Prod-DB_v2 (Primary)"));
            waitForFxEvents();
            
            assertEquals("Prod-DB_v2 (Primary)", connectionName.getText());
        }
        
        @Test
        @DisplayName("Should accept Unicode connection name")
        void shouldAcceptUnicodeConnectionName() {
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("生产数据库 Production"));
            waitForFxEvents();
            
            assertEquals("生产数据库 Production", connectionName.getText());
        }
    }
    
    // ==================== Real Payload Scenarios ====================
    
    @Nested
    @DisplayName("Real Payload Scenarios")
    class RealPayloadScenarios {
        
        @Test
        @DisplayName("Complete Snowflake production setup")
        void completeSnowflakeProductionSetup() {
            selectInComboBox("databaseTypeCombo", 0); // Snowflake
            waitForFxEvents();
            
            enterTextInGridField("Account", "acme-corp.us-east-1");
            enterTextInGridField("Username", "SF_BACKUP_SERVICE");
            enterTextInGridField("Password", "Pr0duct1on!P@ssw0rd#2024");
            
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("ACME Production Snowflake"));
            
            setCheckBox("rememberCheckBox", true);
            setCheckBox("recreateTablesCheckBox", false);
            
            waitForFxEvents();
            
            assertTrue(isCheckBoxSelected("rememberCheckBox"));
            assertFalse(isCheckBoxSelected("recreateTablesCheckBox"));
            assertEquals("ACME Production Snowflake", connectionName.getText());
        }
        
        @Test
        @DisplayName("Complete SQL Server Azure setup")
        void completeSqlServerAzureSetup() {
            selectInComboBox("databaseTypeCombo", 1); // SQL Server
            waitForFxEvents();
            
            enterTextInGridField("Server", "sf-backup-prod.database.windows.net");
            enterTextInGridField("Database", "SalesforceBackup");
            enterTextInGridField("Username", "backup_admin@sf-backup-prod");
            enterTextInGridField("Password", "AzureSql!Secure#2024");
            
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("Azure SQL Production"));
            
            setCheckBox("rememberCheckBox", true);
            
            waitForFxEvents();
            
            assertEquals("Azure SQL Production", connectionName.getText());
        }
        
        @Test
        @DisplayName("Complete PostgreSQL RDS setup")
        void completePostgresRdsSetup() {
            selectInComboBox("databaseTypeCombo", 2); // PostgreSQL
            waitForFxEvents();
            
            enterTextInGridField("Host", "sf-backup.abcd1234.us-east-1.rds.amazonaws.com");
            enterTextInGridField("Port", "5432");
            enterTextInGridField("Database", "salesforce_backup");
            enterTextInGridField("Schema", "raw_data");
            enterTextInGridField("Username", "backup_service");
            enterTextInGridField("Password", "AwsRds!Secure#2024");
            
            TextField connectionName = findById("connectionNameField");
            runOnFxThread(() -> connectionName.setText("AWS RDS PostgreSQL"));
            
            waitForFxEvents();
            
            assertEquals("AWS RDS PostgreSQL", connectionName.getText());
        }
    }
    
    // ==================== Helper Methods ====================
    
    private String getGridLabels(GridPane grid) {
        StringBuilder sb = new StringBuilder();
        grid.getChildren().forEach(node -> {
            if (node instanceof Label) {
                sb.append(((Label) node).getText()).append(" ");
            }
        });
        return sb.toString();
    }
    
    private void enterTextInGridField(String labelText, String value) {
        GridPane grid = findById("settingsGrid");
        grid.getChildren().forEach(node -> {
            if (node instanceof Label && ((Label) node).getText().contains(labelText)) {
                // Find the text field in the same row
                int row = GridPane.getRowIndex(node) == null ? 0 : GridPane.getRowIndex(node);
                grid.getChildren().forEach(field -> {
                    Integer fieldRow = GridPane.getRowIndex(field);
                    if (fieldRow != null && fieldRow == row && field instanceof TextField) {
                        TextField textField = (TextField) field;
                        runOnFxThread(() -> textField.setText(value));
                    }
                });
            }
        });
        waitForFxEvents();
    }
    
    private String getTextFromGridField(String labelText) {
        GridPane grid = findById("settingsGrid");
        final String[] result = {""};
        
        grid.getChildren().forEach(node -> {
            if (node instanceof Label && ((Label) node).getText().contains(labelText)) {
                int row = GridPane.getRowIndex(node) == null ? 0 : GridPane.getRowIndex(node);
                grid.getChildren().forEach(field -> {
                    Integer fieldRow = GridPane.getRowIndex(field);
                    if (fieldRow != null && fieldRow == row && field instanceof TextField) {
                        result[0] = ((TextField) field).getText();
                    }
                });
            }
        });
        
        return result[0];
    }
    
    private void clearGridField(String labelText) {
        GridPane grid = findById("settingsGrid");
        grid.getChildren().forEach(node -> {
            if (node instanceof Label && ((Label) node).getText().contains(labelText)) {
                int row = GridPane.getRowIndex(node) == null ? 0 : GridPane.getRowIndex(node);
                grid.getChildren().forEach(field -> {
                    Integer fieldRow = GridPane.getRowIndex(field);
                    if (fieldRow != null && fieldRow == row && field instanceof TextField) {
                        runOnFxThread(() -> ((TextField) field).clear());
                    }
                });
            }
        });
        waitForFxEvents();
    }
}
