package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight Weather & AQI provider for home screen pill with zero continuous background drain.
 */
final class WeatherProvider {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS = "home_weather";
    private static final String KEY_ENABLED = "weather_enabled";
    private static final String KEY_CACHED_LINE = "cached_line";
    private static final String KEY_CACHED_TIME = "cached_time";
    private static final String KEY_NAME = "location_name";
    private static final String KEY_LAT = "latitude";
    private static final String KEY_LON = "longitude";

    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30 mins
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    interface Callback {
        void onWeatherUpdated(String weatherText);
    }

    private WeatherProvider() {}

    static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static String getLocationName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "Pandhari Nagar, Kharadi, Pune");
    }

    static double getLatitude(Context ctx) {
        return Double.longBitsToDouble(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAT, Double.doubleToRawLongBits(18.5534)));
    }

    static double getLongitude(Context ctx) {
        return Double.longBitsToDouble(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LON, Double.doubleToRawLongBits(73.9379)));
    }

    static void setLocation(Context ctx, String name, double lat, double lon) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_NAME, name)
                .putLong(KEY_LAT, Double.doubleToRawLongBits(lat))
                .putLong(KEY_LON, Double.doubleToRawLongBits(lon))
                .remove(KEY_CACHED_TIME)
                .apply();
    }

    static void update(Context ctx, Callback callback) {
        if (!isEnabled(ctx)) {
            callback.onWeatherUpdated(null);
            return;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long cachedTime = prefs.getLong(KEY_CACHED_TIME, 0);
        String cachedLine = prefs.getString(KEY_CACHED_LINE, null);
        long now = System.currentTimeMillis();

        if (cachedLine != null && (now - cachedTime < CACHE_TTL_MS)) {
            callback.onWeatherUpdated(cachedLine);
            return;
        }

        if (cachedLine != null) callback.onWeatherUpdated(cachedLine);

        final double lat = getLatitude(ctx);
        final double lon = getLongitude(ctx);
        final String name = getLocationName(ctx);

        executor.execute(() -> {
            try {
                String line = fetchWeather(lat, lon, name);
                if (line != null) {
                    prefs.edit()
                            .putString(KEY_CACHED_LINE, line)
                            .putLong(KEY_CACHED_TIME, System.currentTimeMillis())
                            .apply();
                    main.post(() -> callback.onWeatherUpdated(line));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch weather: " + e.getMessage());
            }
        });
    }

    private static String fetchWeather(double lat, double lon, String name) {
        try {
            String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", lat, lon);
            String url = "https://api.open-meteo.com/v1/forecast?" + where
                    + "&models=icon_seamless&current=temperature_2m,apparent_temperature,weather_code&timezone=auto";
            JSONObject reply = getJson(url);
            if (reply == null) return null;
            JSONObject current = reply.optJSONObject("current");
            if (current == null) return null;
            int temp = (int) Math.round(current.optDouble("temperature_2m", 0));
            int code = current.optInt("weather_code", 0);

            // Fetch AQI
            int aqi = -1;
            try {
                JSONObject aqiReply = getJson("https://air-quality-api.open-meteo.com/v1/air-quality?" + where + "&current=us_aqi");
                if (aqiReply != null && aqiReply.has("current")) {
                    aqi = (int) Math.round(aqiReply.getJSONObject("current").optDouble("us_aqi", -1));
                }
            } catch (Exception ignored) {}

            String skyDesc = getSkyDescription(code);
            String cityShort = name.contains(",") ? name.split(",")[0].trim() : name;
            if (aqi >= 0) {
                return cityShort + " · " + temp + "°C " + skyDesc + " · AQI " + aqi;
            } else {
                return cityShort + " · " + temp + "°C " + skyDesc;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String getSkyDescription(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 71: case 73: case 75: return "Snow";
            case 80: case 81: case 82: return "Showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Fair";
        }
    }

    private static JSONObject getJson(String urlString) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(urlString);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "CleanYourTV");
            if (con.getResponseCode() != 200) return null;
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            return new JSONObject(new String(out.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
