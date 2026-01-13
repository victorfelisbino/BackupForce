# Restore Feature - Comprehensive TODO List

**Last Updated:** January 12, 2026  
**Purpose:** Complete implementation and testing of the restore feature in BackupForce

---

## 🎯 Executive Summary

The restore feature is **~60% complete** with the following status:
- ✅ **Core Infrastructure:** RestoreExecutor, DatabaseScanner, RelationshipManager - Complete
- ✅ **Basic UI:** RestoreController with source selection and object listing - Complete  
- ⚠️ **Restore Wizard:** UI created but missing critical backend integrations
- ❌ **Missing Classes:** RestorePreviewGenerator, SensitiveDataDetector, DataTypeValidator
- ❌ **Salesforce Integration:** No connection to target Salesforce org for restore
- ❌ **Folder Source:** Database scanning works, but folder scanning incomplete
- ❌ **Testing:** Limited unit tests, no integration or E2E tests

---

## 🔴 CRITICAL - Must Fix First

### 1. Create Missing Core Classes (Priority: CRITICAL)

Three classes are imported in RestoreController but don't exist:

#### 1.1 RestorePreviewGenerator.java
**Location:** `src/main/java/com/backupforce/restore/RestorePreviewGenerator.java`

**Purpose:** Generate preview summaries before restore execution

**Required Classes:**
```java
public class RestorePreviewGenerator {
    // Main preview for entire restore operation
    public static class RestorePreview {
        private long totalRecords;
        private long totalFileSize;
        private int estimatedApiCalls;
        private int estimatedTimeMinutes;
        private List<ObjectPreview> objectPreviews;
        // + getters/setters
    }
    
    // Preview for individual object
    public static class ObjectPreview {
        private String objectName;
        private long recordCount;
        private long fileSize;
        private String statusIcon; // ✅, ⚠️, ❌
        private List<String> warnings;
        private List<String> errors;
        // + getters/setters
    }
    
    // Static helper
    public static String formatSize(long bytes);
}
```

**Key Methods:**
- `generatePreview(List<RestoreObject> objects, RestoreOptions options)` - Main entry point
- `analyzeObject(RestoreObject obj)` - Analyze single object
- `estimateApiCalls(long recordCount, int batchSize)` - Calculate API usage
- `estimateTime(long recordCount)` - Estimate restore duration
- `detectWarnings(RestoreObject obj)` - Check for potential issues

**Integration Points:**
- Called from RestoreController.handleGeneratePreview()
- Used to populate preview panels in UI
- Validates data before restore starts

---

#### 1.2 SensitiveDataDetector.java
**Location:** `src/main/java/com/backupforce/restore/SensitiveDataDetector.java`

**Purpose:** Scan data for sensitive fields (PII, financial data, etc.)

**Required Classes:**
```java
public class SensitiveDataDetector {
    public static class SensitiveDataReport {
        private int totalFieldsScanned;
        private int sensitiveFieldsFound;
        private Map<String, List<String>> sensitiveFields; // objectName -> field names
        private List<SensitiveField> details;
        // + getters/setters
    }
    
    public static class SensitiveField {
        private String objectName;
        private String fieldName;
        private String fieldType;
        private SensitivityLevel level; // HIGH, MEDIUM, LOW
        private String reason; // Why it's sensitive
        // + getters/setters
    }
    
    public enum SensitivityLevel { HIGH, MEDIUM, LOW }
}
```

**Detection Rules:**
- **HIGH:** SSN, Credit Card, Password, API Key fields
- **MEDIUM:** Email, Phone, Address, DOB fields  
- **LOW:** Name, Department, Title fields

**Key Methods:**
- `scanObjects(List<RestoreObject> objects)` - Scan all objects
- `scanFields(String objectName, List<String> fieldNames)` - Scan specific fields
- `isSensitiveField(String fieldName)` - Check if field is sensitive
- `getSensitivityLevel(String fieldName)` - Determine sensitivity level

**Integration Points:**
- Called from RestoreController when objects are selected
- Results shown in sensitive data panel
- User must acknowledge before restore if sensitive data found

---

#### 1.3 DataTypeValidator.java
**Location:** `src/main/java/com/backupforce/restore/DataTypeValidator.java`

