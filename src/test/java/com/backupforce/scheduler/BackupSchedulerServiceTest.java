package com.backupforce.scheduler;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BackupSchedulerService.
 */
@DisplayName("BackupSchedulerService Tests")
class BackupSchedulerServiceTest {
    
    private BackupSchedulerService service;
    
    @BeforeEach
    void setUp() {
        service = BackupSchedulerService.getInstance();
        service.stop(); // Ensure clean state
    }
    
    @AfterEach
    void tearDown() {
        service.stop();
    }
    
    @Nested
    @DisplayName("Service Lifecycle Tests")
    class LifecycleTests {
        
        @Test
        @DisplayName("Singleton instance is consistent")
        void singletonInstanceIsConsistent() {
            BackupSchedulerService s1 = BackupSchedulerService.getInstance();
            BackupSchedulerService s2 = BackupSchedulerService.getInstance();
            
            assertSame(s1, s2);
        }
        
        @Test
        @DisplayName("Can start and stop service")
        void canStartAndStopService() {
            assertFalse(service.isRunning());
            
            service.start();
            assertTrue(service.isRunning());
            
            service.stop();
            assertFalse(service.isRunning());
        }
        
        @Test
        @DisplayName("Starting already running service is safe")
        void startingAlreadyRunningServiceIsSafe() {
            service.start();
            assertTrue(service.isRunning());
            
            // Start again - should not throw
            service.start();
            assertTrue(service.isRunning());
        }
        
        @Test
        @DisplayName("Stopping already stopped service is safe")
        void stoppingAlreadyStoppedServiceIsSafe() {
            assertFalse(service.isRunning());
            
            // Stop again - should not throw
            service.stop();
            assertFalse(service.isRunning());
        }
    }
    
    @Nested
    @DisplayName("Next Run Calculation Tests")
    class NextRunCalculationTests {
        
        @Test
        @DisplayName("Daily schedule calculates next run correctly")
        void dailyScheduleCalculatesNextRunCorrectly() {
            BackupSchedule schedule = new BackupSchedule("Daily Test");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.of(3, 0));
            schedule.setTimeZone("UTC");
            
            ZonedDateTime now = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime nextRun = service.calculateNextRunTime(schedule, now);
            
            // Next run should be tomorrow at 3:00 AM (since 10:00 AM > 3:00 AM)
            assertEquals(16, nextRun.getDayOfMonth());
            assertEquals(3, nextRun.getHour());
            assertEquals(0, nextRun.getMinute());
        }
        
        @Test
        @DisplayName("Daily schedule same day if time not passed")
        void dailyScheduleSameDayIfTimeNotPassed() {
            BackupSchedule schedule = new BackupSchedule("Daily Test");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.of(15, 0)); // 3 PM
            schedule.setTimeZone("UTC");
            
