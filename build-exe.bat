@echo off
REM BackupForce - Native Windows Executable Builder
REM Developed by Victor Felisbino

echo ========================================
echo BackupForce Native Executable Builder
echo ========================================
echo.

REM Step 1: Clean and build the JAR
echo [1/3] Building application JAR...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)
echo       Build successful!
echo.

REM Step 2: Create custom JRE with jlink
echo [2/3] Creating custom Java runtime...
jlink --add-modules java.base,java.desktop,java.logging,java.sql,java.xml,java.naming,java.prefs,jdk.crypto.ec,jdk.unsupported ^
      --strip-debug ^
      --no-man-pages ^
      --no-header-files ^
      --compress=2 ^
      --output target/java-runtime
if errorlevel 1 (
    echo ERROR: jlink failed! Make sure you're using JDK 14+
    pause
    exit /b 1
)
echo       Custom runtime created!
echo.

REM Step 3: Create native installer with jpackage
echo [3/3] Creating Windows executable...
jpackage --input target ^
         --name BackupForce ^
         --main-jar BackupForce.jar ^
         --main-class com.backupforce.Launcher ^
         --type exe ^
         --dest target/installer ^
         --runtime-image target/java-runtime ^
         --app-version 2.0.0 ^
         --vendor "Victor Felisbino" ^
         --description "Salesforce Backup Tool with Windows 11 UI" ^
         --win-dir-chooser ^
         --win-menu ^
         --win-shortcut ^
         --win-menu-group BackupForce
if errorlevel 1 (
    echo ERROR: jpackage failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS! Executable created at:
echo target\installer\BackupForce-2.0.0.exe
echo ========================================
echo.
echo You can now distribute this .exe file.
echo Users don't need Java installed!
echo.
pause