**Purpose:** Validate data types and field compatibility

**Required Classes:**
```java
public class DataTypeValidator {
    public static class TypeValidationResult {
        private boolean valid;
        private int totalFields;
        private int validFields;
        private int invalidFields;
        private List<FieldTypeIssue> issues;
        // + getters/setters
    }
    
    public static class FieldTypeIssue {
        private String objectName;
        private String fieldName;
        private String sourceType;
        private String targetType;
        private String issue; // Description of the problem
        private IssueSeverity severity; // ERROR, WARNING, INFO
        // + getters/setters
    }
    
    public enum IssueSeverity { ERROR, WARNING, INFO }
}
```

**Validation Rules:**
- Field exists in target org
- Data type compatibility (Text -> Text, Number -> Number, etc.)
- Field length validation
- Required field checks
- Picklist value validation

**Key Methods:**
- `validateFields(String objectName, Map<String, String> sourceFields, ObjectMetadata targetMetadata)` - Validate all fields
- `validateFieldType(String fieldName, String sourceType, String targetType)` - Validate single field
- `validateFieldLength(String value, int maxLength)` - Check length constraints
- `validatePicklistValue(String value, List<String> validValues)` - Validate picklist

**Integration Points:**
- Called from RestoreController.handleValidateFields()
- Results shown in type validation panel
- Blocks restore if ERROR-level issues found

---

### 2. Complete RestoreWizardController Implementation (Priority: CRITICAL)

The wizard UI exists but has several TODOs and incomplete implementations:

#### 2.1 Implement Folder Scanning (Step 1)
**File:** RestoreWizardController.java, line ~663

**Current:** `// TODO: Implement folder scanning`

**Implementation:**
```java
private void loadBackupObjectsFromFolder() {
    Task<List<BackupObject>> task = new Task<>() {
        @Override
        protected List<BackupObject> call() throws Exception {
            List<BackupObject> objects = new ArrayList<>();
            File[] csvFiles = selectedFolder.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".csv"));
            
            if (csvFiles != null) {
                for (File csvFile : csvFiles) {
                    String objectName = extractObjectName(csvFile.getName());
                    long recordCount = countRecordsInCsv(csvFile);
                    String schema = readCsvSchema(csvFile);
                    
                    objects.add(new BackupObject(
                        csvFile.getName(), objectName, recordCount, schema
                    ));
                }
            }
            return objects;
        }
    };
    // ... set up callbacks and execute
}
```

**Dependencies:**
- CSV parsing utility methods
- Schema detection from CSV headers
- Record counting without loading entire file

---

#### 2.2 Implement Salesforce Connection (Step 5)
**File:** RestoreWizardController.java, line ~1209

**Current:** `// TODO: Open Salesforce login`

**Implementation:**
```java
@FXML
private void handleConnectOrg() {
    try {
        // Open Salesforce OAuth login
        FXMLLoader loader = new FXMLLoader(getClass()
            .getResource("/fxml/login-content.fxml"));
        Parent loginPane = loader.load();
        LoginController loginController = loader.getController();
        
        // Set callback to receive connection
        loginController.setOnConnectionSuccess(connInfo -> {
            this.connectionInfo = connInfo;
            orgStatusLabel.setText("✓ Connected: " + connInfo.username);
            orgConnectBtn.setDisable(true);
            updateNavigation();
        });
        
        // Show in dialog or replace step5Content
        // ... dialog setup
        
    } catch (IOException e) {
        logger.error("Failed to open login", e);
        showError("Failed to open Salesforce login: " + e.getMessage());
    }
}
```

**Dependencies:**
- LoginController integration
- OAuth flow completion
- Connection info storage

---

#### 2.3 Implement Actual Restore Execution (Step 5)
**File:** RestoreWizardController.java, line ~1214

**Current:** `// TODO: Implement actual restore` (just simulation)

