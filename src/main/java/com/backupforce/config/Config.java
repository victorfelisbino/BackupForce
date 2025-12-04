package com.backupforce.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class Config {
    private Properties properties;

    public Config(String configFilePath) throws IOException {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream(configFilePath)) {
            properties.load(fis);
        }
    }

    public String getUsername() {
        return getRequiredProperty("sf.username");
    }

    public String getPassword() {
        return getRequiredProperty("sf.password");
    }

    public String getServerUrl() {
        return getRequiredProperty("sf.serverurl");
    }

    public String getOutputFolder() {
        return getProperty("outputFolder", "C:/backupForce2/backup");
    }

    public String getBackupObjects() {
        return getProperty("backup.objects", "*");
    }

    public Set<String> getBackupObjectsExclude() {
        String exclude = getProperty("backup.objects.exclude", "");
        if (exclude.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(exclude.toLowerCase().split(",")));
    }

    public int getConnectionTimeout() {
        return Integer.parseInt(getProperty("http.connectionTimeoutSecs", "60"));
    }

    public int getReadTimeout() {
        return Integer.parseInt(getProperty("http.readTimeoutSecs", "540"));
    }

    public String getApiVersion() {
        return "62.0";
    }

    private String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue).trim();
    }

    private String getRequiredProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Required property '" + key + "' is not set");
        }
        return value.trim();
    }
}
