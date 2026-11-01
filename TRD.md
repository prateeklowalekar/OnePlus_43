# Technical Requirements Document (TRD): CleanYourTV

## 1. Technical Stack & Build Environment

| Component | Target / Specification |
|---|---|
| **Language** | Java 17 (Pure Android Java, zero Kotlin runtime overhead) |
| **Android SDK Support** | `minSdk 26` (Android 8.0 Oreo) through `targetSdk 34` (Android 14) |
| **Build Tools** | Android Build Tools `34.0.0`, Gradle 8.4+ |
| **Core Libraries** | `androidx.recyclerview:recyclerview:1.3.2`, `androidx.dynamicanimation:dynamicanimation:1.0.0` |
| **Network Permission** | Explicitly omitted (`android.permission.INTERNET` is **forbidden** in launcher) |
| **Binary Size Gate** | Release APK must stay strictly $\le 524,288$ Bytes (512 KB) |
| **Memory Allocation** | Heap consumption $\le 15$ MB steady-state RAM |

---

## 2. Technical Constraints & Architecture Invariants

### 2.1 Launcher Security & Sandbox
- The launcher runs with standard application sandbox privileges (`UID 10xxx`).
- Does not request root, system UID, or special accessibility bypasses.
- Interacts with installed applications solely via `PackageManager.queryIntentActivities()` matching `Intent.ACTION_MAIN` with `Intent.CATEGORY_LEANBACK_LAUNCHER` and `Intent.CATEGORY_LAUNCHER`.

### 2.2 Focus Navigation & View Traversal
- **Focus Leak Prevention:** To eliminate random focus jumping when traversing dock items, the top control bar container enforces:
  ```xml
  android:descendantFocusability="blocksDescendants"
  ```
  during horizontal scrolling. Direct navigation to top-bar actions occurs solely when an explicit `DPAD_UP` key is received from the top-most row.
- **Hardware Remote Key Handling:**
  - Standard Android `KeyEvent` handling: `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_DOWN`, `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT`, `KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER`, `KEYCODE_BACK`.
  - Back key consumption: pressing back on any non-dock view or submenu returns focus immediately to the main app dock without exiting Home.

---

## 3. Weather Add-on Technical Specifications (`addons/weather`)

### 3.1 REST Endpoints (Open-Meteo)
- **Forecast Endpoint:**
  ```http
  GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code&hourly=temperature_2m,relative_humidity_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7
  ```
- **Air Quality Endpoint:**
  ```http
  GET https://air-quality-api.open-meteo.com/v1/air-quality?latitude={lat}&longitude={lon}&hourly=us_aqi&timezone=auto
  ```

### 3.2 Threading & Network Lifecycle
- Requests run strictly in background daemon threads (`ExecutorService`) while the activity is in foreground (`onResume`).
- Network queries are automatically halted when the activity goes into background (`onPause` / `onStop`).
- Zero background polling or persistent alarms; queries trigger on view presentation or manual city change.

---

## 4. Debloat Script Verification Engine (`tools/check.py`)
- Every debloat list must pass automated validation:
  1. No duplicate package entries.
  2. Alphabetical ordering within vendor sections.
  3. Strict exclusion of protected core system packages:
     - `com.android.systemui`, `com.android.shell`, `com.android.phone`
     - `com.android.settings`, `com.android.tv.settings`, `android`
     - `com.google.android.permissioncontroller`, `com.google.android.packageinstaller`
  4. Underscore/dash naming compliance.
