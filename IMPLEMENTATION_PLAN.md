# Implementation Plan and History: CleanYourTV

## 1. Evolution & Milestones

### Phase 1: Foundation & Safe Debloat Baseline (V1) [COMPLETED]
- [x] Initial device probe and backup automation (`tools/tv.py`, `tools/disable.sh`).
- [x] Clean, zero-network native Android TV launcher (`com.gathin.tvhome`).
- [x] Base debloat validation for TCL G08 platform (`debloat_q6c.txt`).
- [x] Initial module card apps: Capacity Stats, HDMI Sources, Sleep Timer.

### Phase 2: Ergonomic Refinement & macOS/Apple TV Style Dock (V2) [COMPLETED]
- [x] **20% UI Compaction:** Compacted dock items from 110dp × 140dp down to 88dp × 108dp to liberate screen real estate.
- [x] **Centered 14dp Squircle Illumination:** Refactored focus glow drawable (`v2_spotlight_glow.xml`) into a centered squircle spotlight surrounding the focused app icon.
- [x] **Multiline Title Wrapping:** Implemented 2-line auto-wrapping text views to eliminate truncated titles on long app names.
- [x] **Inputs Button Removal:** Deprecated and removed top-bar Inputs trigger button to streamline the status bar.
- [x] **Static Wallpaper Retention:** Replaced random boot wallpaper shuffling with persistent SharedPreferences state locking.
- [x] **Dual-Zone Adaptive Contrast:** Added wallpaper luminance analysis dynamically inverting icons and fonts to dark charcoal on high-brightness artwork.

### Phase 3: Focus Isolation & Navigation Hardening [COMPLETED]
- [x] **Top-Bar Focus Isolation:** Set `android:descendantFocusability="blocksDescendants"` on `topBar` during dock scrolling, eliminating button flicker and accidental jumps to the Wi-Fi icon.
- [x] **Weather Widget Flash Fix:** Isolated the weather widget card during dock row paging to prevent split-second focus flashing.

### Phase 4: Weather Add-on Overhaul (`addons/weather`) [COMPLETED]
- [x] **Hourly Forecast Trend Strip:** Added remaining hours of today with temperature bar, relative humidity (`💧 %`), and US AQI colored pill.
- [x] **Interactive 7-Day Forecast:** Integrated focusable, clickable day cards allowing users to inspect full 24-hour hourly curves for any upcoming day.
- [x] **Unit Testing & Packaging:** Verified release assembly and test suites via Gradle (`./gradlew test assembleRelease`).

---

## 2. Verification & Validation Matrix

| Test Suite / Target | Command | Result |
|---|---|---|
| **Launcher Unit Tests** | `cd launcher && ./gradlew testReleaseUnitTest` | **PASSED** (0 errors) |
| **Launcher Lint** | `cd launcher && ./gradlew lintRelease` | **PASSED** (0 errors) |
| **APK Size Gate** | `stat -c %s launcher/app/build/outputs/apk/release/app-release.apk` | **PASSED** (< 512 KB) |
| **Weather Addon Tests** | `cd addons/weather && ./gradlew test` | **PASSED** (0 errors) |
| **Live Device ADB Run** | `tools/tv.py --screenshot shots/tv_weather_day2.png` | **VERIFIED** on TCL 65Q6C |
