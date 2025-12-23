package com.backupforce.scheduler;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScheduleManager persistence and CRUD operations.
 */
@DisplayName("ScheduleManager Tests")
class ScheduleManagerTest {
    
    // NOTE: ScheduleManager is a singleton, so tests may interact
    // For full isolation, we'd need to reset the singleton between tests
    
    private ScheduleManager manager;
    
    @BeforeEach
    void setUp() {
        manager = ScheduleManager.getInstance();
        // Clean up any existing test schedules
        List<BackupSchedule> existing = new ArrayList<>(manager.getSchedules());
        for (BackupSchedule schedule : existing) {
            if (schedule.getName() != null && schedule.getName().startsWith("Test-")) {
                manager.deleteSchedule(schedule.getId());
            }
        }
    }
    
    @AfterEach
    void tearDown() {
        // Clean up test schedules
        List<BackupSchedule> existing = new ArrayList<>(manager.getSchedules());
        for (BackupSchedule schedule : existing) {
            if (schedule.getName() != null && schedule.getName().startsWith("Test-")) {
                manager.deleteSchedule(schedule.getId());
            }
        }
    }
    
    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperationsTests {
        
        @Test
        @DisplayName("Can add a schedule")
        void canAddSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test-Add-Schedule");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            
            int countBefore = manager.getSchedules().size();
            manager.addSchedule(schedule);
            int countAfter = manager.getSchedules().size();
            
            assertEquals(countBefore + 1, countAfter);
            assertTrue(manager.getScheduleById(schedule.getId()).isPresent());
        }
        
        @Test
        @DisplayName("Cannot add null schedule")
        void cannotAddNullSchedule() {
            assertThrows(IllegalArgumentException.class, () -> {
                manager.addSchedule(null);
            });
        }
        
        @Test
        @DisplayName("Cannot add duplicate ID")
        void cannotAddDuplicateId() {
            BackupSchedule schedule = new BackupSchedule("Test-Duplicate");
            manager.addSchedule(schedule);
            
            BackupSchedule duplicate = new BackupSchedule("Test-Duplicate-2");
            duplicate.setId(schedule.getId());
            
            assertThrows(IllegalArgumentException.class, () -> {
                manager.addSchedule(duplicate);
            });
        }
        
        @Test
        @DisplayName("Can update a schedule")
        void canUpdateSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test-Update");
            manager.addSchedule(schedule);
            
            schedule.setName("Test-Update-Modified");
            schedule.setFrequency(BackupSchedule.Frequency.WEEKLY);
            manager.updateSchedule(schedule);
            
