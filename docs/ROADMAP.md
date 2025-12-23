# BackupForce Roadmap

This document outlines the development roadmap for BackupForce — the free, open-source Salesforce backup and recovery tool.

**Last Updated:** December 19, 2025

---

## 🎯 Overview

BackupForce is designed to be the best **free alternative** to expensive Salesforce backup tools like OwnBackup ($6K+/year), Spanning, and Gearset. Our goal is to provide enterprise-grade backup and recovery features at zero cost, built by Salesforce admins for the community.

### Key Differentiators
- **100% Free** - No subscriptions, no per-user pricing, no storage limits
- **Relationship-Aware** - Smart backup/restore that preserves data relationships
- **Cross-Platform** - Native apps for Windows, macOS, and Linux (no Java required)
- **Open Source** - Full transparency, community-driven development

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
| **Phase 10** | Relationship-Aware Backup | ✅ Complete | High |
| **Phase 11** | Cascade Delete | ⏳ Planned | High |
| **Phase 12** | Scheduled Automated Backups | ✅ Complete | High |
| **Phase 13** | CLI & Automation | 🔥 Next | Medium |
| **Phase 14** | Advanced Data Management | ⏳ Planned | Medium |
| **Phase 15** | Enterprise Features | ⏳ Future | Low |

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

## � Phase 10: Relationship-Aware Backup (Next)

**User Pain Point:** *"If I set a limit on Account, but say to preserve relationships, it should know to look at relationship objects and also download those records."*

When backing up with record limits, automatically include related records to maintain data integrity.

### 10.1 Smart Relationship Detection
- [ ] Analyze object relationships on backup start
- [ ] Build relationship graph (parent → child mappings)
- [ ] Identify lookup and master-detail relationships
- [ ] Detect polymorphic relationships (WhoId, WhatId)

### 10.2 Preserve Relationships Mode
- [ ] "Preserve Relationships" checkbox in backup options
- [ ] When enabled with record limit, auto-include related records
- [ ] Query related child records for each parent
- [ ] Example: Backup 100 Accounts → include all their Contacts, Opportunities, Cases

### 10.3 Relationship Depth Configuration
- [ ] Configure relationship depth (1, 2, 3 levels or unlimited)
- [ ] Level 1: Direct children (Account → Contact)
- [ ] Level 2: Grandchildren (Account → Opportunity → OpportunityLineItem)
- [ ] Level 3+: Deep hierarchies

### 10.4 Smart Query Building
- [ ] Generate WHERE clauses based on parent IDs
- [ ] Batch queries for large parent sets
- [ ] Use Bulk API for large related record sets
- [ ] Optimize query order for efficiency

### 10.5 Backup Manifest
- [ ] Generate manifest file listing all objects and counts
- [ ] Record relationship mappings in manifest
- [ ] Enable restoration in correct dependency order
- [ ] Include source org metadata snapshot

---

## 🔥 Phase 11: Cascade Delete (Next)

**User Pain Point:** *"I want to be able to delete [a record] and delete all related records accordingly."*

Smart deletion of records with all their related children — like deleting an Account and all its Cases, Contacts, Opportunities, etc.

### 11.1 Cascade Delete Detection
- [ ] Analyze relationships for selected record(s)
- [ ] Build complete dependency tree
- [ ] Identify all child records across all objects
- [ ] Handle circular relationships

### 11.2 Preview Mode
- [ ] Show what WILL be deleted before execution
- [ ] Display record counts per object
- [ ] Tree view of deletion hierarchy
- [ ] Export preview to CSV for approval

### 11.3 Delete Options
- [ ] **Full Cascade** - Delete record and ALL related children
- [ ] **Selective Cascade** - Choose which child objects to include
- [ ] **Orphan Mode** - Delete only, don't cascade (for cleanup)
- [ ] **Backup First** - Auto-backup before deletion (safety net)

### 11.4 Deletion Execution
- [ ] Delete in reverse dependency order (children first)
- [ ] Use Bulk API 2.0 for efficiency
- [ ] Progress tracking with record counts
- [ ] Error handling with partial rollback option

### 11.5 Query-Based Delete
- [ ] Delete records matching a SOQL query
- [ ] Example: Delete all Accounts WHERE Industry = 'Test'
- [ ] Include all related records for each matched parent
- [ ] Useful for data cleanup and testing

### 11.6 Recycle Bin Integration
- [ ] Option to hard delete (bypass recycle bin)
- [ ] Empty recycle bin after deletion
- [ ] Restore from recycle bin option

---

## ✅ Phase 12: Scheduled Automated Backups (Completed)

**User Pain Point:** *"Someone can write scripts to auto run data loader and dump the files onto a server"* — Let's make this easy without code!

### 12.1 Scheduler UI
- [x] Backup schedule configuration screen
- [x] Daily, weekly, monthly schedule options
- [x] Time zone support
- [x] Multiple schedules per connection

