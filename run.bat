@echo off
REM BackupForce Launcher with Snowflake JDBC Support
REM Includes required JVM arguments for Apache Arrow memory access

echo Starting BackupForce v3.1.1...
echo.

REM Check if JAR exists
if not exist "target\backupforce-3.1.1-shaded.jar" (
    echo ERROR: JAR file not found!
    echo Please run 'mvn package' first.
    pause
    exit /b 1
)

REM Launch with required JVM arguments
REM --add-opens=java.base/java.nio=ALL-UNNAMED: Required for Snowflake JDBC Arrow format
java --add-opens=java.base/java.nio=ALL-UNNAMED ^
     -jar target\backupforce-3.1.1-shaded.jar

if errorlevel 1 (
    echo.
    echo Application exited with error code %errorlevel%
    pause
)
