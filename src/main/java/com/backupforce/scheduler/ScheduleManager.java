package com.backupforce.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages persistence of backup schedules to a JSON file.
 * Stores schedules in ~/.backupforce/schedules.json
 */
public class ScheduleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduleManager.class);
    private static final String SCHEDULES_FILE = "schedules.json";
    private static ScheduleManager instance;
    
    private final Path configDir;
    private final Path schedulesFile;
    private final List<BackupSchedule> schedules;
    private final Gson gson;
    
    private ScheduleManager() {
        String appData = System.getProperty("user.home") + "/.backupforce";
        this.configDir = Paths.get(appData);
        this.schedulesFile = configDir.resolve(SCHEDULES_FILE);
        this.schedules = new CopyOnWriteArrayList<>();
        
        // Configure Gson for proper serialization
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
            .registerTypeAdapter(DayOfWeek.class, new DayOfWeekAdapter())
            .create();
        
        loadSchedules();
    }
    
    public static synchronized ScheduleManager getInstance() {
        if (instance == null) {
            instance = new ScheduleManager();
        }
        return instance;
    }
    
    /**
     * Get all schedules.
     */
    public List<BackupSchedule> getSchedules() {
        return new ArrayList<>(schedules);
    }
    
    /**
     * Get enabled schedules only.
     */
    public List<BackupSchedule> getEnabledSchedules() {
        return schedules.stream()
            .filter(BackupSchedule::isEnabled)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get a schedule by ID.
     */
    public Optional<BackupSchedule> getScheduleById(String id) {
        return schedules.stream()
            .filter(s -> s.getId().equals(id))
            .findFirst();
    }
    
    /**
     * Get schedules for a specific Salesforce user.
     */
    public List<BackupSchedule> getSchedulesForUser(String username) {
        return schedules.stream()
            .filter(s -> username.equals(s.getSalesforceUsername()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Add a new schedule.
     */
    public void addSchedule(BackupSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule cannot be null");
        }
        
        // Check for duplicate ID
        if (getScheduleById(schedule.getId()).isPresent()) {
            throw new IllegalArgumentException("Schedule with ID already exists: " + schedule.getId());
        }
        
        schedules.add(schedule);
        saveSchedules();
        logger.info("Added schedule: {} ({})", schedule.getName(), schedule.getId());
    }
    
    /**
     * Update an existing schedule.
     */
    public void updateSchedule(BackupSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule cannot be null");
        }
        
        int index = -1;
        for (int i = 0; i < schedules.size(); i++) {
            if (schedules.get(i).getId().equals(schedule.getId())) {
                index = i;
                break;
            }
        }
        
        if (index == -1) {
            throw new IllegalArgumentException("Schedule not found: " + schedule.getId());
        }
        
        schedule.setModifiedAt(System.currentTimeMillis());
        schedules.set(index, schedule);
        saveSchedules();
        logger.info("Updated schedule: {} ({})", schedule.getName(), schedule.getId());
    }
    
    /**
     * Delete a schedule.
     */
    public boolean deleteSchedule(String id) {
        boolean removed = schedules.removeIf(s -> s.getId().equals(id));
        if (removed) {
            saveSchedules();
            logger.info("Deleted schedule: {}", id);
        }
        return removed;
    }
    
    /**
     * Enable or disable a schedule.
     */
    public void setScheduleEnabled(String id, boolean enabled) {
        getScheduleById(id).ifPresent(schedule -> {
            schedule.setEnabled(enabled);
            saveSchedules();
            logger.info("Schedule {} {} ", id, enabled ? "enabled" : "disabled");
        });
    }
    
    /**
     * Update the last run information for a schedule.
     */
    public void updateLastRun(String id, String status) {
        getScheduleById(id).ifPresent(schedule -> {
            schedule.setLastRunTime(System.currentTimeMillis());
            schedule.setLastRunStatus(status);
            saveSchedules();
        });
    }
    
    /**
     * Update the next run time for a schedule.
     */
    public void updateNextRun(String id, long nextRunTime) {
        getScheduleById(id).ifPresent(schedule -> {
            schedule.setNextRunTime(nextRunTime);
            saveSchedules();
        });
    }
    
    /**
     * Load schedules from disk.
     */
    private void loadSchedules() {
        schedules.clear();
        
        if (!Files.exists(schedulesFile)) {
            logger.info("No schedules file found, starting with empty list");
            return;
        }
        
        try (Reader reader = new FileReader(schedulesFile.toFile())) {
            Type listType = new TypeToken<List<BackupSchedule>>(){}.getType();
            List<BackupSchedule> loaded = gson.fromJson(reader, listType);
            
            if (loaded != null) {
                schedules.addAll(loaded);
                logger.info("Loaded {} schedules from disk", schedules.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load schedules: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save schedules to disk.
     */
    private void saveSchedules() {
        try {
            // Ensure directory exists
            Files.createDirectories(configDir);
            
            try (Writer writer = new FileWriter(schedulesFile.toFile())) {
                gson.toJson(schedules, writer);
            }
            
            logger.debug("Saved {} schedules to disk", schedules.size());
        } catch (IOException e) {
            logger.error("Failed to save schedules: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Force reload from disk (for testing).
     */
    public void reload() {
        loadSchedules();
    }
    
    // ==================== Gson Type Adapters ====================
    
    /**
     * Gson adapter for LocalTime serialization.
     */
    private static class LocalTimeAdapter implements com.google.gson.JsonSerializer<LocalTime>,
            com.google.gson.JsonDeserializer<LocalTime> {
        
        @Override
        public com.google.gson.JsonElement serialize(LocalTime src, java.lang.reflect.Type typeOfSrc,
                com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.toString());
        }
        
        @Override
        public LocalTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
            return LocalTime.parse(json.getAsString());
        }
    }
    
    /**
     * Gson adapter for DayOfWeek serialization.
     */
    private static class DayOfWeekAdapter implements com.google.gson.JsonSerializer<DayOfWeek>,
            com.google.gson.JsonDeserializer<DayOfWeek> {
        
        @Override
        public com.google.gson.JsonElement serialize(DayOfWeek src, java.lang.reflect.Type typeOfSrc,
                com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.name());
        }
        
        @Override
        public DayOfWeek deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
            return DayOfWeek.valueOf(json.getAsString());
        }
    }
}
