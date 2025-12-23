package com.backupforce.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Service that manages scheduled backup execution.
 * Uses ScheduledExecutorService for reliable timing and supports
 * daily, weekly, and monthly schedules.
 */
public class BackupSchedulerService {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupSchedulerService.class);
    private static BackupSchedulerService instance;
    
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private final ScheduleManager scheduleManager;
    private Consumer<BackupSchedule> backupExecutor;
    private volatile boolean running;
    
    private BackupSchedulerService() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "BackupScheduler");
            t.setDaemon(true);
            return t;
        });
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.scheduleManager = ScheduleManager.getInstance();
        this.running = false;
    }
    
    public static synchronized BackupSchedulerService getInstance() {
        if (instance == null) {
            instance = new BackupSchedulerService();
        }
        return instance;
    }
    
    /**
     * Set the callback that executes backups.
     * This allows the UI layer to provide the actual backup implementation.
     */
    public void setBackupExecutor(Consumer<BackupSchedule> executor) {
        this.backupExecutor = executor;
    }
    
    /**
     * Start the scheduler service.
     * Loads all enabled schedules and schedules them for execution.
     */
    public void start() {
        if (running) {
            logger.warn("Scheduler already running");
            return;
        }
        
        running = true;
        logger.info("Starting backup scheduler service");
        
        // Schedule all enabled backups
        List<BackupSchedule> enabledSchedules = scheduleManager.getEnabledSchedules();
        for (BackupSchedule schedule : enabledSchedules) {
            scheduleBackup(schedule);
        }
        
        logger.info("Scheduled {} backup tasks", enabledSchedules.size());
    }
    
    /**
     * Stop the scheduler service.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        logger.info("Stopping backup scheduler service");
        
        // Cancel all scheduled tasks
        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }
    
    /**
     * Shutdown the scheduler completely.
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Backup scheduler service shutdown complete");
    }
    
    /**
     * Check if the scheduler is running.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Schedule a backup for execution.
     */
    public void scheduleBackup(BackupSchedule schedule) {
        if (!schedule.isEnabled()) {
            logger.debug("Schedule {} is disabled, skipping", schedule.getName());
            return;
        }
        
        // Cancel existing task if any
        cancelSchedule(schedule.getId());
        
        // Calculate delay until next run
        long delayMillis = calculateDelayUntilNextRun(schedule);
        long nextRunTime = System.currentTimeMillis() + delayMillis;
        
        // Update next run time in schedule
        scheduleManager.updateNextRun(schedule.getId(), nextRunTime);
        
        // Schedule the task
        ScheduledFuture<?> future = scheduler.schedule(
            () -> executeScheduledBackup(schedule),
            delayMillis,
            TimeUnit.MILLISECONDS
        );
        
        scheduledTasks.put(schedule.getId(), future);
        
        logger.info("Scheduled backup '{}' to run in {} ms (at {})", 
            schedule.getName(), 
            delayMillis,
            Instant.ofEpochMilli(nextRunTime).atZone(ZoneId.systemDefault()));
    }
    
    /**
     * Cancel a scheduled backup.
     */
    public void cancelSchedule(String scheduleId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(scheduleId);
        if (existing != null) {
            existing.cancel(false);
            logger.debug("Cancelled schedule: {}", scheduleId);
        }
    }
    
    /**
     * Reschedule a backup (e.g., after settings change).
     */
    public void reschedule(BackupSchedule schedule) {
        cancelSchedule(schedule.getId());
        if (running && schedule.isEnabled()) {
            scheduleBackup(schedule);
        }
    }
    
    /**
     * Run a backup immediately (on-demand).
     */
    public void runNow(String scheduleId) {
        scheduleManager.getScheduleById(scheduleId).ifPresent(schedule -> {
            logger.info("Running backup on-demand: {}", schedule.getName());
            scheduler.execute(() -> executeScheduledBackup(schedule));
        });
    }
    
    /**
     * Execute a scheduled backup.
     */
    private void executeScheduledBackup(BackupSchedule schedule) {
        logger.info("Executing scheduled backup: {}", schedule.getName());
        
        try {
            if (backupExecutor == null) {
                logger.error("No backup executor configured!");
                scheduleManager.updateLastRun(schedule.getId(), "FAILED: No executor");
                return;
            }
            
            // Execute the backup
            backupExecutor.accept(schedule);
            
            // Update last run status
            scheduleManager.updateLastRun(schedule.getId(), "SUCCESS");
            
        } catch (Exception e) {
            logger.error("Scheduled backup failed: {}", e.getMessage(), e);
            scheduleManager.updateLastRun(schedule.getId(), "FAILED: " + e.getMessage());
        } finally {
            // Reschedule for next run
            if (running && schedule.isEnabled()) {
                scheduleBackup(schedule);
            }
        }
    }
    
    /**
     * Calculate milliseconds until the next scheduled run.
     */
    public long calculateDelayUntilNextRun(BackupSchedule schedule) {
        ZoneId zone = ZoneId.of(schedule.getTimeZone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextRun = calculateNextRunTime(schedule, now);
        
        return Duration.between(now, nextRun).toMillis();
    }
    
    /**
     * Calculate the next run time for a schedule.
     */
    public ZonedDateTime calculateNextRunTime(BackupSchedule schedule, ZonedDateTime from) {
        ZoneId zone = ZoneId.of(schedule.getTimeZone());
        LocalTime scheduledTime = schedule.getScheduledTime();
        
        ZonedDateTime candidate;
        
        switch (schedule.getFrequency()) {
            case DAILY:
                candidate = from.with(scheduledTime);
                if (!candidate.isAfter(from)) {
                    candidate = candidate.plusDays(1);
                }
                break;
                
            case WEEKLY:
                Set<DayOfWeek> days = schedule.getDaysOfWeek();
                if (days.isEmpty()) {
                    days = Set.of(DayOfWeek.MONDAY); // Default
                }
                
                candidate = from.with(scheduledTime);
                
                // Find next matching day
                for (int i = 0; i < 8; i++) {
                    ZonedDateTime test = candidate.plusDays(i);
                    if (days.contains(test.getDayOfWeek()) && test.isAfter(from)) {
                        candidate = test;
                        break;
                    }
                }
                break;
                
            case MONTHLY:
                int dayOfMonth = schedule.getDayOfMonth();
                
                if (dayOfMonth == 0) {
                    // Last day of month
                    candidate = from.with(TemporalAdjusters.lastDayOfMonth()).with(scheduledTime);
                } else {
                    // Specific day of month
                    int maxDay = from.toLocalDate().lengthOfMonth();
                    int targetDay = Math.min(dayOfMonth, maxDay);
                    candidate = from.withDayOfMonth(targetDay).with(scheduledTime);
                }
                
                if (!candidate.isAfter(from)) {
                    // Move to next month
                    candidate = candidate.plusMonths(1);
                    if (dayOfMonth == 0) {
                        candidate = candidate.with(TemporalAdjusters.lastDayOfMonth()).with(scheduledTime);
                    } else {
                        int maxDay = candidate.toLocalDate().lengthOfMonth();
                        candidate = candidate.withDayOfMonth(Math.min(dayOfMonth, maxDay));
                    }
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unknown frequency: " + schedule.getFrequency());
        }
        
        return candidate;
    }
    
    /**
     * Get the next run time as a formatted string.
     */
    public String getNextRunTimeFormatted(BackupSchedule schedule) {
        if (!schedule.isEnabled()) {
            return "Disabled";
        }
        
        ZoneId zone = ZoneId.of(schedule.getTimeZone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextRun = calculateNextRunTime(schedule, now);
        
        return nextRun.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z"));
    }
    
    /**
     * Get count of scheduled (enabled) backups.
     */
    public int getScheduledCount() {
        return scheduledTasks.size();
    }
    
    /**
     * Get all schedule IDs that are currently scheduled.
     */
    public Set<String> getScheduledIds() {
        return new HashSet<>(scheduledTasks.keySet());
    }
}
