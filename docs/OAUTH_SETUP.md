# Salesforce Connected App Setup for BackupForce

## Quick Setup (5 minutes)

### Step 1: Create Connected App in Salesforce

1. Log in to your Salesforce org
2. Go to **Setup** (gear icon)
3. In Quick Find, search for **"App Manager"**
4. Click **"New Connected App"**

### Step 2: Fill in Basic Information

- **Connected App Name**: `BackupForce Desktop`
- **API Name**: `BackupForce_Desktop` (auto-filled)
- **Contact Email**: Your email

### Step 3: Enable OAuth Settings

Check the box: **"Enable OAuth Settings"**

- **Callback URL**: `http://localhost:8989/oauth/callback`
- **Selected OAuth Scopes**: Add these scopes:
  - `Access and manage your data (api)`
  - `Perform requests on your behalf at any time (refresh_token, offline_access)`
  - `Access your basic information (id, profile, email, address, phone)`

### Step 4: Additional Settings

- **Require Secret for Web Server Flow**: Uncheck this (for desktop apps)
- **Require Secret for Refresh Token Flow**: Uncheck this
- **Enable PKCE Extension for Supported Authorization Flows**: Check this (recommended)

### Step 5: Save and Get Credentials

1. Click **Save**
2. Click **Continue**
3. You'll see your **Consumer Key** (Client ID)
4. Copy the **Consumer Key** - you'll need this!

### Step 6: Update the Application

Open this file: `src/main/java/com/backupforce/auth/OAuthHelper.java`

Replace this line:
```java
private static final String CLIENT_ID = "3MVG9pRzvMkjMb6lZlt3YjDQwe.hMLg7wFTgMJKvDVdLTEZHvX6P3YHVdvKCLXBPDYdaLnBmkCvfxfL6.FqSY";
```

With:
```java
private static final String CLIENT_ID = "YOUR_CONSUMER_KEY_HERE";
```

You can also remove the CLIENT_SECRET line since we're not using it for desktop apps.

### Step 7: Rebuild and Run

```bash
mvn clean compile javafx:run
```

That's it! Your app will now authenticate with your own Connected App.

---

## Alternative: Use a Configuration File

Instead of hardcoding the client ID, you can store it in `myconfig.properties`:

```properties
oauth.client.id=YOUR_CONSUMER_KEY_HERE
```

This keeps credentials out of the code.