            Optional<BackupSchedule> retrieved = manager.getScheduleById(schedule.getId());
            assertTrue(retrieved.isPresent());
            assertEquals("Test-Update-Modified", retrieved.get().getName());
            assertEquals(BackupSchedule.Frequency.WEEKLY, retrieved.get().getFrequency());
        }
        
        @Test
        @DisplayName("Cannot update non-existent schedule")
        void cannotUpdateNonExistentSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test-NonExistent");
            // Not added to manager
            
            assertThrows(IllegalArgumentException.class, () -> {
                manager.updateSchedule(schedule);
            });
        }
        
        @Test
        @DisplayName("Can delete a schedule")
        void canDeleteSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test-Delete");
            manager.addSchedule(schedule);
            
            assertTrue(manager.getScheduleById(schedule.getId()).isPresent());
            
            boolean deleted = manager.deleteSchedule(schedule.getId());
            
            assertTrue(deleted);
            assertFalse(manager.getScheduleById(schedule.getId()).isPresent());
        }
        
        @Test
        @DisplayName("Delete non-existent returns false")
        void deleteNonExistentReturnsFalse() {
            boolean deleted = manager.deleteSchedule("non-existent-id");
            assertFalse(deleted);
        }
    }
    
    @Nested
    @DisplayName("Query Operations")
    class QueryOperationsTests {
        
        @Test
        @DisplayName("Get all schedules")
        void getAllSchedules() {
            BackupSchedule s1 = new BackupSchedule("Test-Query-1");
            BackupSchedule s2 = new BackupSchedule("Test-Query-2");
            
            manager.addSchedule(s1);
            manager.addSchedule(s2);
            
            List<BackupSchedule> all = manager.getSchedules();
            
            assertTrue(all.stream().anyMatch(s -> s.getId().equals(s1.getId())));
            assertTrue(all.stream().anyMatch(s -> s.getId().equals(s2.getId())));
        }
        
        @Test
        @DisplayName("Get enabled schedules only")
        void getEnabledSchedulesOnly() {
            BackupSchedule enabled = new BackupSchedule("Test-Enabled");
            enabled.setEnabled(true);
            
            BackupSchedule disabled = new BackupSchedule("Test-Disabled");
            disabled.setEnabled(false);
            
            manager.addSchedule(enabled);
            manager.addSchedule(disabled);
            
            List<BackupSchedule> enabledOnly = manager.getEnabledSchedules();
            
            assertTrue(enabledOnly.stream().anyMatch(s -> s.getId().equals(enabled.getId())));
            assertFalse(enabledOnly.stream().anyMatch(s -> s.getId().equals(disabled.getId())));
        }
        
        @Test
        @DisplayName("Get schedule by ID")
        void getScheduleById() {
            BackupSchedule schedule = new BackupSchedule("Test-ById");
            manager.addSchedule(schedule);
            
            Optional<BackupSchedule> found = manager.getScheduleById(schedule.getId());
            
            assertTrue(found.isPresent());
            assertEquals("Test-ById", found.get().getName());
        }
        
        @Test
        @DisplayName("Get schedules for user")
        void getSchedulesForUser() {
            BackupSchedule s1 = new BackupSchedule("Test-User1");
            s1.setSalesforceUsername("user1@example.com");
            
            BackupSchedule s2 = new BackupSchedule("Test-User2");
            s2.setSalesforceUsername("user2@example.com");
            
            manager.addSchedule(s1);
            manager.addSchedule(s2);
            
            List<BackupSchedule> user1Schedules = manager.getSchedulesForUser("user1@example.com");
            
            assertTrue(user1Schedules.stream().anyMatch(s -> s.getId().equals(s1.getId())));
            assertFalse(user1Schedules.stream().anyMatch(s -> s.getId().equals(s2.getId())));
        }
    }
    
    @Nested
    @DisplayName("State Update Operations")
    class StateUpdateOperationsTests {
        
        @Test
        @DisplayName("Can enable/disable schedule")
        void canEnableDisableSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test-EnableDisable");
            schedule.setEnabled(true);
            manager.addSchedule(schedule);
            
            manager.setScheduleEnabled(schedule.getId(), false);
            
            assertFalse(manager.getScheduleById(schedule.getId()).get().isEnabled());
            
            manager.setScheduleEnabled(schedule.getId(), true);
            
            assertTrue(manager.getScheduleById(schedule.getId()).get().isEnabled());
        }
        
        @Test
        @DisplayName("Can update last run info")
        void canUpdateLastRunInfo() {
            BackupSchedule schedule = new BackupSchedule("Test-LastRun");
            manager.addSchedule(schedule);
            
            manager.updateLastRun(schedule.getId(), "SUCCESS");
            
            BackupSchedule updated = manager.getScheduleById(schedule.getId()).get();
            assertEquals("SUCCESS", updated.getLastRunStatus());
            assertTrue(updated.getLastRunTime() > 0);
        }
        
        @Test
        @DisplayName("Can update next run time")
        void canUpdateNextRunTime() {
            BackupSchedule schedule = new BackupSchedule("Test-NextRun");
            manager.addSchedule(schedule);
            
            long nextRun = System.currentTimeMillis() + 3600000;
            manager.updateNextRun(schedule.getId(), nextRun);
            
            BackupSchedule updated = manager.getScheduleById(schedule.getId()).get();
            assertEquals(nextRun, updated.getNextRunTime());
        }
    }
    
    @Nested
    @DisplayName("Singleton Tests")
    class SingletonTests {
        
        @Test
        @DisplayName("Singleton instance is consistent")
        void singletonInstanceIsConsistent() {
            ScheduleManager m1 = ScheduleManager.getInstance();
            ScheduleManager m2 = ScheduleManager.getInstance();
            
            assertSame(m1, m2);
        }
    }
    
    @Nested
    @DisplayName("Schedule Returns Copies")
    class DefensiveCopyTests {
        
        @Test
        @DisplayName("getSchedules returns new list")
        void getSchedulesReturnsNewList() {
            List<BackupSchedule> list1 = manager.getSchedules();
            List<BackupSchedule> list2 = manager.getSchedules();
            
            assertNotSame(list1, list2);
        }
    }
}
