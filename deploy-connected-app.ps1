# Deploy BackupForce OAuth Connected App to Salesforce
# This script uses Salesforce CLI to create the Connected App

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Deploy BackupForce Connected App" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if SF CLI is installed
try {
    $version = sf --version 2>&1
    Write-Host "✓ Salesforce CLI found: $version" -ForegroundColor Green
} catch {
    Write-Host "✗ Salesforce CLI not found!" -ForegroundColor Red
    Write-Host "Please install it from: https://developer.salesforce.com/tools/salesforcecli" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Create metadata directory structure
Write-Host "Creating metadata structure..." -ForegroundColor Cyan
$metadataDir = "force-app\main\default\connectedApps"
New-Item -ItemType Directory -Path $metadataDir -Force | Out-Null

# Create Connected App metadata XML
$connectedAppXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<ConnectedApp xmlns="http://soap.sforce.com/2006/04/metadata">
    <label>BackupForce OAuth</label>
    <contactEmail>victor.felisbino@loves.com</contactEmail>
    <oauthConfig>
        <callbackUrl>http://localhost:8888/oauth/callback</callbackUrl>
        <scopes>Api</scopes>
        <scopes>RefreshToken</scopes>
        <scopes>Full</scopes>
    </oauthConfig>
</ConnectedApp>
"@

$xmlPath = "$metadataDir\BackupForce_OAuth.connectedApp-meta.xml"
Set-Content -Path $xmlPath -Value $connectedAppXml -Encoding UTF8
Write-Host "✓ Created Connected App metadata: $xmlPath" -ForegroundColor Green

# Check if already logged in
Write-Host ""
Write-Host "Checking Salesforce authentication..." -ForegroundColor Cyan
$orgList = sf org list --json 2>&1 | ConvertFrom-Json

if ($orgList.result.nonScratchOrgs.Count -eq 0) {
    Write-Host "Not logged in. Opening browser to authenticate..." -ForegroundColor Yellow
    sf login org --instance-url https://loves--dev.sandbox.my.salesforce.com --alias loves-dev --set-default
} else {
    Write-Host "✓ Already authenticated" -ForegroundColor Green
    Write-Host "Orgs:" -ForegroundColor White
    $orgList.result.nonScratchOrgs | ForEach-Object {
        Write-Host "  - $($_.alias): $($_.username)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Deploying Connected App to Salesforce..." -ForegroundColor Cyan
Write-Host ""

# Deploy using metadata API
sf project deploy start --source-dir force-app --target-org loves-dev

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✓ Connected App deployed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Next Steps" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. Go to Setup -> App Manager in Salesforce" -ForegroundColor White
    Write-Host "2. Find 'BackupForce OAuth'" -ForegroundColor White
    Write-Host "3. Click the dropdown -> Manage" -ForegroundColor White
    Write-Host "4. Click 'Manage Consumer Details'" -ForegroundColor White
    Write-Host "5. Copy your Consumer Key and Consumer Secret" -ForegroundColor White
    Write-Host ""
    Write-Host "6. Run the setup script with those values:" -ForegroundColor White
    Write-Host "   .\setup-oauth.ps1" -ForegroundColor Cyan
    Write-Host ""
    
    # Try to open the Setup page
    Write-Host "Opening Salesforce Setup..." -ForegroundColor Cyan
    sf org open --target-org loves-dev --path "/lightning/setup/NavigationMenus/home"
    
} else {
    Write-Host ""
    Write-Host "✗ Deployment failed!" -ForegroundColor Red
    Write-Host ""
    Write-Host "You can deploy manually by:" -ForegroundColor Yellow
    Write-Host "1. Go to Setup -> App Manager" -ForegroundColor White
    Write-Host "2. Click 'New Connected App'" -ForegroundColor White
    Write-Host "3. Fill in the details from the XML file" -ForegroundColor White
    Write-Host ""
}
