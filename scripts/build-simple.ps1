# BackupForce - Simple EXE Wrapper (Works with Java 11)
# Uses Launch4j-style exe creation without external tools

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BackupForce Simple Executable Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Build the JAR
Write-Host "[1/2] Building JAR..." -ForegroundColor Yellow
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "      Build successful!" -ForegroundColor Green
Copy-Item target\BackupForce.jar . -Force
Write-Host ""

# Create a simple launcher script that hides console
Write-Host "[2/2] Creating launcher..." -ForegroundColor Yellow

# Create VBS launcher (hides console window)
$vbsContent = @'
Set oShell = CreateObject("WScript.Shell")
oShell.Run "javaw.exe -jar BackupForce.jar", 0, false
'@
Set-Content -Path "BackupForce.vbs" -Value $vbsContent

# Create PowerShell launcher
$ps1Content = @'
# BackupForce Launcher
$javaw = "javaw.exe"
Start-Process -FilePath $javaw -ArgumentList "-jar","BackupForce.jar" -WindowStyle Hidden
'@
Set-Content -Path "BackupForce-Launch.ps1" -Value $ps1Content

# Create batch launcher
$batContent = @'
@echo off
start javaw.exe -jar BackupForce.jar
exit
'@
Set-Content -Path "BackupForce.bat" -Value $batContent

Write-Host "      Launchers created!" -ForegroundColor Green
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "Created launch options:" -ForegroundColor Green
Write-Host "  1. BackupForce.vbs      (No console - RECOMMENDED)" -ForegroundColor Cyan
Write-Host "  2. BackupForce-Launch.ps1 (PowerShell)" -ForegroundColor Cyan
Write-Host "  3. BackupForce.bat        (Console window)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "RECOMMENDED: Double-click BackupForce.vbs to launch" -ForegroundColor Yellow
Write-Host ""
Write-Host "To create true .exe (no Java required):" -ForegroundColor White
Write-Host "  1. Download JDK 17: https://adoptium.net/" -ForegroundColor White
Write-Host "  2. Run: .\build-exe.ps1" -ForegroundColor White
Write-Host ""
