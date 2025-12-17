# Salesforce OAuth Setup Helper for BackupForce
# This script helps you configure OAuth credentials

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BackupForce OAuth Setup Helper" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "First, you need to create a Connected App in Salesforce:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Log in to Salesforce" -ForegroundColor White
Write-Host "2. Go to Setup -> App Manager" -ForegroundColor White
Write-Host "3. Click 'New Connected App'" -ForegroundColor White
Write-Host "4. Fill in basic info (name: BackupForce OAuth)" -ForegroundColor White
Write-Host "5. Enable OAuth Settings:" -ForegroundColor White
Write-Host "   - Callback URL: http://localhost:8888/oauth/callback" -ForegroundColor White
Write-Host "   - Scopes: api, refresh_token, id" -ForegroundColor White
Write-Host "6. Save and get your Consumer Key and Secret" -ForegroundColor White
Write-Host ""

$choice = Read-Host "Have you created the Connected App? (y/n)"
if ($choice -ne 'y') {
    Write-Host ""
    Write-Host "Please create the Connected App first, then run this script again." -ForegroundColor Yellow
    Write-Host "For detailed instructions, see OAUTH_SETUP.md" -ForegroundColor Yellow
    exit
}

Write-Host ""
Write-Host "Great! Now let's configure your credentials." -ForegroundColor Green
Write-Host ""

$clientId = Read-Host "Enter your Consumer Key (Client ID)"
$clientSecret = Read-Host "Enter your Consumer Secret" -AsSecureString
$clientSecretPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($clientSecret)
)

Write-Host ""
Write-Host "Choose how to store these credentials:" -ForegroundColor Cyan
Write-Host "1. Environment Variables (recommended for testing)" -ForegroundColor White
Write-Host "2. Update SalesforceOAuthServer.java (permanent)" -ForegroundColor White
Write-Host ""

$method = Read-Host "Enter your choice (1 or 2)"

if ($method -eq "1") {
    # Set environment variables for current session
    [Environment]::SetEnvironmentVariable("SFDC_CLIENT_ID", $clientId, "User")
    [Environment]::SetEnvironmentVariable("SFDC_CLIENT_SECRET", $clientSecretPlain, "User")
    
    Write-Host ""
    Write-Host "✓ Environment variables set!" -ForegroundColor Green
    Write-Host ""
    Write-Host "IMPORTANT: You need to restart VS Code or your terminal for the" -ForegroundColor Yellow
    Write-Host "environment variables to take effect." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Or run these commands in your current session:" -ForegroundColor Cyan
    Write-Host "  `$env:SFDC_CLIENT_ID='$clientId'" -ForegroundColor White
    Write-Host "  `$env:SFDC_CLIENT_SECRET='$clientSecretPlain'" -ForegroundColor White
    
    # Also set for current session
    $env:SFDC_CLIENT_ID = $clientId
    $env:SFDC_CLIENT_SECRET = $clientSecretPlain
    Write-Host ""
    Write-Host "✓ Also set for current PowerShell session" -ForegroundColor Green
    
} elseif ($method -eq "2") {
    # Update the Java file
    $javaFile = "src\main\java\com\backupforce\auth\SalesforceOAuthServer.java"
    
    if (Test-Path $javaFile) {
        $content = Get-Content $javaFile -Raw
        
        # Replace CLIENT_ID
        $content = $content -replace 'private static final String CLIENT_ID = .*?;', 
            "private static final String CLIENT_ID = `"$clientId`";"
        
        # Replace CLIENT_SECRET
        $content = $content -replace 'private static final String CLIENT_SECRET = .*?;',
            "private static final String CLIENT_SECRET = `"$clientSecretPlain`";"
        
        Set-Content $javaFile -Value $content
        
        Write-Host ""
        Write-Host "✓ Updated SalesforceOAuthServer.java" -ForegroundColor Green
        Write-Host ""
        Write-Host "Now rebuild the application:" -ForegroundColor Cyan
        Write-Host "  mvn clean package -DskipTests" -ForegroundColor White
        Write-Host "  Copy-Item target\BackupForce.jar . -Force" -ForegroundColor White
    } else {
        Write-Host ""
        Write-Host "ERROR: Could not find $javaFile" -ForegroundColor Red
        Write-Host "Make sure you're running this script from the backupForce3 directory" -ForegroundColor Red
    }
} else {
    Write-Host ""
    Write-Host "Invalid choice. Please run the script again." -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Setup Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "You can now use the OAuth login feature in BackupForce!" -ForegroundColor Green
Write-Host ""