**Implementation:**
```java
private void startRestore() {
    if (connectionInfo == null) {
        showError("Please connect to Salesforce first");
        return;
    }
    
    progressPanel.setVisible(true);
    nextBtn.setDisable(true);
    
    Task<Void> restoreTask = new Task<>() {
        @Override
        protected Void call() throws Exception {
            logArea.appendText("Starting restore...\n");
            
            // Create RestoreExecutor
            RestoreExecutor executor = new RestoreExecutor(
                connectionInfo.instanceUrl,
                connectionInfo.accessToken,
                connectionInfo.apiVersion
            );
            
            // Set up callbacks
            executor.setProgressCallback(progress -> {
                Platform.runLater(() -> {
                    progressBar.setProgress(progress.getProgressPercent() / 100.0);
                    progressPercentLabel.setText(progress.getProgressPercent() + "%");
                    progressStatusLabel.setText(progress.getCurrentObject());
                });
            });
            
            executor.setLogCallback(msg -> {
                Platform.runLater(() -> logArea.appendText(msg + "\n"));
            });
            
            // Build restore options
            RestoreOptions options = buildRestoreOptions();
            
            // Execute restore for each selected object
            List<RecordRow> selectedRecords = getSelectedRecords();
            // Group by object
            Map<String, List<RecordRow>> recordsByObject = groupByObject(selectedRecords);
            
            for (Map.Entry<String, List<RecordRow>> entry : recordsByObject.entrySet()) {
                String objectName = entry.getKey();
                List<RecordRow> records = entry.getValue();
                
                updateMessage("Restoring " + objectName + "...");
                
                // Convert to CSV or direct insert
                if (sourceType == SourceType.DATABASE) {
                    Path tempCsv = exportRecordsToCsv(records);
                    RestoreResult result = executor.restoreFromCsv(
                        objectName, tempCsv, getRestoreMode(), options
                    );
                    handleRestoreResult(result);
                } else {
                    // Handle folder source
                }
            }
            
            return null;
        }
    };
    
    restoreTask.setOnSucceeded(e -> {
        logArea.appendText("\n✅ Restore completed successfully!\n");
        progressStatusLabel.setText("Restore complete");
        // Enable "Finish" button
    });
    
    restoreTask.setOnFailed(e -> {
        Throwable ex = restoreTask.getException();
        logger.error("Restore failed", ex);
        logArea.appendText("\n❌ Restore failed: " + ex.getMessage() + "\n");
        showError("Restore failed: " + ex.getMessage());
    });
    
    new Thread(restoreTask).start();
}
```

**Dependencies:**
- ConnectionInfo from Salesforce login
- RestoreExecutor integration
- CSV export for database records
- Progress tracking and error handling

---

#### 2.4 Implement Value Translation Dialog (Step 4)
**File:** RestoreWizardController.java, line ~1176

**Current:** `// TODO: Open value translation dialog`

**Implementation:**
```java
@FXML
private void handleManageTransformations() {
    try {
        FXMLLoader loader = new FXMLLoader(getClass()
            .getResource("/fxml/value-translation-dialog.fxml"));
        Parent root = loader.load();
        // ValueTranslationController controller = loader.getController();
        
        Stage dialog = new Stage();
        dialog.setTitle("Manage Value Translations");
        dialog.setScene(new Scene(root));
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.showAndWait();
        
    } catch (IOException e) {
        logger.error("Failed to open value translation dialog", e);
        showError("Failed to open value translation: " + e.getMessage());
    }
}
```

**Note:** value-translation-dialog.fxml exists but may need controller

---

### 3. Fix Database Issues (Priority: HIGH)

#### 3.1 Fix Duplicate Method in JdbcDatabaseSink
**File:** `src/main/java/com/backupforce/sink/JdbcDatabaseSink.java`

**Error:** Duplicate method `listBackedUpTables()` at lines 736 and 916

**Fix:** Remove one of the duplicate methods or merge their implementations

---

## 🟡 HIGH PRIORITY - Complete Core Features

### 4. Implement Salesforce Connection for Restore

The restore feature needs to connect to a target Salesforce org, but currently:
- RestoreController has `connectionInfo` field but no way to set it
- RestoreWizardController needs Salesforce login integration
- No OAuth flow for restore (backup uses LoginController)

**Tasks:**
- [ ] Add "Connect to Salesforce" button in RestoreController
- [ ] Integrate LoginController for OAuth in restore wizard
- [ ] Store connection info for restore execution
- [ ] Add connection status indicator
- [ ] Handle session expiration during restore

