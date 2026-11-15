package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: a pinned dock row of the most used apps, an app grid with a System folder, a small popup under the focused tile, and a Settings side panel.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    private static final int LAUNCHER_SETTINGS_REQUEST = 42;
    private static final String TOGGLE_SETTINGS_TEST = "com.gathin.tvhome.TOGGLE_SETTINGS";
    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private boolean rtlAppGrid;
    private static final long MINUTE_MS = 60_000L;
    private static final String TAG = "TvHome";
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private TextView heroName;
    private View focusedCard;
    private boolean heroShown;
    /** Where the top-shelf name sits, from the dock's last position (the grid's names use the same place). */
    private float dockHeroAnchor = -1f;
    private boolean animations;
    private boolean advancedEffects;
    private TextView greeting;
    private View btnInputs;
    private TextView btnWifi;
    private View btnSettings;
    private ViewGroup topButtons;
    private Config cfg;
    private TextView shamsiDate;
    private boolean shamsi;
    /** Optional time-of-day backdrop (config.xml, sky_intensity); 0 keeps it hidden. */
    private SkyView sky;
    private String vpnApp;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** Rebuild the tiles on resume: Home was fully hidden by an app, or Home was pressed. Not after a side panel such as Wi-Fi settings. */
    private boolean rebuildOnResume = true;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;
    /** A theme's wallpaper.jpg; shown when the wallpaper folder is not set or has no pictures. */
    private File themeWallpaper;
    /** Folder of rotating pictures (config.xml, wallpaper_folder), or null. */
    private File wallpaperFolder;
    private long wallpaperRotateMs;
    /** The one view that holds the wallpaper, created on the first picture. */
    private ImageView wallpaperView;
    /** Path of the picture in wallpaperView, so an unchanged choice is not decoded again. */
    private String shownWallpaperPath;
    private long shownWallpaperModified = 0;
    /** Settings for the prayer line under the clock; its place is null when prayer times are off. */
    private Config prayerConfig;
    private TextView prayerLine;
    private TextView weatherLine;
    private ViewGroup topBar;
    private VideoWallpaperPlayer videoWallpaper;
    private boolean isHomeResumed = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            if (!isHomeResumed) return;
            updateGreeting();
            updatePrayerLine();
            updateWeatherLine();
            updateWifiLabel();
            updateWallpaper();
            sky.update();
            long interval = WallpaperFolder.getRotationIntervalMs(MainActivity.this, wallpaperRotateMs);
            long delay = interval > 0 && interval < MINUTE_MS ? interval : MINUTE_MS;
            handler.postDelayed(this, delay);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        topBar = findViewById(R.id.topBar);
        lockTopBar(true);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        cfg = Config.load(this);
        COLUMNS = cfg.columns;
        animations = cfg.animations;
        advancedEffects = cfg.animations && cfg.advancedEffects;
        reducedMotion = cfg.reducedMotion;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);
        root.setBackgroundColor(cfg.backgroundColor);
        themeWallpaper = cfg.wallpaper;
        prayerConfig = cfg;
        prayerLine = findViewById(R.id.prayerLine);
        prayerLine.setVisibility(cfg.prayerTimes ? View.VISIBLE : View.GONE);
        weatherLine = findViewById(R.id.weatherLine);
        if (weatherLine != null) {
            weatherLine.setOnClickListener(v -> {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("io.github.sumyabazar1.weather", "io.github.sumyabazar1.weather.WeatherActivity"));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "Weather app not installed", Toast.LENGTH_SHORT).show();
                }
            });
            weatherLine.setOnLongClickListener(v -> {
                boolean next = !WeatherProvider.isEnabled(this);
                WeatherProvider.setEnabled(this, next);
                updateWeatherLine();
                Toast.makeText(this, next ? "Weather widget enabled" : "Weather widget disabled", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
        View.OnKeyListener topElementKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                lockTopBar(true);
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            if (v == weatherLine && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                    btnWifi.requestFocus();
                    return true;
                } else if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                    btnSettings.requestFocus();
                    return true;
                }
            }
            if (v == btnWifi && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) {
                    weatherLine.requestFocus();
                    return true;
                }
            }
            if (v == btnSettings && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                    btnWifi.requestFocus();
                    return true;
                } else if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) {
                    weatherLine.requestFocus();
                    return true;
                }
            }
            return false;
        };
        if (weatherLine != null) weatherLine.setOnKeyListener(topElementKeys);
        if (btnWifi != null) btnWifi.setOnKeyListener(topElementKeys);
        if (btnSettings != null) btnSettings.setOnKeyListener(topElementKeys);
        if (prayerLine != null) prayerLine.setOnKeyListener(topElementKeys);
        updateWeatherLine();
        wallpaperFolder = cfg.wallpaperFolder;
        wallpaperRotateMs = cfg.wallpaperRotateMinutes * MINUTE_MS;
        videoWallpaper = new VideoWallpaperPlayer(this, root);
        updateWallpaper();
        sky = findViewById(R.id.sky);
        sky.setIntensity(cfg.skyIntensity);
        heroName = findViewById(R.id.heroName);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        rtlAppGrid = getResources().getBoolean(R.bool.rtl_app_grid);
        // Mirror only the app grid; the header and other locales keep their own layouts.
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override
            protected boolean isLayoutRTL() {
                return rtlAppGrid;
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (heroShown && focusedCard != null && focusedCard.isAttachedToWindow()) {
                    Rect r = new Rect(0, 0, focusedCard.getWidth(), focusedCard.getHeight());
                    root.offsetDescendantRectToMyCoords(focusedCard, r);
                    int bubbleW = heroName.getMeasuredWidth();
                    int bubbleH = heroName.getMeasuredHeight();
                    float targetX = r.left + (r.width() - bubbleW) / 2f;
                    targetX = Math.max(20 * density, Math.min(root.getWidth() - bubbleW - 20 * density, targetX));
                    float targetY = r.top - bubbleH - (8 * density);
                    heroName.animate().cancel();
                    heroName.setX(targetX);
                    heroName.setY(targetY);
                }
            }
        });
        grid.addItemDecoration(new DockDecoration(COLUMNS, density, cfg.dockBand, cfg.isV2()));
        if (animations) {
            int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
            float enterDelay = reducedMotion ? 0f : 0.06f;
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);

        // tvOS / V2 layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        int rowsBelowDock = cfg.rowsBelowDock;
        boolean isV2 = cfg.isV2();
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int itemH = isV2
                    ? AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density, true), true), density, true)
                    : AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density, false), false), density, false);
            int dockRow = isV2 ? itemH : itemH + (int) ((6 + 12) * density);   // DockDecoration offset only in V1
            int gridRows = isV2 ? 0 : itemH * rowsBelowDock;
            int bottomMargin = isV2 ? (int) (10 * density) : (int) (30 * density);
            int want = Math.max(0, h - dockRow - gridRows - bottomMargin);
            int bottomPad = isV2 ? (int) (10 * density) : grid.getPaddingBottom();
            if (grid.getPaddingTop() != want || grid.getPaddingBottom() != bottomPad) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), bottomPad);
            }
        });

        btnInputs = null;
        topButtons = findViewById(R.id.topButtons);
        btnSettings = findViewById(R.id.btnSettings);
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> open(systemIntent(Settings.ACTION_SETTINGS)));
        btnSettings.setOnLongClickListener(v -> {
            showSettingsMenu();
            return true;
        });


        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        boolean rtlMenuBar = getResources().getBoolean(R.bool.rtl_menu_bar);
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                lockTopBar(true);
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            int firstButton = R.id.btnWifi;
            int leftButton = rtlMenuBar ? R.id.btnSettings : firstButton;
            int rightButton = rtlMenuBar ? firstButton : R.id.btnSettings;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == leftButton;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == rightButton;
        };
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        if (advancedEffects) {
            View[] buttons = new View[]{btnWifi, btnSettings};
            for (View button : buttons) {
                button.setOnFocusChangeListener((view, focused) -> view.animate()
                        .scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                        .setDuration(160).start());
            }
        }

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
    }

    /**
     * Shows the picture WallpaperRotation picks from the wallpaper folder, or the theme's wallpaper.jpg
     * when the folder is not set, unreadable or empty. Called on create, on resume and every minute.
     */
    private void updateWallpaper() {
        if (videoWallpaper != null && videoWallpaper.hasVideo()) {
            if (wallpaperView != null) wallpaperView.setVisibility(View.GONE);
            shownWallpaperPath = null;
            return;
        }
        if (wallpaperView != null) wallpaperView.setVisibility(View.VISIBLE);
        File picked = pickFolderWallpaper();
        File file = picked != null ? picked : themeWallpaper;
        if (file == null) {
            File custom = new File(getExternalFilesDir(null), "wallpaper.jpg");
            if (custom.isFile()) file = custom;
        }
        if (file == null || (file.getPath().equals(shownWallpaperPath) && file.lastModified() == shownWallpaperModified)) return;
        if (showWallpaper(file)) {
            shownWallpaperPath = file.getPath();
            shownWallpaperModified = file.lastModified();
            Log.i(TAG, "wallpaper: " + file);
        }
    }

    /** The folder picture to show now, with the rotation state saved; null when there is none. */
    private File pickFolderWallpaper() {
        boolean shuffle = WallpaperFolder.isShuffleEnabled(this);
        long effectiveInterval = shuffle ? WallpaperFolder.getRotationIntervalMs(this, wallpaperRotateMs) : 0L;

        List<WallpaperRotation.Picture> pictures = WallpaperFolder.list(wallpaperFolder);
        File effectiveFolder = wallpaperFolder;
        if (pictures == null || pictures.isEmpty()) {
            File internalFolder = new File(getExternalFilesDir(null), "Wallpapers");
            List<WallpaperRotation.Picture> fallback = WallpaperFolder.list(internalFolder);
            if (fallback != null && !fallback.isEmpty()) {
                effectiveFolder = internalFolder;
                pictures = fallback;
            }
        }

        if (!shuffle) {
            WallpaperRotation.State saved = WallpaperFolder.load(this);
            if (saved != null && saved.current != null && effectiveFolder != null) {
                File savedFile = new File(effectiveFolder, saved.current);
                if (savedFile.isFile()) return savedFile;
            }
            File internalCustom = new File(getExternalFilesDir(null), "wallpaper.jpg");
            if (internalCustom.isFile()) {
                return internalCustom;
            }
        }
        if (pictures == null || pictures.isEmpty()) return null;

        // If user selected specific pictures to shuffle, filter pictures to that subset
        if (shuffle) {
            java.util.Set<String> selection = WallpaperFolder.getShuffleSelection(this);
            if (selection != null && !selection.isEmpty()) {
                List<WallpaperRotation.Picture> subset = new ArrayList<>();
                for (WallpaperRotation.Picture p : pictures) {
                    if (selection.contains(p.name)) subset.add(p);
                }
                if (!subset.isEmpty()) {
                    pictures = subset;
                }
            }
        }

        WallpaperRotation.State state = WallpaperRotation.choose(
                pictures, WallpaperFolder.load(this), System.currentTimeMillis(), effectiveInterval, shuffle);
        WallpaperFolder.save(this, state);
        return state.current != null ? new File(effectiveFolder, state.current) : null;
    }

    /**
     * Full-screen picture behind everything. Decoded downsampled to the screen so a 4K JPEG costs a 1080p
     * bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV. The previous bitmap is
     * released, so a rotation never holds two. Returns false when the file cannot be decoded.
     */
    private boolean showWallpaper(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.Bitmap bmp = WallpaperFolder.decode(file, dm.widthPixels, dm.heightPixels);
            if (bmp == null) return false;
            if (wallpaperView == null) {
                wallpaperView = new ImageView(this);
                wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                root.addView(wallpaperView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            android.graphics.drawable.Drawable old = wallpaperView.getDrawable();
            wallpaperView.setImageBitmap(bmp);
            if (old instanceof android.graphics.drawable.BitmapDrawable) {
                ((android.graphics.drawable.BitmapDrawable) old).getBitmap().recycle();
            }
            applyAdaptiveTextColors(bmp);
            return true;
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the current picture or colour stays.
            Log.w(TAG, "wallpaper " + file + " failed", e);
            return false;
        }
    }

    /** Samples luminance of top and bottom portions of the wallpaper and adapts UI colors. */
    private void applyAdaptiveTextColors(android.graphics.Bitmap bmp) {
        try {
            if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) return;
            int sampleW = 120;
            int sampleH = 80;
            android.graphics.Bitmap thumb = android.graphics.Bitmap.createScaledBitmap(bmp, sampleW, sampleH, false);
            int count = sampleW * sampleH;
            int[] pixels = new int[count];
            thumb.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH);
            thumb.recycle();

            long topLum = 0;
            int topCount = 0;
            long dockLum = 0;
            int dockCount = 0;

            for (int y = 0; y < sampleH; y++) {
                for (int x = 0; x < sampleW; x++) {
                    int p = pixels[y * sampleW + x];
                    int r = (p >> 16) & 0xFF;
                    int g = (p >> 8) & 0xFF;
                    int b = p & 0xFF;
                    long lum = (299L * r + 587L * g + 114L * b) / 1000;
                    if (y < sampleH * 0.4f) {
                        topLum += lum;
                        topCount++;
                    } else if (y > sampleH * 0.65f) {
                        dockLum += lum;
                        dockCount++;
                    }
                }
            }

            boolean isLightTop = (topCount > 0) && (topLum / topCount > 65);
            boolean isLightDock = (dockCount > 0) && (dockLum / dockCount > 65);

            // Determine adapted colors for top header
            int primaryColor = isLightTop ? 0xDD000000 : 0xFFFFFFFF;
            int secondaryColor = isLightTop ? 0x99000000 : 0xB3FFFFFF;
            int tertiaryColor = isLightTop ? 0x77000000 : 0x8CFFFFFF;
            int dividerColor = isLightTop ? 0x33000000 : 0x33FFFFFF;

            TextView clock = findViewById(R.id.clock);
            if (clock != null) clock.setTextColor(primaryColor);
            TextView ampm = findViewById(R.id.ampm);
            if (ampm != null) ampm.setTextColor(secondaryColor);
            TextView date = findViewById(R.id.date);
            if (date != null) date.setTextColor(secondaryColor);
            if (greeting != null) greeting.setTextColor(tertiaryColor);
            if (shamsiDate != null) shamsiDate.setTextColor(primaryColor);
            if (heroName != null) heroName.setTextColor(primaryColor);

            View divider = findViewById(R.id.leftBlock);
            if (divider != null) {
                View divLine = divider.findViewById(R.id.ampm);
                // Clock/Date divider bar
                if (divLine != null && divLine.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) divLine.getParent();
                    if (parent.getChildCount() >= 3) {
                        View line = parent.getChildAt(2);
                        if (line != null) line.setBackgroundColor(dividerColor);
                    }
                }
            }

            // Adapt top buttons (Wi-Fi, Settings, Weather, Prayer)
            applyPillTheme(findViewById(R.id.btnWifi), isLightTop);
            applyPillTheme(findViewById(R.id.btnSettings), isLightTop);
            if (weatherLine != null) applyPillTheme(weatherLine, isLightTop);
            if (prayerLine != null) applyPillTheme(prayerLine, isLightTop);

            if (adapter != null) {
                adapter.setLightWallpaper(isLightDock);
            }
        } catch (Exception ex) {
            Log.w(TAG, "applyAdaptiveTextColors failed", ex);
        }
    }

    private void applyPillTheme(TextView pill, boolean isLight) {
        if (pill == null) return;
        int cornerRadius = (int) (21 * density);
        int strokeWidth = Math.max(1, (int) (1 * density));

        // Normal state shape
        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        normal.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        normal.setCornerRadius(cornerRadius);
        if (isLight) {
            normal.setColor(0x22000000);
            normal.setStroke(strokeWidth, 0x33000000);
        } else {
            normal.setColor(0x22FFFFFF);
            normal.setStroke(strokeWidth, 0x38FFFFFF);
        }

        // Focused state shape
        android.graphics.drawable.GradientDrawable focused = new android.graphics.drawable.GradientDrawable();
        focused.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        focused.setCornerRadius(cornerRadius);
        focused.setColor(isLight ? 0xF5111111 : 0xF5FFFFFF);

        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, normal);
        pill.setBackground(bg);

        // Text & icon color state list
        int textColorNormal = isLight ? 0xDD000000 : 0xFFFFFFFF;
        int textColorFocused = isLight ? 0xFFFFFFFF : 0xFF111111;
        android.content.res.ColorStateList csl = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{
                        textColorFocused,
                        textColorNormal
                }
        );
        pill.setTextColor(csl);
        pill.setCompoundDrawableTintList(csl);
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Test hook: `am start -n com.gathin.tvhome/.MainActivity -a com.gathin.tvhome.TOGGLE_SETTINGS`
        if (intent != null && TOGGLE_SETTINGS_TEST.equals(intent.getAction()) && HomeKeyService.running != null) {
            HomeKeyService.running.toggle();
            return;
        }
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        grid.scrollToPosition(0);
        lastFocused = 0;
        lastFocusedKey = adapter.getItemCount() > 0 ? adapter.keyAt(0) : null;
        rebuildOnResume = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config latest = Config.load(this);
        if (cfg != null && (latest.columns != COLUMNS || !latest.launcherVersion.equals(cfg.launcherVersion) || latest.animations != animations || latest.advancedEffects != advancedEffects)) {
            recreate();
            return;
        }
        isHomeResumed = true;
        promptsPaused = false;
        if (rebuildOnResume) {
            tilesReady = false;
            reloadApps(true);
        }
        rebuildOnResume = false;
        updateGreeting();
        updatePrayerLine();
        updateWeatherLine();
        updateWifiLabel();
        updateInputsButton();
        updateWallpaper();
        if (videoWallpaper != null && videoWallpaper.hasVideo()) {
            videoWallpaper.start();
        } else if (videoWallpaper != null) {
            videoWallpaper.stop();
        }
        sky.update();
        handler.removeCallbacks(minuteTick);
        long interval = WallpaperFolder.getRotationIntervalMs(this, wallpaperRotateMs);
        long delay = interval > 0 && interval < MINUTE_MS ? interval : MINUTE_MS;
        handler.postDelayed(minuteTick, delay);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCHER_SETTINGS_REQUEST && resultCode == RESULT_OK) recreate();
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        if (restoreFocus) {
            heroShown = false;
            heroName.setAlpha(0f);
        }
        reloadTiles();
        if (animate && animations && !reducedMotion) grid.scheduleLayoutAnimation();

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private void reloadTiles() {
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps, Cards.load(Cards.storeFile(this))), none) : none;
        adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none));
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        isHomeResumed = false;
        super.onPause();
        if (videoWallpaper != null) videoWallpaper.stop();
        heroName.animate().cancel();
        handler.removeCallbacks(minuteTick);
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
    }

    @Override
    protected void onStop() {
        isHomeResumed = false;
        super.onStop();
        handler.removeCallbacks(minuteTick);
        unregisterReceiver(packageReceiver);
        rebuildOnResume = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    @Override
    protected void onDestroy() {
        isHomeResumed = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (videoWallpaper != null) videoWallpaper.close();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- focus -----------------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        focusedCard = card;
        if (adapter.isV2()) {
            return;
        }
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = Math.abs(dy) > (int) (8 * density);
            if (scroll) {
                if (!animations || reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            float targetTop = scroll ? r.top - dy : r.top;
            showHero(position, r.left, targetTop, r.width());
        } else {
            showHero(position, r.left, r.top, r.width());
            // Scroll just enough so the focused tile is on screen.
            Rect want = new Rect(0, 0, item.getWidth(), item.getHeight());
            grid.requestChildRectangleOnScreen(item, want, reducedMotion);
        }
    }

    // ---- top shelf / tile bubble label -------------------------------------------------------------

    /** Pops up a floating bubble/word-cloud label directly above the currently focused tile. */
    private void showHero(int position, float tileLeft, float tileTop, int tileWidth) {
        if (adapter != null && adapter.isV2()) {
            heroName.setVisibility(View.GONE);
            return;
        }
        heroName.setVisibility(View.VISIBLE);
        String label = adapter.labelAt(position);
        heroName.setText(label);
        heroName.measure(
                View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        int bubbleW = heroName.getMeasuredWidth();
        int bubbleH = heroName.getMeasuredHeight();

        // Center bubble horizontally above the tile, constrained within screen margins
        float targetX = tileLeft + (tileWidth - bubbleW) / 2f;
        targetX = Math.max(20 * density, Math.min(root.getWidth() - bubbleW - 20 * density, targetX));

        // Place bubble strictly above the tile's top border at all times (never flip to bottom)
        float targetY = tileTop - bubbleH - (8 * density);

        final float finalX = targetX;
        final float finalY = targetY;
        boolean changed = !label.contentEquals(heroName.getText());

        if (!animations || reducedMotion) {
            heroName.animate().cancel();
            heroName.setX(finalX);
            heroName.setY(finalY);
            heroName.setAlpha(1f);
            heroShown = true;
            return;
        }

        if (!heroShown) {
            heroName.animate().cancel();
            heroName.setX(finalX);
            heroName.setY(finalY + 6 * density);
            heroName.setAlpha(0f);
            heroName.animate()
                    .alpha(1f)
                    .y(finalY)
                    .setDuration(160)
                    .start();
            heroShown = true;
        } else {
            heroName.animate().cancel();
            heroName.animate()
                    .x(finalX)
                    .y(finalY)
                    .alpha(1f)
                    .setDuration(120)
                    .start();
        }
    }

    private void hideHero() {
        if (!heroShown) return;
        heroName.animate().cancel();
        heroShown = false;
        if (animations) {
            heroName.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
        } else {
            heroName.setAlpha(0f);
        }
    }

    // ---- navigation ------------------------------------------------------------------------------

    private void lockTopBar(boolean lock) {
        if (topBar != null) {
            topBar.setDescendantFocusability(lock ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        if (adapter.isV2()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (position == 0) {
                    if (adapter.getActivePage() > 0) {
                        lockTopBar(true);
                        adapter.setActivePage(adapter.getActivePage() - 1, grid);
                        grid.post(() -> focusTile(adapter.getItemCount() - 1));
                        return true;
                    }
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (position >= adapter.getItemCount() - 1) {
                    if (adapter.getActivePage() < adapter.getPageCount() - 1) {
                        lockTopBar(true);
                        adapter.setActivePage(adapter.getActivePage() + 1, grid);
                        grid.post(() -> focusTile(0));
                        return true;
                    }
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (adapter.getActivePage() > 0) {
                    final int targetPos = Math.min(position, adapter.getItemCount() - 1);
                    lockTopBar(true);
                    adapter.setActivePage(adapter.getActivePage() - 1, grid);
                    grid.post(() -> {
                        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(targetPos);
                        if (vh != null) {
                            View c = vh.itemView.findViewById(R.id.card);
                            if (c != null) c.requestFocus();
                        }
                    });
                    return true;
                }
                lockTopBar(false);
                if (position >= COLUMNS / 2) {
                    if (btnSettings != null) btnSettings.requestFocus();
                } else if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) {
                    weatherLine.requestFocus();
                } else if (btnWifi != null) {
                    btnWifi.requestFocus();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (adapter.getActivePage() < adapter.getPageCount() - 1) {
                    final int targetPos = Math.min(position, adapter.getItemCount() - 1);
                    lockTopBar(true);
                    adapter.setActivePage(adapter.getActivePage() + 1, grid);
                    grid.post(() -> {
                        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(targetPos);
                        if (vh != null) {
                            View c = vh.itemView.findViewById(R.id.card);
                            if (c != null) c.requestFocus();
                        }
                    });
                    return true;
                }
                return true; // last page: stay put in dock
            }
            return false;
        }

        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return GridNavigation.atHorizontalEdge(position, count, COLUMNS,
                    rtlAppGrid, keyCode == KeyEvent.KEYCODE_DPAD_LEFT);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                lockTopBar(false);
                if (position >= COLUMNS / 2) {
                    if (btnSettings != null) btnSettings.requestFocus();
                } else if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) {
                    weatherLine.requestFocus();
                } else if (btnWifi != null) {
                    btnWifi.requestFocus();
                }
                return true;
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        lockTopBar(true);
        if (adapter.getItemCount() == 0) {
            lockTopBar(false);
            if (btnWifi != null) btnWifi.requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so refresh the focus state by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (adapter.isV2() || !animations) {
            grid.scrollToPosition(pos);
            grid.post(() -> {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) {
                    View c = h.itemView.findViewById(R.id.card);
                    if (c != null) c.requestFocus();
                }
            });
            return;
        }
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateWeatherLine() {
        if (weatherLine == null) return;
        WeatherProvider.update(this, text -> {
            if (!isHomeResumed) return;
            if (text != null && WeatherProvider.isEnabled(MainActivity.this)) {
                weatherLine.setText(text);
                weatherLine.setVisibility(View.VISIBLE);
            } else {
                weatherLine.setVisibility(View.GONE);
            }
        });
    }

    /** "Now Dhuhr 12:04 PM · Next Asr 4:21 PM, in 1 h 10 min" under the clock. */
    private void updatePrayerLine() {
        PrayerUi.Day day = PrayerUi.today(prayerConfig);
        if (day != null) prayerLine.setText(PrayerUi.homeLine(this, day));
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpn || vpnDown) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? getDrawable(R.drawable.ic_lock) : null, density);
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The total paddings
            // include the Wi-Fi icon.
            TextPaint paint = btnWifi.getPaint();
            float room = btnWifi.getMaxWidth() - btnWifi.getTotalPaddingStart() - btnWifi.getTotalPaddingEnd()
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * density;
            text = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWifi.setText(label);
        } else {
            btnWifi.setText(text);
        }
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : systemIntent(Settings.ACTION_WIFI_SETTINGS));
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        if (btnInputs != null) {
            btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
        }
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = btnWifi != null ? btnWifi : root;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = glassPopup(panel, width);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        int alignment = getResources().getBoolean(R.bool.rtl_menu_bar) ? Gravity.END : Gravity.START;
        dropIn(popup, panel, anchor, alignment);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow glassPopup(View panel, int width) {
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- launcher settings and module cards -------------------------------------------------------

    /** Keep a normal Settings click for the TV; the launcher's choices live behind hold OK. */
    private void showSettingsMenu() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (200 * density));
        addSettingsMenuRow(rows, popup, R.string.wallpaper, () -> startActivity(
                new Intent(this, WallpaperPickerActivity.class)));
        addSettingsMenuRow(rows, popup, R.string.launcher_settings, () -> startActivityForResult(
                new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST));
        if (moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, this::showModuleCards);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (200 * density), panel.getMeasuredWidth()));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private void addSettingsMenuRow(LinearLayout rows, PopupWindow popup, int title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        // The OK-up after opening the menu must not activate its first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                popup.dismiss();
                action.run();
            }
            return true;
        });
        rows.addView(row);
    }

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            // Number keys 1 to 9 (and 0) -> quick launch dock apps
            if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
                int index = keyCode - KeyEvent.KEYCODE_1;
                if (index < adapter.getItemCount()) {
                    adapter.launchAt(index);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_0) {
                if (9 < adapter.getItemCount()) {
                    adapter.launchAt(9);
                    return true;
                }
            }

            // Settings button: 83, 4077, KEYCODE_SETTINGS, KEYCODE_MENU
            if (keyCode == 83 || keyCode == 4077 || keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_MENU) {
                if (HomeKeyService.running != null) {
                    HomeKeyService.running.toggle();
                } else {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {}
                }
                return true;
            }

            // Live TV: KEYCODE_TV (170), 4001, KEYCODE_GUIDE (165), 278
            if (keyCode == KeyEvent.KEYCODE_TV || keyCode == 4001 || keyCode == KeyEvent.KEYCODE_GUIDE || keyCode == 278) {
                Intent tvIntent = new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.tcl.tv", "com.tcl.player.TVActivity"));
                tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(tvIntent);
                } catch (Exception e) {
                    Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage("com.tcl.tv");
                    if (fallback != null) startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                return true;
            }

            // YouTube: 190, 4065
            if (keyCode == 190 || keyCode == 4065) {
                launchDirect("com.google.android.youtube.tv");
                return true;
            }

            // Netflix: 4062, 191
            if (keyCode == 4062 || keyCode == 191) {
                launchDirect("com.netflix.ninja");
                return true;
            }

            // Prime Video: 4099, 193
            if (keyCode == 4099 || keyCode == 193) {
                launchDirect("com.amazon.amazonvideo.livingroom");
                return true;
            }

            // TCL Channel: 4020
            if (keyCode == 4020) {
                Intent channel = new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.tcl.waterfall.overseas", "com.tcl.waterfall.overseas.main.MainActivity"));
                channel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(channel);
                } catch (Exception e) {
                    launchDirect("com.tcl.channelplus");
                }
                return true;
            }

            // Web Browser: 4159, 4057
            if (keyCode == 4159 || keyCode == 4057) {
                launchDirect("com.tcl.browser");
                return true;
            }

            // Sony LIV: 4141
            if (keyCode == 4141) {
                Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage("com.sonyliv");
                if (intent == null) intent = getPackageManager().getLaunchIntentForPackage("com.sonyliv");
                if (intent == null) {
                    launchDirect("in.startv.hotstar");
                } else {
                    try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void launchDirect(String pkg) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
        }
    }
}
