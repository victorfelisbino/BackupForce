# Changelog

All notable changes to BackupForce will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.1.0] - 2025-12-22

### 🎉 Relationship-Aware Backup & Scheduled Backups

This release completes the data backup story with intelligent relationship handling and automated scheduling!

### Added

#### Relationship-Aware Backup (Phase 10)
- **Relationship Preview Dialog**: Visual tree view showing all related objects before backup
- **Smart Relationship Discovery**: Automatically discovers parent and child relationships
- **Configurable Depth**: Control how many levels of related records to include (1-5)
- **Priority Object Detection**: Identifies critical objects like Contacts, Opportunities
- **Backup Manifest Generator**: Creates `_backup_manifest.json` with full relationship metadata
- **ID Mapping File**: Generates `_id_mapping.json` for cross-org restore preparation
- **Restore Order Calculation**: Determines correct insertion sequence based on dependencies
- **External ID Detection**: Identifies external ID fields for upsert operations

#### Scheduled Automated Backups (Phase 12)
- **Backup Scheduler Service**: Background service for automated backups
- **Multiple Frequencies**: Daily, Weekly (select days), Monthly (select day)
- **Time Zone Support**: Schedule in your local time zone
- **Schedule Manager**: Persistent storage of schedules in `~/.backupforce/schedules.json`
- **Schedule UI**: Full management interface with table view
- **Enable/Disable Schedules**: Toggle individual schedules on/off
- **Run Now Button**: Manually trigger any scheduled backup
- **Scheduler Toggle**: Global on/off for the scheduler service
- **Next Run Preview**: See when each backup will next execute

#### Enhanced Backup Options
- **Snowflake Session Caching Fix**: Resolved session reuse issue for Snowflake destinations
- **Incremental Backup**: Full support for change-data-capture based incremental backups
- **Preserve Relationships Checkbox**: Easy toggle for relationship-aware backups
- **Include Related Records Checkbox**: Quick enable for related object inclusion

### Changed
- Navigation sidebar now includes Schedules section with 🕐 icon
- Backup screen includes relationship configuration options
- Improved backup progress reporting with relationship context

### Technical
- 660 unit tests passing (341 new tests since v3.0.0)
- New packages: `com.backupforce.scheduler`, `com.backupforce.relationship`
- New classes: BackupSchedule, ScheduleManager, BackupSchedulerService, ScheduleController
- New classes: BackupManifestGenerator, RelationshipAwareBackupService
- Java 11+ compatible (uses `.collect(Collectors.toList())`)

---

## [3.0.0] - 2025-12-19

### 🎉 Major Release - Multi-Platform & Native Builds

This release brings BackupForce to all major platforms with native executables that require no Java installation!

### Added

#### Multi-Platform Native Builds
- **Windows**: Native `.msi` installer and portable `.zip`
- **macOS**: Native `.dmg` installer and portable `.zip`
- **Linux**: Native `.deb`, `.rpm` packages and portable `.tar.gz`
- **No Java Required**: Bundled JLink runtime for all platforms
- **GitHub Actions**: Automated cross-platform builds on release

#### Nature-Inspired Design (v3.0 Theme)
- **GitHub-Inspired Dark Theme**: Sleek, modern interface
- **Mountain Landscape Background**: Calming nature imagery
- **Custom Title Bar**: Integrated window controls
- **Semi-Transparent Panels**: Glass-like aesthetic

#### OAuth Improvements
- **Fixed**: OAuth browser not opening in jpackaged builds
- **Added**: `jdk.httpserver` module to runtime for embedded HTTP server
- **Fixed**: Native access warnings with `--enable-native-access=ALL-UNNAMED`

### Changed
- **Java Version**: Updated to Java 25 (Temurin-25.0.1+8)
- **Build System**: JLink custom runtime with minimal modules
- **Package Size**: Optimized portable builds (~50MB vs 200MB+ full JDK)

### Fixed
- OAuth callback server now works in native executables
- Database Settings dialog "Location is not set" error resolved
- All FXML files properly included in builds

### Technical
- 319+ unit tests passing
- JLink modules: java.base, java.sql, java.desktop, java.naming, java.management, java.logging, java.xml, java.prefs, java.net.http, java.scripting, jdk.crypto.ec, jdk.unsupported, java.datatransfer, java.security.jgss, java.security.sasl, jdk.httpserver

---

## [2.3.0] - 2025-12-18

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

## [2.2.0] - 2025-12-15

### Added
- Dry run mode for restore preview
- Retry logic for transient API failures (up to 3 retries)
- Improved error handling with categorized messages
- Comprehensive unit tests for restore components

### Changed
- Enhanced error reporting with detailed categorization

---

## [2.1.0] - 2025-12-10

### Added
- Data restoration feature with Bulk API 2.0
- Support for INSERT, UPDATE, UPSERT operations
- Relationship resolution using external ID references
- Object dependency ordering (topological sort)
- Field validation against target org schema
- Database source scanning (Snowflake, PostgreSQL, SQL Server)

---

## [2.0.0] - 2025-11-01

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
