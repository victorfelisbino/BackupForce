# Screenshots for BackupForce Website

This folder is for app screenshots to display on the website.

## Recommended Screenshots

Add these screenshots to make the website shine:

1. **dashboard.png** - The main dashboard view
2. **backup.png** - The backup interface with object selection
3. **restore.png** - The restore/transformation screen
4. **settings.png** - Database settings configuration

## Recommended Dimensions

- **Width:** 1200px (minimum 900px)
- **Height:** Auto (but aim for ~700px for consistency)
- **Format:** PNG or WebP for best quality
- **DPI:** 2x (retina) if possible

## How to Add Screenshots

1. Take screenshots of the running app
2. Save them in this folder with the names above
3. Update `index.html` to reference the images:

```html
<!-- Replace the placeholder divs with actual images -->
<div class="preview-slide active" id="preview-dashboard">
    <img src="images/dashboard.png" alt="BackupForce Dashboard" class="preview-image">
</div>
```

## Tips for Great Screenshots

- Use a clean Salesforce org with sample data
- Show the app with a connected state if possible
- Capture the full window including the custom title bar
- Make sure the mountain background is visible
