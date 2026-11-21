# Handover & Future Scope: CleanYourTV

## 1. Project Handover Summary
CleanYourTV has been successfully tailored and deployed across two production environments:
1. **OnePlus TV Y1S Edge 43" (Model D00, codename `Dubai`, Android TV 11, API 30)**:
   - Configured with a dedicated 6-column flat home screen grid (holding up to 3 lines / 18 cards) anchored towards the lower half of the screen.
   - Sizing scaled up by 15% (72dp squircle box, 32dp glyph, 11.5sp font) optimized for 43" 1080p viewing.
   - Elimination of system stub duplicates (`com.mediatek.tv.service` / `netflixobserver`).
   - Auto-filling app gaps upon removal.
   - Interactive remote tile reordering (`Move` mode via D-pad ◄ / ►) and options menu on OK long-press.
   - 10 curated non-Marvel 1080p wallpapers with integrated wallpaper switcher tile.
   - Real-time Open-Meteo Weather & AQI status pill.
   - AdGuard Private DNS enabled.
2. **TCL 65Q6C QD-Mini LED (G08, Android 14, API 34)**:
   - 65" 4K Google TV debloated with compact macOS-style dock, dual-zone contrast, and full module-card suite (Capacity Stats, Sleep Timer, Sources, Brightness, Quiet Hours, Weather).

---

## 2. Key Device Coordinates & Local Assets

### 2.1 OnePlus TV Y1S Edge 43"
- **Target Device:** OnePlus TV Y1S Edge 43" (Model `D00`, Build `RTM1.210315.027`, Android TV 11 / API 30)
- **Device Guide:** [`work/README_ONEPLUS_43.md`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/README_ONEPLUS_43.md)
- **Safe Recovery Script:** [`work/restore_oneplus_43.ps1`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/restore_oneplus_43.ps1)
- **Debloat List:** [`work/debloat_oneplus_43.txt`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/debloat_oneplus_43.txt)
- **Device Backup Folder:** `backup/oneplus-43y1s/`

### 2.2 TCL 65Q6C QD-Mini LED
- **Target Device:** TCL 65Q6C QD-Mini LED (G08 / tcl9618, Android 14 / API 34)
- **Device Guide:** [`work/README_Q6C.md`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/README_Q6C.md)
- **Safe Recovery Script:** [`work/restore_q6c.ps1`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/restore_q6c.ps1)
- **Debloat List:** [`work/debloat_q6c.txt`](file:///c:/Users/Prateek/AntiGravity_Project/CleanYourTV/work/debloat_q6c.txt)

---

## 3. Maintenance & Common Operations

### 3.1 Building & Sideloading the Launcher
```powershell
cd launcher
.\gradlew.bat assembleRelease
cd ..

# Verify size gate (< 512 KB):
(Get-Item "launcher\app\build\outputs\apk\release\app-release.apk").Length

# Sideload to TV:
tools\platform-tools\adb.exe connect <tv ip>:5555
tools\platform-tools\adb.exe -s <tv ip>:5555 install -r launcher\app\build\outputs\apk\release\app-release.apk
tools\platform-tools\adb.exe -s <tv ip>:5555 shell "am force-stop com.gathin.tvhome && am start -n com.gathin.tvhome/.MainActivity"
```

### 3.2 Sideloading the Weather Addon
```powershell
cd addons\weather
.\gradlew.bat assembleRelease
cd ..\..
tools\platform-tools\adb.exe -s <tv ip>:5555 install -r addons\weather\app\build\outputs\apk\release\app-release.apk
```

### 3.3 Managing Wallpapers
Wallpapers are stored on the device at:
- `/sdcard/Wallpapers/`
- `/sdcard/Android/data/com.gathin.tvhome/files/Wallpapers/`

To push new wallpapers:
```powershell
tools\platform-tools\adb.exe -s <tv ip>:5555 push path\to\wallpaper.jpg /sdcard/Wallpapers/
```
The in-launcher **Wallpaper** tile allows instant cycling and locking across reboots.

### 3.4 Full Emergency Restore
If the device needs to be restored to stock at any time:
```powershell
# For OnePlus 43":
powershell -ExecutionPolicy Bypass -File work\restore_oneplus_43.ps1 -TvHost <tv ip>

# For TCL 65":
powershell -ExecutionPolicy Bypass -File work\restore_q6c.ps1 -TvHost <tv ip>
```

---

## 4. Future Scope & Roadmap for Forking Agent

If you are continuing development from this repository, here is the prioritized roadmap:

### 4.1 Sideloading Missing Streaming Services
- The user requested Tata Play Fiber, Zee5, and SonyLIV. Since these are not pre-installed system apps on this OnePlus model, they should be installed from the Google Play Store on the TV or sideloaded via clean APKs.
- Once installed, the launcher automatically detects and displays them in the home screen grid, and they can be reordered using the built-in `Move` feature.

### 4.2 Hardware Remote Button Remapping (`HomeKeyService`)
- CleanYourTV includes an accessibility helper (`com.gathin.tvhome.HomeKeyService`) capable of intercepting vendor remote buttons (such as Netflix, Prime Video, Hotstar, SonyLIV, or YouTube dedicated keys).
- In this session, button remapping was intentionally deferred per user request.
- To implement:
  1. Capture Linux scancodes using `getevent -lt /dev/input/eventX` while pressing remote keys.
  2. Map keycodes inside `HomeKeyService.java` to launch target intents.
  3. Enable the service in TV Settings -> Device Preferences -> Accessibility.

### 4.3 Retro Gaming & Emulators
- Retro gaming integration (e.g., NDS emulator DraStic, RetroArch, PPSSPP) was also deferred for this build.
- To implement:
  1. Build or sideload the emulator APK.
  2. Map Bluetooth controller pairing or remote D-pad controls.
  3. Place ROM shortcuts directly on the launcher home screen or inside an add-on card.

### 4.4 Additional Add-ons & Widgets
- Module cards in `addons/` can be independently compiled and deployed without increasing the core launcher binary size:
  - `capacity-stats`: RAM and storage monitor.
  - `sleep-timer`: Auto-power off overlay.
  - `sources`: HDMI input selector.
  - `brightness`: Panel backlight override.

---

## 5. Security & Contribution Invariants
- **No Internet in Core Launcher:** The core launcher (`launcher/app/src/main/AndroidManifest.xml`) must **never** request `android.permission.INTERNET`.
- **APK Size Gate:** Release APK must stay strictly $\le 524,288$ Bytes (512 KB).
- **Non-Destructive Debloat:** Packages are disabled with `pm disable-user --user 0`, never uninstalled or unrecoverable.
- **Privacy Guard:** Never commit device serial numbers, MAC addresses, ADB keys, or Wi-Fi names to git history.
