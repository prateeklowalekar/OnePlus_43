# Design Brief: CleanYourTV Visual Language & Aesthetics

## 1. Design Vision & Philosophy
CleanYourTV follows an **Apple TV / macOS inspired minimalist design language**. It prioritizes visual calm, premium typography, content immersion, and high-performance tactile feedback for large screens viewed from 6 to 12 feet away (10-foot UI ergonomics).

### Core Pillars
1. **Wallpaper Primacy:** The background artwork is the visual star. UI elements stay out of the way until requested.
2. **Tactile Focus Feedback:** Smooth physics-based spring scaling and subtle spotlight luminescence communicate the active remote position instantly.
3. **Dual-Zone Adaptive Contrast:** Text and iconography dynamically evaluate underlying luminance to guarantee AAA readability on both pitch-black OLEDs and bright sunny nature photography.
4. **Zero Visual Noise:** No borders, no heavy drop-shadows, no carousel banners, and no unsolicited notifications.

---

## 2. Layout & Spatial System

### 2.1 Screen Dimensions & Safe Zones
- **Target Resolution:** 3840×2160 (4K UHD) and 1920×1080 (FHD).
- **Television Overscan Margins:**
  - Horizontal margin: `48dp` to `64dp` safe zone padding from left/right bezel edges.
  - Vertical margin: `36dp` top margin for status controls; `48dp` bottom margin for the dock.
- **Screen Scaling Profiles:**
  - **4K UHD (65" Profile):** `88dp × 108dp` card dimensions, `54dp` icon, 14dp squircle.
  - **1080p FHD (43" Profile):** `72dp × 72dp` squircle box, `32dp` glyph icon, `11.5sp` typography, and 16dp corner radius. Lowered placement anchors up to 3 lines (18 cards) in the bottom half of the display, preserving the upper wallpaper vista.

---

## 3. Component Design & States

### 3.1 App Dock Item States
Each dock item consists of an icon container, an optional squircle spotlight glow, and a multiline title label.

```
       [Normal State]                     [Focused State]
                                       .-----------------.
                                      /   14dp Squircle   \
                                     |    Spotlight Glow   |
        +---------+                  |     +---------+     |
        |  Icon   |   -- D-Pad ->    |     |  Icon   |     |  (1.08x Spring Scale)
        +---------+                  |     +---------+     |
         YouTube                     \       YouTube       /
                                      '-----------------'
```

- **Unfocused State:**
  - Opacity: 85% icon opacity.
  - Background: 100% transparent.
  - Scale: 1.0x baseline.
- **Focused State:**
  - Scale: Animated smoothly to 1.08x via `SpringAnimation` (stiffness: `SpringForce.STIFFNESS_LOW`, damping: `SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY`).
  - Spotlight Glow: Centered translucent squircle shape (`v2_spotlight_glow.xml`) with a 14dp corner radius, radial cyan/white gradient, and 28% alpha.
  - Label: High-contrast font, multiline text wrapping up to 2 lines (`maxLines="2"`, `ellipsize="end"`).

---

## 4. Typography & Color Palette

### 4.1 Typography
- **Font Family:** `sans-serif-medium` for app titles; `sans-serif` for secondary captions and weather subtext.
- **Size Hierarchy:**
  - App Titles: `13sp`, line spacing `1.1x`.
  - Clock Display: `28sp` bold.
  - Weather Temperature: `36sp` light/medium.
  - Module Card Captions: `11sp`.

### 4.2 Dynamic Color Engine
The launcher calculates the perceptual luminance ($L = 0.299R + 0.587G + 0.114B$) of the wallpaper directly beneath the dock and top bar:

| Mode | Trigger Luminance | Text Color | Icon Tint | Spotlight Alpha |
|---|---|---|---|---|
| **Dark Wallpaper** | $L < 0.55$ | `#FFFFFFFF` (Pure White with subtle text shadow) | Unmodified / Original | 30% soft white/cyan |
| **Light Wallpaper** | $L \ge 0.55$ | `#DE1A1A1A` (87% Charcoal Gray) | Tone-mapped dark glyphs | 20% dark slate |

---

## 5. Weather Add-on UI Specifications
- **Layout:** Horizontal split layout.
  - Left panel: Current temperature hero (`36sp`), feels-like metric, and dynamic weather condition icon.
  - Center strip: Horizontal hourly forecast for remaining hours of today, relative humidity indicator (`💧 %`), and a color-coded US AQI rating pill.
  - Bottom strip: 7-day forecast cards with Day name, weather icon, high/low range bar, and focus selection highlight.
- **AQI Color Scale:**
  - 0–50: Good (`#4CAF50` Green)
  - 51–100: Moderate (`#FFEB3B` Yellow)
  - 101–150: Unhealthy for Sensitive Groups (`#FF9800` Orange)
  - 151+: Unhealthy / Hazardous (`#F44336` Red)
