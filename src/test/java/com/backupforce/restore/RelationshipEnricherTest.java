package com.backupforce.restore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RelationshipEnricher.
 * Uses reflection to test private helper methods for CSV parsing.
 */
@DisplayName("RelationshipEnricher Tests")
class RelationshipEnricherTest {

    @TempDir
    Path tempDir;

    private RelationshipEnricher enricher;

    @BeforeEach
    void setUp() {
        // Create enricher with dummy credentials (won't make HTTP calls in these tests)
        enricher = new RelationshipEnricher("https://test.salesforce.com", "dummy", "60.0");
    }

    @AfterEach
    void tearDown() {
        if (enricher != null) {
            enricher.close();
        }
    }

    // ==================== CSV Parsing Tests ====================

    @Nested
    @DisplayName("CSV Parsing Tests")
    class CsvParsingTests {

        @Test
        @DisplayName("parseCsvLine handles simple values")
        void testParseCsvLineSimple() throws Exception {
            String line = "value1,value2,value3";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(3, result.length);
            assertEquals("value1", result[0]);
            assertEquals("value2", result[1]);
            assertEquals("value3", result[2]);
        }

        @Test
        @DisplayName("parseCsvLine handles quoted values")
        void testParseCsvLineQuoted() throws Exception {
            String line = "\"value1\",\"value2\",\"value3\"";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(3, result.length);
            assertEquals("value1", result[0]);
            assertEquals("value2", result[1]);
            assertEquals("value3", result[2]);
        }

        @Test
        @DisplayName("parseCsvLine handles values with commas inside quotes")
        void testParseCsvLineCommasInQuotes() throws Exception {
            String line = "\"value1, with comma\",value2,\"another, comma\"";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(3, result.length);
            assertEquals("value1, with comma", result[0]);
            assertEquals("value2", result[1]);
            assertEquals("another, comma", result[2]);
        }

        @Test
        @DisplayName("parseCsvLine handles escaped quotes")
        void testParseCsvLineEscapedQuotes() throws Exception {
            String line = "\"value with \"\"quotes\"\"\",normal,\"end\"";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(3, result.length);
            assertEquals("value with \"quotes\"", result[0]);
            assertEquals("normal", result[1]);
            assertEquals("end", result[2]);
        }

        @Test
        @DisplayName("parseCsvLine handles empty values")
        void testParseCsvLineEmptyValues() throws Exception {
            String line = "value1,,value3";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(3, result.length);
            assertEquals("value1", result[0]);
            assertEquals("", result[1]);
            assertEquals("value3", result[2]);
        }

        @Test
        @DisplayName("parseCsvLine handles single value")
        void testParseCsvLineSingleValue() throws Exception {
            String line = "onlyvalue";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(1, result.length);
            assertEquals("onlyvalue", result[0]);
        }

        @Test
        @DisplayName("parseCsvLine handles empty string")
        void testParseCsvLineEmpty() throws Exception {
            String line = "";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(1, result.length);
            assertEquals("", result[0]);
        }

        @Test
        @DisplayName("parseCsvLine handles trailing comma")
        void testParseCsvLineTrailingComma() throws Exception {
            String line = "value1,value2,";
            String[] result = invokeParseCsvLine(line);
            
            assertEquals(3, result.length);
            assertEquals("value1", result[0]);
            assertEquals("value2", result[1]);
            assertEquals("", result[2]);
        }
    }

    // ==================== CSV Escape Tests ====================

    @Nested
    @DisplayName("CSV Escape Tests")
    class CsvEscapeTests {

