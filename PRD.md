# Product Requirements Document (PRD): CleanYourTV

## 1. Executive Summary
Modern Smart TVs (Google TV, Android TV, PatchWall, VIDAA, Fire OS) ship with intrusive full-screen video ads, autoplay promotions, telemetry services, and manufacturer bloatware that consume up to 70% of available RAM and degrade system performance. 

**CleanYourTV** transforms bloated Android TVs into ultra-clean, privacy-respecting, high-performance entertainment hubs. It provides:
1. A zero-bloat, lightweight native launcher (`com.gathin.tvhome`) with no network access, zero background services, and sub-512 KB footprint.
2. A safe, non-destructive debloat procedure using ADB user-space disabling (`pm disable-user --user 0`).
3. An extensible, sandboxed module-card add-on system for widgets (Weather, Capacity Stats, HDMI Sources, Sleep Timer, Brightness, Quiet Hours).
4. A luxury glass / minimalist aesthetic with dynamic focus illumination, adaptive contrast, and TV remote D-pad ergonomics.

---

## 2. Problem Statement & User Persona

### 2.1 Problems
- **Aggressive Advertising:** 80% of TV home screen real estate is reserved for promotional banners, sponsored carousels, and unmuted video trailers.
- **Hardware Starvation:** Low-tier to mid-range smart TVs (typically 1 GB to 2 GB RAM, Cortex-A53/A73 quad-core SoCs) stutter, lag, and drop frames under heavy stock launcher weight.
- **Privacy & Telemetry:** Continuous background telemetry logging, voice listening services, and silent partner app installations.
- **App Clutter:** Inability to easily hide or uninstall pre-bundled manufacturer apps (demo modes, hotel menus, shopping portals).

### 2.2 Target Users
- **Power Users & Enthusiasts:** Users who want instant, responsive TV navigation without distraction.
- **Families & Non-Technical Households:** Remote control must remain 100% intuitive with no broken HDMI inputs or disabled remote keys.
- **Minimalists:** Users who appreciate premium typography, high-resolution static or curated wallpapers, and clean app docks.

---

## 3. Product Principles
1. **Safety First (Non-Destructive):** Never uninstall (`pm uninstall`), root, or flash. Disable packages using `pm disable-user --user 0` so any operation can be reversed instantly via `tools/restore.sh` / `restore.ps1`.
2. **Zero Core Network Access:** The launcher (`com.gathin.tvhome`) has **no `INTERNET` permission**. It never makes network calls, tracks telemetry, or renders web views.
3. **Hardware Guardian:** Core TV functions (HDMI switcher, Live TV/tuner, remote Bluetooth pairing, on-screen keyboard, Play Store, Wi-Fi stack) are strictly protected.
4. **Lean & Fast:** APK binary size stays under 512 KB. Cold boot to Home is instantaneous (< 300 ms). Memory footprint remains under 15 MB RAM.
5. **Decoupled Architecture:** Widgets and extended features live strictly as standalone add-on applications adhering to the module-card contract, never bloating the core launcher.

---

## 4. Functional Requirements

### FR-1: Home Screen Launcher Core
- **App Grid & Dock:** Displays installed user applications directly on the home screen in an ergonomic 6-column grid accommodating up to 3 lines (18 apps) without pagination glitches or hidden menus. Extra apps naturally scroll down.
- **Auto-Fill Gaps:** Gaps left by removed or uninstalled applications automatically pack forward.
- **Interactive Remote App Options & Reordering:** Long-pressing the remote's OK/Center button triggers an options modal:
  - `Open`: Launches target application.
  - `Move`: Enables interactive D-pad (◄ / ►) tile swapping with immediate persistence to `dock_prefs`.
  - `Uninstall`: Initiates uninstallation intent for eligible user applications.
- **Focus Spotlight Illumination:** Dynamic 16dp squircle glow highlighting the active focused app with smooth spring scaling (DynamicAnimation).
- **Scaled Ergonomics:** 15% enlarged touchpoints (72dp squircle box, 32dp glyph, 11.5sp font) optimized for 43" 1080p viewing from 8-10 feet away.
- **Multiline Label Support:** High-legibility multiline text wrapping for long application titles without clipping.
- **Top Bar System Controls:**
  - Clock display with time, date, and live Open-Meteo Weather & AQI status pill.
  - Quick action buttons: Wi-Fi status modal, Settings trigger, and Wallpaper cycle trigger.
  - Dynamic focus isolation (`blocksDescendants` during grid scrolling) to guarantee zero D-pad focus flicker or accidental header jumping.
- **Adaptive Contrast Engine:** Auto-detects wallpaper luminance and dynamically switches icon rendering and text color between crisp white (dark wallpapers) and dark charcoal (light wallpapers).
- **Persistent Wallpaper State:** User-selected wallpapers remain locked across system reboots and app restarts without randomizing.

### FR-2: Safe Debloat & Recovery Framework
- **Automated Device Inspection:** `tools/tv.py --probe` reads Android version, chipset, build fingerprint, and active packages.
- **Pre-execution Backup:** Automatic backup of package list, disabled list, system properties (`getprop`), and secure settings into `backup/`.
- **Targeted Debloat Scripts:** Brand-tested debloat definitions across TCL, Sony, Hisense, Xiaomi, Philips, Google Chromecast, and others.
- **Full Reversibility:** One-click restoration restoring all disabled packages and recovering default stock launchers.

### FR-3: Sandboxed Add-on Ecosystem (Module Cards)
- Add-ons run in their own sandboxed processes without direct coupling to the launcher.
- **Weather Add-on (`addons/weather`):**
  - City search via TV on-screen keyboard.
  - Live temperature, feels-like, and weather conditions from Open-Meteo.
  - Hourly forecast strip displaying remaining hours of today with temperature trend bar, relative humidity, and US AQI status pill.
  - Interactive 7-day forecast cards: focus and click any day to view that day's complete 24-hour prediction curve.
- **System Monitors:** Real-time RAM & internal storage monitor (`capacity-stats`).
- **HDMI & Hardware Controls:** CEC-named HDMI source switcher (`sources`), warm night mode & low-light panel dimmer (`brightness`), sleep timer with auto-standby (`sleep-timer`), and late-night volume limiter (`quiet-hours`).

---

## 5. Non-Functional Requirements
- **Compatibility:** Android TV / Google TV OS versions 8.0 (Oreo / API 26) through Android 14 (API 34).
- **Input Methods:** 100% remote control operable via standard 5-way D-pad (Up, Down, Left, Right, OK/Select, Back, Home).
- **Performance:** 60 FPS silky-smooth scroll animation on quad-core 1.1 GHz SoCs with 1 GB RAM.
- **Stability:** Zero crash tolerance during rapid remote key presses or network disconnections.