**Files to Modify:**
- RestoreController.java - Add connect button and handler
- RestoreWizardController.java - Implement handleConnectOrg()
- restore-content.fxml - Add connection UI section
- restore-wizard.fxml - Add connection status in Step 5

---

### 5. Complete Folder Source Support

Currently, database source scanning works, but folder scanning is incomplete:

#### 5.1 CSV File Scanning
**Status:** Partial implementation in RestoreController

**Missing:**
- [ ] Scan folder recursively for all CSV files
- [ ] Parse CSV headers to detect fields
- [ ] Count records without loading entire file
- [ ] Detect backup manifest (_backup_manifest.json)
- [ ] Validate CSV structure

**Implementation:**
```java
private void scanFolderSource() {
    File folder = new File(restoreSourceField.getText());
    if (!folder.exists() || !folder.isDirectory()) {
        showError("Invalid folder path");
        return;
    }
    
    Task<List<RestoreObject>> scanTask = new Task<>() {
        @Override
        protected List<RestoreObject> call() throws Exception {
            List<RestoreObject> objects = new ArrayList<>();
            
            // Look for manifest first
            File manifestFile = new File(folder, "_backup_manifest.json");
            if (manifestFile.exists()) {
                loadedManifest = BackupManifestLoader.loadManifest(manifestFile.toPath());
                loadedIdMapping = BackupManifestLoader.loadIdMapping(folder.toPath());
            }
            
            // Scan for CSV files
            File[] csvFiles = folder.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".csv"));
            
            if (csvFiles != null) {
                for (File csvFile : csvFiles) {
                    updateMessage("Scanning " + csvFile.getName());
                    
                    String objectName = extractObjectName(csvFile.getName());
                    long recordCount = countCsvRecords(csvFile);
                    long fileSize = csvFile.length();
                    String lastModified = formatDate(csvFile.lastModified());
                    
                    RestoreObject obj = new RestoreObject(
                        objectName, recordCount, lastModified, 
                        csvFile.getAbsolutePath()
                    );
                    obj.setFileSize(formatFileSize(fileSize));
                    
                    objects.add(obj);
                }
            }
            
            return objects;
        }
    };
    
    // ... execute task with progress UI
}

private long countCsvRecords(File csvFile) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
        return reader.lines().count() - 1; // Exclude header
    }
}

private String extractObjectName(String fileName) {
    // Remove .csv extension
    String name = fileName.substring(0, fileName.lastIndexOf('.'));
    // Handle common patterns: Account.csv, sf_Account.csv, etc.
    if (name.startsWith("sf_")) {
        name = name.substring(3);
    }
    return name;
}
```

---

#### 5.2 Manifest Integration
**Status:** Partially implemented

**Missing:**
- [ ] Load and parse _backup_manifest.json
- [ ] Load ID mapping from _id_mapping.json
- [ ] Use manifest restore order
- [ ] Apply recommended upsert fields
- [ ] Handle relationship metadata

**Files:**
- BackupManifestLoader.java (exists, verify completeness)
- RestoreController.java - updateManifestPanel() (implemented but unused)

---

### 6. Complete Relationship Resolution

The infrastructure exists (RelationshipResolver, RelationshipManager) but needs UI integration:

#### 6.1 Relationship Preview Dialog
**Status:** Dialog exists but not integrated with restore wizard

**Tasks:**
- [ ] Open relationship preview from Step 3
- [ ] Load related records from database
- [ ] Build relationship tree
- [ ] Allow user to select which relationships to restore
- [ ] Store selections for restore execution

