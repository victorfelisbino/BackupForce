package com.backupforce.restore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Compares schema between backup data and target Salesforce org.
 * Identifies mismatches in RecordTypes, picklist values, users, and fields
 * to help users configure proper transformations before restore.
 */
public class SchemaComparer {
    
    private static final Logger logger = LoggerFactory.getLogger(SchemaComparer.class);
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;
    
    private Consumer<String> logCallback;
    
    public SchemaComparer(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
    }
    
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }
    
    private void log(String message) {
        logger.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
    
    /**
     * Compares backup data against target org schema for a specific object.
     * Returns a detailed comparison result with all mismatches.
     */
    public ObjectComparisonResult compareObject(String objectName, 
                                                  Set<String> backupFields,
                                                  Map<String, Set<String>> backupPicklistValues,
                                                  Set<String> backupRecordTypeIds,
                                                  Set<String> backupUserIds) 
                                                  throws IOException, ParseException {
        
        log("Comparing schema for " + objectName + "...");
        
        ObjectComparisonResult result = new ObjectComparisonResult(objectName);
        
        // Get target org metadata
        ObjectMetadata targetMetadata = describeObject(objectName);
        
        // Compare fields
        compareFields(backupFields, targetMetadata, result);
        
        // Compare picklist values
        comparePicklistValues(objectName, backupPicklistValues, targetMetadata, result);
        
        // Compare RecordTypes
        if (!backupRecordTypeIds.isEmpty()) {
            compareRecordTypes(objectName, backupRecordTypeIds, result);
        }
        
        // Compare Users (if user IDs found in backup)
        if (!backupUserIds.isEmpty()) {
            compareUsers(backupUserIds, result);
        }
        
        log(objectName + " comparison complete: " + result.getSummary());
        
        return result;
    }
    
    /**
     * Describes an object and returns its metadata
     */
    private ObjectMetadata describeObject(String objectName) throws IOException, ParseException {
        String url = String.format("%s/services/data/v%s/sobjects/%s/describe", 
                                   instanceUrl, apiVersion, objectName);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                throw new IOException("Failed to describe object " + objectName + ": " + responseBody);
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return parseObjectMetadata(objectName, json);
        }
    }
    
    private ObjectMetadata parseObjectMetadata(String objectName, JsonObject json) {
        ObjectMetadata metadata = new ObjectMetadata(objectName);
        
        JsonArray fields = json.getAsJsonArray("fields");
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.get(i).getAsJsonObject();
            String fieldName = field.get("name").getAsString();
            String fieldType = field.has("type") ? field.get("type").getAsString() : "";
            boolean createable = field.has("createable") && field.get("createable").getAsBoolean();
            boolean updateable = field.has("updateable") && field.get("updateable").getAsBoolean();
            
            FieldMetadata fieldMeta = new FieldMetadata(fieldName, fieldType, createable, updateable);
            
            // Parse picklist values
            if (fieldType.equals("picklist") || fieldType.equals("multipicklist")) {
                JsonArray picklistValues = field.getAsJsonArray("picklistValues");
                if (picklistValues != null) {
                    for (int j = 0; j < picklistValues.size(); j++) {
                        JsonObject plValue = picklistValues.get(j).getAsJsonObject();
                        String value = plValue.get("value").getAsString();
                        String label = plValue.get("label").getAsString();
                        boolean active = plValue.get("active").getAsBoolean();
                        
                        if (active) {
                            fieldMeta.addPicklistValue(value, label);
                        }
                    }
                }
            }
            
            metadata.addField(fieldMeta);
        }
        
        // Parse RecordType info
        if (json.has("recordTypeInfos")) {
            JsonArray recordTypes = json.getAsJsonArray("recordTypeInfos");
            for (int i = 0; i < recordTypes.size(); i++) {
                JsonObject rt = recordTypes.get(i).getAsJsonObject();
                String rtId = rt.get("recordTypeId").getAsString();
                String rtName = rt.get("name").getAsString();
                String devName = rt.has("developerName") ? rt.get("developerName").getAsString() : "";
                boolean available = rt.get("available").getAsBoolean();
                boolean isDefault = rt.get("defaultRecordTypeMapping").getAsBoolean();
                
                if (available && !rtName.equals("Master")) {
                    metadata.addRecordType(new RecordTypeInfo(rtId, rtName, devName, isDefault));
                }
            }
        }
        
        return metadata;
    }
    
    private void compareFields(Set<String> backupFields, ObjectMetadata targetMetadata, 
                               ObjectComparisonResult result) {
        
        Set<String> targetFieldNames = targetMetadata.getFieldNames();
        
        for (String backupField : backupFields) {
            // Skip reference columns (they're not actual SF fields)
            if (backupField.startsWith("_ref_") || backupField.startsWith("_rel_")) {
                continue;
            }
            
            if (!targetFieldNames.contains(backupField)) {
                result.addMissingField(backupField);
            } else {
                FieldMetadata targetField = targetMetadata.getField(backupField);
                if (!targetField.isCreateable() && !backupField.equals("Id")) {
                    result.addNonCreateableField(backupField);
                }
            }
        }
    }
    
    private void comparePicklistValues(String objectName,
                                        Map<String, Set<String>> backupPicklistValues,
                                        ObjectMetadata targetMetadata,
                                        ObjectComparisonResult result) {
        
        for (Map.Entry<String, Set<String>> entry : backupPicklistValues.entrySet()) {
            String fieldName = entry.getKey();
            Set<String> sourceValues = entry.getValue();
            
            FieldMetadata targetField = targetMetadata.getField(fieldName);
            if (targetField == null) {
                continue; // Field doesn't exist - already handled
            }
            
            Set<String> targetValues = targetField.getPicklistValues();
            
            for (String sourceValue : sourceValues) {
                if (sourceValue == null || sourceValue.isEmpty()) {
                    continue;
                }
                
                if (!targetValues.contains(sourceValue)) {
                    result.addPicklistMismatch(fieldName, sourceValue, targetValues);
                }
            }
        }
    }
    
    private void compareRecordTypes(String objectName, Set<String> backupRecordTypeIds,
                                     ObjectComparisonResult result) throws IOException, ParseException {
        
        // Query target org for RecordTypes
        Map<String, RecordTypeInfo> targetRecordTypes = queryRecordTypes(objectName);
        
        for (String sourceRtId : backupRecordTypeIds) {
            if (sourceRtId == null || sourceRtId.isEmpty()) {
                continue;
            }
            
            if (!targetRecordTypes.containsKey(sourceRtId)) {
                // RecordType ID doesn't exist in target - need mapping
                result.addRecordTypeMismatch(sourceRtId, targetRecordTypes.values());
            }
        }
        
        result.setTargetRecordTypes(new ArrayList<>(targetRecordTypes.values()));
    }
    
    private Map<String, RecordTypeInfo> queryRecordTypes(String objectName) 
            throws IOException, ParseException {
        
        String soql = "SELECT Id, Name, DeveloperName, IsActive FROM RecordType " +
                      "WHERE SobjectType = '" + objectName + "' AND IsActive = true";
        
        String url = instanceUrl + "/services/data/v" + apiVersion + "/query?q=" + 
                     URLEncoder.encode(soql, StandardCharsets.UTF_8);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        Map<String, RecordTypeInfo> recordTypes = new LinkedHashMap<>();
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                logger.warn("Failed to query RecordTypes for {}: {}", objectName, responseBody);
                return recordTypes;
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray records = json.getAsJsonArray("records");
            
            for (int i = 0; i < records.size(); i++) {
                JsonObject rt = records.get(i).getAsJsonObject();
                String id = rt.get("Id").getAsString();
                String name = rt.get("Name").getAsString();
                String devName = rt.has("DeveloperName") && !rt.get("DeveloperName").isJsonNull() 
                    ? rt.get("DeveloperName").getAsString() : "";
                
                recordTypes.put(id, new RecordTypeInfo(id, name, devName, false));
            }
        }
        
        return recordTypes;
    }
    
    private void compareUsers(Set<String> backupUserIds, ObjectComparisonResult result) 
            throws IOException, ParseException {
        
        // Query target org for users by ID
        List<String> userIdList = new ArrayList<>(backupUserIds);
        Map<String, UserInfo> targetUsers = new LinkedHashMap<>();
        
        // Batch query (IN clause limit)
        int batchSize = 100;
        for (int i = 0; i < userIdList.size(); i += batchSize) {
            List<String> batch = userIdList.subList(i, Math.min(i + batchSize, userIdList.size()));
            Map<String, UserInfo> batchUsers = queryUsers(batch);
            targetUsers.putAll(batchUsers);
        }
        
        // Also get all active users for suggestions
        List<UserInfo> allActiveUsers = queryAllActiveUsers();
        result.setTargetUsers(allActiveUsers);
        
        for (String sourceUserId : backupUserIds) {
            if (sourceUserId == null || sourceUserId.isEmpty()) {
                continue;
            }
            
            if (!targetUsers.containsKey(sourceUserId)) {
                result.addUserMismatch(sourceUserId, allActiveUsers);
            }
        }
    }
    
    private Map<String, UserInfo> queryUsers(List<String> userIds) throws IOException, ParseException {
        StringBuilder soql = new StringBuilder();
        soql.append("SELECT Id, Username, Name, Email, IsActive FROM User WHERE Id IN (");
        
        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) soql.append(", ");
            soql.append("'").append(userIds.get(i)).append("'");
        }
        soql.append(")");
        
        return executeUserQuery(soql.toString());
    }
    
    private List<UserInfo> queryAllActiveUsers() throws IOException, ParseException {
        String soql = "SELECT Id, Username, Name, Email, IsActive FROM User WHERE IsActive = true ORDER BY Name LIMIT 500";
        Map<String, UserInfo> users = executeUserQuery(soql);
        return new ArrayList<>(users.values());
    }
    
    private Map<String, UserInfo> executeUserQuery(String soql) throws IOException, ParseException {
        String url = instanceUrl + "/services/data/v" + apiVersion + "/query?q=" + 
                     URLEncoder.encode(soql, StandardCharsets.UTF_8);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        Map<String, UserInfo> users = new LinkedHashMap<>();
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                logger.warn("Failed to query Users: {}", responseBody);
                return users;
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray records = json.getAsJsonArray("records");
            
            for (int i = 0; i < records.size(); i++) {
                JsonObject user = records.get(i).getAsJsonObject();
                String id = user.get("Id").getAsString();
                String username = user.has("Username") && !user.get("Username").isJsonNull() 
                    ? user.get("Username").getAsString() : "";
                String name = user.has("Name") && !user.get("Name").isJsonNull() 
                    ? user.get("Name").getAsString() : "";
                String email = user.has("Email") && !user.get("Email").isJsonNull() 
                    ? user.get("Email").getAsString() : "";
                boolean isActive = user.has("IsActive") && user.get("IsActive").getAsBoolean();
                
                users.put(id, new UserInfo(id, username, name, email, isActive));
            }
        }
        
        return users;
    }
    
    /**
     * Analyzes backup data to extract unique picklist values, RecordType IDs, and User IDs
     */
    public static BackupDataAnalysis analyzeBackupData(String objectName,
                                                        List<Map<String, String>> records,
                                                        Set<String> picklistFields,
                                                        boolean hasRecordTypeId) {
        
        BackupDataAnalysis analysis = new BackupDataAnalysis(objectName);
        
        Set<String> userFields = Set.of("OwnerId", "CreatedById", "LastModifiedById");
        
        for (Map<String, String> record : records) {
            // Collect RecordType IDs
            if (hasRecordTypeId && record.containsKey("RecordTypeId")) {
                String rtId = record.get("RecordTypeId");
                if (rtId != null && !rtId.isEmpty()) {
                    analysis.addRecordTypeId(rtId);
                }
            }
            
            // Collect picklist values
            for (String picklistField : picklistFields) {
                if (record.containsKey(picklistField)) {
                    String value = record.get(picklistField);
                    if (value != null && !value.isEmpty()) {
                        analysis.addPicklistValue(picklistField, value);
                    }
                }
            }
            
            // Collect user IDs
            for (String userField : userFields) {
                if (record.containsKey(userField)) {
                    String userId = record.get(userField);
                    if (userId != null && !userId.isEmpty() && userId.startsWith("005")) {
                        analysis.addUserId(userId);
                    }
                }
            }
        }
        
        return analysis;
    }
    
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
    
    // ==================== Inner Classes ====================
    
    public static class ObjectMetadata {
        private final String objectName;
        private final Map<String, FieldMetadata> fields = new LinkedHashMap<>();
        private final List<RecordTypeInfo> recordTypes = new ArrayList<>();
        
        public ObjectMetadata(String objectName) {
            this.objectName = objectName;
        }
        
        public void addField(FieldMetadata field) {
            fields.put(field.getName(), field);
        }
        
        public FieldMetadata getField(String name) {
            return fields.get(name);
        }
        
        public Set<String> getFieldNames() {
            return fields.keySet();
        }
        
        public void addRecordType(RecordTypeInfo rt) {
            recordTypes.add(rt);
        }
        
        public List<RecordTypeInfo> getRecordTypes() {
            return recordTypes;
        }
        
        public String getObjectName() { return objectName; }
    }
    
    public static class FieldMetadata {
        private final String name;
        private final String type;
        private final boolean createable;
        private final boolean updateable;
        private final Map<String, String> picklistValues = new LinkedHashMap<>(); // value -> label
        
        public FieldMetadata(String name, String type, boolean createable, boolean updateable) {
            this.name = name;
            this.type = type;
            this.createable = createable;
            this.updateable = updateable;
        }
        
        public void addPicklistValue(String value, String label) {
            picklistValues.put(value, label);
        }
        
        public Set<String> getPicklistValues() {
            return picklistValues.keySet();
        }
        
        public Map<String, String> getPicklistValuesWithLabels() {
            return picklistValues;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isCreateable() { return createable; }
        public boolean isUpdateable() { return updateable; }
    }
    
    public static class RecordTypeInfo {
        private final String id;
        private final String name;
        private final String developerName;
        private final boolean isDefault;
        
        public RecordTypeInfo(String id, String name, String developerName, boolean isDefault) {
            this.id = id;
            this.name = name;
            this.developerName = developerName;
            this.isDefault = isDefault;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDeveloperName() { return developerName; }
        public boolean isDefault() { return isDefault; }
        
        @Override
        public String toString() {
            return name + " (" + developerName + ")";
        }
    }
    
    public static class UserInfo {
        private final String id;
        private final String username;
        private final String name;
        private final String email;
        private final boolean isActive;
        
        public UserInfo(String id, String username, String name, String email, boolean isActive) {
            this.id = id;
            this.username = username;
            this.name = name;
            this.email = email;
            this.isActive = isActive;
        }
        
        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public boolean isActive() { return isActive; }
        
        @Override
        public String toString() {
            return name + " (" + username + ")";
        }
    }
    
    public static class BackupDataAnalysis {
        private final String objectName;
        private final Set<String> recordTypeIds = new LinkedHashSet<>();
        private final Map<String, Set<String>> picklistValues = new LinkedHashMap<>();
        private final Set<String> userIds = new LinkedHashSet<>();
        
        public BackupDataAnalysis(String objectName) {
            this.objectName = objectName;
        }
        
        public void addRecordTypeId(String id) {
            recordTypeIds.add(id);
        }
        
        public void addPicklistValue(String fieldName, String value) {
            picklistValues.computeIfAbsent(fieldName, k -> new LinkedHashSet<>()).add(value);
        }
        
        public void addUserId(String userId) {
            userIds.add(userId);
        }
        
        public String getObjectName() { return objectName; }
        public Set<String> getRecordTypeIds() { return recordTypeIds; }
        public Map<String, Set<String>> getPicklistValues() { return picklistValues; }
        public Set<String> getUserIds() { return userIds; }
    }
    
    /**
     * Result of comparing backup data against target org schema
     */
    public static class ObjectComparisonResult {
        private final String objectName;
        
        private final List<String> missingFields = new ArrayList<>();
        private final List<String> nonCreateableFields = new ArrayList<>();
        private final List<PicklistMismatch> picklistMismatches = new ArrayList<>();
        private final List<RecordTypeMismatch> recordTypeMismatches = new ArrayList<>();
        private final List<UserMismatch> userMismatches = new ArrayList<>();
        
        private List<RecordTypeInfo> targetRecordTypes = new ArrayList<>();
        private List<UserInfo> targetUsers = new ArrayList<>();
        
        public ObjectComparisonResult(String objectName) {
            this.objectName = objectName;
        }
        
        public void addMissingField(String fieldName) {
            missingFields.add(fieldName);
        }
        
        public void addNonCreateableField(String fieldName) {
            nonCreateableFields.add(fieldName);
        }
        
        public void addPicklistMismatch(String fieldName, String sourceValue, Set<String> targetValues) {
            picklistMismatches.add(new PicklistMismatch(fieldName, sourceValue, new ArrayList<>(targetValues)));
        }
        
        public void addRecordTypeMismatch(String sourceRecordTypeId, Collection<RecordTypeInfo> targetOptions) {
            recordTypeMismatches.add(new RecordTypeMismatch(sourceRecordTypeId, new ArrayList<>(targetOptions)));
        }
        
        public void addUserMismatch(String sourceUserId, List<UserInfo> targetOptions) {
            userMismatches.add(new UserMismatch(sourceUserId, targetOptions));
        }
        
        public void setTargetRecordTypes(List<RecordTypeInfo> recordTypes) {
            this.targetRecordTypes = recordTypes;
        }
        
        public void setTargetUsers(List<UserInfo> users) {
            this.targetUsers = users;
        }
        
        public boolean hasMismatches() {
            return !missingFields.isEmpty() || !picklistMismatches.isEmpty() || 
                   !recordTypeMismatches.isEmpty() || !userMismatches.isEmpty();
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (!missingFields.isEmpty()) {
                sb.append(missingFields.size()).append(" missing fields, ");
            }
            if (!nonCreateableFields.isEmpty()) {
                sb.append(nonCreateableFields.size()).append(" non-createable fields, ");
            }
            if (!picklistMismatches.isEmpty()) {
                sb.append(picklistMismatches.size()).append(" picklist mismatches, ");
            }
            if (!recordTypeMismatches.isEmpty()) {
                sb.append(recordTypeMismatches.size()).append(" RecordType mismatches, ");
            }
            if (!userMismatches.isEmpty()) {
                sb.append(userMismatches.size()).append(" User mismatches");
            }
            
            if (sb.length() == 0) {
                return "No mismatches found";
            }
            
            // Remove trailing comma and space
            String result = sb.toString();
            if (result.endsWith(", ")) {
                result = result.substring(0, result.length() - 2);
            }
            return result;
        }
        
        // Getters
        public String getObjectName() { return objectName; }
        public List<String> getMissingFields() { return missingFields; }
        public List<String> getNonCreateableFields() { return nonCreateableFields; }
        public List<PicklistMismatch> getPicklistMismatches() { return picklistMismatches; }
        public List<RecordTypeMismatch> getRecordTypeMismatches() { return recordTypeMismatches; }
        public List<UserMismatch> getUserMismatches() { return userMismatches; }
        public List<RecordTypeInfo> getTargetRecordTypes() { return targetRecordTypes; }
        public List<UserInfo> getTargetUsers() { return targetUsers; }
    }
    
    public static class PicklistMismatch {
        private final String fieldName;
        private final String sourceValue;
        private final List<String> targetOptions;
        
        public PicklistMismatch(String fieldName, String sourceValue, List<String> targetOptions) {
            this.fieldName = fieldName;
            this.sourceValue = sourceValue;
            this.targetOptions = targetOptions;
        }
        
        public String getFieldName() { return fieldName; }
        public String getSourceValue() { return sourceValue; }
        public List<String> getTargetOptions() { return targetOptions; }
    }
    
    public static class RecordTypeMismatch {
        private final String sourceRecordTypeId;
        private final List<RecordTypeInfo> targetOptions;
        
        public RecordTypeMismatch(String sourceRecordTypeId, List<RecordTypeInfo> targetOptions) {
            this.sourceRecordTypeId = sourceRecordTypeId;
            this.targetOptions = targetOptions;
        }
        
        public String getSourceRecordTypeId() { return sourceRecordTypeId; }
        public List<RecordTypeInfo> getTargetOptions() { return targetOptions; }
    }
    
    public static class UserMismatch {
        private final String sourceUserId;
        private final List<UserInfo> targetOptions;
        
        public UserMismatch(String sourceUserId, List<UserInfo> targetOptions) {
            this.sourceUserId = sourceUserId;
            this.targetOptions = targetOptions;
        }
        
        public String getSourceUserId() { return sourceUserId; }
        public List<UserInfo> getTargetOptions() { return targetOptions; }
    }
}
