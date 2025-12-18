package com.backupforce.restore;

import com.backupforce.restore.RestoreExecutor.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestoreExecutor classes.
 * Tests RestoreOptions, RestoreResult, BatchResult, and RestoreMode.
 */
@DisplayName("RestoreExecutor Tests")
class RestoreExecutorTest {
    
    @Nested
    @DisplayName("RestoreOptions Tests")
    class RestoreOptionsTests {
        
        @Test
        @DisplayName("Default options values")
        void testDefaultValues() {
            RestoreOptions options = new RestoreOptions();
            
            assertEquals(200, options.getBatchSize());
            assertFalse(options.isStopOnError());
            assertTrue(options.isValidateBeforeRestore());
            assertTrue(options.isResolveRelationships());
            assertFalse(options.isPreserveIds());
            assertFalse(options.isDryRun());
            assertNull(options.getExternalIdField());
            assertEquals(3, options.getMaxRetries());
            assertEquals(2000, options.getRetryDelayMs());
        }
        
        @Test
        @DisplayName("Options setters work correctly")
        void testSetters() {
            RestoreOptions options = new RestoreOptions();
            
            options.setBatchSize(500);
            options.setStopOnError(true);
            options.setValidateBeforeRestore(false);
            options.setResolveRelationships(false);
            options.setPreserveIds(true);
            options.setDryRun(true);
            options.setExternalIdField("External_Id__c");
            options.setMaxRetries(5);
            options.setRetryDelayMs(5000);
            
            assertEquals(500, options.getBatchSize());
            assertTrue(options.isStopOnError());
            assertFalse(options.isValidateBeforeRestore());
            assertFalse(options.isResolveRelationships());
            assertTrue(options.isPreserveIds());
            assertTrue(options.isDryRun());
            assertEquals("External_Id__c", options.getExternalIdField());
            assertEquals(5, options.getMaxRetries());
            assertEquals(5000, options.getRetryDelayMs());
        }
    }
    
    @Nested
    @DisplayName("RestoreResult Tests")
    class RestoreResultTests {
        
        @Test
        @DisplayName("New result has correct initial state")
        void testInitialState() {
            RestoreResult result = new RestoreResult("Account");
            
            assertEquals("Account", result.getObjectName());
            assertEquals(0, result.getTotalRecords());
            assertEquals(0, result.getSuccessCount());
            assertEquals(0, result.getFailureCount());
            assertFalse(result.isCompleted());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getCreatedIds().isEmpty());
        }
        
        @Test
        @DisplayName("Adding batch result accumulates counts")
        void testAddBatchResult() {
            RestoreResult result = new RestoreResult("Account");
            
            BatchResult batch1 = new BatchResult();
            batch1.setSuccessCount(50);
            batch1.setFailureCount(5);
            batch1.addError("Error 1");
            batch1.addCreatedId("001xxx");
            
            BatchResult batch2 = new BatchResult();
            batch2.setSuccessCount(40);
            batch2.setFailureCount(10);
            batch2.addError("Error 2");
            
            result.addBatchResult(batch1);
            result.addBatchResult(batch2);
            
            assertEquals(90, result.getSuccessCount());
            assertEquals(15, result.getFailureCount());
            assertEquals(2, result.getErrors().size());
            assertEquals(1, result.getCreatedIds().size());
        }
        
        @Test
        @DisplayName("setTotalRecords works")
        void testSetTotalRecords() {
            RestoreResult result = new RestoreResult("Contact");
            result.setTotalRecords(1000);
            
            assertEquals(1000, result.getTotalRecords());
        }
        
        @Test
        @DisplayName("setCompleted works")
        void testSetCompleted() {
            RestoreResult result = new RestoreResult("Lead");
            
            assertFalse(result.isCompleted());
            result.setCompleted(true);
            assertTrue(result.isCompleted());
        }
        