**Implementation in RestoreWizardController:**
```java
private void loadRelationships(List<RecordRow> selectedRecords) {
    // Group records by object
    Map<String, List<RecordRow>> byObject = groupByObject(selectedRecords);
    
    // For each object, query child relationships from database
    for (Map.Entry<String, List<RecordRow>> entry : byObject.entrySet()) {
        String objectName = entry.getKey();
        List<RecordRow> records = entry.getValue();
        
        // Query child records (e.g., Contacts for Account)
        List<String> ids = records.stream()
            .map(r -> r.getData().get("Id"))
            .collect(Collectors.toList());
        
        // Find related tables in database
        List<RelatedTable> related = findRelatedTables(objectName, ids);
        
        // Add to tree
        addToRelationshipTree(objectName, related);
    }
}

private List<RelatedTable> findRelatedTables(String parentObject, List<String> parentIds) {
    // Query database for tables with foreign keys to parent
    // Example: SELECT * FROM Contact WHERE AccountId IN (...)
    List<RelatedTable> related = new ArrayList<>();
    
    // Get all tables from database
    for (BackupObject table : backupObjects) {
        // Check if table has reference field to parent
        String refField = table.getSchema().findReferenceField(parentObject);
        if (refField != null) {
            // Query related records
            List<Map<String, Object>> relatedRecords = 
                queryRelatedRecords(table, refField, parentIds);
            
            related.add(new RelatedTable(table.getObjectName(), 
                refField, relatedRecords.size()));
        }
    }
    
    return related;
}
```

---

#### 6.2 Dependency Ordering
**Status:** DependencyOrderer class exists

**Tasks:**
- [ ] Integrate with restore execution
- [ ] Sort objects by dependency before restore
- [ ] Handle circular dependencies
- [ ] Show restore order in UI

---

### 7. Complete Field Mapping Step (Step 4)

The wizard has a field mapping step but it's not functional:

**Tasks:**
- [ ] Load source fields from database/CSV
- [ ] Load target fields from Salesforce metadata
- [ ] Auto-map matching fields
- [ ] Allow manual field mapping
- [ ] Support field transformations
- [ ] Validate mappings before restore

**Implementation:**
```java
private void prepareFieldMappings() {
    List<RecordRow> selected = getSelectedRecords();
    if (selected.isEmpty()) return;
    
    // Get first selected object
    String objectName = selected.get(0).getObjectName();
    
    // Load source fields
    Set<String> sourceFields = getSourceFields(objectName);
    
    // Load target fields from Salesforce
    Task<ObjectMetadata> task = new Task<>() {
        @Override
        protected ObjectMetadata call() throws Exception {
            RelationshipManager rm = new RelationshipManager(
                connectionInfo.instanceUrl,
                connectionInfo.accessToken,
                connectionInfo.apiVersion
            );
            return rm.describeObject(objectName);
        }
    };
    
    task.setOnSucceeded(e -> {
        ObjectMetadata metadata = task.getValue();
        List<FieldInfo> targetFields = metadata.getFields();
        
        // Auto-map matching fields
        List<FieldMapping> mappings = new ArrayList<>();
        for (String sourceField : sourceFields) {
            FieldInfo targetField = findMatchingField(sourceField, targetFields);
            if (targetField != null) {
                mappings.add(new FieldMapping(
                    sourceField, 
                    targetField.getName(),
                    "None", // No transformation
                    getSampleValue(sourceField)
                ));
            }
        }
        
        fieldMappingsData.setAll(mappings);
    });
    
    new Thread(task).start();
}
```

---

### 8. Complete Transformation Integration

TransformationConfig class exists but needs UI integration:

**Tasks:**
- [ ] Load transformation rules
- [ ] Apply transformations during restore
- [ ] Support record type mapping
- [ ] Support owner mapping
- [ ] Support value translations
- [ ] Show transformation status in UI

**Files:**
- TransformationConfig.java (exists)
- DataTransformer.java (exists)
- RestoreController.java - Wire up transformation loading/config

---

## 🟢 MEDIUM PRIORITY - Enhancements

### 9. Complete Restore Execution Features

#### 9.1 Dry Run Mode
**Status:** UI checkbox exists but not implemented

**Tasks:**
- [ ] Pass dryRun flag to RestoreExecutor
- [ ] Validate data without inserting
- [ ] Show preview of what would be restored
- [ ] Report validation errors

---

#### 9.2 Record Limit
**Status:** UI field exists but not used

**Tasks:**
- [ ] Apply record limit during restore
- [ ] Show warning if limit applied
- [ ] Update preview with limited count

---

#### 9.3 Business Data Only
**Status:** Checkbox exists but not implemented

**Tasks:**
- [ ] Filter out system fields (CreatedBy, LastModifiedBy, etc.)
- [ ] Remove audit fields
- [ ] Keep only business-relevant data

---

### 10. Add Error Handling and Validation

