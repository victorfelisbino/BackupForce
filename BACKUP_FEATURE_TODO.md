# Backup Feature - Comprehensive TODO List

**Last Updated:** January 12, 2026  
**Purpose:** Complete implementation, testing, and polish of the backup feature in BackupForce

---

## 🎯 Executive Summary

The backup feature is **~85% complete** with the following status:
- ✅ **Core Infrastructure:** BulkV2Client, data sinks (CSV, database), backup execution - Complete
- ✅ **UI Framework:** BackupController with object selection, filtering, progress tracking - Complete
- ✅ **Relationship-Aware Backup:** ChildRelationshipAnalyzer, BackupManifestGenerator - Complete
- ✅ **Scheduled Backups:** BackupSchedulerService, ScheduleManager, Schedule UI - Complete
- ✅ **Verification:** BackupVerifier with integrity checks - Complete
- ⚠️ **Database Export:** Basic JDBC sinks work, but has duplicate method bug
- ⚠️ **Testing:** Good unit test coverage, missing integration tests
- ⚠️ **Polish:** Minor TODOs, edge cases, user experience improvements

---

## 🔴 CRITICAL - Must Fix First

### 1. Fix Duplicate Method in JdbcDatabaseSink (Priority: CRITICAL)

**File:** `src/main/java/com/backupforce/sink/JdbcDatabaseSink.java`

**Error:** Duplicate method `listBackedUpTables()` at lines 736 and 916

**Issue:** Compile error preventing builds

**Fix Strategy:**
1. Compare both implementations
2. Determine which is the correct/complete version
3. Remove the duplicate
4. Verify all callers work correctly

**Expected Outcome:** Clean compilation, no errors

---

## 🟡 HIGH PRIORITY - Complete Core Features

### 2. Resolve Remaining TODO in BackupController

**File:** `src/main/java/com/backupforce/ui/BackupController.java`, line 1061

**Current:** `// TODO: Update UI based on backup type (data, config, metadata)`

**Context:** This appears to be related to different backup modes (data vs metadata vs config)

**Implementation Needed:**
- [ ] Clarify if there are different backup types planned
- [ ] If yes, implement UI changes based on backup type selection
- [ ] If no, remove the TODO comment

**Related Questions:**
- Does the app support metadata backup (Apex, Workflows, etc.)?
- Does it support configuration backup (settings, permissions)?
- Or is this TODO obsolete and only data backup is supported?

---

### 3. Improve Backup Verification Integration

**Status:** BackupVerifier exists and works well, but could be better integrated

**Current Implementation:**
- BackupVerifier can verify backups after completion
- Manual invocation required
- Results shown in separate report

**Enhancements Needed:**

#### 3.1 Auto-Verification After Backup
```java
// In BackupController, after backup completes:
private void onBackupComplete() {
    if (autoVerifyCheck != null && autoVerifyCheck.isSelected()) {
        runVerification(lastBackupId);
    }
}
```

**Tasks:**
- [ ] Add "Auto-verify after backup" checkbox in UI
- [ ] Wire up automatic verification trigger
- [ ] Show verification results in backup completion dialog
- [ ] Store verification status in BackupHistory

---

#### 3.2 Verification Progress Indicator
**Current:** Verification runs silently

**Needed:**
- [ ] Add progress bar for verification
- [ ] Show current object being verified
- [ ] Display verification status (checking counts, checksums, etc.)

---

#### 3.3 Quick Verification Action
**Current:** Must go through multiple clicks to verify

**Needed:**
- [ ] Add "Verify" button to backup history rows
- [ ] Quick verification dialog with progress
- [ ] Display key metrics (count matches, integrity status, missing objects)

---

### 4. Enhance Large Object Handling

**Issue:** Objects with binary data (Attachment, ContentVersion) can cause memory issues

**Current Implementation:**
- Objects marked as "LARGE_OBJECTS" in BackupController
- Warning shown to user
- No automatic handling

**Enhancements Needed:**

#### 4.1 Automatic Batch Size Reduction
```java
private int getBatchSizeForObject(String objectName) {
    if (LARGE_OBJECTS.contains(objectName)) {
        return 50; // Smaller batches for large objects
    }
    return userSelectedBatchSize;
}
```

**Tasks:**
- [ ] Implement automatic batch size reduction for large objects
- [ ] Show notification: "Using smaller batches for Attachment (contains binary data)"
- [ ] Add preference for large object batch size

