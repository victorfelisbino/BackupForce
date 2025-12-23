package com.backupforce.scheduler;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BackupSchedule model class.
 */
@DisplayName("BackupSchedule Tests")
class BackupScheduleTest {
    
    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {
        
        @Test
        @DisplayName("Default constructor creates valid schedule")
        void defaultConstructorCreatesValidSchedule() {
            BackupSchedule schedule = new BackupSchedule();
            
            assertNotNull(schedule.getId());
            assertFalse(schedule.getId().isEmpty());
            assertTrue(schedule.isEnabled());
            assertEquals(BackupSchedule.Frequency.DAILY, schedule.getFrequency());
            assertEquals(LocalTime.of(2, 0), schedule.getScheduledTime());
            assertNotNull(schedule.getDaysOfWeek());
            assertTrue(schedule.getDaysOfWeek().contains(DayOfWeek.MONDAY));
            assertEquals(1, schedule.getDayOfMonth());
            assertNotNull(schedule.getTimeZone());
            assertNotNull(schedule.getSelectedObjects());
            assertTrue(schedule.isIncremental());
            assertFalse(schedule.isCompress());
            assertTrue(schedule.isPreserveRelationships());
            assertTrue(schedule.isNotifyOnFailure());
        }
        
        @Test
        @DisplayName("Named constructor sets name")
        void namedConstructorSetsName() {
            BackupSchedule schedule = new BackupSchedule("Daily Backup");
            
            assertEquals("Daily Backup", schedule.getName());
            assertNotNull(schedule.getId());
        }
        
        @Test
        @DisplayName("Each schedule has unique ID")
        void eachScheduleHasUniqueId() {
            BackupSchedule s1 = new BackupSchedule();
            BackupSchedule s2 = new BackupSchedule();
            BackupSchedule s3 = new BackupSchedule();
            
            assertNotEquals(s1.getId(), s2.getId());
            assertNotEquals(s2.getId(), s3.getId());
            assertNotEquals(s1.getId(), s3.getId());
        }
    }
    
    @Nested
    @DisplayName("Frequency Tests")
    class FrequencyTests {
        
        @Test
        @DisplayName("Frequency enum has display names")
        void frequencyEnumHasDisplayNames() {
            assertEquals("Daily", BackupSchedule.Frequency.DAILY.getDisplayName());
            assertEquals("Weekly", BackupSchedule.Frequency.WEEKLY.getDisplayName());
            assertEquals("Monthly", BackupSchedule.Frequency.MONTHLY.getDisplayName());
        }
        
        @Test
        @DisplayName("Can set and get frequency")
        void canSetAndGetFrequency() {
            BackupSchedule schedule = new BackupSchedule();
            
            schedule.setFrequency(BackupSchedule.Frequency.WEEKLY);
            assertEquals(BackupSchedule.Frequency.WEEKLY, schedule.getFrequency());
            
            schedule.setFrequency(BackupSchedule.Frequency.MONTHLY);
            assertEquals(BackupSchedule.Frequency.MONTHLY, schedule.getFrequency());
        }
    }
    
    @Nested
    @DisplayName("Schedule Description Tests")
    class ScheduleDescriptionTests {
        
        @Test
        @DisplayName("Daily schedule description")
        void dailyScheduleDescription() {
            BackupSchedule schedule = new BackupSchedule();
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.of(3, 30));
            
            String desc = schedule.getScheduleDescription();
            assertTrue(desc.contains("Daily"));
            assertTrue(desc.contains("03:30"));
        }
        
        @Test
        @DisplayName("Weekly schedule description")
        void weeklyScheduleDescription() {
            BackupSchedule schedule = new BackupSchedule();
            schedule.setFrequency(BackupSchedule.Frequency.WEEKLY);
            schedule.setScheduledTime(LocalTime.of(2, 0));
            schedule.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
            
            String desc = schedule.getScheduleDescription();
            assertTrue(desc.contains("Weekly"));
            assertTrue(desc.contains("MON") || desc.contains("Mon"));
            assertTrue(desc.contains("FRI") || desc.contains("Fri"));
        }
        
        @Test
        @DisplayName("Monthly schedule description")
        void monthlyScheduleDescription() {
            BackupSchedule schedule = new BackupSchedule();
            schedule.setFrequency(BackupSchedule.Frequency.MONTHLY);
            schedule.setScheduledTime(LocalTime.of(1, 0));
            schedule.setDayOfMonth(15);
            
            String desc = schedule.getScheduleDescription();
            assertTrue(desc.contains("Monthly"));
            assertTrue(desc.contains("15"));
        }
        