#### 10.1 Pre-Restore Validation
**Tasks:**
- [ ] Validate connection before restore
- [ ] Check API limits
- [ ] Validate field mappings
- [ ] Check for required fields
- [ ] Validate data types
- [ ] Verify external ID fields exist

---

#### 10.2 During-Restore Error Handling
**Tasks:**
- [ ] Capture and log API errors
- [ ] Track failed records
- [ ] Support "stop on error" option
- [ ] Support retry logic
- [ ] Generate error report

---

#### 10.3 Post-Restore Reporting
**Tasks:**
- [ ] Summary report (success/failed counts)
- [ ] Export failed records to CSV
- [ ] Show ID mappings (old ID -> new ID)
- [ ] Log file generation

---

### 11. Complete Preview Functionality

Once RestorePreviewGenerator is created:

**Tasks:**
- [ ] Generate preview on object selection
- [ ] Show estimated API calls
- [ ] Show estimated time
- [ ] Display warnings (large files, many records, etc.)
- [ ] Refresh preview when options change
- [ ] Preview relationship resolution

---

## 🧪 TESTING - Critical for Release

### 12. Write Integration Tests

**Test Scenarios:**

#### 12.1 Database Source Tests
```java
@Test
void testScanSnowflakeDatabase() {
    SavedConnection conn = createTestConnection("Snowflake");
    DatabaseScanner scanner = new DatabaseScanner(conn);
    List<BackupTable> tables = scanner.scanForBackupTables();
    
    assertFalse(tables.isEmpty());
    assertTrue(tables.stream().anyMatch(t -> t.getObjectName().equals("Account")));
}

@Test
void testQueryRecordsFromDatabase() {
    // Test querying records with WHERE clause
}
```

---

#### 12.2 CSV Source Tests
```java
@Test
void testScanCsvFolder() {
    Path testFolder = createTestCsvFiles();
    // Scan folder and verify objects found
}

@Test
void testParseCsvHeaders() {
    // Test CSV parsing and field detection
}

@Test
void testCountCsvRecords() {
    // Test record counting without loading entire file
}
```

---

#### 12.3 Restore Execution Tests
```java
@Test
void testRestoreFromCsv() {
    // Create test CSV
    // Execute restore to test org
    // Verify records created
}

@Test
void testRestoreWithRelationships() {
    // Test parent-child restore
    // Verify relationships preserved
}

@Test
void testUpsertMode() {
    // Test upsert with external ID
}

@Test
void testPreserveIds() {
    // Test preserve IDs option
}
```

---

#### 12.4 Validation Tests
```java
@Test
void testSensitiveDataDetection() {
    // Test sensitive field detection
}

@Test
void testDataTypeValidation() {
    // Test type compatibility checks
}

@Test
void testPreviewGeneration() {
    // Test preview calculation
}
```

---

### 13. Write UI Tests

**Test Scenarios:**

#### 13.1 Restore Wizard Flow
```java
@Test
void testWizardStepNavigation() {
    // Test navigating through all 5 steps
}

@Test
void testDatabaseSourceSelection() {
    // Test selecting database connection
    // Verify objects loaded
}

@Test
void testRecordSearch() {
    // Test searching records with WHERE clause
}

@Test
void testRecordSelection() {
    // Test selecting multiple records
}

@Test
void testFieldMapping() {
    // Test field mapping UI
}

@Test
void testRestoreExecution() {
    // Test restore button and progress
}
```

---

#### 13.2 RestoreController Tests
```java
@Test
void testSourceToggle() {
    // Test switching between folder/database source
}

@Test
void testObjectSearch() {
    // Test filtering objects
}

@Test
void testRestoreModeSelection() {
    // Test insert/update/upsert modes
}

@Test
void testValidationPanels() {
    // Test sensitive data panel
    // Test type validation panel
}
```

---

### 14. Complete Documentation

#### 14.1 Code Documentation
**Tasks:**
- [ ] Add JavaDoc to all public methods
- [ ] Document class purposes
- [ ] Add usage examples
- [ ] Document error handling

---

#### 14.2 User Documentation
**Tasks:**
- [ ] Write restore feature guide
- [ ] Document restore wizard steps
- [ ] Create troubleshooting guide
- [ ] Add screenshots

