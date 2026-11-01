package io.github.sumyabazar1.weather;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather for one place, full screen: now, the air quality and the next days (options.xml). It reads Open-Meteo only
 * while it is on screen (every {@link Forecast#REFRESH_MS}, or {@link Forecast#RETRY_MS} after a failure) and
 * does nothing in the background. The place is chosen once with a search and kept in the app's preferences.
 */
public final class WeatherActivity extends Activity {
    private static final String PREFS = "place";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;
    private SharedPreferences place;
    private View root;
    private View content;
    private TextView message;
    private boolean shown;
    /** options.xml: days after today, and the extras on the "Feels like" line. */
    private int forecastDays;
    private boolean showHumidity;
    private boolean showWind;
    private Forecast.Report currentReport;
    private int selectedDayIndex = 0;
    private LinearLayout hourlyContainer;
    private TextView hourlyTitle;
    private HorizontalScrollView hourlyScroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather);
        place = getSharedPreferences(PREFS, MODE_PRIVATE);
        forecastDays = Forecast.forecastDays(getResources().getInteger(R.integer.forecast_days));
        showHumidity = getResources().getBoolean(R.bool.show_humidity);
        showWind = getResources().getBoolean(R.bool.show_wind);
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        message = findViewById(R.id.message);
        hourlyContainer = findViewById(R.id.hourlyContainer);
        hourlyTitle = findViewById(R.id.hourlyTitle);
        hourlyScroll = findViewById(R.id.hourlyScroll);
        View changeCity = findViewById(R.id.changeCity);
        changeCity.setOnClickListener(v -> pickCity());
        changeCity.requestFocus();
        if (!place.contains("name")) {
            // Pre-seed default location for Pandhari Nagar, Kharadi, Pune 411014
            place.edit()
                    .putString("name", "Pandhari Nagar, Kharadi, Pune")
                    .putLong("lat", Double.doubleToRawLongBits(18.5534))
                    .putLong("lon", Double.doubleToRawLongBits(73.9379))
                    .apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (place.contains("name")) refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);   // nothing runs while the page is not on screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void refresh() {
        main.removeCallbacks(tick);
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        io.execute(() -> {
            Forecast.Report report;
            try {
                report = Forecast.fetch(lat, lon, forecastDays);
            } catch (Exception e) {
                report = null;
            }
            Forecast.Report result = report;
            main.post(() -> {
                if (isDestroyed()) return;
                if (result != null) show(result);
                else if (!shown) message.setText(R.string.offline);
                main.postDelayed(tick, result != null ? Forecast.REFRESH_MS : Forecast.RETRY_MS);
            });
        });
    }

    private void show(Forecast.Report r) {
        shown = true;
        currentReport = r;
        selectedDayIndex = 0;
        root.setBackground(Forecast.backdrop(r.code, r.day));
        ((TextView) findViewById(R.id.place)).setText(place.getString("name", ""));

        renderOverview(r.tempC, r.feelsC, r.code, r.day, r.aqi, r.humidity, r.windKmh);
        renderDays(r);
        if (!r.days.isEmpty()) {
            renderHourly(r.days.get(0));
        }

        ((TextView) findViewById(R.id.updated))
                .setText(getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date())));
        message.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    private void renderOverview(int tempC, int feelsC, int code, boolean day, int aqi, int humidity, int windKmh) {
        ((TextView) findViewById(R.id.temp)).setText(tempC + "°");
        ((TextView) findViewById(R.id.sky)).setText(Forecast.sky(code, day));
        StringBuilder feels = new StringBuilder(getString(R.string.feels_like, feelsC));
        if (showHumidity && humidity >= 0) feels.append(" · ").append(getString(R.string.humidity, humidity));
        if (showWind && windKmh >= 0) feels.append(" · ").append(getString(R.string.wind, windKmh));
        ((TextView) findViewById(R.id.feels)).setText(feels);

        TextView air = findViewById(R.id.airQuality);
        if (aqi >= 0) {
            air.setText(getString(R.string.aqi_line, aqi, getString(Forecast.aqiLabel(aqi))));
            air.setTextColor(Forecast.aqiTextColour(aqi));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Forecast.aqiColour(aqi));
            pill.setCornerRadius(100 * getResources().getDisplayMetrics().density);
            air.setBackground(pill);
            air.setVisibility(View.VISIBLE);
        } else {
            air.setVisibility(View.GONE);
        }
    }

    private void renderDays(Forecast.Report r) {
        LinearLayout days = findViewById(R.id.days);
        days.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        for (int i = 0; i < r.days.size(); i++) {
            final int index = i;
            Forecast.Day d = r.days.get(i);
            View card = inflater.inflate(R.layout.item_day, days, false);
            TextView name = card.findViewById(R.id.dayName);
            if (i == 0) {
                name.setText("TODAY");
            } else {
                name.setText(d.date.getDayOfWeek()
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase());
            }
            ((TextView) card.findViewById(R.id.daySky)).setText(Forecast.sky(d.code, true));
            TextView temps = card.findViewById(R.id.dayTemps);
            temps.setText(getString(R.string.high_low, d.maxC, d.minC));

            card.setSelected(i == selectedDayIndex);
            card.setOnClickListener(v -> selectDay(index));
            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) selectDay(index);
            });

            days.addView(card);
        }
    }

    private void selectDay(int index) {
        if (currentReport == null || index < 0 || index >= currentReport.days.size()) return;
        selectedDayIndex = index;
        LinearLayout days = findViewById(R.id.days);
        for (int i = 0; i < days.getChildCount(); i++) {
            days.getChildAt(i).setSelected(i == index);
        }

        Forecast.Day day = currentReport.days.get(index);
        if (index == 0) {
            hourlyTitle.setText("HOURLY FORECAST · TODAY (REMAINING HOURS & PREDICTIONS)");
            renderOverview(currentReport.tempC, currentReport.feelsC, currentReport.code, currentReport.day,
                    currentReport.aqi, currentReport.humidity, currentReport.windKmh);
        } else {
            String dayName = day.date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()).toUpperCase()
                    + ", " + day.date.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase()
                    + " " + day.date.getDayOfMonth();
            hourlyTitle.setText("HOURLY FORECAST · " + dayName + " (FULL 24-HOUR PREDICTION)");
            renderOverview(day.maxC, day.minC, day.code, true, -1, -1, -1);
            StringBuilder daySummary = new StringBuilder();
            daySummary.append("High ").append(day.maxC).append("° · Low ").append(day.minC).append("°");
            ((TextView) findViewById(R.id.feels)).setText(daySummary);
        }

        renderHourly(day);
    }

    private void renderHourly(Forecast.Day day) {
        if (hourlyContainer == null) return;
        hourlyContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        List<Forecast.Hour> hoursToShow = new ArrayList<>();
        if (selectedDayIndex == 0) {
            int currentHour = java.time.LocalTime.now().getHour();
            for (Forecast.Hour h : day.hours) {
                if (h.hourOfDay >= currentHour) {
                    hoursToShow.add(h);
                }
            }
            if (hoursToShow.size() < 8 && currentReport.days.size() > 1) {
                Forecast.Day tomorrow = currentReport.days.get(1);
                int needed = 12 - hoursToShow.size();
                for (int i = 0; i < Math.min(needed, tomorrow.hours.size()); i++) {
                    hoursToShow.add(tomorrow.hours.get(i));
                }
            }
        } else {
            hoursToShow.addAll(day.hours);
        }

        int currentHour = java.time.LocalTime.now().getHour();
        boolean isToday = selectedDayIndex == 0;

        for (int i = 0; i < hoursToShow.size(); i++) {
            Forecast.Hour h = hoursToShow.get(i);
            View card = inflater.inflate(R.layout.item_hourly, hourlyContainer, false);

            TextView timeTv = card.findViewById(R.id.hourTime);
            if (isToday && i == 0 && h.hourOfDay == currentHour) {
                timeTv.setText("NOW");
                timeTv.setTextColor(0xFF10B981);
            } else {
                timeTv.setText(h.timeStr);
                timeTv.setTextColor(0xCCFFFFFF);
            }

            TextView iconTv = card.findViewById(R.id.hourIcon);
            iconTv.setText(Forecast.skyGlyph(h.code, h.hourOfDay >= 6 && h.hourOfDay < 19));

            TextView tempTv = card.findViewById(R.id.hourTemp);
            tempTv.setText(h.tempC + "°");

            View bar = card.findViewById(R.id.hourTrendBar);
            if (h.tempC >= 28) bar.setBackgroundColor(0xFFF59E0B);
            else if (h.tempC >= 20) bar.setBackgroundColor(0xFF10B981);
            else bar.setBackgroundColor(0xFF06B6D4);

            TextView humTv = card.findViewById(R.id.hourHumidity);
            humTv.setText("💧 " + h.humidity + "%");

            TextView aqiTv = card.findViewById(R.id.hourAqi);
            if (h.aqi >= 0) {
                aqiTv.setText("AQI " + h.aqi);
                aqiTv.setTextColor(Forecast.aqiTextColour(h.aqi));
                GradientDrawable chip = new GradientDrawable();
                chip.setColor(Forecast.aqiColour(h.aqi));
                chip.setCornerRadius(10 * getResources().getDisplayMetrics().density);
                aqiTv.setBackground(chip);
                aqiTv.setVisibility(View.VISIBLE);
            } else {
                aqiTv.setVisibility(View.GONE);
            }

            hourlyContainer.addView(card);
        }

        if (hourlyScroll != null) {
            hourlyScroll.post(() -> hourlyScroll.scrollTo(0, 0));
        }
    }

    /** Type a city, pick it from the matches, keep it. Back leaves the current place as it was. */
    private void pickCity() {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.pick_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setView(box)
                .setPositiveButton(R.string.pick_search, (d, w) -> search(name.getText().toString()))
                .create();
        name.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            dialog.dismiss();
            search(name.getText().toString());
            return true;
        });
        dialog.show();
        name.requestFocus();
    }

    private void search(String typed) {
        if (typed.trim().isEmpty()) {
            pickCity();
            return;
        }
        io.execute(() -> {
            List<Forecast.Place> found;
            try {
                found = Forecast.search(typed);
            } catch (Exception e) {
                found = null;
            }
            List<Forecast.Place> places = found;
            main.post(() -> {
                if (isDestroyed()) return;
                if (places == null) {
                    Toast.makeText(this, R.string.offline, Toast.LENGTH_LONG).show();
                } else if (places.isEmpty()) {
                    Toast.makeText(this, getString(R.string.pick_none, typed.trim()), Toast.LENGTH_LONG).show();
                    pickCity();
                } else {
                    choose(places);
                }
            });
        });
    }

    private void choose(List<Forecast.Place> places) {
        CharSequence[] rows = new CharSequence[places.size()];
        for (int i = 0; i < rows.length; i++) {
            Forecast.Place p = places.get(i);
            rows[i] = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setItems(rows, (d, which) -> {
                    Forecast.Place p = places.get(which);
                    place.edit()
                            .putString("name", p.name)
                            .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                            .putLong("lon", Double.doubleToRawLongBits(p.longitude))
                            .apply();
                    message.setText(R.string.loading);
                    refresh();
                })
                .show();
    }
}
