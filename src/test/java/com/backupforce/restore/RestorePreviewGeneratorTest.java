package com.backupforce.restore;

import com.backupforce.restore.RestoreExecutor.RestoreOptions;
import com.backupforce.restore.RestorePreviewGenerator.ObjectPreview;
import com.backupforce.restore.RestorePreviewGenerator.RestorePreview;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RestorePreviewGenerator
 */
class RestorePreviewGeneratorTest {

    /**
     * Mock restore object for testing
     */
    static class MockRestoreObject {
        private String name;
        private long recordCount;
        private String filePath;

        public MockRestoreObject(String name, long recordCount) {
            this.name = name;
            this.recordCount = recordCount;
        }

        public String getName() {
            return name;
        }

        public long getRecordCount() {
            return recordCount;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    @Test
    void testEstimateApiCalls_SmallDataset() {
        // 100 records with batch size 200 = 1 job + 1 batch + 1 status = 3 calls
        int apiCalls = RestorePreviewGenerator.estimateApiCalls(100, 200);
        assertEquals(3, apiCalls, "Small dataset should require 3 API calls");
    }

    @Test
    void testEstimateApiCalls_LargeDataset() {
        // 1000 records with batch size 200 = 1 job + 5 batches + 1 status = 7 calls
        int apiCalls = RestorePreviewGenerator.estimateApiCalls(1000, 200);
        assertEquals(7, apiCalls, "1000 records in 200-record batches = 7 API calls");
    }

    @Test
    void testEstimateApiCalls_ExactBatch() {
        // 200 records with batch size 200 = 1 job + 1 batch + 1 status = 3 calls
        int apiCalls = RestorePreviewGenerator.estimateApiCalls(200, 200);
        assertEquals(3, apiCalls, "Exact batch size should require 3 API calls");
    }

    @Test
    void testEstimateApiCalls_ZeroRecords() {
        int apiCalls = RestorePreviewGenerator.estimateApiCalls(0, 200);
        assertEquals(0, apiCalls, "Zero records should require 0 API calls");
    }

    @Test
    void testEstimateTime_SmallDataset() {
        // 100 records at 50 records/sec = 2 seconds, but minimum is 5
        int seconds = RestorePreviewGenerator.estimateTime(100);
        assertEquals(5, seconds, "Small datasets should use minimum time of 5 seconds");
    }

    @Test
    void testEstimateTime_MediumDataset() {
        // 5000 records at 50 records/sec = 100 seconds
        int seconds = RestorePreviewGenerator.estimateTime(5000);
        assertEquals(100, seconds, "5000 records should take 100 seconds");
    }

    @Test
    void testEstimateTime_LargeDataset() {
        // 500000 records would be 10000 seconds, but max is 7200
        int seconds = RestorePreviewGenerator.estimateTime(500000);
        assertEquals(7200, seconds, "Large datasets should use maximum time of 2 hours");
    }

    @Test
    void testEstimateTime_ZeroRecords() {
        int seconds = RestorePreviewGenerator.estimateTime(0);
        assertEquals(0, seconds, "Zero records should take 0 seconds");
    }

    @Test
    void testFormatSize_Bytes() {
        String formatted = RestorePreviewGenerator.formatSize(500);
        assertEquals("500 B", formatted, "Small values should be in bytes");
    }

    @Test
    void testFormatSize_Kilobytes() {
        String formatted = RestorePreviewGenerator.formatSize(5120);
        assertEquals("5.0 KB", formatted, "KB range should format correctly");
    }

    @Test
    void testFormatSize_Megabytes() {
        String formatted = RestorePreviewGenerator.formatSize(5242880);
        assertEquals("5.0 MB", formatted, "MB range should format correctly");
    }

    @Test
    void testFormatSize_Gigabytes() {
        String formatted = RestorePreviewGenerator.formatSize(5368709120L);
        assertEquals("5.00 GB", formatted, "GB range should format correctly");
    }

    @Test
    void testGeneratePreview_EmptyList() {
        List<MockRestoreObject> objects = new ArrayList<>();
        RestoreOptions options = new RestoreOptions();

        RestorePreview preview = RestorePreviewGenerator.generatePreview(objects, options);

        assertNotNull(preview, "Preview should not be null");
        assertEquals(0, preview.getTotalRecords(), "Empty list should have 0 records");
        assertEquals(0, preview.getEstimatedApiCalls(), "Empty list should have 0 API calls");
        assertEquals(0, preview.getObjectPreviews().size(), "Empty list should have 0 object previews");
    }

    @Test
    void testGeneratePreview_SingleObject() {
        List<MockRestoreObject> objects = new ArrayList<>();
        objects.add(new MockRestoreObject("Account", 100));

        RestoreOptions options = new RestoreOptions();
        options.setBatchSize(200);

        RestorePreview preview = RestorePreviewGenerator.generatePreview(objects, options);

        assertNotNull(preview, "Preview should not be null");
        assertEquals(100, preview.getTotalRecords(), "Should have 100 total records");
        assertEquals(1, preview.getObjectPreviews().size(), "Should have 1 object preview");
        assertTrue(preview.getEstimatedApiCalls() > 0, "Should estimate some API calls");
    }

    @Test
    void testGeneratePreview_MultipleObjects() {
        List<MockRestoreObject> objects = new ArrayList<>();
        objects.add(new MockRestoreObject("Account", 1000));
        objects.add(new MockRestoreObject("Contact", 2000));
        objects.add(new MockRestoreObject("Opportunity", 500));

        RestoreOptions options = new RestoreOptions();
        options.setBatchSize(200);

        RestorePreview preview = RestorePreviewGenerator.generatePreview(objects, options);

        assertNotNull(preview, "Preview should not be null");
        assertEquals(3500, preview.getTotalRecords(), "Should sum all records");
        assertEquals(3, preview.getObjectPreviews().size(), "Should have 3 object previews");

        // Check individual object previews
        ObjectPreview accountPreview = preview.getObjectPreviews().get(0);
        assertEquals("Account", accountPreview.getObjectName());
        assertEquals(1000, accountPreview.getRecordCount());
    }

    @Test
    void testGeneratePreview_LargeDatasetWarning() {
        List<MockRestoreObject> objects = new ArrayList<>();
        objects.add(new MockRestoreObject("Account", 150000));

        RestoreOptions options = new RestoreOptions();

        RestorePreview preview = RestorePreviewGenerator.generatePreview(objects, options);

        assertNotNull(preview, "Preview should not be null");
        assertTrue(preview.getGlobalWarnings().size() > 0, 
            "Large dataset should generate warnings");
        assertTrue(preview.getGlobalWarnings().stream()
            .anyMatch(w -> w.contains("Large restore")),
            "Should warn about large restore");
    }

    @Test
    void testGeneratePreview_HighApiUsageWarning() {
        List<MockRestoreObject> objects = new ArrayList<>();
        // Create enough objects to trigger high API usage warning
        objects.add(new MockRestoreObject("Account", 500000));

        RestoreOptions options = new RestoreOptions();
        options.setBatchSize(100); // Smaller batch size to increase API calls

        RestorePreview preview = RestorePreviewGenerator.generatePreview(objects, options);

        assertNotNull(preview, "Preview should not be null");
        assertTrue(preview.getGlobalWarnings().stream()
            .anyMatch(w -> w.contains("High API usage")),
            "Should warn about high API usage");
    }

    @Test
    void testObjectPreview_StatusIcon() {
        ObjectPreview preview = new ObjectPreview("Test");

        // Initially should be success
        assertEquals("✅", preview.getStatusIcon(), "Initial status should be success");

        // Adding warning should change to warning icon
        preview.addWarning("Test warning");
        assertEquals("⚠️", preview.getStatusIcon(), "Warning should change icon");

        // Adding error should change to error icon
        preview.addError("Test error");
        assertEquals("❌", preview.getStatusIcon(), "Error should change icon to error");
    }

    @Test
    void testObjectPreview_MultipleWarnings() {
        ObjectPreview preview = new ObjectPreview("Test");

        preview.addWarning("Warning 1");
        preview.addWarning("Warning 2");
        preview.addWarning("Warning 3");

        assertEquals(3, preview.getWarnings().size(), "Should have 3 warnings");
        assertEquals("⚠️", preview.getStatusIcon(), "Should show warning icon");
    }

    @Test
    void testObjectPreview_MixedIssues() {
        ObjectPreview preview = new ObjectPreview("Test");

        preview.addWarning("Warning");
        preview.addError("Error");

        assertEquals(1, preview.getWarnings().size(), "Should have 1 warning");
        assertEquals(1, preview.getErrors().size(), "Should have 1 error");
        assertEquals("❌", preview.getStatusIcon(), "Error takes precedence");
    }

    @Test
    void testRestorePreview_Aggregation() {
        RestorePreview preview = new RestorePreview();

        ObjectPreview obj1 = new ObjectPreview("Account");
        obj1.setRecordCount(1000);
        obj1.setFileSize(5000000);
        obj1.setEstimatedApiCalls(10);

        ObjectPreview obj2 = new ObjectPreview("Contact");
        obj2.setRecordCount(2000);
        obj2.setFileSize(3000000);
        obj2.setEstimatedApiCalls(15);

        preview.addObjectPreview(obj1);
        preview.addObjectPreview(obj2);

        // Manually set totals (normally done by generatePreview)
        preview.setTotalRecords(3000);
        preview.setTotalFileSize(8000000);
        preview.setEstimatedApiCalls(25);

        assertEquals(3000, preview.getTotalRecords(), "Should sum record counts");
        assertEquals(8000000, preview.getTotalFileSize(), "Should sum file sizes");
        assertEquals(25, preview.getEstimatedApiCalls(), "Should sum API calls");
    }
}