### 12.2 Schedule Triggers
- [x] Time-based scheduling (cron-like)
- [x] On-demand manual trigger
- [ ] System startup trigger
- [ ] Post-deployment trigger (via webhook)

### 12.3 Background Service
- [x] In-app scheduler with ScheduledExecutorService
- [ ] Windows Task Scheduler integration
- [ ] macOS launchd integration
- [ ] Linux cron/systemd integration
- [ ] Headless backup execution

### 12.4 Incremental Backups
- [x] Track LastModifiedDate for each object
- [x] Only backup changed records since last run
- [x] Full vs incremental backup modes
- [ ] Merge incremental into full periodically

### 12.5 Notifications
- [ ] Email notifications on completion/failure
- [ ] Slack/Teams webhook integration
- [ ] Desktop notifications
- [x] Backup log history

### 12.6 Retention Policies
- [ ] Keep last N backups
- [ ] Delete backups older than X days
- [ ] Archive to external storage (S3, Azure, GCS)
- [ ] Compression for old backups

---

## ⏳ Phase 13: CLI & Automation (Planned)

**User Pain Point:** *"SFDMU is the cheapest way to create restorable backups, but also the most unfriendly UX wise"*

### 13.1 Command Line Interface
- [ ] `backupforce backup` - Run backup from CLI
- [ ] `backupforce restore` - Run restore from CLI
- [ ] `backupforce delete` - Run cascade delete from CLI
- [ ] Configuration via JSON/YAML files

### 13.2 CI/CD Integration
- [ ] GitHub Actions workflow examples
- [ ] Azure DevOps pipeline support
- [ ] Jenkins integration guide
- [ ] Docker container image

### 13.3 Scripting Support
- [ ] PowerShell module
- [ ] Bash scripts
- [ ] Python SDK
- [ ] Node.js package

---

## ⏳ Phase 14: Advanced Data Management (Planned)

Based on common Salesforce pain points from the community.

### 14.1 Field History Archiving
- [ ] Export field history beyond 24-month limit
- [ ] Track all historical changes
- [ ] Rebuild field history timeline
- [ ] Analytics on field change patterns

### 14.2 Data Comparison
- [ ] Compare backup to live org
- [ ] Show added/modified/deleted records
- [ ] Field-level diff view
- [ ] Generate change report

### 14.3 Data Masking
- [ ] Mask sensitive data during export
- [ ] Configurable masking rules per field
- [ ] Faker-style data generation
- [ ] GDPR/HIPAA compliance helpers

### 14.4 Sandbox Seeding
- [ ] Smart sandbox data population
- [ ] Configurable data volume
- [ ] Maintain relationships
- [ ] Anonymize for non-production

### 14.5 Contract Record Handling
- [ ] Handle Contract status limitations
- [ ] Special logic for activated contracts
- [ ] Status field mapping during restore

---

## ⏳ Phase 15: Enterprise Features (Future)

### 15.1 Metadata Backup
- [ ] Full metadata export (classes, triggers, flows)
- [ ] Version control integration (Git)
- [ ] Metadata diff between backups
- [ ] Deploy metadata from backup

### 15.2 Multi-Org Management
- [ ] Manage multiple Salesforce orgs
- [ ] Org-to-org data sync
- [ ] Central backup dashboard
- [ ] Org comparison tools

### 15.3 Audit & Compliance
- [ ] Backup audit trail
- [ ] SOC 2 compliance features
- [ ] Encryption at rest
- [ ] Access logging

| Phase | Target |
|-------|--------|
| Phase 1-7 | ✅ December 2025 (Complete) |
| Phase 8-9 | ⏳ January 2026 |
| **Phase 10** | 🔥 **January 2026** (Relationship-Aware Backup) |
| **Phase 11** | 🔥 **January 2026** (Cascade Delete) |
| Phase 12 | ⏳ February 2026 (Scheduled Backups) |
| Phase 13 | ⏳ March 2026 (CLI) |
| Phase 14-15 | ⏳ Q2 2026 (Advanced Features) |

---

## 🏆 Why BackupForce vs Paid Alternatives

| Feature | BackupForce | OwnBackup | Spanning | Gearset |
|---------|-------------|-----------|----------|---------|
| **Price** | **$0** | $6K+/year | ~$432/year | ~$5K+/year |
| Data Backup | ✅ | ✅ | ✅ | ✅ |
| Data Restore | ✅ | ✅ | ✅ | ✅ |
| Relationship-Aware | 🔥 Coming | Partial | Partial | Partial |
| Cascade Delete | 🔥 Coming | ❌ | ❌ | ❌ |
| Blob/Attachments | ✅ | ✅ | ✅ | ✅ |
| Cross-Org Transform | ✅ | ✅ | ❌ | ✅ |
| Open Source | ✅ | ❌ | ❌ | ❌ |
| Self-Hosted | ✅ | ❌ | ❌ | ❌ |
| No Java Required | ✅ | N/A | N/A | N/A |

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