        @Test
        @DisplayName("addError works")
        void testAddError() {
            RestoreResult result = new RestoreResult("Opportunity");
            result.addError("Test error");
            
            assertEquals(1, result.getErrors().size());
            assertEquals("Test error", result.getErrors().get(0));
        }
    }
    
    @Nested
    @DisplayName("BatchResult Tests")
    class BatchResultTests {
        
        @Test
        @DisplayName("New batch result has correct initial state")
        void testInitialState() {
            BatchResult result = new BatchResult();
            
            assertEquals(0, result.getSuccessCount());
            assertEquals(0, result.getFailureCount());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getCreatedIds().isEmpty());
            assertTrue(result.isSuccess());
            assertFalse(result.hasRetryableErrors());
        }
        
        @Test
        @DisplayName("incrementSuccess works")
        void testIncrementSuccess() {
            BatchResult result = new BatchResult();
            result.incrementSuccess();
            result.incrementSuccess();
            
            assertEquals(2, result.getSuccessCount());
        }
        
        @Test
        @DisplayName("incrementFailure affects isSuccess")
        void testIncrementFailure() {
            BatchResult result = new BatchResult();
            assertTrue(result.isSuccess());
            
            result.incrementFailure();
            assertFalse(result.isSuccess());
            assertEquals(1, result.getFailureCount());
        }
        
        @Test
        @DisplayName("Retryable errors are detected")
        void testRetryableErrors() {
            BatchResult result = new BatchResult();
            
            assertFalse(result.hasRetryableErrors());
            result.addError("Connection timeout occurred");
            assertTrue(result.hasRetryableErrors());
        }
        
        @Test
        @DisplayName("Lock errors are retryable")
        void testLockErrorRetryable() {
            BatchResult result = new BatchResult();
            result.addError("UNABLE_TO_LOCK_ROW: trying to lock a row that is already locked");
            
            assertTrue(result.hasRetryableErrors());
        }
        
        @Test
        @DisplayName("Validation errors are not retryable")
        void testValidationErrorNotRetryable() {
            BatchResult result = new BatchResult();
            result.addError("REQUIRED_FIELD_MISSING: Name is required");
            
            assertFalse(result.hasRetryableErrors());
        }
        
        @Test
        @DisplayName("addCreatedId works")
        void testAddCreatedId() {
            BatchResult result = new BatchResult();
            result.addCreatedId("001000000000001AAA");
            result.addCreatedId("001000000000002AAA");
            
            assertEquals(2, result.getCreatedIds().size());
            assertTrue(result.getCreatedIds().contains("001000000000001AAA"));
        }
    }
    
    @Nested
    @DisplayName("RestoreProgress Tests")
    class RestoreProgressTests {
        
        @Test
        @DisplayName("Progress properties work correctly")
        void testProgressProperties() {
            RestoreProgress progress = new RestoreProgress();
            
            progress.setCurrentObject("Account");
            progress.setProcessedRecords(50);
            progress.setTotalRecords(100);
            progress.setSuccessCount(45);
            progress.setFailureCount(5);
            progress.setPercentComplete(50.0);
            
            assertEquals("Account", progress.getCurrentObject());
            assertEquals(50, progress.getProcessedRecords());
            assertEquals(100, progress.getTotalRecords());
            assertEquals(45, progress.getSuccessCount());
            assertEquals(5, progress.getFailureCount());
            assertEquals(50.0, progress.getPercentComplete(), 0.001);
        }
    }
    
    @Nested
    @DisplayName("RestoreMode Tests")
    class RestoreModeTests {
        
        @Test
        @DisplayName("All restore modes exist")
        void testAllModesExist() {
            RestoreMode insert = RestoreMode.INSERT;
            RestoreMode upsert = RestoreMode.UPSERT;
            RestoreMode update = RestoreMode.UPDATE;
            
            assertNotNull(insert);
            assertNotNull(upsert);
            assertNotNull(update);
        }
        
        @Test
        @DisplayName("RestoreMode values")
        void testModeValues() {
            RestoreMode[] modes = RestoreMode.values();
            
            assertEquals(3, modes.length);
            assertEquals("INSERT", RestoreMode.INSERT.name());
            assertEquals("UPSERT", RestoreMode.UPSERT.name());
            assertEquals("UPDATE", RestoreMode.UPDATE.name());
        }
    }
}
