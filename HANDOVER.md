# Handover & Future Scope: CleanYourTV

## 1. Project Handover Summary
CleanYourTV is in a stable, debloated, and fully operational production state on the target TCL 65Q6C QD-Mini LED TV (Android 14, MediaTek MT9653 / G08 platform). The custom native launcher (`com.gathin.tvhome`) is set as default Home, running with the V2 macOS/Apple TV-style minimalist dock, centered 14dp squircle focus spotlight, 20% compacted layout, persistent static wallpaper, and dual-zone adaptive contrast. The interactive Weather add-on (`addons/weather`) provides real-time conditions, hourly trends, AQI, and 7-day interactive forecasting.

---

## 2. Key Device Coordinates & Local Assets
- **Target TV Model:** TCL 65Q6C QD-Mini LED (G08 / tcl9618, Android 14, SDK 34)
- **Local Workspace Setup:** `c:\Users\Prateek\AntiGravity_Project\CleanYourTV`
- **Device-Specific Guide:** [`work/README_Q6C.md`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/README_Q6C.md)
- **Safe Recovery Script:** [`work/restore_q6c.ps1`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/restore_q6c.ps1)
- **Active Debloat Manifest:** [`work/debloat_q6c.txt`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/debloat_q6c.txt)

---

## 3. Maintenance & Common Operations

### 3.1 Rebuilding & Sideloading the Launcher
```powershell
cd launcher
.\gradlew.bat assembleRelease
cd ..
$env:TV_HOST = "<tv ip>"
tools\venv\Scripts\python.exe tools\tv.py --push launcher\app\build\outputs\apk\release\app-release.apk /data/local/tmp/app.apk
tools\venv\Scripts\python.exe tools\tv.py "pm install -r /data/local/tmp/app.apk; rm -f /data/local/tmp/app.apk"
```

### 3.2 Rebuilding & Sideloading the Weather Addon
```powershell
cd addons\weather
.\gradlew.bat assembleRelease
cd ..\..
$env:TV_HOST = "<tv ip>"
tools\venv\Scripts\python.exe tools\tv.py --push addons\weather\app\build\outputs\apk\release\app-release.apk /data/local/tmp/addon.apk
tools\venv\Scripts\python.exe tools\tv.py "pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk"
```

### 3.3 Switching Wallpapers or Themes
```powershell
# Push a specific theme:
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> themes\scenes

# Reset to default built-in configuration:
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> -Reset
```

### 3.4 Full Emergency Restore to Stock
```powershell
powershell -ExecutionPolicy Bypass -File work\restore_q6c.ps1 -TvHost <tv ip>
```

---

## 4. Future Scope & Roadmap

### 4.1 Launcher Enhancements
1. **Dock App Reordering via Remote Long-Press:**
   - Allow users to long-press the D-pad OK button on any dock icon to enter an "Edit Mode", enabling left/right reordering without touching configuration files.
2. **Custom App Folders / Categories:**
   - Group installed streaming apps vs utility apps vs retro games into sleek popover folders.
3. **Screensaver Coordinator:**
   - Integrate with the Photo Prints / Photo screensaver add-on to smoothly transition into ambient gallery mode after configurable idle periods (e.g. 3, 5, or 10 minutes).

### 4.2 Weather Add-on Enhancements
1. **Precipitation Probability & Wind Direction:**
   - Add rain chance percentages (`☔ %`) and wind vector indicators to the hourly forecast trend strip.
2. **Severe Weather Alert Banners:**
   - Highlight high-severity weather alerts or extreme AQI warnings as subtle accent badges on the weather card.
3. **Dynamic Day/Night Weather Icons:**
   - Differentiate between clear day sun and clear night moon in the hourly breakdown based on local sunrise/sunset calculations.

### 4.3 Add-on Ecosystem Expansion
1. **Home Assistant Quick-Pills:**
   - Lightweight module card displaying smart home scenes (e.g. "Movie Night", "Dim Lights") toggled directly with the remote.
2. **Bluetooth Audio Device Quick Switcher:**
   - Add-on to switch between TV internal speakers, soundbar, and Bluetooth headphones with a single click.

---

## 5. Security & Contribution Invariants
- **No Internet in Core Launcher:** Never add `INTERNET` permission to `launcher/app/src/main/AndroidManifest.xml`.
- **Size Gate:** Keep release APK below 512 KB.
- **Privacy:** Never commit device identifiers (IPs, MAC addresses, SSIDs, serial numbers) to public git repositories.
