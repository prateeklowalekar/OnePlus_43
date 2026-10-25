package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Intercepts remote shortcut buttons globally across all apps:
 * - Live TV (TV button) -> opens com.tcl.tv
 * - Settings (Gear button) -> toggles launcher SettingsOverlay or opens system settings
 * - App hotkeys -> YouTube, Netflix, Prime Video, TCL Channel, Web Browser, Sony LIV
 * - Input -> opens input switcher
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private void launchPackage(String pkg, String fallbackPkg) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (intent == null && fallbackPkg != null) {
            intent = getPackageManager().getLeanbackLaunchIntentForPackage(fallbackPkg);
            if (intent == null) {
                intent = getPackageManager().getLaunchIntentForPackage(fallbackPkg);
            }
        }
        if (intent != null) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
    }

    private void launchLiveTv() {
        Intent tvIntent = new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName("com.tcl.tv", "com.tcl.player.TVActivity"));
        tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(tvIntent);
        } catch (ActivityNotFoundException | SecurityException e) {
            launchPackage("com.tcl.tv", null);
        }
    }

    private void launchSettings() {
        if (HomeKeyService.running != null) {
            HomeKeyService.running.toggle();
        } else {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
    }

    private void launchInputs() {
        try {
            startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();

        // Settings: keycode 83 (KEYCODE_NOTIFICATION / Quick settings), 4077 (KEYCODE_TCL_SET), 176 (KEYCODE_SETTINGS), 82 (KEYCODE_MENU)
        if (code == 83 || code == 4077 || code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchSettings();
            }
            return true;
        }

        // Live TV: keycode 170 (KEYCODE_TV), 4001, 165 (KEYCODE_GUIDE), 278 (KEYCODE_LIVE_TV)
        if (code == KeyEvent.KEYCODE_TV || code == 4001 || code == KeyEvent.KEYCODE_GUIDE || code == 278) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchLiveTv();
            }
            return true;
        }

        // Input menu: keycode 178 (KEYCODE_TV_INPUT)
        if (code == KeyEvent.KEYCODE_TV_INPUT) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchInputs();
            }
            return true;
        }

        // YouTube: keycode 190 (KEYCODE_BUTTON_3), 4065
        if (code == 190 || code == 4065) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchPackage("com.google.android.youtube.tv", null);
            }
            return true;
        }

        // Netflix: keycode 4062, 191 (KEYCODE_BUTTON_4)
        if (code == 4062 || code == 191) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchPackage("com.netflix.ninja", null);
            }
            return true;
        }

        // Prime Video: keycode 4099, 193 (KEYCODE_BUTTON_6)
        if (code == 4099 || code == 193) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchPackage("com.amazon.amazonvideo.livingroom", null);
            }
            return true;
        }

        // TCL Channel: keycode 4020
        if (code == 4020) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent channel = new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.tcl.waterfall.overseas", "com.tcl.waterfall.overseas.main.MainActivity"));
                channel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(channel);
                } catch (Exception e) {
                    launchPackage("com.tcl.channelplus", null);
                }
            }
            return true;
        }

        // Web: keycode 4159, 4057
        if (code == 4159 || code == 4057) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchPackage("com.tcl.browser", null);
            }
            return true;
        }

        // Sony LIV: keycode 4141
        if (code == 4141) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchPackage("com.sonyliv", "in.startv.hotstar");
            }
            return true;
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
