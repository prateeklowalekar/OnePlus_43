# OnePlus TV Y1S Edge 43" (Model D00, Dubai)

## Overview
- **Device**: OnePlus TV Y1S Edge 43" (Model `D00`, Codename `Dubai`, Build `RTM1.210315.027`, Android TV 11, API 30)
- **Display**: 1080p FHD output (Physical viewport `1280x720` at 213 tvdpi)
- **Status**: Tested, debloated, and running CleanYourTV launcher (`com.gathin.tvhome`) as default Home.

---

## 1. Debloat Package List

Run using standard user-space disabling:
```powershell
tools\platform-tools\adb.exe connect <tv ip>:5555
```

### Packages Disabled
```text
# Vendor & Partner Bloat
com.lionsgateplay.videoapp
com.jio.web.androidtv
com.spotify.tv.android
com.oneplus.tv.screensaver
com.mediatek.android.leanbacklauncher.partnercustomizer
com.google.android.youtube.tvmusic
com.heytap.tv.filebrowser
com.oneplus.tv.userguide
com.jiostb.jiogames
com.google.android.play.games
com.google.android.videos

# Stock Android TV Ad Launchers
com.google.android.tvlauncher
com.google.android.tvrecommendations
com.oneplus.tv.launcher
```

---

## 2. Recovery Script

To re-enable all original packages and restore the stock OnePlus launcher:

```powershell
$packages = @(
    "com.google.android.tvlauncher",
    "com.google.android.tvrecommendations",
    "com.oneplus.tv.launcher",
    "com.lionsgateplay.videoapp",
    "com.jio.web.androidtv",
    "com.spotify.tv.android",
    "com.oneplus.tv.screensaver",
    "com.mediatek.android.leanbacklauncher.partnercustomizer",
    "com.google.android.youtube.tvmusic",
    "com.heytap.tv.filebrowser",
    "com.oneplus.tv.userguide",
    "com.jiostb.jiogames",
    "com.google.android.play.games",
    "com.google.android.videos"
)

foreach ($pkg in $packages) {
    Write-Host "Re-enabling $pkg..."
    tools\platform-tools\adb.exe -s <tv ip>:5555 shell "pm enable-user --user 0 $pkg"
}
```

---

## 3. Launcher Configuration
- **Columns**: 6 columns (`<integer name="columns">6</integer>`).
- **Layout**: Flat grid displaying up to 3 lines (18 cards) on screen, anchored to the lower half.
- **Sizing**: Scaled up by 15% (72dp squircle box, 32dp glyph icon, 11.5sp font).
- **Long-Press Menu**: Long-pressing the D-pad OK button triggers `Open`, `Move` (interactive ◄ / ► tile reordering), and `Uninstall`.
- **System Duplicates Filter**: Filtered out `com.mediatek.tv.service` (`com.mstar.netflixobserver.NetflixLauncher`).
- **Live TV**: Set to `com.heytap.tv.livetv`.
