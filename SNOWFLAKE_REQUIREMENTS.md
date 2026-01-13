# Snowflake JDBC Requirements

## Overview
BackupForce uses Snowflake JDBC driver which requires specific JVM arguments when using Apache Arrow result format.

## Required JVM Arguments

When launching BackupForce, you **must** include this JVM argument:

```bash
--add-opens=java.base/java.nio=ALL-UNNAMED
```

## Why This Is Required

The Snowflake JDBC driver (v3.16.1+) uses Apache Arrow for efficient data transfer. Arrow requires access to internal Java NIO buffer memory addresses, which are restricted in Java 9+ by the module system.

Without this flag, you'll see this error:
```
java.lang.RuntimeException: Failed to initialize MemoryUtil. 
Was Java started with `--add-opens=java.base/java.nio=ALL-UNNAMED`?
```

## How to Launch

### Option 1: Use the provided launch scripts
```bash
# Windows
run.bat

# Linux/Mac
./run.sh
```

### Option 2: Manual launch
```bash
java --add-opens=java.base/java.nio=ALL-UNNAMED -jar target/backupforce-3.1.1-shaded.jar
```

### Option 3: IDE Configuration (IntelliJ IDEA / Eclipse)
Add to VM options:
```
--add-opens=java.base/java.nio=ALL-UNNAMED
```

## External Browser Authentication

When connecting to Snowflake with `externalbrowser` authentication:
1. A browser window will open automatically
2. Complete the SSO/authentication flow in the browser
3. The application will proceed once authentication succeeds

## Troubleshooting

**Problem**: "Failed to initialize MemoryUtil"
- **Solution**: Ensure JVM argument is included

**Problem**: SSL certificate errors
- **Solution**: This is now handled automatically by the JDBC driver with `insecure_mode=true`

**Problem**: Browser doesn't open for Snowflake auth
- **Solution**: Check your OS settings, firewall, or try opening a browser manually

## References
- [Apache Arrow Java Installation](https://arrow.apache.org/docs/java/install.html)
- [Snowflake JDBC Driver Documentation](https://docs.snowflake.com/en/developer-guide/jdbc/jdbc)