---

#### 4.2 Blob Download Options
**Current:** Binary fields (e.g., Attachment.Body) downloaded inline

**Options to Implement:**
- [ ] **Option 1:** Skip blob fields by default, download separately if requested
- [ ] **Option 2:** Save blobs to separate files (e.g., `Attachment/[Id].blob`)
- [ ] **Option 3:** Store blob references only (URL to fetch later)

**UI Changes:**
- Add checkbox: "Include binary data (slower, uses more storage)"
- Add checkbox: "Save blobs as separate files"

---

### 5. Database Export Enhancements

**Status:** Database export works but needs polish

#### 5.1 Connection Testing
**Current:** Connection errors only appear during backup

**Needed:**
- [ ] Add "Test Connection" button in database settings
- [ ] Validate connection before starting backup
- [ ] Show friendly error messages for common issues

---

#### 5.2 Schema Migration Support
**Current:** Tables created with schema inference, no versioning

**Needed:**
- [ ] Track schema version in database
- [ ] Handle schema changes gracefully (add columns, not drop)
- [ ] Detect schema conflicts before backup
- [ ] Option to recreate tables if schema changed

**Implementation:**
```java
// Create a schema_version table
CREATE TABLE _backup_schema_version (
    object_name VARCHAR(255) PRIMARY KEY,
    schema_hash VARCHAR(64),
    last_updated TIMESTAMP,
    column_list TEXT
)

// Before backing up object:
String currentSchema = getSchemaHash(objectName);
String storedSchema = getStoredSchema(objectName);
if (!currentSchema.equals(storedSchema)) {
    handleSchemaChange(objectName, currentSchema);
}
```

---

#### 5.3 Incremental Database Backup
**Current:** Full export every time

**Needed:**
- [ ] Track last backup timestamp per object
- [ ] Add WHERE clause: `LastModifiedDate > @lastBackupDate`
- [ ] Merge/upsert instead of truncate/insert
- [ ] UI option: "Incremental backup (only changed records)"

---

### 6. Relationship-Aware Backup Enhancements

**Status:** Core functionality works well, needs user experience polish

#### 6.1 Better Preview Dialog
**Current:** Relationship preview shows tree, but estimations are rough

**Enhancements:**
- [ ] Show actual record counts for related objects (query Salesforce)
- [ ] Show total estimated backup size
- [ ] Show estimated API calls
- [ ] Show estimated time
- [ ] Allow editing of included relationships

---

#### 6.2 Relationship Depth Warning
**Current:** User can set depth up to 5, which could be huge

**Needed:**
- [ ] Warn if depth > 2: "This may include many objects and take a long time"
- [ ] Show count of objects that will be included at each depth
- [ ] Option to preview relationships before starting backup

---

#### 6.3 Relationship Backup Progress
**Current:** Progress shows "Backing up Contact" but doesn't indicate it's a related object

**Needed:**
- [ ] Show relationship hierarchy in progress:
  ```
  ✓ Account (100 records)
    → Contact (250 records) [Parent: Account]
    → Opportunity (80 records) [Parent: Account]
      → OpportunityLineItem (320 records) [Parent: Opportunity]
  ```

---

### 7. Scheduled Backup Enhancements

**Status:** Scheduler works well, needs monitoring and notifications

#### 7.1 Backup Execution Integration
**Current:** Scheduler triggers backup, but integration could be better

**Tasks:**
- [ ] Ensure scheduled backups use correct Salesforce connection
- [ ] Handle expired sessions gracefully (re-login?)
- [ ] Run in background without UI
- [ ] Generate execution logs

---

#### 7.2 Email Notifications
**Current:** Settings exist but not implemented

**Implementation Needed:**
```java
public class EmailNotificationService {
    public void sendBackupSuccessEmail(String to, BackupRun run) {
        // Email details:
        // - Backup name
        // - Objects backed up
        // - Record counts
        // - Time taken
        // - Output location
    }
    
    public void sendBackupFailureEmail(String to, BackupRun run, Exception error) {
        // Email details:
        // - Backup name
        // - Error message
        // - Objects that succeeded
        // - Objects that failed
        // - Log file location
    }
}
```

