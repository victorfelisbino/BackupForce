# Changelog

All notable changes to BackupForce will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.3.0] - 2024-12-18

### Added

#### Cross-Org Data Transformation
- **Schema Comparison**: Compare backup metadata with target org to detect mismatches
- **RecordType Mapping**: Map RecordType IDs between source and target orgs
- **User/Owner Mapping**: Map user IDs for Owner, CreatedBy, LastModifiedBy fields
- **Picklist Value Mapping**: Handle picklist values that differ between orgs
- **Field Mapping**: Rename or exclude fields during restore
- **Value Transformations**: REGEX_REPLACE, PREFIX, SUFFIX, TRIM, UPPERCASE, LOWERCASE, CONSTANT, LOOKUP
- **Transformation UI**: Full dialog for configuring cross-org mappings with auto-suggest
- **Save/Load Mappings**: Persist transformation configs as JSON files

#### Data Restore Enhancements
- **Dry Run Preview**: Interactive preview dialog showing data before actual restore
- **Data Restore Card**: Added to home screen for quick access
- **Background Folder Scanning**: Non-blocking UI during large backup scans
- **Fast Line Counting**: Optimized record counting with file size estimation for 50MB+ files
- **Progress Indicators**: Real-time file count and scanning progress

#### UI/UX Improvements
- **Consistent Dark Theme**: All screens now use Windows 11 Fluent dark theme
- **Resizable Windows**: All screens can be resized with proper minimum sizes
- **Transformation Dialog Styling**: Consistent styling with main application
- **Preview Dialog Styling**: CSS classes for dry run preview

### Changed
- Restore screen "Back" button now returns to Home (not Backup screen)
- Home screen reorganized to 2x2 card grid layout
- Improved window management with min width/height constraints

### Fixed
- Fixed transformation.fxml XML parse error (unescaped `&` character)
- Fixed Java 11 compatibility (`.toList()` → `.collect(Collectors.toList())`)
- Fixed missing stylesheet in transformation dialog
- Fixed window resize restrictions across all navigation paths

### Technical
- 319 unit tests passing (2 skipped for Docker/Testcontainers)
- New classes: DataTransformer, SchemaComparer, TransformationConfig, TransformationController
- Added CSS classes: transformation-container, section-title, modern-combo, modern-text-area

---

## [2.2.0] - 2024-12-15

### Added
- Dry run mode for restore preview
- Retry logic for transient API failures (up to 3 retries)
- Improved error handling with categorized messages
- Comprehensive unit tests for restore components

### Changed
- Enhanced error reporting with detailed categorization

---

## [2.1.0] - 2024-12-10

### Added
- Data restoration feature with Bulk API 2.0
- Support for INSERT, UPDATE, UPSERT operations
- Relationship resolution using external ID references
- Object dependency ordering (topological sort)
- Field validation against target org schema
- Database source scanning (Snowflake, PostgreSQL, SQL Server)

---

## [2.0.0] - 2024-11-01

### Added
- Complete rewrite with JavaFX modern UI
- Windows 11 Fluent dark theme
- Bulk API v2 for high-performance backups
- Database sink support (Snowflake, PostgreSQL, SQL Server)
- OAuth browser-based authentication
- Backup history tracking
- Session caching for faster reconnection

---

## [1.0.0] - 2024-01-01

### Added
- Initial release
- Basic Salesforce backup to CSV files
- Username/password authentication
- Object selection and field filtering