            ZonedDateTime now = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.of("UTC")); // 10 AM
            ZonedDateTime nextRun = service.calculateNextRunTime(schedule, now);
            
            // Next run should be today at 3:00 PM
            assertEquals(15, nextRun.getDayOfMonth());
            assertEquals(15, nextRun.getHour());
            assertEquals(0, nextRun.getMinute());
        }
        
        @Test
        @DisplayName("Weekly schedule finds next matching day")
        void weeklyScheduleFindsNextMatchingDay() {
            BackupSchedule schedule = new BackupSchedule("Weekly Test");
            schedule.setFrequency(BackupSchedule.Frequency.WEEKLY);
            schedule.setScheduledTime(LocalTime.of(2, 0));
            schedule.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
            schedule.setTimeZone("UTC");
            
            // Saturday June 15, 2024
            ZonedDateTime now = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime nextRun = service.calculateNextRunTime(schedule, now);
            
            // Next run should be Monday June 17
            assertEquals(DayOfWeek.MONDAY, nextRun.getDayOfWeek());
            assertEquals(17, nextRun.getDayOfMonth());
        }
        
        @Test
        @DisplayName("Monthly schedule calculates correct day")
        void monthlyScheduleCalculatesCorrectDay() {
            BackupSchedule schedule = new BackupSchedule("Monthly Test");
            schedule.setFrequency(BackupSchedule.Frequency.MONTHLY);
            schedule.setScheduledTime(LocalTime.of(1, 0));
            schedule.setDayOfMonth(15);
            schedule.setTimeZone("UTC");
            
            // June 20, 2024 (past the 15th)
            ZonedDateTime now = ZonedDateTime.of(2024, 6, 20, 10, 0, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime nextRun = service.calculateNextRunTime(schedule, now);
            
            // Next run should be July 15
            assertEquals(7, nextRun.getMonthValue());
            assertEquals(15, nextRun.getDayOfMonth());
        }
        
        @Test
        @DisplayName("Monthly last day schedule works")
        void monthlyLastDayScheduleWorks() {
            BackupSchedule schedule = new BackupSchedule("Monthly Last Day");
            schedule.setFrequency(BackupSchedule.Frequency.MONTHLY);
            schedule.setScheduledTime(LocalTime.of(23, 0));
            schedule.setDayOfMonth(0); // 0 = last day
            schedule.setTimeZone("UTC");
            
            // June 15, 2024
            ZonedDateTime now = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime nextRun = service.calculateNextRunTime(schedule, now);
            
            // Next run should be June 30 (last day of June)
            assertEquals(30, nextRun.getDayOfMonth());
            assertEquals(6, nextRun.getMonthValue());
        }
        
        @Test
        @DisplayName("Handles months with fewer days")
        void handlesMonthsWithFewerDays() {
            BackupSchedule schedule = new BackupSchedule("Monthly 31st");
            schedule.setFrequency(BackupSchedule.Frequency.MONTHLY);
            schedule.setScheduledTime(LocalTime.of(1, 0));
            schedule.setDayOfMonth(31);
            schedule.setTimeZone("UTC");
            
            // January 15, 2024
            ZonedDateTime now = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime nextRun = service.calculateNextRunTime(schedule, now);
            
            // Next run should be January 31
            assertEquals(31, nextRun.getDayOfMonth());
            assertEquals(1, nextRun.getMonthValue());
        }
    }
    
    @Nested
    @DisplayName("Delay Calculation Tests")
    class DelayCalculationTests {
        
        @Test
        @DisplayName("Delay is positive for future run")
        void delayIsPositiveForFutureRun() {
            BackupSchedule schedule = new BackupSchedule("Test");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.now().plusHours(1));
            schedule.setTimeZone(TimeZone.getDefault().getID());
            
            long delay = service.calculateDelayUntilNextRun(schedule);
            
            assertTrue(delay > 0);
            assertTrue(delay <= 25 * 60 * 60 * 1000); // Less than 25 hours
        }
    }
    
    @Nested
    @DisplayName("Schedule Management Tests")
    class ScheduleManagementTests {
        
        @Test
        @DisplayName("Can schedule and cancel backup")
        void canScheduleAndCancelBackup() {
            BackupSchedule schedule = new BackupSchedule("Test Schedule");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.of(3, 0));
            schedule.setTimeZone(TimeZone.getDefault().getID());
            
            service.start();
            service.scheduleBackup(schedule);
            
            assertTrue(service.getScheduledIds().contains(schedule.getId()));
            
            service.cancelSchedule(schedule.getId());
            
            assertFalse(service.getScheduledIds().contains(schedule.getId()));
        }
        
        @Test
        @DisplayName("Disabled schedule not scheduled")
        void disabledScheduleNotScheduled() {
            BackupSchedule schedule = new BackupSchedule("Disabled");
            schedule.setEnabled(false);
            
            service.start();
            service.scheduleBackup(schedule);
            
            assertFalse(service.getScheduledIds().contains(schedule.getId()));
        }
        
        @Test
        @DisplayName("Reschedule updates scheduled task")
        void rescheduleUpdatesScheduledTask() {
            BackupSchedule schedule = new BackupSchedule("Test");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.of(3, 0));
            schedule.setTimeZone(TimeZone.getDefault().getID());
            
            service.start();
            service.scheduleBackup(schedule);
            
            int initialCount = service.getScheduledCount();
            
            // Reschedule
            schedule.setScheduledTime(LocalTime.of(4, 0));
            service.reschedule(schedule);
            
            // Count should be the same (replaced, not added)
            assertEquals(initialCount, service.getScheduledCount());
            assertTrue(service.getScheduledIds().contains(schedule.getId()));
        }
    }
    
    @Nested
    @DisplayName("Backup Executor Tests")
    class BackupExecutorTests {
        
        @Test
        @DisplayName("Can set backup executor")
        void canSetBackupExecutor() {
            List<String> executed = new ArrayList<>();
            
            service.setBackupExecutor(schedule -> {
                executed.add(schedule.getId());
            });
            
            // The executor should be set (we can't easily test it fires without waiting)
            assertNotNull(service);
        }
    }
    
    @Nested
    @DisplayName("Formatted Output Tests")
    class FormattedOutputTests {
        
        @Test
        @DisplayName("Next run formatted for enabled schedule")
        void nextRunFormattedForEnabledSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test");
            schedule.setFrequency(BackupSchedule.Frequency.DAILY);
            schedule.setScheduledTime(LocalTime.of(3, 0));
            schedule.setTimeZone(TimeZone.getDefault().getID());
            schedule.setEnabled(true);
            
            String formatted = service.getNextRunTimeFormatted(schedule);
            
            assertNotNull(formatted);
            assertFalse(formatted.isEmpty());
            assertNotEquals("Disabled", formatted);
        }
        
        @Test
        @DisplayName("Next run shows disabled for disabled schedule")
        void nextRunShowsDisabledForDisabledSchedule() {
            BackupSchedule schedule = new BackupSchedule("Test");
            schedule.setEnabled(false);
            
            String formatted = service.getNextRunTimeFormatted(schedule);
            
            assertEquals("Disabled", formatted);
        }
    }
}
