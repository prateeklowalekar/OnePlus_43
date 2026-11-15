package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Intercepts remote button events on Android TV.
 * - Routes dedicated streaming buttons (Netflix, YouTube, Prime, SonyLiv) directly to apps.
 * - Routes media playback keys (Play/Pause, Next, Prev) to AudioManager for background music.
 * - Opens Control Center overlay over any screen on Settings key.
 * - Sends Home key to this launcher on remotes that emit KEYCODE_ALL_APPS.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    /** The Settings panel, drawn over whatever app is open. */
    private SettingsOverlay settings;
    /** The running service's panel, for MainActivity's test hook (the service and the launcher share a process). */
    static SettingsOverlay running;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        settingsKeycodes = getResources().getIntArray(R.array.settings_key_keycodes);
        settings = new SettingsOverlay(this);
        running = settings;
    }

    @Override
    public void onDestroy() {
        if (running == settings) running = null;
        super.onDestroy();
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    private void launchApp(String packageName) {
        try {
            Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
            if (intent == null) {
                intent = getPackageManager().getLaunchIntentForPackage(packageName);
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void sendMediaKey(int keyCode) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            long now = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        // 1. Dedicated remote streaming app buttons:
        // Netflix: KEYCODE_BUTTON_3 (290), KEYCODE_FEATURED_APP_1 (297), scancode 566, code 4062
        if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == 290 || keyCode == 4062 || scanCode == 566) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.netflix.ninja");
            }
            return true;
        }

        // YouTube: KEYCODE_FEATURED_APP_1 (297), KEYCODE_EXPLORER (64), KEYCODE_BUTTON_4 (291), KEYCODE_FEATURED_APP_3 (299)
        if (keyCode == 297 || keyCode == KeyEvent.KEYCODE_EXPLORER || keyCode == KeyEvent.KEYCODE_BUTTON_4 || keyCode == 291 || keyCode == 299) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.google.android.youtube.tv");
            }
            return true;
        }

        // Prime Video: KEYCODE_BUTTON_2 (289), KEYCODE_FEATURED_APP_2 (298), scancode 752, code 4099
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == 289 || keyCode == 298 || keyCode == 4099 || scanCode == 752) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.amazon.amazonvideo.livingroom");
            }
            return true;
        }

        // SonyLiv: KEYCODE_MACRO_1 (313), KEYCODE_BUTTON_1 (288), KEYCODE_FEATURED_APP_4 (300)
        if (keyCode == 313 || keyCode == KeyEvent.KEYCODE_BUTTON_1 || keyCode == 288 || keyCode == 300) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.sonyliv");
            }
            return true;
        }

        // 2. Media playback keys routing for background music:
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
            return true;
        }

        // 3. Settings / Control Center overlay:
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && settings != null) {
                settings.toggle();
            }
            return true;
        }

        // While the panel is open, Back closes it and does not reach the app underneath.
        if (settings != null && settings.isShowing() && keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) settings.hide();
            return true;
        }

        // 4. Home key routing:
        if (keyCode != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