        @Test
        @DisplayName("escapeForCsv returns empty string for null")
        void testEscapeNull() throws Exception {
            String result = invokeEscapeForCsv(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("escapeForCsv returns value unchanged if no special chars")
        void testEscapeSimple() throws Exception {
            String result = invokeEscapeForCsv("simple value");
            assertEquals("simple value", result);
        }

        @Test
        @DisplayName("escapeForCsv quotes value containing comma")
        void testEscapeWithComma() throws Exception {
            String result = invokeEscapeForCsv("value, with comma");
            assertEquals("\"value, with comma\"", result);
        }

        @Test
        @DisplayName("escapeForCsv quotes value containing quotes")
        void testEscapeWithQuotes() throws Exception {
            String result = invokeEscapeForCsv("value with \"quotes\"");
            assertEquals("\"value with \"\"quotes\"\"\"", result);
        }

        @Test
        @DisplayName("escapeForCsv quotes value containing newline")
        void testEscapeWithNewline() throws Exception {
            String result = invokeEscapeForCsv("line1\nline2");
            assertEquals("\"line1\nline2\"", result);
        }

        @Test
        @DisplayName("escapeForCsv handles multiple special characters")
        void testEscapeMultipleSpecial() throws Exception {
            String result = invokeEscapeForCsv("value, with \"quotes\" and\nnewline");
            assertEquals("\"value, with \"\"quotes\"\" and\nnewline\"", result);
        }
    }

    // ==================== Header Index Tests ====================

    @Nested
    @DisplayName("Header Index Tests")
    class HeaderIndexTests {

        @Test
        @DisplayName("buildHeaderIndex creates correct index")
        void testBuildHeaderIndex() throws Exception {
            String[] headers = {"Id", "Name", "Industry", "AccountId"};
            Map<String, Integer> index = invokeBuildHeaderIndex(headers);
            
            assertEquals(4, index.size());
            assertEquals(0, index.get("Id").intValue());
            assertEquals(1, index.get("Name").intValue());
            assertEquals(2, index.get("Industry").intValue());
            assertEquals(3, index.get("AccountId").intValue());
        }

        @Test
        @DisplayName("buildHeaderIndex handles empty array")
        void testBuildHeaderIndexEmpty() throws Exception {
            String[] headers = {};
            Map<String, Integer> index = invokeBuildHeaderIndex(headers);
            assertTrue(index.isEmpty());
        }

        @Test
        @DisplayName("buildHeaderIndex handles single header")
        void testBuildHeaderIndexSingle() throws Exception {
            String[] headers = {"Id"};
            Map<String, Integer> index = invokeBuildHeaderIndex(headers);
            
            assertEquals(1, index.size());
            assertEquals(0, index.get("Id").intValue());
        }
    }

    // ==================== CSV Read/Write Integration Tests ====================

    @Nested
    @DisplayName("CSV File Operations Tests")
    class CsvFileOperationsTests {

        @Test
        @DisplayName("readCsv reads simple CSV file correctly")
        void testReadCsv() throws Exception {
            // Create a test CSV file
            Path csvFile = tempDir.resolve("test.csv");
            Files.writeString(csvFile, 
                "Id,Name,Industry\n" +
                "001xxx001,Account 1,Technology\n" +
                "001xxx002,Account 2,Finance\n");
            
            List<String[]> rows = invokeReadCsv(csvFile);
            
            assertEquals(3, rows.size());
            
            // Header row
            assertArrayEquals(new String[]{"Id", "Name", "Industry"}, rows.get(0));
            
            // Data rows
            assertEquals("001xxx001", rows.get(1)[0]);
            assertEquals("Account 1", rows.get(1)[1]);
            assertEquals("Technology", rows.get(1)[2]);
            
            assertEquals("001xxx002", rows.get(2)[0]);
            assertEquals("Account 2", rows.get(2)[1]);
            assertEquals("Finance", rows.get(2)[2]);
        }

        @Test
        @DisplayName("readCsv handles empty file")
        void testReadCsvEmpty() throws Exception {
            Path csvFile = tempDir.resolve("empty.csv");
            Files.writeString(csvFile, "");
            
            List<String[]> rows = invokeReadCsv(csvFile);
            assertTrue(rows.isEmpty());
        }

        @Test
        @DisplayName("readCsv handles quoted values with commas")
        void testReadCsvWithQuotedCommas() throws Exception {
            Path csvFile = tempDir.resolve("quoted.csv");
            Files.writeString(csvFile, 
                "Id,Name,Address\n" +
                "001xxx001,\"Acme, Inc.\",\"123 Main St, Suite 100\"\n");
            
            List<String[]> rows = invokeReadCsv(csvFile);
            
            assertEquals(2, rows.size());
            assertEquals("Acme, Inc.", rows.get(1)[1]);
            assertEquals("123 Main St, Suite 100", rows.get(1)[2]);
        }
    }

    // ==================== escapeForCsv List Tests ====================

    @Nested
    @DisplayName("escapeForCsv List Tests")
    class EscapeForCsvListTests {

        @Test
        @DisplayName("escapeForCsv list escapes all values")
        void testEscapeList() throws Exception {
            List<String> values = Arrays.asList("simple", "with, comma", "with \"quotes\"");
            List<String> result = invokeEscapeForCsvList(values);
            
            assertEquals(3, result.size());
            assertEquals("simple", result.get(0));
            assertEquals("\"with, comma\"", result.get(1));
            assertEquals("\"with \"\"quotes\"\"\"", result.get(2));
        }

        @Test
        @DisplayName("escapeForCsv list handles empty list")
        void testEscapeEmptyList() throws Exception {
            List<String> values = Collections.emptyList();
            List<String> result = invokeEscapeForCsvList(values);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== Helper Methods using Reflection ====================

    private String[] invokeParseCsvLine(String line) throws Exception {
        Method method = RelationshipEnricher.class.getDeclaredMethod("parseCsvLine", String.class);
        method.setAccessible(true);
        return (String[]) method.invoke(enricher, line);
    }

    private String invokeEscapeForCsv(String value) throws Exception {
        Method method = RelationshipEnricher.class.getDeclaredMethod("escapeForCsv", String.class);
        method.setAccessible(true);
        return (String) method.invoke(enricher, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> invokeBuildHeaderIndex(String[] headers) throws Exception {
        Method method = RelationshipEnricher.class.getDeclaredMethod("buildHeaderIndex", String[].class);
        method.setAccessible(true);
        return (Map<String, Integer>) method.invoke(enricher, (Object) headers);
    }

    @SuppressWarnings("unchecked")
    private List<String[]> invokeReadCsv(Path path) throws Exception {
        Method method = RelationshipEnricher.class.getDeclaredMethod("readCsv", Path.class);
        method.setAccessible(true);
        return (List<String[]>) method.invoke(enricher, path);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeEscapeForCsvList(List<String> values) throws Exception {
        Method method = RelationshipEnricher.class.getDeclaredMethod("escapeForCsv", List.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(enricher, values);
    }

    // ==================== Resource Cleanup Tests ====================

    @Nested
    @DisplayName("Resource Cleanup Tests")
    class ResourceCleanupTests {

        @Test
        @DisplayName("close() doesn't throw exceptions")
        void testClose() {
            RelationshipEnricher testEnricher = new RelationshipEnricher(
                "https://test.salesforce.com", "dummy", "60.0"
            );
            assertDoesNotThrow(() -> testEnricher.close());
        }

        @Test
        @DisplayName("close() can be called multiple times")
        void testCloseMultipleTimes() {
            RelationshipEnricher testEnricher = new RelationshipEnricher(
                "https://test.salesforce.com", "dummy", "60.0"
            );
            assertDoesNotThrow(() -> {
                testEnricher.close();
                testEnricher.close();
            });
        }
    }
}
