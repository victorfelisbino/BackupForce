@echo off
REM BackupForce Launcher
REM Launches the BackupForce JavaFX application

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Java is not installed or not in PATH
    echo Please install Java 11 or higher from https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

REM Set JavaFX module path (adjust if needed)
set JAVAFX_PATH=--module-path "%JAVA_HOME%\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml

REM Launch the application
echo Starting BackupForce...
java -jar BackupForce.jar

if %errorlevel% neq 0 (
    echo.
    echo Failed to start BackupForce
    echo If you see module errors, ensure JavaFX is properly installed
    pause
)
