package com.backupforce.ui;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Database Settings UI logic.
 * Tests database configuration validation and connection string building.
 */
@DisplayName("Database Settings Logic Tests")
class DatabaseSettingsLogicTest {
    
    // ==================== Snowflake Configuration Tests ====================
    
    @Nested
    @DisplayName("Snowflake Configuration Tests")
    class SnowflakeConfigurationTests {
        
        @Test
        @DisplayName("Valid Snowflake account formats")
        void validSnowflakeAccountFormats() {
            String[] validAccounts = {
                "xy12345",
                "xy12345.us-east-1",
                "acme-corp.us-west-2.aws",
                "myorg-account.east-us-2.azure",
                "abc123.eu-west-1.aws"
            };
            
            for (String account : validAccounts) {
                assertTrue(SnowflakeConfig.isValidAccountFormat(account),
                    "Should accept: " + account);
            }
        }
        
        @Test
        @DisplayName("Invalid Snowflake account formats")
        void invalidSnowflakeAccountFormats() {
            String[] invalidAccounts = {
                "",
                "   ",
                "account with spaces",
                "account<script>",
                "account;DROP TABLE"
            };
            
            for (String account : invalidAccounts) {
                assertFalse(SnowflakeConfig.isValidAccountFormat(account),
                    "Should reject: " + account);
            }
        }
        
        @Test
        @DisplayName("Build Snowflake JDBC URL with username/password")
        void buildSnowflakeJdbcUrlWithUsernamePassword() {
            SnowflakeConfig config = new SnowflakeConfig();
            config.account = "xy12345.us-east-1";
            config.warehouse = "COMPUTE_WH";
            config.database = "SALESFORCE_BACKUP";
            config.schema = "RAW_DATA";
            config.username = "backup_user";
            config.password = "SecureP@ss123";
            config.useSso = false;
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.startsWith("jdbc:snowflake://"));
            assertTrue(url.contains("xy12345.us-east-1"));
            assertTrue(url.contains("warehouse=COMPUTE_WH"));
            assertTrue(url.contains("db=SALESFORCE_BACKUP"));
            assertTrue(url.contains("schema=RAW_DATA"));
        }
        
