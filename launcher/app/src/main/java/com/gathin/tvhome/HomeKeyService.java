package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
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
            if (intent == null) {
                if ("com.sonyliv".equals(packageName)) {
                    intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.sonyliv", "com.sonyliv.ui.splash.SplashActivity"));
                } else if ("com.brave.browser".equals(packageName)) {
                    intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.brave.browser", "com.google.android.apps.chrome.Main"));
                }
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void launchBrowser() {
        String[] browsers = {"com.brave.browser", "org.mozilla.tv.firefox", "com.android.chrome"};
        for (String pkg : browsers) {
            try {
                Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (intent == null) {
                    intent = getPackageManager().getLaunchIntentForPackage(pkg);
                }
                if (intent == null && "com.brave.browser".equals(pkg)) {
                    intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.brave.browser", "com.google.android.apps.chrome.Main"));
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://google.com"));
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(viewIntent);
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

        // 1. Dedicated remote streaming & app buttons:
        // YouTube: on TCL remote sends keycode 190, 4065, or KEYCODE_EXPLORER (64), 297, 299
        if (keyCode == 190 || keyCode == 4065 || keyCode == 297 || keyCode == KeyEvent.KEYCODE_EXPLORER || keyCode == 299) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.google.android.youtube.tv");
            }
            return true;
        }

        // Netflix: on TCL remote sends keycode 4062, 191, scancode 566, scancode 472
        if (keyCode == 4062 || keyCode == 191 || scanCode == 566 || scanCode == 472) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.netflix.ninja");
            }
            return true;
        }

        // Prime Video: on TCL remote sends keycode 4099, 193, scancode 752, scancode 473
        if (keyCode == 4099 || keyCode == 193 || scanCode == 752 || scanCode == 473) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.amazon.amazonvideo.livingroom");
            }
            return true;
        }

        // Sony LIV: on TCL remote sends keycode 4166, 4141, 313, 288, 300
        if (keyCode == 4166 || keyCode == 4141 || keyCode == 313 || keyCode == 288 || keyCode == 300) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.sonyliv");
            }
            return true;
        }

        // Web Browser: on TCL remote sends keycode 4159, 4057, KEYCODE_WINDOW
        if (keyCode == 4159 || keyCode == 4057 || keyCode == KeyEvent.KEYCODE_WINDOW) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchBrowser();
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
