# BackupForce Roadmap

This document outlines the development roadmap for BackupForce's data restoration feature.

**Last Updated:** December 18, 2025

---

## 🎯 Overview

The Data Restoration feature enables restoring backed-up Salesforce data (CSV files or database tables) back into a Salesforce org. This is a critical feature for disaster recovery, data migration, and sandbox refresh scenarios.

---

## 📊 Feature Status

| Phase | Feature | Status | Priority |
|-------|---------|--------|----------|
| **Phase 1** | Core Restore Infrastructure | ✅ Complete | High |
| **Phase 2** | Data Source Handling | ✅ Complete | High |
| **Phase 3** | Relationship Resolution | ✅ Complete | High |
| **Phase 4** | Advanced Options | ✅ Complete | Medium |
| **Phase 5** | Validation & Safety | ✅ Complete | Medium |
| **Phase 6** | Testing | ✅ Complete | High |

---

## ✅ Phase 1: Core Restore Infrastructure (Complete)

### 1.1 RestoreExecutor with Bulk API 2.0
- [x] Create RestoreExecutor class with Bulk API 2.0 integration
- [x] Support for INSERT, UPDATE, UPSERT operations
- [x] Automatic fallback to Composite API for small batches (≤200 records)
- [x] Batch processing with configurable batch size
- [x] Progress callbacks and cancellation support

### 1.2 UI Integration
- [x] Restore screen with source selection
- [x] Objects table with record counts
- [x] Restore mode selection (Insert/Update/Upsert)
- [x] Progress bar and status updates
- [x] Stop/Cancel functionality

### 1.3 CSV File Scanning
- [x] Scan folders for backup CSV files
- [x] Parse CSV headers to determine fields
- [x] Count records in each file
- [x] Display in UI table

---

## ✅ Phase 2: Data Source Handling (Complete)

### 2.1 Database Source Scanning
- [x] Query backup tables from configured database (DatabaseScanner)
- [x] Support Snowflake, PostgreSQL, SQL Server sources
- [x] Display available tables with record counts
- [x] Handle schema differences between database types

### 2.2 JSON File Support
- [ ] Parse JSON backup files (Future enhancement)
- [ ] Convert JSON to records for restore
- [ ] Support nested JSON structures

### 2.3 Source Selection UI
- [x] Radio buttons for CSV/Database source
- [x] Database connection picker
- [x] Source validation before restore

---

## ✅ Phase 3: Relationship Resolution (Complete)

### 3.1 External ID Reference Resolution
- [x] Use `_ref_*` columns from enriched backups (RelationshipResolver)
- [x] Resolve lookup field values during restore
- [x] Query target org for external ID matches
- [x] Cache resolved references for performance

### 3.2 Object Dependency Ordering
- [x] Analyze object relationships (DependencyOrderer)
- [x] Build dependency graph using topological sort
- [x] Sort objects: parents before children
- [x] Handle circular dependencies gracefully

### 3.3 Relationship Field Mapping
- [x] Auto-detect relationship fields via metadata
- [x] Map source IDs to target IDs
- [x] Support polymorphic relationships

---

## ✅ Phase 4: Advanced Options (Complete)

### 4.1 Preserve Original IDs
- [x] Wire up `preserveIdsCheck` checkbox
- [x] Use external ID upsert with original Salesforce ID
- [x] Validate ID format before restore

### 4.2 External ID Field Selection
- [x] Dropdown to select external ID field for upsert
- [x] Query available external ID fields from org
- [x] Validate field exists on target object

### 4.3 Field Mapping
- [x] Compare source fields to target schema (FieldValidator)
- [x] Auto-map matching fields
- [x] Exclude non-createable/non-updateable fields
- [x] Skip enrichment fields (_ref_*)

---

## ✅ Phase 5: Validation & Safety (Complete)

### 5.1 Pre-Restore Validation
- [x] Validate CSV structure before restore (FieldValidator)
- [x] Check required fields are present
- [x] Verify field data types match target
- [x] Display validation errors before starting

### 5.2 Dry Run Mode
- [x] Preview mode without making changes
- [x] Show what would be inserted/updated
- [x] Generate validation report

### 5.3 Error Handling Improvements
- [x] Retry logic for transient failures (up to 3 retries)
- [x] Individual record error tracking
- [x] Continue on error option
- [x] Detailed error reporting with categorized messages

### 5.4 Rollback Capability
- [ ] Track created record IDs (Partial - IDs are tracked)
- [ ] Option to rollback on failure (Future enhancement)
- [ ] Delete records created in failed batch

---

## ✅ Phase 6: Testing (Complete)

### 6.1 Unit Tests
- [x] RestoreExecutor tests (RestoreExecutorTest)
- [x] DependencyOrderer tests (DependencyOrdererTest)
- [x] FieldValidator tests (FieldValidatorTest)
- [x] Options and result classes tests

### 6.2 Integration Tests
- [ ] End-to-end restore tests (Future enhancement)
- [ ] Mock Salesforce API responses
- [ ] Database source tests

### 6.3 Performance Tests
- [ ] Large file handling (1M+ records)
- [ ] Memory optimization tests
- [ ] Concurrent restore tests

---

## 📅 Timeline

| Phase | Target |
|-------|--------|
| Phase 1 | ✅ December 2025 (Complete) |
| Phase 2 | ✅ December 2025 (Complete) |
| Phase 3 | ✅ December 2025 (Complete) |
| Phase 4 | ✅ December 2025 (Complete) |
| Phase 5 | ✅ December 2025 (Complete) |
| Phase 6 | ✅ December 2025 (Complete) |

---

## 🚀 Future Enhancements

These features are planned for future releases:

- **JSON File Support**: Parse and restore from JSON backup files
- **Manual Field Mapping**: UI for mapping source fields to different target fields
- **Full Rollback**: Automatic rollback of all created records on failure
- **Integration Tests**: End-to-end tests with mocked Salesforce APIs
- **Performance Tests**: Large file and concurrent restore testing

---

## 🤝 Contributing

Contributions are welcome! If you'd like to help with any of these features:

1. Check the [Issues](https://github.com/victorfelisbino/BackupForce/issues) page
2. Comment on the feature you'd like to work on
3. Submit a pull request

---

## 📝 Changelog

### December 18, 2025
- ✅ DatabaseScanner for Snowflake/PostgreSQL/SQL Server
- ✅ RelationshipResolver for _ref_* column processing
- ✅ DependencyOrderer with topological sort
- ✅ External ID field selector UI
- ✅ FieldValidator for pre-restore validation
- ✅ Dry run mode for restore preview
- ✅ Retry logic with categorized error messages
- ✅ Unit tests for restore components

### December 17, 2025
- ✅ RestoreExecutor with Bulk API 2.0 support
- ✅ RestoreController UI integration
- ✅ CSV folder scanning
- ✅ Basic insert/update/upsert operations