**Location:** `docs/RESTORE_GUIDE.md`

---

#### 14.3 Developer Documentation
**Tasks:**
- [ ] Document restore architecture
- [ ] Explain relationship resolution
- [ ] Document transformation system
- [ ] Add sequence diagrams

**Location:** `docs/RESTORE_ARCHITECTURE.md`

---

## 🚀 END-TO-END TESTING

### 15. Complete E2E Test Scenarios

Before considering the restore feature "complete":

#### 15.1 Basic Restore Scenarios
- [ ] Restore Accounts from CSV folder
- [ ] Restore Contacts from database
- [ ] Restore with insert mode
- [ ] Restore with upsert mode
- [ ] Restore with update mode

---

#### 15.2 Advanced Scenarios
- [ ] Restore parent-child relationships (Account -> Contact)
- [ ] Restore with preserve IDs
- [ ] Restore with field transformations
- [ ] Restore with record type mapping
- [ ] Restore with owner mapping

---

#### 15.3 Error Scenarios
- [ ] Restore with invalid credentials
- [ ] Restore with missing required fields
- [ ] Restore with type mismatches
- [ ] Restore with API limit exceeded
- [ ] Restore with network failure

---

#### 15.4 Performance Testing
- [ ] Restore 10,000 records
- [ ] Restore 100,000 records
- [ ] Restore multiple objects simultaneously
- [ ] Measure API call efficiency

---

## 📊 Progress Tracking

### Overall Completion: ~60%

| Component | Status | % Complete |
|-----------|--------|------------|
| Core Infrastructure | ✅ Complete | 100% |
| Database Scanner | ✅ Complete | 100% |
| Relationship Manager | ✅ Complete | 100% |
| RestoreExecutor | ✅ Complete | 95% |
| RestoreController UI | ⚠️ Partial | 70% |
| RestoreWizard UI | ⚠️ Partial | 60% |
| Missing Classes | ❌ Not Started | 0% |
| Salesforce Connection | ❌ Not Started | 0% |
| Folder Source | ⚠️ Partial | 30% |
| Field Mapping | ⚠️ Partial | 20% |
| Transformation | ⚠️ Partial | 50% |
| Testing | ⚠️ Partial | 30% |
| Documentation | ⚠️ Partial | 40% |

---

## 🎯 Recommended Implementation Order

### Phase 1: Critical Fixes (Week 1)
1. Create RestorePreviewGenerator.java
2. Create SensitiveDataDetector.java
3. Create DataTypeValidator.java
4. Fix JdbcDatabaseSink duplicate method
5. Implement Salesforce connection in wizard

### Phase 2: Core Functionality (Week 2)
6. Complete folder scanning
7. Implement actual restore execution in wizard
8. Integrate field mapping
9. Complete relationship resolution UI
10. Add error handling

### Phase 3: Testing (Week 3)
11. Write unit tests for new classes
12. Write integration tests
13. Write UI tests
14. Perform E2E testing

### Phase 4: Polish (Week 4)
15. Complete documentation
16. Add remaining features (dry run, record limit, etc.)
17. Performance testing
18. Bug fixes and refinement

---

## 📝 Notes

### Known Issues
1. Duplicate method in JdbcDatabaseSink.java (line 736 & 916)
2. CSS warnings in backupforce-modern.css (non-critical)
3. RestoreWizard has TODO placeholders for core functionality
4. No integration between wizard and actual restore execution

### Dependencies
- All restore operations require Salesforce connection
- Relationship resolution requires metadata from target org
- Field mapping requires both source and target schemas
- Transformation requires TransformationConfig

### Future Enhancements (Post-MVP)
- Batch scheduling for restore jobs
- Multi-org restore support
- Incremental restore (delta changes only)
- Restore from cloud storage (S3, Azure Blob)
- Advanced transformation rules (formulas, lookups)
- Restore preview with data samples
- Rollback capability

---

## 🤝 Contributing

When working on these tasks:
1. Create a feature branch for each major task
2. Write tests before implementation (TDD)
3. Update this TODO list as tasks are completed
4. Document your changes in CHANGELOG.md
5. Test thoroughly before marking as complete

---

**Last Updated:** January 12, 2026  
**Next Review:** After Phase 1 completion
