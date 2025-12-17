# BackupForce v2.0 - Database Integration

## Overview
Version 2.0 adds support for backing up Salesforce data directly to databases (Snowflake, SQL Server, PostgreSQL) in addition to CSV files.

## Architecture

### DataSink Abstraction Layer
- **DataSink Interface**: Core abstraction for all backup destinations
- **CsvFileSink**: File-based backup (original v1.0 functionality)
- **JdbcDatabaseSink**: Generic JDBC database implementation

### Database Dialect System
- **DatabaseDialect Interface**: Handles database-specific SQL generation
- **SnowflakeDialect**: Snowflake-specific optimizations (UPPERCASE, VARCHAR 16MB)
- **SqlServerDialect**: SQL Server syntax (brackets, NVARCHAR(MAX))
- **PostgresDialect**: PostgreSQL conventions (lowercase, TEXT type)

### Factory Pattern
- **DataSinkFactory**: Creates configured sink instances based on user selection

## Features

### UI Enhancements
1. **Backup Destination Selection**
   - Radio buttons: CSV Files vs Database
   - Dynamic form that adapts to database type
   - Database Settings button (disabled until database selected)

2. **Database Configuration Dialog**
   - Database type selector (Snowflake, SQL Server, PostgreSQL)
   - Dynamic field generation based on selected database
   - Test Connection functionality
   - Encrypted credential storage (Base64)

3. **Modern Styling**
   - GitHub-inspired color scheme
   - Card-based layouts with shadows
   - Smooth transitions and hover effects

### Backup Workflow

#### CSV Backup (unchanged)
1. User selects output folder
2. BulkV2Client queries Salesforce
3. CSV files written directly to output folder

#### Database Backup (new)
1. User configures database connection
2. Application connects to database at backup start
3. For each object:
   - BulkV2Client queries Salesforce and writes to temp CSV
   - Auto-create database table from CSV headers (if not exists)
   - Read CSV and batch-insert records into database
   - Add metadata columns: BACKUP_ID, BACKUP_TIMESTAMP
4. Disconnect from database at completion

## Technical Details

### Database Table Structure
- All Salesforce fields mapped to VARCHAR (max size)
- Additional metadata columns:
  - `BACKUP_ID`: Unique identifier for each backup run
  - `BACKUP_TIMESTAMP`: When the backup was performed

### Connection Properties
- **Snowflake**: Account, Warehouse, Database, Schema, Username, Password
- **SQL Server**: Server, Database, Username, Password
- **PostgreSQL**: Host, Port, Database, Schema, Username, Password

### Performance Optimizations
- Batch inserts (configurable per database)
- Parallel backup threads (10 concurrent objects)
- Streaming CSV processing (low memory footprint)

## Extensibility

### Adding New Databases
1. Create new dialect class implementing `DatabaseDialect`
2. Add factory method in `DataSinkFactory`
3. Add database type to UI dropdown
4. Add field definitions in `DatabaseSettingsController`

### Example: Adding Oracle Support
```java
public class OracleDialect implements DatabaseDialect {
    public String sanitizeTableName(String name) {
        return "\"" + name.toUpperCase() + "\"";
    }
    // ... implement other methods
}
```

### Adding Cloud Storage
Create new sink implementing `DataSink`:
- `S3Sink`: Amazon S3
- `AzureBlobSink`: Azure Blob Storage
- `GoogleCloudStorageSink`: Google Cloud Storage

## Dependencies
- **Snowflake JDBC**: 3.16.1 (optional)
- **SQL Server JDBC**: 12.4.2.jre11 (optional)
- **PostgreSQL JDBC**: 42.7.1 (optional)

All JDBC drivers marked as optional - only included if needed.

## Testing

### CSV Backup Test
1. Launch application: `java -jar BackupForce.jar`
2. Login to Salesforce
3. Select "CSV Files" as backup destination
4. Choose output folder
5. Select objects and start backup
6. Verify CSV files created

### Database Backup Test
1. Launch application
2. Login to Salesforce
3. Select "Database" as backup destination
4. Click "Database Settings"
5. Configure connection (e.g., Snowflake account details)
6. Test connection (should succeed)
7. Save settings
8. Select objects and start backup
9. Monitor log panel for:
   - "Connecting to [database]..."
   - "Successfully connected to [database]"
   - "Creating table..." (for each object)
   - "Wrote X records to [database]"
   - "Disconnecting from [database]..."
10. Verify tables created in database
11. Query tables to verify data

### Connection String Examples

**Snowflake**:
```
Account: myorg-myaccount
Warehouse: COMPUTE_WH
Database: SALESFORCE_BACKUP
Schema: PUBLIC
Username: myuser
Password: ********
```

**SQL Server**:
```
Server: localhost:1433
Database: SalesforceBackup
Username: sa
Password: ********
```

**PostgreSQL**:
```
Host: localhost
Port: 5432
Database: salesforce_backup
Schema: public
Username: postgres
Password: ********
```

## Known Limitations

1. **Type Mapping**: All fields stored as VARCHAR (no native type conversion yet)
2. **Incremental Backups**: Not implemented (each backup is full)
3. **Field Metadata**: Tables created from CSV headers (no Salesforce type info)
4. **Temp Files**: CSV files written to temp dir for database backups (not cleaned up)

## Future Enhancements

1. **Type-Safe Mapping**: Use Salesforce field metadata for proper type mapping
2. **Incremental Backups**: Track last backup timestamp, only sync changes
3. **Parallel Database Writes**: Write multiple objects to database concurrently
4. **Cleanup**: Delete temp CSV files after database write
5. **Data Validation**: Verify record counts match between Salesforce and database
6. **Compression**: Compress large text fields before storage
7. **Partitioning**: Auto-partition large tables by date
8. **Cloud Storage**: Add S3, Azure Blob, Google Cloud Storage sinks

## Migration from v1.0

No changes required - v2.0 is fully backward compatible. CSV backup works exactly as before.

## Build Instructions

```bash
# Compile
mvn clean compile

# Package
mvn package -DskipTests

# Run
java -jar BackupForce.jar
```

## File Structure

```
src/main/java/com/backupforce/
├── sink/
│   ├── DataSink.java                 # Core interface
│   ├── CsvFileSink.java              # CSV implementation
│   ├── JdbcDatabaseSink.java         # JDBC implementation
│   ├── DataSinkFactory.java          # Factory
│   ├── SnowflakeDialect.java         # Snowflake SQL
│   ├── SqlServerDialect.java         # SQL Server SQL
│   └── PostgresDialect.java          # PostgreSQL SQL
├── ui/
│   ├── BackupController.java         # Main controller (updated)
│   └── DatabaseSettingsController.java # Config dialog
└── resources/
    ├── fxml/
    │   ├── backup.fxml               # Main UI (updated)
    │   └── database-settings.fxml    # Config dialog
    └── css/
        └── modern-styles.css         # Styling (updated)
```

## Version History

- **v1.0**: CSV file backup only
- **v2.0**: Added database backup support (Snowflake, SQL Server, PostgreSQL)