        @Test
        @DisplayName("Monthly last day description")
        void monthlyLastDayDescription() {
            BackupSchedule schedule = new BackupSchedule();
            schedule.setFrequency(BackupSchedule.Frequency.MONTHLY);
            schedule.setDayOfMonth(0); // 0 = last day
            
            String desc = schedule.getScheduleDescription();
            assertTrue(desc.contains("last"));
        }
    }
    
    @Nested
    @DisplayName("Backup Settings Tests")
    class BackupSettingsTests {
        
        @Test
        @DisplayName("Can configure backup options")
        void canConfigureBackupOptions() {
            BackupSchedule schedule = new BackupSchedule();
            
            schedule.setIncremental(true);
            schedule.setCompress(true);
            schedule.setPreserveRelationships(true);
            schedule.setIncludeRelatedRecords(true);
            schedule.setRecordLimit(10000);
            
            assertTrue(schedule.isIncremental());
            assertTrue(schedule.isCompress());
            assertTrue(schedule.isPreserveRelationships());
            assertTrue(schedule.isIncludeRelatedRecords());
            assertEquals(10000, schedule.getRecordLimit());
        }
        
        @Test
        @DisplayName("Can set selected objects")
        void canSetSelectedObjects() {
            BackupSchedule schedule = new BackupSchedule();
            List<String> objects = Arrays.asList("Account", "Contact", "Opportunity");
            
            schedule.setSelectedObjects(objects);
            
            assertEquals(3, schedule.getSelectedObjects().size());
            assertTrue(schedule.getSelectedObjects().contains("Account"));
            assertTrue(schedule.getSelectedObjects().contains("Contact"));
            assertTrue(schedule.getSelectedObjects().contains("Opportunity"));
        }
        
        @Test
        @DisplayName("Can set output folder")
        void canSetOutputFolder() {
            BackupSchedule schedule = new BackupSchedule();
            
            schedule.setOutputFolder("/home/user/backups");
            
            assertEquals("/home/user/backups", schedule.getOutputFolder());
        }
    }
    
    @Nested
    @DisplayName("Salesforce Connection Tests")
    class SalesforceConnectionTests {
        
        @Test
        @DisplayName("Can configure Salesforce connection")
        void canConfigureSalesforceConnection() {
            BackupSchedule schedule = new BackupSchedule();
            
            schedule.setSalesforceUsername("user@company.com");
            schedule.setSalesforceInstanceUrl("https://na1.salesforce.com");
            schedule.setUseSandbox(false);
            
            assertEquals("user@company.com", schedule.getSalesforceUsername());
            assertEquals("https://na1.salesforce.com", schedule.getSalesforceInstanceUrl());
            assertFalse(schedule.isUseSandbox());
        }
        
        @Test
        @DisplayName("Can configure database export")
        void canConfigureDatabaseExport() {
            BackupSchedule schedule = new BackupSchedule();
            
            schedule.setExportToDatabase(true);
            schedule.setDatabaseConnectionId("conn-123");
            
            assertTrue(schedule.isExportToDatabase());
            assertEquals("conn-123", schedule.getDatabaseConnectionId());
        }
    }
    
    @Nested
    @DisplayName("Tracking Tests")
    class TrackingTests {
        
        @Test
        @DisplayName("Can track last run info")
        void canTrackLastRunInfo() {
            BackupSchedule schedule = new BackupSchedule();
            long now = System.currentTimeMillis();
            
            schedule.setLastRunTime(now);
            schedule.setLastRunStatus("SUCCESS");
            
            assertEquals(now, schedule.getLastRunTime());
            assertEquals("SUCCESS", schedule.getLastRunStatus());
        }
        
        @Test
        @DisplayName("Can track next run time")
        void canTrackNextRunTime() {
            BackupSchedule schedule = new BackupSchedule();
            long future = System.currentTimeMillis() + 3600000;
            
            schedule.setNextRunTime(future);
            
            assertEquals(future, schedule.getNextRunTime());
        }
        
        @Test
        @DisplayName("Status string reflects state")
        void statusStringReflectsState() {
            BackupSchedule schedule = new BackupSchedule();
            
            // Enabled, no run yet
            assertEquals("Pending", schedule.getStatusString());
            
            // After successful run
            schedule.setLastRunStatus("SUCCESS");
            assertEquals("SUCCESS", schedule.getStatusString());
            
            // Disabled
            schedule.setEnabled(false);
            assertEquals("Disabled", schedule.getStatusString());
        }
    }
    
    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {
        
        @Test
        @DisplayName("Schedules with same ID are equal")
        void schedulesWithSameIdAreEqual() {
            BackupSchedule s1 = new BackupSchedule("Test 1");
            BackupSchedule s2 = new BackupSchedule("Test 2");
            s2.setId(s1.getId());
            
            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }
        
        @Test
        @DisplayName("Schedules with different IDs are not equal")
        void schedulesWithDifferentIdsAreNotEqual() {
            BackupSchedule s1 = new BackupSchedule("Test 1");
            BackupSchedule s2 = new BackupSchedule("Test 1"); // Same name, different ID
            
            assertNotEquals(s1, s2);
        }
    }
    
    @Nested
    @DisplayName("Modification Tracking Tests")
    class ModificationTrackingTests {
        
        @Test
        @DisplayName("modifiedAt updates on property changes")
        void modifiedAtUpdatesOnPropertyChanges() throws InterruptedException {
            BackupSchedule schedule = new BackupSchedule();
            long original = schedule.getModifiedAt();
            
            Thread.sleep(10); // Ensure time passes
            schedule.setName("Updated Name");
            
            assertTrue(schedule.getModifiedAt() >= original);
        }
    }
}
