# Data Schemas and Contracts: CleanYourTV

## 1. Module Card Contract (`docs/specs/card.md`)

Add-ons communicate their presence to the launcher by declaring an `<activity-alias>` in `AndroidManifest.xml`:

```xml
<activity-alias
    android:name=".ModuleCard"
    android:targetActivity=".WeatherActivity"
    android:exported="true"
    android:icon="@drawable/ic_card"
    android:label="@string/card_label">
    <intent-filter>
        <action android:name="com.gathin.tvhome.MODULE_CARD" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="com.gathin.tvhome.card.COLOR"
        android:resource="@color/card_glow" />
    <meta-data
        android:name="com.gathin.tvhome.card.SUMMARY"
        android:resource="@string/card_summary" />
</activity-alias>
```

### Metadata Fields

| Key | Type | Description |
|---|---|---|
| `android:label` | String Resource | The title text displayed on the launcher tile (e.g., "Weather"). |
| `android:icon` | Drawable Resource | 24dp vector or raster glyph drawn on the card. |
| `com.gathin.tvhome.card.COLOR` | Color Resource | Hex color used for the module card's ambient focus glow (e.g. `#4FC3F7`). |
| `com.gathin.tvhome.card.SUMMARY` | String Resource | Subtitle / status text (e.g. "Live Forecast & AQI"). |

---

## 2. Theme Configuration Schema (`themes/<name>/config.json`)

Themes alter the launcher appearance via JSON definitions placed in `themes/<name>/`:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CleanYourTV Theme Configuration",
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "dock_columns": { "type": "integer", "minimum": 3, "maximum": 10 },
    "dock_style": { "type": "string", "enum": ["pill", "glass", "minimal", "dock"] },
    "spotlight_radius_dp": { "type": "integer", "default": 14 },
    "show_clock": { "type": "boolean", "default": true },
    "show_weather_pill": { "type": "boolean", "default": true },
    "show_wifi_pill": { "type": "boolean", "default": true },
    "show_settings_pill": { "type": "boolean", "default": true },
    "show_inputs_pill": { "type": "boolean", "default": false },
    "adaptive_contrast": { "type": "boolean", "default": true },
    "ambient_glow_color": { "type": "string", "pattern": "^#[0-9A-Fa-f]{6,8}$" }
  },
  "required": ["name"]
}
```

---

## 3. TV Device Profile Schema (`examples/<model>/tv.json`)

Device entries indexed in README tables follow this structure:

```json
{
  "brand": "TCL",
  "model": "65Q6C",
  "marketing_name": "TCL 65Q6C QD-Mini LED",
  "board": "tcl9618",
  "platform": "G08",
  "soc": "MediaTek MT9653",
  "android_version": "14",
  "sdk": 34,
  "tested_theme": "glass",
  "retained_packages": [
    "com.tcl.tv",
    "com.tcl.suspension",
    "com.google.android.youtube.tv"
  ],
  "disabled_count": 28,
  "notes": "Mini LED dimming engine intact; stock launcherx disabled safely."
}
```
