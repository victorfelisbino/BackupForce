package com.backupforce.config;

import com.backupforce.config.BackupHistory.BackupRun;
import com.backupforce.config.BackupHistory.ObjectBackupResult;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BackupHistory class.
 */
class BackupHistoryTest {
    
    private BackupHistory history;
    
    @BeforeEach
    void setUp() {
        // Use singleton instance (it persists to user's home directory)
        history = BackupHistory.getInstance();
    }
    
    @Test
    void testStartBackup() {
        BackupRun run = history.startBackup("user@test.com", "FULL", "CSV", "/output/path", 10);
        
        assertNotNull(run);
        assertNotNull(run.getId());
        assertEquals("user@test.com", run.getUsername());
        assertEquals("FULL", run.getBackupType());
        assertEquals("CSV", run.getDestination());
        assertEquals("/output/path", run.getOutputPath());
        assertEquals(10, run.getTotalObjects());
        assertEquals("RUNNING", run.getStatus());
        assertNotNull(run.getStartTime());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testCompleteBackupSuccess() {
        BackupRun run = history.startBackup("complete-test@test.com", "FULL", "CSV", "/output", 5);
        
        // Add some object results
        ObjectBackupResult result1 = new ObjectBackupResult("Account");
        result1.setStatus("COMPLETED");
        result1.setRecordCount(100);
        run.getObjectResults().add(result1);
        
        ObjectBackupResult result2 = new ObjectBackupResult("Contact");
        result2.setStatus("COMPLETED");
        result2.setRecordCount(50);
        run.getObjectResults().add(result2);
        
        history.completeBackup(run, true);
        
        assertEquals("COMPLETED", run.getStatus());
        assertNotNull(run.getEndTime());
        assertEquals(150, run.getTotalRecords());
        assertNull(run.getErrorMessage());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testCompleteBackupWithFailures() {
        BackupRun run = history.startBackup("failure-test@test.com", "FULL", "CSV", "/output", 3);
        
        ObjectBackupResult success = new ObjectBackupResult("Account");
        success.setStatus("COMPLETED");
        success.setRecordCount(100);
        run.getObjectResults().add(success);
        
        ObjectBackupResult failed = new ObjectBackupResult("Task");
        failed.setStatus("FAILED");
        failed.setErrorMessage("Object not supported");
        run.getObjectResults().add(failed);
        
        history.completeBackup(run, false);
        
        assertEquals("FAILED", run.getStatus());
        assertEquals(100, run.getTotalRecords());
        assertEquals(1, run.getFailedObjects());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testCancelBackup() {
        BackupRun run = history.startBackup("cancel-test@test.com", "FULL", "CSV", "/output", 10);
        
        history.cancelBackup(run);
        
        assertEquals("CANCELLED", run.getStatus());
        assertNotNull(run.getEndTime());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testGetHistory() {
        BackupRun run1 = history.startBackup("history-test@test.com", "FULL", "CSV", "/output1", 5);
        history.completeBackup(run1, true);
        
        List<BackupRun> allHistory = history.getHistory();
        
        assertTrue(allHistory.size() >= 1);
        assertTrue(allHistory.stream().anyMatch(r -> r.getId().equals(run1.getId())));
        
        // Clean up
        history.deleteBackupRun(run1.getId());
    }
    
    @Test
    void testGetHistoryForUser() {
        String testUser = "user-specific-" + System.currentTimeMillis() + "@test.com";
        
        BackupRun run = history.startBackup(testUser, "FULL", "CSV", "/output", 5);
        history.completeBackup(run, true);
        
        List<BackupRun> userHistory = history.getHistoryForUser(testUser);
        
        assertEquals(1, userHistory.size());
        assertEquals(testUser, userHistory.get(0).getUsername());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testGetLastSuccessfulBackup() {
        String testUser = "last-success-" + System.currentTimeMillis() + "@test.com";
        
        // Create a successful backup with object results
        BackupRun success = history.startBackup(testUser, "FULL", "CSV", "/output1", 1);
        ObjectBackupResult objResult = new ObjectBackupResult("Account");
        objResult.setStatus("COMPLETED");
        objResult.setRecordCount(100);
        objResult.setLastModifiedDate("2024-01-15T10:30:00");
        success.getObjectResults().add(objResult);
        history.completeBackup(success, true);
        
        Optional<ObjectBackupResult> lastSuccess = history.getLastSuccessfulBackup(testUser, "Account");
        
        assertTrue(lastSuccess.isPresent());
        assertEquals("Account", lastSuccess.get().getObjectName());
        assertEquals("2024-01-15T10:30:00", lastSuccess.get().getLastModifiedDate());
        
        // Clean up
        history.deleteBackupRun(success.getId());
    }
    
    @Test
    void testGetLastBackup() {
        String testUser = "last-backup-" + System.currentTimeMillis() + "@test.com";
        
        BackupRun run = history.startBackup(testUser, "FULL", "CSV", "/output", 3);
        history.completeBackup(run, true);
        
        Optional<BackupRun> lastBackup = history.getLastBackup(testUser);
        
        assertTrue(lastBackup.isPresent());
        assertEquals(run.getId(), lastBackup.get().getId());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testGetSuccessfulBackupCount() {
        String testUser = "count-test-" + System.currentTimeMillis() + "@test.com";
        
        // Create 2 successful and 1 cancelled backup
        BackupRun run1 = history.startBackup(testUser, "FULL", "CSV", "/output1", 1);
        history.completeBackup(run1, true);
        
        BackupRun run2 = history.startBackup(testUser, "FULL", "CSV", "/output2", 1);
        history.completeBackup(run2, true);
        
        BackupRun run3 = history.startBackup(testUser, "FULL", "CSV", "/output3", 1);
        history.cancelBackup(run3);
        
        int successCount = history.getSuccessfulBackupCount(testUser);
        
        assertEquals(2, successCount);
        
        // Clean up
        history.deleteBackupRun(run1.getId());
        history.deleteBackupRun(run2.getId());
        history.deleteBackupRun(run3.getId());
    }
    
    @Test
    void testObjectBackupResult() {
        ObjectBackupResult result = new ObjectBackupResult("Account");
        
        assertEquals("Account", result.getObjectName());
        
        result.setStatus("COMPLETED");
        result.setRecordCount(500);
        result.setByteCount(1024 * 1024);
        result.setDurationMs(5000);
        result.setLastModifiedDate("2024-01-15T10:30:00");
        
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(500, result.getRecordCount());
        assertEquals(1024 * 1024, result.getByteCount());
        assertEquals(5000, result.getDurationMs());
        assertEquals("2024-01-15T10:30:00", result.getLastModifiedDate());
    }
    
    @Test
    void testBackupRunFormatters() {
        BackupRun run = history.startBackup("formatter-test@test.com", "FULL", "CSV", "/output", 2);
        
        ObjectBackupResult obj = new ObjectBackupResult("Account");
        obj.setStatus("COMPLETED");
        obj.setRecordCount(100);
        obj.setByteCount(1024 * 1024 * 5); // 5 MB
        run.getObjectResults().add(obj);
        
        history.completeBackup(run, true);
        
        // Test duration formatter
        assertNotNull(run.getDuration());
        
        // Test size formatter
        assertEquals("5.0 MB", run.getFormattedSize());
        
        // Clean up
        history.deleteBackupRun(run.getId());
    }
    
    @Test
    void testDeleteBackupRun() {
        BackupRun run = history.startBackup("delete-test@test.com", "FULL", "CSV", "/output", 1);
        String runId = run.getId();
        
        history.deleteBackupRun(runId);
        
        List<BackupRun> allHistory = history.getHistory();
        assertFalse(allHistory.stream().anyMatch(r -> r.getId().equals(runId)));
    }
}