**Tasks:**
- [ ] Create EmailNotificationService
- [ ] Configure SMTP settings (user preferences)
- [ ] Send notifications based on schedule settings
- [ ] Include backup summary and key metrics

---

#### 7.3 Schedule History View
**Current:** Only shows last run status, no history

**Needed:**
- [ ] Store history of scheduled backup runs
- [ ] Show table of past executions (timestamp, status, records backed up)
- [ ] Link to backup verification results
- [ ] Option to view logs for each run

---

#### 7.4 Scheduler Dashboard
**Needed:**
- [ ] Summary view: X schedules enabled, Y ran today, Z failed
- [ ] Calendar view showing upcoming backups
- [ ] Next 7 days of scheduled backups
- [ ] Quick actions: Run now, Disable all, View logs

---

## 🟢 MEDIUM PRIORITY - Enhancements & Polish

### 8. Backup History Improvements

**Status:** BackupHistory tracks backups, but UI could be better

#### 8.1 Enhanced History View
**Current:** Basic history available in code, minimal UI

**Needed:**
- [ ] Dedicated "History" tab or screen
- [ ] Table showing all backup runs
- [ ] Columns: Date, Objects, Records, Duration, Status, Size
- [ ] Filter by date range, status, object
- [ ] Sort by any column

---

#### 8.2 Backup Comparison
**Needed:**
- [ ] Select two backup runs and compare
- [ ] Show differences in record counts
- [ ] Highlight new/removed objects
- [ ] Identify significant changes (e.g., 50% increase in records)

---

#### 8.3 Backup Restoration
**Current:** Separate restore feature, no direct link from history

**Integration:**
- [ ] Add "Restore this backup" button in history
- [ ] Pre-populate restore wizard with backup location
- [ ] Auto-load manifest if available
- [ ] One-click restore from history

---

### 9. Object Selection Improvements

**Status:** Object selection works well, minor improvements possible

#### 9.1 Smart Object Selection
**Current:** User must manually select all objects

**Enhancements:**
- [ ] "Select All Data Objects" (exclude metadata, logs, etc.)
- [ ] "Select Common Objects" (Account, Contact, Lead, Opportunity, etc.)
- [ ] "Select Objects with Data" (query counts, only show non-empty)
- [ ] Save selection as template for reuse

---

#### 9.2 Object Search Enhancements
**Current:** Basic text filter

**Needed:**
- [ ] Search by object type (Standard, Custom, Managed Package)
- [ ] Search by prefix (e.g., "My_" for all custom objects with prefix)
- [ ] Multi-column search (name, label, API name)
- [ ] Recent/favorite objects

---

#### 9.3 Object Info Tooltips
**Needed:**
- [ ] Hover over object to see:
  - Description
  - Record count
  - Last modified date
  - Key relationships
  - Backup history (last backed up, frequency)

---

### 10. Progress Monitoring Enhancements

**Status:** Progress bar and logs work well, could add more detail

#### 10.1 Real-Time Metrics
**Current:** Shows current object and progress percentage

**Add:**
- [ ] Records per second
- [ ] Estimated time remaining
- [ ] API calls used / remaining
- [ ] Data transferred (MB/GB)
- [ ] Average object completion time

---

#### 10.2 Pause/Resume Backup
**Current:** Can only stop (cancel) backup

**Needed:**
- [ ] Pause button to temporarily halt backup
- [ ] Resume from where it left off
- [ ] Save state to disk (in case app crashes)

---

#### 10.3 Background Backup
**Needed:**
- [ ] Option to minimize backup to system tray
- [ ] Continue backup in background
- [ ] Show notification when complete
- [ ] Option to close app and continue backup (daemon mode?)

---

### 11. Export Format Options

**Status:** CSV and database export work

#### 11.1 Compression Support
**Current:** Compression option exists in schedule but not fully implemented

**Implementation:**
```java
public class CompressionHelper {
    public static void compressBackup(Path backupFolder) {
        // Create ZIP archive
        // Include all CSV files
        // Include manifest
        // Optionally include logs
    }
}
```

**Tasks:**
- [ ] Implement ZIP compression after backup
- [ ] Option for GZIP per file vs single ZIP
- [ ] UI checkbox: "Compress backups"
- [ ] Show size savings in UI

---

#### 11.2 JSON Export Option
**Needed:**
- [ ] Export as JSON instead of CSV
- [ ] Nested structure for relationships
- [ ] More accurate type preservation
- [ ] Better for programmatic consumption

