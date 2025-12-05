# BackupForce - Quick Portable App Builder
# Creates a simple portable executable

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BackupForce Quick Executable Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Build the JAR
Write-Host "[1/2] Building JAR..." -ForegroundColor Yellow
mvn clean package -DskipTests -q
Copy-Item target\BackupForce.jar . -Force
Write-Host "      Build successful!" -ForegroundColor Green
Write-Host ""

# Create portable app folder
Write-Host "[2/2] Creating portable app..." -ForegroundColor Yellow
$portableDir = "BackupForce-Portable"
if (Test-Path $portableDir) {
    Remove-Item -Recurse -Force $portableDir
}
New-Item -ItemType Directory -Path $portableDir | Out-Null

# Copy JAR
Copy-Item "BackupForce.jar" "$portableDir\" -Force

# Create launcher script
$launcherVBS = @'
Set oShell = CreateObject("WScript.Shell")
oShell.Run "javaw.exe -jar BackupForce.jar", 0, false
'@
Set-Content -Path "$portableDir\BackupForce.vbs" -Value $launcherVBS

# Create launcher BAT (as fallback)
$launcherBAT = @'
@echo off
start javaw.exe -jar BackupForce.jar
exit
'@
Set-Content -Path "$portableDir\BackupForce.bat" -Value $launcherBAT

# Create README
$readme = @'
# BackupForce Portable

## Quick Start
Double-click: **BackupForce.vbs**

## Requirements
Java 11 or higher must be installed on your computer.
Download from: https://adoptium.net/

## Files
- BackupForce.jar - Main application
- BackupForce.vbs - Launcher (recommended - no console)
- BackupForce.bat - Launcher (alternative - shows console)

## Support
Developed by Victor Felisbino
'@
Set-Content -Path "$portableDir\README.txt" -Value $readme

Write-Host "      Portable app created!" -ForegroundColor Green
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "SUCCESS! Portable app at:" -ForegroundColor Green
Write-Host "$portableDir\" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "To distribute:" -ForegroundColor Yellow
Write-Host "  1. Compress '$portableDir' folder to ZIP" -ForegroundColor White
Write-Host "  2. Share the ZIP file" -ForegroundColor White
Write-Host "  3. Users extract and double-click BackupForce.vbs" -ForegroundColor White
Write-Host ""
Write-Host "Note: Users need Java 11+ installed" -ForegroundColor Yellow
Write-Host ""
Write-Host "Launching application..." -ForegroundColor Yellow
Start-Process -FilePath "$portableDir\BackupForce.vbs"
Start-Sleep -Seconds 2
Write-Host "Application launched!" -ForegroundColor Green
Write-Host ""
