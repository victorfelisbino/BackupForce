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
| **Phase 7** | Cross-Org Data Transformation | ✅ Complete | High |
| **Phase 8** | Duplicate Record Handling | ⏳ Planned | High |
| **Phase 9** | Related Record Creation | ⏳ Planned | Medium |

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

## ✅ Phase 7: Cross-Org Data Transformation (Complete)

Cross-org restore allows restoring data between different Salesforce orgs that may have different configurations (RecordTypes, picklist values, users, etc.).

### 7.1 Schema Comparison
- [x] Compare backup metadata with target org (SchemaComparer)
- [x] Detect RecordType mismatches (missing, renamed)
- [x] Detect picklist value differences
- [x] Detect user reference mismatches
- [x] Generate comparison report with suggestions

### 7.2 Transformation Configuration
- [x] TransformationConfig for persisting mappings
- [x] Global user mappings (source user ID → target user ID)
- [x] Global RecordType mappings (source → target)
- [x] Per-object configurations
- [x] Unmapped value behaviors (KEEP_ORIGINAL, USE_DEFAULT, SET_NULL, SKIP_RECORD, FAIL)
- [x] Save/Load mappings to JSON file

### 7.3 Data Transformation Engine
- [x] DataTransformer applies mappings during restore
- [x] RecordType ID resolution
- [x] User/Owner ID resolution
- [x] Picklist value mapping
- [x] Field renaming/mapping
- [x] Custom value transformations (regex, prefix, suffix, etc.)
- [x] Transformation statistics tracking

### 7.4 Value Transformation Types
- [x] REGEX_REPLACE - Pattern-based text replacement
- [x] PREFIX - Add text before values
- [x] SUFFIX - Add text after values
- [x] TRIM - Remove whitespace
- [x] UPPERCASE/LOWERCASE - Case conversion
- [x] CONSTANT - Replace with fixed value
- [x] LOOKUP - Replace based on lookup map

### 7.5 Transformation UI
- [x] Schema analysis with target org
- [x] RecordType mapping tab with auto-suggest
- [x] User mapping tab with auto-suggest
- [x] Picklist mapping tab with auto-suggest
- [x] Field mapping tab
- [x] Custom value transformations tab
- [x] Validation and apply functionality

### 7.6 Unit Tests
- [x] DataTransformerTest (37 tests)
- [x] RecordType mapping tests
- [x] User mapping tests  
- [x] Picklist mapping tests
- [x] Field mapping tests
- [x] Value transformation tests
- [x] Statistics tests
- [x] Edge case tests

---

## ⏳ Phase 8: Duplicate Record Handling (Planned)

Handle duplicate records during restore with configurable rules and actions.

### 8.1 Duplicate Detection Rules
- [ ] Define duplicate matching rules per object
- [ ] Match by field(s): Email, Name, External ID, custom fields
- [ ] Match criteria: Exact match, fuzzy match, case-insensitive
- [ ] Compound matching (multiple fields AND/OR)
- [ ] Query target org to find existing duplicates

### 8.2 Duplicate Actions
- [ ] **Skip** - Don't import duplicate records
- [ ] **Update** - Update existing record with import data
- [ ] **Overwrite** - Replace all fields in existing record
- [ ] **Merge** - Update only empty fields in existing record
- [ ] **Create Anyway** - Ignore duplicates, create new record
- [ ] **Fail** - Stop import on duplicate found

### 8.3 Field-Level Update Rules
- [ ] Choose which fields to update on duplicates
- [ ] Update only if source value is not null/empty
- [ ] Update only if source value is newer (date comparison)
- [ ] Append to multi-select picklists
- [ ] Custom merge logic per field

### 8.4 Duplicate Handling UI
- [ ] Configure duplicate rules per object
- [ ] Preview duplicates before import
- [ ] Duplicate action selection
- [ ] Field-level merge configuration
- [ ] Duplicate handling statistics

### 8.5 Reporting
- [ ] Track duplicates found vs created vs updated
- [ ] Export duplicate report (which records matched)
- [ ] Show field-level differences for review

---

## ⏳ Phase 9: Related Record Creation (Planned)

Create related child records automatically during parent import (e.g., create Opportunity for each Account).

### 9.1 Related Record Configuration
- [ ] Define parent-child relationships for import
- [ ] Select child object to create per parent
- [ ] Map parent fields to child fields
- [ ] Define default values for child fields
- [ ] Multiple child objects per parent (Account → Opportunity + Contact)

### 9.2 Field Mapping
- [ ] Map parent fields to child fields (Account.Name → Opportunity.Name)
- [ ] Formula-based field values (e.g., Opportunity.CloseDate = TODAY() + 30)
- [ ] Static default values
- [ ] Conditional field population (if parent field X = Y, set child field Z)

### 9.3 Default Value Templates
- [ ] Pre-defined templates for common scenarios
- [ ] Opportunity defaults (Stage, CloseDate, Amount)
- [ ] Contact defaults (Role, Status)
- [ ] Custom object templates
- [ ] Save/load templates for reuse

### 9.4 Integration with Duplicate Handling
- [ ] Check child duplicate rules before creating
- [ ] Skip child creation if duplicate exists
- [ ] Update existing child if duplicate found
- [ ] Parent-child atomic transactions (rollback both on failure)

### 9.5 Related Record UI
- [ ] Visual relationship mapping interface
- [ ] Field mapping with drag-and-drop
- [ ] Default value configuration
- [ ] Preview generated child records
- [ ] Template management

### 9.6 Use Cases
- [ ] Import Accounts → Auto-create Opportunities
- [ ] Import Accounts → Auto-create Contacts
- [ ] Import Leads → Auto-create Tasks
- [ ] Import custom objects with related children

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
| Phase 7 | ✅ December 2025 (Complete) |
| Phase 8 | ⏳ January 2026 (Planned) |
| Phase 9 | ⏳ January 2026 (Planned) |

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
- ✅ Cross-Org Data Transformation (Phase 7)
  - SchemaComparer for detecting org differences
  - TransformationConfig for mapping configuration
  - DataTransformer for applying transformations
  - Transformation UI with auto-suggest
  - 37 comprehensive unit tests
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