---

#### 11.3 Parquet Export
**Advanced Option:**
- [ ] Export to Parquet format (columnar, compressed)
- [ ] Better for large datasets
- [ ] Compatible with data analysis tools (Spark, Pandas)

---

### 12. Backup Encryption

**Status:** Not implemented, needed for security

#### 12.1 Encryption at Rest
**Implementation:**
```java
public class BackupEncryption {
    // Use AES-256 encryption
    // Password-based or key-based
    // Encrypt CSV files or ZIP archive
    
    public void encryptBackup(Path backupPath, String password);
    public void decryptBackup(Path backupPath, String password);
}
```

**Tasks:**
- [ ] Implement AES-256 encryption
- [ ] Password dialog before backup
- [ ] Store encrypted indicator in manifest
- [ ] Auto-decrypt on restore (if password provided)

---

#### 12.2 Field-Level Encryption
**Advanced:**
- [ ] Encrypt sensitive fields only (SSN, Credit Card, etc.)
- [ ] Use field-level encryption keys
- [ ] Maintain referential integrity
- [ ] Decrypt on restore

---

### 13. API Limit Management

**Status:** BulkV2Client tracks API limits, but could be more proactive

#### 13.1 Pre-Backup Limit Check
**Needed:**
- [ ] Before starting backup, query org limits
- [ ] Estimate API calls needed for backup
- [ ] Warn if backup might exceed limits
- [ ] Option to proceed or adjust selection

**Implementation:**
```java
private void checkApiLimits(List<String> objects) {
    ApiLimits limits = bulkClient.getApiLimits();
    int estimated = estimateApiCalls(objects);
    int remaining = limits.getBulkApiRemaining();
    
    if (estimated > remaining) {
        showWarning("This backup may exceed your API limits. " +
                   "Estimated: " + estimated + ", Remaining: " + remaining);
    }
}
```

---

#### 13.2 Throttling
**Needed:**
- [ ] Automatic throttling when approaching limits
- [ ] Configurable: "Slow down when X% of daily limit used"
- [ ] Option: "Stop backup if limit reached"

---

#### 13.3 Limit Dashboard
**Nice to Have:**
- [ ] Show API limit usage in UI
- [ ] Daily limit, used, remaining
- [ ] Historical usage chart
- [ ] Reset time (when limits reset)

---

## 🧪 TESTING - Critical for Quality

### 14. Integration Tests

**Status:** Good unit test coverage, missing integration tests

#### 14.1 End-to-End Backup Tests
**Needed:**
```java
@Test
void testFullBackupToCSV() {
    // 1. Connect to test Salesforce org
    // 2. Select Account, Contact
    // 3. Run backup
    // 4. Verify CSV files created
    // 5. Verify record counts match
    // 6. Verify manifest generated
}

@Test
void testFullBackupToDatabase() {
    // 1. Connect to test Salesforce org
    // 2. Connect to test database
    // 3. Select objects
    // 4. Run backup
    // 5. Verify tables created
    // 6. Verify record counts match
}

@Test
void testRelationshipAwareBackup() {
    // 1. Back up Account with limit 10
    // 2. Enable related records (depth 2)
    // 3. Verify Contact backed up for those 10 Accounts
    // 4. Verify Opportunities backed up
    // 5. Verify manifest has correct order
}
```

---

#### 14.2 Database Export Tests
**Needed:**
```java
@Test
void testSnowflakeExport() { /* ... */ }

@Test
void testPostgreSQLExport() { /* ... */ }

@Test
void testSQLServerExport() { /* ... */ }

@Test
void testSchemaEvolution() {
    // 1. Back up Account with fields A, B, C
    // 2. Add field D to Salesforce
    // 3. Back up again
    // 4. Verify table has A, B, C, D
    // 5. Verify old records have NULL for D
}
```

---

#### 14.3 Scheduler Integration Tests
**Needed:**
```java
@Test
void testScheduledBackupExecution() {
    // 1. Create schedule (next run in 1 second)
    // 2. Wait for execution
    // 3. Verify backup completed
    // 4. Verify next run scheduled
}

@Test
void testScheduledBackupWithFailure() {
    // 1. Create schedule with invalid credentials
    // 2. Wait for execution
    // 3. Verify failure logged
    // 4. Verify next run still scheduled
}
```

