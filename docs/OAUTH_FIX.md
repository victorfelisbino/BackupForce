# OAuth Portability Fix

## Problem
When running BackupForce on different computers, Salesforce OAuth login was failing. The issue was the hardcoded port 1717 could be blocked by:
- Firewall/antivirus software
- Corporate network restrictions
- Port already in use by another application
- Localhost resolution issues

## Solution
Implemented **multi-port fallback** with better error handling and diagnostics:

### 1. Multiple Fallback Ports
Instead of only trying port 1717, the app now tries these ports in order:
- **1717** (primary, Salesforce CLI default)
- **8888** (common development port)
- **3000** (popular web server port)
- **8080** (standard HTTP alternate port)
- **9090** (another common fallback)

### 2. Better Error Messages
If all ports fail, users see a helpful message:
```
Cannot start OAuth server - all ports are blocked.

Tried ports: 1717, 8888, 3000, 8080, 9090

Possible solutions:
1. Check your firewall settings
2. Temporarily disable antivirus
3. Close applications using these ports
4. Run as administrator
```

### 3. Detailed Logging
The app now logs each step with emojis for easy visual parsing:
- ✅ Success: "OAuth server started successfully on port 8888"
- ⚠️ Warning: "Port 1717 is unavailable: Address already in use"
- ❌ Error: "OAuth flow failed: timeout"

### 4. Browser Check
Enhanced browser opening with better error handling:
- Checks if Desktop.browse() is supported
- Catches browser launch failures
- Provides the OAuth URL for manual copying if needed

### 5. Timeout Handling
Clear 5-minute timeout with helpful message:
```
Authentication timeout.

The OAuth callback was not received within 5 minutes.
Please try again and complete the login faster.
```

## Changes Made

### SalesforceOAuthServer.java
```java
// OLD:
private static final int PORT = 1717;
private static final String REDIRECT_URI = "http://localhost:1717/OauthRedirect";

// NEW:
private static final int[] FALLBACK_PORTS = {1717, 8888, 3000, 8080, 9090};
private int activePort = -1;
private String activeRedirectUri = null;
```

### authenticate() method
- Tries each port in sequence until one succeeds
- Stores `activePort` and `activeRedirectUri` when successful
- Binds to `127.0.0.1` instead of default (slightly more reliable)
- Provides detailed error messages for each failure scenario

### exchangeCodeForToken() method
- Uses `activeRedirectUri` instead of hardcoded value
- Ensures the redirect URI matches what Salesforce sent the callback to

## Testing
To test the fix on a different computer:

1. **Copy the updated portable app:**
   ```
   BackupForce-Portable\
   ├── BackupForce.jar  ← Updated with OAuth fix
   ├── BackupForce.vbs  ← Launcher (no console)
   ├── BackupForce.bat  ← Launcher (with console)
   └── README.txt       ← Usage instructions
   ```

2. **Double-click** `BackupForce.vbs` to launch

3. **Watch the console** (if using .bat) or check logs for:
   - Which port successfully started
   - Any firewall/port blocking messages
   - Browser opening confirmation

4. **If OAuth still fails**, check:
   - Windows Firewall (allow Java/BackupForce)
   - Antivirus settings (whitelist the app)
   - Corporate network restrictions
   - Try running as Administrator

## Logs Location
Check application logs for detailed OAuth diagnostics:
- Look for lines containing "OAuth server" 
- Check for port availability warnings
- Verify browser opened successfully

## Fallback Option
If all automatic methods fail, the error message will show the full OAuth URL that can be:
1. Copied manually
2. Pasted into any browser
3. Completed on the same or different computer
4. Authorization code entered manually (future enhancement)

## Next Steps (Optional)
If the multi-port approach still doesn't work in some environments:

1. **Device Flow OAuth**: Use device code flow instead of localhost callback
   - User gets a code to enter
   - No local server needed
   - Works through any network/firewall

2. **Manual Code Entry**: Add a text field for users to paste the authorization code
   - Browser redirects to error page with code in URL
   - User copies code from browser
   - Pastes into app

3. **Windows Firewall Rule**: Auto-create firewall exception during first run
   - Requires admin privileges
   - Only needed once per computer

## Benefits
✅ Works on more computers (tries 5 different ports)  
✅ Clear error messages guide troubleshooting  
✅ Detailed logging helps diagnose issues  
✅ No code changes needed by users  
✅ Backwards compatible (tries 1717 first)  
✅ More professional and robust  

## Updated Files
- `src/main/java/com/backupforce/auth/SalesforceOAuthServer.java` ← Main fix
- `target/BackupForce.jar` ← Rebuilt with fix
- `BackupForce-Portable/BackupForce.jar` ← Updated portable distribution

---
**Build Date**: December 5, 2024  
**Version**: 1.0.0-SNAPSHOT (with OAuth multi-port fix)
