#!/bin/bash
# BackupForce Launcher with Snowflake JDBC Support
# Includes required JVM arguments for Apache Arrow memory access

echo "Starting BackupForce v3.1.1..."
echo

# Check if JAR exists
if [ ! -f "target/backupforce-3.1.1-shaded.jar" ]; then
    echo "ERROR: JAR file not found!"
    echo "Please run 'mvn package' first."
    exit 1
fi

# Launch with required JVM arguments
# --add-opens=java.base/java.nio=ALL-UNNAMED: Required for Snowflake JDBC Arrow format
java --add-opens=java.base/java.nio=ALL-UNNAMED \
     -jar target/backupforce-3.1.1-shaded.jar

exit_code=$?
if [ $exit_code -ne 0 ]; then
    echo
    echo "Application exited with error code $exit_code"
fi

exit $exit_code