---

#### 14.4 Large Dataset Tests
**Needed:**
```java
@Test
@Tag("slow")
void testBackup10KRecords() { /* ... */ }

@Test
@Tag("slow")
void testBackup100KRecords() { /* ... */ }

@Test
void testBackupWithLargeBlobs() {
    // Test Attachment with large files
}
```

---

### 15. Performance Testing

#### 15.1 Throughput Benchmarks
**Needed:**
- [ ] Measure records per second for various objects
- [ ] Measure API call efficiency
- [ ] Identify bottlenecks
- [ ] Optimize slow paths

**Targets:**
- Simple objects (Account): 5000+ records/min
- Complex objects (Opportunity): 3000+ records/min
- Large objects (Attachment): 100+ records/min

---

#### 15.2 Memory Profiling
**Needed:**
- [ ] Profile memory usage during large backups
- [ ] Identify memory leaks
- [ ] Optimize large object handling
- [ ] Set reasonable heap size recommendations

---

#### 15.3 Parallel Execution Optimization
**Current:** Uses ExecutorService with thread pool

**Testing:**
- [ ] Test different thread pool sizes (5, 10, 20)
- [ ] Measure impact on throughput
- [ ] Identify optimal configuration
- [ ] Make thread pool size configurable

---

### 16. Error Recovery Tests

#### 16.1 Network Failure Tests
**Needed:**
```java
@Test
void testBackupRecoveryAfterNetworkFailure() {
    // 1. Start backup
    // 2. Simulate network failure mid-way
    // 3. Verify graceful handling
    // 4. Verify partial results saved
    // 5. Verify clear error message
}
```

---

#### 16.2 API Error Tests
**Needed:**
- [ ] Test: API limit exceeded
- [ ] Test: Session expired
- [ ] Test: Invalid object name
- [ ] Test: Permission denied
- [ ] Test: Timeout

---

#### 16.3 Disk Space Tests
**Needed:**
```java
@Test
void testBackupWithInsufficientDiskSpace() {
    // 1. Start backup
    // 2. Simulate disk full
    // 3. Verify graceful failure
    // 4. Verify partial cleanup
}
```

---

## 📚 DOCUMENTATION

### 17. User Documentation

#### 17.1 Backup Guide
**Create:** `docs/BACKUP_GUIDE.md`

**Content:**
- Getting started with backups
- Selecting objects
- Choosing export format (CSV vs Database)
- Understanding relationship-aware backups
- Scheduling automated backups
- Verifying backup integrity
- Best practices

---

#### 17.2 Scheduler Guide
**Create:** `docs/SCHEDULER_GUIDE.md`

**Content:**
- Creating backup schedules
- Frequency options (daily, weekly, monthly)
- Setting up database connections
- Configuring notifications
- Monitoring scheduled backups
- Troubleshooting

---

#### 17.3 Troubleshooting Guide
**Create:** `docs/BACKUP_TROUBLESHOOTING.md`

**Content:**
- Common errors and solutions
- API limit issues
- Memory problems with large objects
- Database connection issues
- Permission errors
- Network failures

---

#### 17.4 Video Tutorials
**Create:**
- [ ] "Your First Backup" (5 min)
- [ ] "Relationship-Aware Backups" (7 min)
- [ ] "Scheduling Automated Backups" (6 min)
- [ ] "Database Export Setup" (8 min)

---

### 18. Developer Documentation

#### 18.1 Architecture Documentation
**Create:** `docs/BACKUP_ARCHITECTURE.md`

**Content:**
- System overview and components
- BulkV2Client implementation
- Data sink architecture (CSV, JDBC)
- Relationship analysis and manifest generation
- Scheduler architecture
- Threading and concurrency model

---

#### 18.2 API Documentation
**Needed:**
- [ ] JavaDoc for all public classes
- [ ] Code examples for common use cases
- [ ] Extension points for custom sinks
- [ ] Plugin architecture documentation

---

#### 18.3 Testing Guide
**Create:** `docs/BACKUP_TESTING.md`

**Content:**
- How to run tests
- Test organization
- Creating integration tests
- Test data setup
- Mocking Salesforce API

---

## 🚀 FUTURE ENHANCEMENTS (Post-MVP)

### 19. Advanced Features

#### 19.1 Differential Backup
- Only backup changed records since last backup
- Track LastModifiedDate per object
- Significantly faster for incremental backups

