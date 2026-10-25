package com.gathin.tvhome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * How often each app is used, for the dock. Two sources, combined:
 * <ul>
 *   <li>time in the foreground over the last 30 days, from UsageStatsManager. It covers every app
 *       the TV opens, remote app buttons included, but only after the permission is granted over ADB
 *       (see build-and-install.sh); without it this source is empty.</li>
 *   <li>launches from this launcher, kept in shared preferences. Works with no permission.</li>
 * </ul>
 */
final class Usage {

    private static final String PREFS = "usage";
    private static final String LAUNCH_PREFIX = "launch:";
    private static final long WINDOW_MS = 30L * 24 * 60 * 60 * 1000;
    /** One launch from the launcher counts as this many minutes of use. */
    static final int MINUTES_PER_LAUNCH = 10;

    private Usage() {
    }

    /** Foreground minutes per package over the last 30 days; empty when the permission is missing. */
    static Map<String, Long> foregroundMinutes(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        UsageStatsManager usm = ctx.getSystemService(UsageStatsManager.class);
        if (usm == null) return out;
        long now = System.currentTimeMillis();
        try {
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - WINDOW_MS, now);
            if (stats == null) return out;
            for (Map.Entry<String, UsageStats> e : stats.entrySet()) {
                out.put(e.getKey(), e.getValue().getTotalTimeInForeground() / 60_000L);
            }
        } catch (SecurityException ignored) {
            // permission not granted: launch counts only
        }
        return out;
    }

    static int launches(Context ctx, String pkg) {
        return prefs(ctx).getInt(LAUNCH_PREFIX + pkg, 0);
    }

    /** Called when the launcher opens an app. */
    static void recordLaunch(Context ctx, String pkg) {
        SharedPreferences p = prefs(ctx);
        p.edit().putInt(LAUNCH_PREFIX + pkg, p.getInt(LAUNCH_PREFIX + pkg, 0) + 1).apply();
    }

    /** Score used to rank apps: minutes of use plus launches from here, weighted. */
    static long score(long foregroundMinutes, int launches) {
        return foregroundMinutes + (long) launches * MINUTES_PER_LAUNCH;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