        @Test
        @DisplayName("Build Snowflake JDBC URL with SSO")
        void buildSnowflakeJdbcUrlWithSso() {
            SnowflakeConfig config = new SnowflakeConfig();
            config.account = "acme-corp.us-east-1";
            config.warehouse = "ANALYTICS_WH";
            config.database = "PROD_DB";
            config.schema = "PUBLIC";
            config.username = "sso_user@acme.com";
            config.useSso = true;
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.contains("authenticator=externalbrowser"));
        }
        
        @Test
        @DisplayName("Validate required Snowflake fields")
        void validateRequiredSnowflakeFields() {
            SnowflakeConfig config = new SnowflakeConfig();
            
            List<String> errors = config.validate();
            
            assertTrue(errors.contains("Account is required"));
            assertTrue(errors.contains("Warehouse is required"));
            assertTrue(errors.contains("Database is required"));
        }
        
        @Test
        @DisplayName("Password required when not using SSO")
        void passwordRequiredWhenNotUsingSso() {
            SnowflakeConfig config = new SnowflakeConfig();
            config.account = "test";
            config.warehouse = "WH";
            config.database = "DB";
            config.schema = "PUBLIC";
            config.username = "user";
            config.useSso = false;
            config.password = "";
            
            List<String> errors = config.validate();
            
            assertTrue(errors.contains("Password is required when not using SSO"));
        }
        
        @Test
        @DisplayName("Password not required when using SSO")
        void passwordNotRequiredWhenUsingSso() {
            SnowflakeConfig config = new SnowflakeConfig();
            config.account = "test";
            config.warehouse = "WH";
            config.database = "DB";
            config.schema = "PUBLIC";
            config.username = "user";
            config.useSso = true;
            config.password = "";
            
            List<String> errors = config.validate();
            
            assertFalse(errors.contains("Password is required when not using SSO"));
        }
    }
    
    // ==================== SQL Server Configuration Tests ====================
    
    @Nested
    @DisplayName("SQL Server Configuration Tests")
    class SqlServerConfigurationTests {
        
        @Test
        @DisplayName("Valid SQL Server server names")
        void validSqlServerServerNames() {
            String[] validServers = {
                "localhost",
                "localhost\\SQLEXPRESS",
                "192.168.1.100",
                "myserver.database.windows.net",
                "sql-prod.company.com"
            };
            
            for (String server : validServers) {
                assertTrue(SqlServerConfig.isValidServerName(server),
                    "Should accept: " + server);
            }
        }
        
        @Test
        @DisplayName("Build SQL Server JDBC URL")
        void buildSqlServerJdbcUrl() {
            SqlServerConfig config = new SqlServerConfig();
            config.server = "myserver.database.windows.net";
            config.database = "SalesforceBackup";
            config.username = "admin";
            config.password = "P@ssword123";
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.startsWith("jdbc:sqlserver://"));
            assertTrue(url.contains("myserver.database.windows.net"));
            assertTrue(url.contains("databaseName=SalesforceBackup"));
        }
        
        @Test
        @DisplayName("SQL Server with integrated security")
        void sqlServerWithIntegratedSecurity() {
            SqlServerConfig config = new SqlServerConfig();
            config.server = "localhost\\SQLEXPRESS";
            config.database = "TestDB";
            config.useIntegratedSecurity = true;
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.contains("integratedSecurity=true"));
        }
        
        @Test
        @DisplayName("SQL Server Azure connection")
        void sqlServerAzureConnection() {
            SqlServerConfig config = new SqlServerConfig();
            config.server = "sf-backup-prod.database.windows.net";
            config.database = "SalesforceBackup";
            config.username = "backup_admin@sf-backup-prod";
            config.password = "AzureSql!Secure#2024";
            config.encrypt = true;
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.contains("encrypt=true"));
        }
        
        @Test
        @DisplayName("Validate required SQL Server fields")
        void validateRequiredSqlServerFields() {
            SqlServerConfig config = new SqlServerConfig();
            
            List<String> errors = config.validate();
            
            assertTrue(errors.contains("Server is required"));
            assertTrue(errors.contains("Database is required"));
        }
    }
    
    // ==================== PostgreSQL Configuration Tests ====================
    
    @Nested
    @DisplayName("PostgreSQL Configuration Tests")
    class PostgresConfigurationTests {
        
        @Test
        @DisplayName("Valid PostgreSQL port numbers")
        void validPostgresPortNumbers() {
            int[] validPorts = {5432, 5433, 15432, 1, 65535, 5434};
            
            for (int port : validPorts) {
                assertTrue(PostgresConfig.isValidPort(port),
                    "Should accept port: " + port);
            }
        }
        
        @Test
        @DisplayName("Invalid PostgreSQL port numbers")
        void invalidPostgresPortNumbers() {
            int[] invalidPorts = {0, -1, 65536, -5432, 100000};
            
            for (int port : invalidPorts) {
                assertFalse(PostgresConfig.isValidPort(port),
                    "Should reject port: " + port);
            }
        }
        
        @Test
        @DisplayName("Parse port from string")
        void parsePortFromString() {
            assertEquals(5432, PostgresConfig.parsePort("5432"));
            assertEquals(5433, PostgresConfig.parsePort("5433"));
            assertEquals(-1, PostgresConfig.parsePort("invalid"));
            assertEquals(-1, PostgresConfig.parsePort(""));
            assertEquals(-1, PostgresConfig.parsePort(null));
        }
        
        @Test
        @DisplayName("Build PostgreSQL JDBC URL")
        void buildPostgresJdbcUrl() {
            PostgresConfig config = new PostgresConfig();
            config.host = "pg-server.company.com";
            config.port = 5432;
            config.database = "salesforce_backup";
            config.schema = "raw_data";
            config.username = "backup_user";
            config.password = "SecurePass123";
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.startsWith("jdbc:postgresql://"));
            assertTrue(url.contains("pg-server.company.com:5432"));
            assertTrue(url.contains("salesforce_backup"));
            assertTrue(url.contains("currentSchema=raw_data"));
        }
        
        @Test
        @DisplayName("PostgreSQL AWS RDS connection")
        void postgresAwsRdsConnection() {
            PostgresConfig config = new PostgresConfig();
            config.host = "sf-backup.abcd1234.us-east-1.rds.amazonaws.com";
            config.port = 5432;
            config.database = "salesforce_backup";
            config.schema = "public";
            config.username = "rds_admin";
            config.password = "AwsRds!Secure#2024";
            config.sslMode = "require";
            
            String url = config.buildJdbcUrl();
            
            assertTrue(url.contains("sslmode=require"));
        }
        
        @Test
        @DisplayName("Validate required PostgreSQL fields")
        void validateRequiredPostgresFields() {
            PostgresConfig config = new PostgresConfig();
            
            List<String> errors = config.validate();
            
            assertTrue(errors.contains("Host is required"));
            assertTrue(errors.contains("Database is required"));
            assertTrue(errors.contains("Port must be between 1 and 65535"));
        }
    }
    
    // ==================== Connection Name Tests ====================
    
    @Nested
    @DisplayName("Connection Name Tests")
    class ConnectionNameTests {
        
        @Test
        @DisplayName("Valid connection names")
        void validConnectionNames() {
            String[] validNames = {
                "Production Snowflake",
                "Dev-SQL-Server",
                "AWS RDS (Primary)",
                "Backup_Connection_v2",
                "生产数据库"
            };
            
            for (String name : validNames) {
                assertTrue(ConnectionNameValidator.isValid(name),
                    "Should accept: " + name);
            }
        }
        
        @Test
        @DisplayName("Invalid connection names")
        void invalidConnectionNames() {
            String[] invalidNames = {
                "",
                "   ",
                null
            };
            
            for (String name : invalidNames) {
                assertFalse(ConnectionNameValidator.isValid(name),
                    "Should reject: " + name);
            }
        }
        
        @Test
        @DisplayName("Connection name length validation")
        void connectionNameLengthValidation() {
            String longName = "A".repeat(256);
            
            assertFalse(ConnectionNameValidator.isValid(longName),
                "Should reject names longer than 255 characters");
        }
    }
    
    // ==================== Recreate Tables Option Tests ====================
    
    @Nested
    @DisplayName("Recreate Tables Option Tests")
    class RecreateTablesOptionTests {
        
        @Test
        @DisplayName("Recreate tables warning message")
        void recreateTablesWarningMessage() {
            DatabaseOptions options = new DatabaseOptions();
            options.setRecreateTables(true);
            
            String warning = options.getWarningMessage();
            
            assertNotNull(warning);
            assertTrue(warning.contains("delete"));
            assertTrue(warning.contains("existing data"));
        }
        
        @Test
        @DisplayName("No warning when recreate tables is false")
        void noWarningWhenRecreateTablesIsFalse() {
            DatabaseOptions options = new DatabaseOptions();
            options.setRecreateTables(false);
            
            String warning = options.getWarningMessage();
            
            assertNull(warning);
        }
    }
    
    // ==================== Negative Tests - Security ====================
    
    @Nested
    @DisplayName("Security Input Validation Tests")
    class SecurityInputValidationTests {
        
        @Test
        @DisplayName("SQL injection in server field")
        void sqlInjectionInServerField() {
            String maliciousServer = "'; DROP TABLE users; --";
            
            SqlServerConfig config = new SqlServerConfig();
            config.server = maliciousServer;
            
            List<String> errors = config.validate();
            
            assertTrue(errors.stream().anyMatch(e -> 
                e.contains("invalid") || e.contains("Server")));
        }
        
        @Test
        @DisplayName("XSS in connection name")
        void xssInConnectionName() {
            String xssName = "<script>alert('xss')</script>";
            
            // Should be stored but sanitized on display
            assertTrue(ConnectionNameValidator.isValid(xssName),
                "Connection name should accept the input (sanitization happens on display)");
        }
        
        @Test
        @DisplayName("Path traversal in host field")
        void pathTraversalInHostField() {
            String maliciousHost = "../../etc/passwd";
            
            PostgresConfig config = new PostgresConfig();
            config.host = maliciousHost;
            config.port = 5432;
            config.database = "test";
            
            List<String> errors = config.validate();
            
            // Host should be validated
            assertTrue(errors.stream().anyMatch(e -> 
                e.toLowerCase().contains("host") || e.toLowerCase().contains("invalid")));
        }
        
        @Test
        @DisplayName("Command injection in password")
        void commandInjectionInPassword() {
            String maliciousPassword = "password; rm -rf /";
            
            // Password should be accepted (it's used in parameterized queries)
            SnowflakeConfig config = new SnowflakeConfig();
            config.account = "test";
            config.warehouse = "WH";
            config.database = "DB";
            config.username = "user";
            config.password = maliciousPassword;
            
            // Should not throw exception
            config.buildJdbcUrl();
        }
    }
    
    // ==================== Helper Classes ====================
    
    public static class SnowflakeConfig {
        String account = "";
        String warehouse = "";
        String database = "";
        String schema = "PUBLIC";
        String username = "";
        String password = "";
        boolean useSso = false;
        
        public static boolean isValidAccountFormat(String account) {
            if (account == null || account.trim().isEmpty()) return false;
            if (account.contains(" ")) return false;
            if (account.contains("<") || account.contains(">") || account.contains(";")) return false;
            return true;
        }
        
        public String buildJdbcUrl() {
            StringBuilder url = new StringBuilder("jdbc:snowflake://");
            url.append(account).append(".snowflakecomputing.com/");
            url.append("?warehouse=").append(warehouse);
            url.append("&db=").append(database);
            url.append("&schema=").append(schema);
            if (useSso) {
                url.append("&authenticator=externalbrowser");
            }
            return url.toString();
        }
        
        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (account == null || account.trim().isEmpty()) errors.add("Account is required");
            if (warehouse == null || warehouse.trim().isEmpty()) errors.add("Warehouse is required");
            if (database == null || database.trim().isEmpty()) errors.add("Database is required");
            if (!useSso && (password == null || password.isEmpty())) {
                errors.add("Password is required when not using SSO");
            }
            return errors;
        }
    }
    
    public static class SqlServerConfig {
        String server = "";
        String database = "";
        String username = "";
        String password = "";
        boolean useIntegratedSecurity = false;
        boolean encrypt = false;
        
        public static boolean isValidServerName(String server) {
            if (server == null || server.trim().isEmpty()) return false;
            // Allow alphanumeric, dots, hyphens, backslash (for named instances)
            return Pattern.matches("[a-zA-Z0-9.\\-\\\\]+", server);
        }
        
        public String buildJdbcUrl() {
            StringBuilder url = new StringBuilder("jdbc:sqlserver://");
            url.append(server);
            url.append(";databaseName=").append(database);
            if (useIntegratedSecurity) {
                url.append(";integratedSecurity=true");
            }
            if (encrypt) {
                url.append(";encrypt=true");
            }
            return url.toString();
        }
        
        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (server == null || server.trim().isEmpty()) errors.add("Server is required");
            if (!isValidServerName(server)) errors.add("Server name is invalid");
            if (database == null || database.trim().isEmpty()) errors.add("Database is required");
            return errors;
        }
    }
    
    public static class PostgresConfig {
        String host = "";
        int port = 0;
        String database = "";
        String schema = "public";
        String username = "";
        String password = "";
        String sslMode = null;
        
        public static boolean isValidPort(int port) {
            return port >= 1 && port <= 65535;
        }
        
        public static int parsePort(String portStr) {
            if (portStr == null || portStr.isEmpty()) return -1;
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        
        public static boolean isValidHost(String host) {
            if (host == null || host.trim().isEmpty()) return false;
            if (host.contains("..") || host.startsWith(".") || host.startsWith("/")) return false;
            return true;
        }
        
        public String buildJdbcUrl() {
            StringBuilder url = new StringBuilder("jdbc:postgresql://");
            url.append(host).append(":").append(port);
            url.append("/").append(database);
            url.append("?currentSchema=").append(schema);
            if (sslMode != null) {
                url.append("&sslmode=").append(sslMode);
            }
            return url.toString();
        }
        
        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (host == null || host.trim().isEmpty()) errors.add("Host is required");
            if (!isValidHost(host)) errors.add("Host is invalid");
            if (!isValidPort(port)) errors.add("Port must be between 1 and 65535");
            if (database == null || database.trim().isEmpty()) errors.add("Database is required");
            return errors;
        }
    }
    
    public static class ConnectionNameValidator {
        public static boolean isValid(String name) {
            if (name == null) return false;
            if (name.trim().isEmpty()) return false;
            if (name.length() > 255) return false;
            return true;
        }
    }
    
    public static class DatabaseOptions {
        private boolean recreateTables = false;
        
        public void setRecreateTables(boolean recreateTables) {
            this.recreateTables = recreateTables;
        }
        
        public boolean isRecreateTables() {
            return recreateTables;
        }
        
        public String getWarningMessage() {
            if (recreateTables) {
                return "Warning: This will delete existing data in the target tables";
            }
            return null;
        }
    }
}