---

#### 19.2 Cloud Storage Integration
- Export directly to AWS S3
- Export to Azure Blob Storage
- Export to Google Cloud Storage
- Automated cloud archival

---

#### 19.3 Backup Retention Policies
- Auto-delete old backups after X days
- Keep daily for 7 days, weekly for 30 days, monthly forever
- Configurable retention per schedule

---

#### 19.4 Multi-Org Backup
- Back up multiple orgs in one job
- Side-by-side comparison
- Cross-org reporting

---

#### 19.5 Metadata Backup
- Export Apex classes, triggers
- Export Flows, Process Builder
- Export Custom Objects, Fields
- Export Profiles, Permission Sets
- Git integration for version control

---

#### 19.6 Audit Trail Backup
- Preserve audit fields (CreatedBy, LastModifiedBy, etc.)
- Store as separate fields
- Restore as reference data

---

## 📊 Progress Tracking

### Overall Completion: ~85%

| Component | Status | % Complete |
|-----------|--------|------------|
| Core Backup Infrastructure | ✅ Complete | 100% |
| BulkV2Client | ✅ Complete | 100% |
| CSV Export | ✅ Complete | 100% |
| Database Export | ⚠️ Has Bug | 95% |
| Relationship-Aware Backup | ✅ Complete | 95% |
| BackupManifestGenerator | ✅ Complete | 100% |
| Scheduled Backups | ✅ Complete | 90% |
| BackupScheduler | ✅ Complete | 100% |
| Schedule UI | ✅ Complete | 90% |
| Backup Verification | ✅ Complete | 85% |
| Object Selection UI | ✅ Complete | 95% |
| Progress Monitoring | ✅ Complete | 90% |
| Error Handling | ⚠️ Good | 85% |
| Testing | ⚠️ Partial | 70% |
| Documentation | ⚠️ Partial | 50% |

---

## 🎯 Recommended Implementation Order

### Phase 1: Critical Fixes (Week 1)
1. Fix JdbcDatabaseSink duplicate method
2. Resolve BackupController TODO
3. Test all core backup paths

### Phase 2: Testing & Reliability (Week 2)
4. Write integration tests
5. Performance testing and optimization
6. Error recovery testing
7. Fix any discovered bugs

### Phase 3: Polish & UX (Week 3)
8. Enhance backup verification integration
9. Improve large object handling
10. Polish scheduled backup execution
11. Better progress monitoring

### Phase 4: Documentation (Week 4)
12. Complete user guides
13. Create video tutorials
14. Write developer documentation
15. Update README with backup examples

### Phase 5: Advanced Features (Optional, Month 2)
16. Email notifications for scheduled backups
17. Backup history enhancements
18. Compression and encryption
19. API limit management dashboard

---

## 📝 Notes

### Strengths
1. **Solid Foundation:** BulkV2Client is robust and well-tested
2. **Feature Complete:** Core backup functionality works end-to-end
3. **Relationship-Aware:** Advanced feature that sets this apart
4. **Flexible Export:** CSV and multiple database options
5. **Scheduling:** Full-featured scheduler with good design
6. **Verification:** Comprehensive verification system

### Known Limitations
1. **No Metadata Backup:** Only data backup, not config/code
2. **Limited Error Recovery:** Some scenarios not handled gracefully
3. **No Cloud Storage:** Direct export to cloud not supported
4. **Single Org:** Can't backup multiple orgs in one job
5. **No Encryption:** Data stored unencrypted

### Areas of Excellence
- BulkV2Client implementation (very robust)
- Relationship analysis (sophisticated)
- Scheduler design (clean, testable)
- Object filtering (comprehensive)
- Test coverage for core components (good)

### Areas Needing Attention
- Database export bug (critical)
- Integration test coverage (important)
- User documentation (needed)
- Performance optimization (beneficial)
- Error messages (could be friendlier)

---

## 🤝 Contributing

When working on these tasks:
1. Fix the critical JdbcDatabaseSink bug first
2. Write tests before implementing new features (TDD)
3. Update this TODO list as tasks are completed
4. Document your changes in CHANGELOG.md
5. Test thoroughly with real Salesforce orgs
6. Consider backward compatibility

---

**Last Updated:** January 12, 2026  
**Next Review:** After Phase 1 completion
