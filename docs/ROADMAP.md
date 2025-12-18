# BackupForce Roadmap

This document outlines the development roadmap for BackupForce's data restoration feature.

**Last Updated:** December 17, 2025

---

## 🎯 Overview

The Data Restoration feature enables restoring backed-up Salesforce data (CSV files or database tables) back into a Salesforce org. This is a critical feature for disaster recovery, data migration, and sandbox refresh scenarios.

---

## 📊 Feature Status

| Phase | Feature | Status | Priority |
|-------|---------|--------|----------|
| **Phase 1** | Core Restore Infrastructure | ✅ Complete | High |
| **Phase 2** | Data Source Handling | 🔄 In Progress | High |
| **Phase 3** | Relationship Resolution | ⏳ Planned | High |
| **Phase 4** | Advanced Options | ⏳ Planned | Medium |
| **Phase 5** | Validation & Safety | ⏳ Planned | Medium |
| **Phase 6** | Testing | ⏳ Planned | High |

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

## 🔄 Phase 2: Data Source Handling (In Progress)

### 2.1 Database Source Scanning
- [ ] Query backup tables from configured database
- [ ] Support Snowflake, PostgreSQL, SQL Server sources
- [ ] Display available tables with record counts
- [ ] Handle schema differences between database types

### 2.2 JSON File Support
- [ ] Parse JSON backup files
- [ ] Convert JSON to records for restore
- [ ] Support nested JSON structures

### 2.3 Source Selection UI
- [ ] Radio buttons for CSV/Database/JSON source
- [ ] Database connection picker
- [ ] Source validation before restore

---

## ⏳ Phase 3: Relationship Resolution (Planned)

### 3.1 External ID Reference Resolution
- [ ] Use `_ref_*` columns from enriched backups
- [ ] Resolve lookup field values during restore
- [ ] Query target org for external ID matches
- [ ] Cache resolved references for performance

### 3.2 Object Dependency Ordering
- [ ] Analyze object relationships
- [ ] Build dependency graph
- [ ] Sort objects: parents before children
- [ ] Handle circular dependencies gracefully

### 3.3 Relationship Field Mapping
- [ ] Auto-detect relationship fields
- [ ] Map source IDs to target IDs
- [ ] Support polymorphic relationships (WhoId, WhatId)

---

## ⏳ Phase 4: Advanced Options (Planned)

### 4.1 Preserve Original IDs
- [ ] Wire up `preserveIdsCheck` checkbox
- [ ] Use external ID upsert with original Salesforce ID
- [ ] Validate ID format before restore

### 4.2 External ID Field Selection
- [ ] Dropdown to select external ID field for upsert
- [ ] Query available external ID fields from org
- [ ] Validate field exists on target object

### 4.3 Field Mapping
- [ ] Compare source fields to target schema
- [ ] Auto-map matching fields
- [ ] Manual mapping for renamed fields
- [ ] Exclude non-createable/non-updateable fields

---

## ⏳ Phase 5: Validation & Safety (Planned)

### 5.1 Pre-Restore Validation
- [ ] Validate CSV structure before restore
- [ ] Check required fields are present
- [ ] Verify field data types match target
- [ ] Display validation errors before starting

### 5.2 Dry Run Mode
- [ ] Preview mode without making changes
- [ ] Show what would be inserted/updated
- [ ] Generate validation report

### 5.3 Error Handling Improvements
- [ ] Retry logic for transient failures
- [ ] Individual record error tracking
- [ ] Continue on error option
- [ ] Detailed error reporting

### 5.4 Rollback Capability
- [ ] Track created record IDs
- [ ] Option to rollback on failure
- [ ] Delete records created in failed batch

---

## ⏳ Phase 6: Testing (Planned)

### 6.1 Unit Tests
- [ ] RestoreExecutor tests
- [ ] RelationshipManager tests
- [ ] RelationshipEnricher tests
- [ ] CSV parsing tests

### 6.2 Integration Tests
- [ ] End-to-end restore tests
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
| Phase 2 | January 2026 |
| Phase 3 | January 2026 |
| Phase 4 | February 2026 |
| Phase 5 | February 2026 |
| Phase 6 | March 2026 |

---

## 🤝 Contributing

Contributions are welcome! If you'd like to help with any of these features:

1. Check the [Issues](https://github.com/victorfelisbino/BackupForce/issues) page
2. Comment on the feature you'd like to work on
3. Submit a pull request

---

## 📝 Changelog

### December 17, 2025
- ✅ RestoreExecutor with Bulk API 2.0 support
- ✅ RestoreController UI integration
- ✅ CSV folder scanning
- ✅ Basic insert/update/upsert operations
