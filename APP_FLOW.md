# Application Flow and User Journeys: CleanYourTV

## 1. System Boot & Home Activation Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as TV Viewer
    participant OS as Android TV OS (Init / SystemServer)
    participant Home as CleanYourTV (MainActivity)
    participant Prefs as SharedPreferences
    participant Recycler as AppAdapter (RecyclerView)
    participant Addons as Installed Module Cards

    OS->>OS: Device Boot / Wake from Standby
    OS->>Home: Intent (ACTION_MAIN, CATEGORY_HOME)
    Home->>Prefs: Load static wallpaper selection & theme
    Home->>Home: Render cached wallpaper bitmap
    Home->>Home: Calculate background luminance (Adaptive Contrast)
    Home->>Home: Query PackageManager for leanback/launcher apps
    Home->>Addons: Query PackageManager for MODULE_CARD intents
    Home->>Recycler: Populate App Dock & Addon cards
    Recycler->>Recycler: Position focus on primary app (e.g. YouTube)
    Home-->>User: Home Screen ready (< 300 ms)
```

---

## 2. Remote Control D-Pad Navigation Flow

```mermaid
flowchart TD
    Start([Focus on App Dock Card]) --> InputCheck{Remote Key Pressed}
    
    InputCheck -->|DPAD_RIGHT| NextCard[Advance to next App in Dock]
    InputCheck -->|DPAD_LEFT| PrevCard[Move to previous App in Dock]
    
    NextCard --> SpringAnim[Trigger 1.08x Spring Scale & 14dp Spotlight Glow]
    PrevCard --> SpringAnim
    
    InputCheck -->|DPAD_UP| CheckTopBar[Navigate to Top Bar Header]
    CheckTopBar --> FocusWifi[Focus Wi-Fi Button or Settings Icon]
    
    InputCheck -->|DPAD_DOWN| CheckAddons[Navigate to Module Cards / Extended Dock]
    CheckAddons --> FocusAddon[Focus Weather / Stats / Sources]
    
    InputCheck -->|DPAD_CENTER / OK (Click)| LaunchApp[Start Target App Activity via Intent]
    InputCheck -->|DPAD_CENTER / OK (Long Press)| AppOptionsModal[Show App Options: Open / Move / Uninstall]
    AppOptionsModal -->|Select Move| EnterMoveMode[Interactive D-pad ◄ / ► Reordering Mode]
    EnterMoveMode -->|DPAD_CENTER / OK| SaveOrder[Persist New Order to dock_prefs]
    
    FocusWifi -->|OK| ShowWifiModal[Display Wi-Fi Information Overlay]
    FocusAddon -->|OK| LaunchModule[Open Addon Activity e.g. WeatherActivity]
```

---

## 3. Weather Add-on Interactive Flow

```mermaid
sequenceDiagram
    autonumber
    actor Viewer as TV Viewer
    participant Dock as Launcher Dock
    participant Weather as WeatherActivity
    participant Engine as Open-Meteo REST Client
    participant UI as Hourly & 7-Day Forecast UI

    Viewer->>Dock: Clicks Weather Module Card
    Dock->>Weather: Start WeatherActivity Intent
    Weather->>Weather: Check stored location coordinates
    Weather->>Engine: GET /v1/forecast (Temperature, Humidity, WMO Code, AQI)
    Engine-->>Weather: Return JSON Response
    Weather->>UI: Populate Current Conditions (Temp, Feels-like, Conditions)
    Weather->>UI: Render Hourly Forecast strip for remaining hours of today
    Weather->>UI: Render 7-Day Forecast Cards
    
    Viewer->>UI: Navigates D-Pad to Day 2 / Day 3 Card
    Viewer->>UI: Presses OK on selected Day Card
    UI->>UI: Dynamically reload Hourly Strip for that selected 24-hour day
    UI-->>Viewer: Updated temperature curve and weather predictions displayed
```

---

## 4. Debloat & Recovery Execution Flow

```mermaid
flowchart TD
    Admin([User runs tools/disable.ps1]) --> Probe[Connect ADB to TV_HOST:5555]
    Probe --> CheckConnection{Device Connected?}
    CheckConnection -->|No| PromptManual[Prompt user to enable USB/Network Debugging]
    CheckConnection -->|Yes| BackupState[Generate snapshot in backup/ folder]
    
    BackupState --> ReadList[Parse device debloat list e.g. debloat_q6c.txt]
    ReadList --> FilterCore[Enforce Protected Core Packages Rule]
    FilterCore --> DisableLoop[Loop: pm disable-user --user 0 package]
    
    DisableLoop --> VerifyHome[Confirm CleanYourTV is active default HOME]
    VerifyHome --> Complete([Debloat Finished - TV Clean & Responsive])
    
    PromptRestore([Emergency Restore: tools/restore.ps1]) --> RestoreLoop[Loop: pm enable package]
    RestoreLoop --> Restored([Factory State Re-established])
```
