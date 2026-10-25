package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wallpaper Picker with two tabs: Static Wallpapers and Live (Video) Wallpapers.
 */
public class WallpaperPickerActivity extends Activity {
    private static final String TAG = "TvHome.Wallpaper";
    private static final int COLUMNS = 4;
    private static final int THUMB_WIDTH_PX = 400;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newFixedThreadPool(2);

    private TextView tabStatic, tabLive, hint;
    private View shuffleControls;
    private TextView btnToggleShuffle, btnInterval, btnSelectAll, shuffleSelectionSummary;
    private RecyclerView grid;
    private boolean showingLive = false;

    private static final long[] INTERVAL_OPTIONS = {
        15 * 1000L,       // 15s
        30 * 1000L,       // 30s
        60 * 1000L,       // 1m
        5 * 60 * 1000L,   // 5m
        10 * 60 * 1000L,  // 10m
        30 * 60 * 1000L,  // 30m
        60 * 60 * 1000L   // 1h
    };
    private static final String[] INTERVAL_LABELS = {
        "15s", "30s", "1m", "5m", "10m", "30m", "1h"
    };

    private final java.util.Set<String> selectedForShuffle = new java.util.HashSet<>();
    private boolean shuffleEnabled = false;
    private long currentIntervalMs = 30 * 60 * 1000L;

    private File staticFolder;
    private File liveFolder;
    private List<WallpaperItem> staticItems = new ArrayList<>();
    private List<WallpaperItem> liveItems = new ArrayList<>();

    static final class WallpaperItem {
        final String name;
        final String displayName;
        final File file;
        final boolean isLive;

        WallpaperItem(String name, String displayName, File file, boolean isLive) {
            this.name = name;
            this.displayName = displayName;
            this.file = file;
            this.isLive = isLive;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper_picker);

        tabStatic = findViewById(R.id.tabStatic);
        tabLive = findViewById(R.id.tabLive);
        hint = findViewById(R.id.wallpaperHint);
        shuffleControls = findViewById(R.id.shuffleControls);
        btnToggleShuffle = findViewById(R.id.btnToggleShuffle);
        btnInterval = findViewById(R.id.btnInterval);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        shuffleSelectionSummary = findViewById(R.id.shuffleSelectionSummary);

        grid = findViewById(R.id.wallpaperGrid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));

        shuffleEnabled = WallpaperFolder.isShuffleEnabled(this);
        currentIntervalMs = WallpaperFolder.getRotationIntervalMs(this, 30 * 60 * 1000L);
        selectedForShuffle.addAll(WallpaperFolder.getShuffleSelection(this));

        initFolders();
        loadItems();

        tabStatic.setOnClickListener(v -> switchTab(false));
        tabLive.setOnClickListener(v -> switchTab(true));

        btnToggleShuffle.setOnClickListener(v -> toggleShuffle());
        btnInterval.setOnClickListener(v -> cycleInterval());
        btnSelectAll.setOnClickListener(v -> toggleSelectAll());

        updateShuffleUI();
        switchTab(false);
    }

    private void initFolders() {
        Config cfg = Config.load(this);
        staticFolder = cfg.wallpaperFolder;
        if (staticFolder == null || !staticFolder.exists() || isEmpty(staticFolder)) {
            staticFolder = new File(getExternalFilesDir(null), "Wallpapers");
        }
        liveFolder = new File(getExternalFilesDir(null), "LiveWallpapers");
        if (!liveFolder.exists() || isEmpty(liveFolder)) {
            liveFolder = new File("/sdcard/Wallpapers/Live");
        }
    }

    private boolean isEmpty(File dir) {
        File[] files = dir.listFiles();
        return files == null || files.length == 0;
    }

    private void loadItems() {
        staticItems.clear();
        liveItems.clear();

        // Load static wallpapers from all potential locations
        List<File> staticDirs = new ArrayList<>();
        if (staticFolder != null) staticDirs.add(staticFolder);
        File appStatic = new File(getExternalFilesDir(null), "Wallpapers");
        if (!staticDirs.contains(appStatic)) staticDirs.add(appStatic);
        File sdStatic = new File("/sdcard/Wallpapers");
        if (!staticDirs.contains(sdStatic)) staticDirs.add(sdStatic);

        for (File dir : staticDirs) {
            if (dir != null && dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> {
                    String lower = n.toLowerCase();
                    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
                });
                if (files != null) {
                    for (File f : files) {
                        boolean exists = false;
                        for (WallpaperItem item : staticItems) {
                            if (item.name.equals(f.getName())) { exists = true; break; }
                        }
                        if (!exists) {
                            staticItems.add(new WallpaperItem(f.getName(), f.getName(), f, false));
                        }
                    }
                }
            }
        }
        Collections.sort(staticItems, (a, b) -> a.name.compareToIgnoreCase(b.name));

        // Load live video wallpapers from all potential locations
        List<File> liveDirs = new ArrayList<>();
        if (liveFolder != null) liveDirs.add(liveFolder);
        File appLive = new File(getExternalFilesDir(null), "LiveWallpapers");
        if (!liveDirs.contains(appLive)) liveDirs.add(appLive);
        File sdLive = new File("/sdcard/Wallpapers/Live");
        if (!liveDirs.contains(sdLive)) liveDirs.add(sdLive);

        for (File dir : liveDirs) {
            if (dir != null && dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> {
                    String lower = n.toLowerCase();
                    return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv");
                });
                if (files != null) {
                    for (File f : files) {
                        boolean exists = false;
                        for (WallpaperItem item : liveItems) {
                            if (item.name.equals(f.getName())) { exists = true; break; }
                        }
                        if (!exists) {
                            String cleanName = f.getName().replaceAll("(?i)\\.(mp4|mov|mkv)$", "").replace("_", " ");
                            liveItems.add(new WallpaperItem(f.getName(), cleanName, f, true));
                        }
                    }
                }
            }
        }
        Collections.sort(liveItems, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
    }

    private void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        WallpaperFolder.setShuffleEnabled(this, shuffleEnabled);
        if (shuffleEnabled && selectedForShuffle.isEmpty()) {
            for (WallpaperItem si : staticItems) selectedForShuffle.add(si.name);
            WallpaperFolder.setShuffleSelection(this, selectedForShuffle);
        }
        updateShuffleUI();
        if (grid.getAdapter() != null) grid.getAdapter().notifyDataSetChanged();
        Toast.makeText(this, shuffleEnabled ? "Wallpaper Randomize Enabled" : "Wallpaper Randomize Disabled", Toast.LENGTH_SHORT).show();
    }

    private void cycleInterval() {
        int currentIndex = 0;
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] == currentIntervalMs) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % INTERVAL_OPTIONS.length;
        currentIntervalMs = INTERVAL_OPTIONS[nextIndex];
        WallpaperFolder.setRotationIntervalMs(this, currentIntervalMs);
        updateShuffleUI();
        Toast.makeText(this, "Shuffle Interval: " + INTERVAL_LABELS[nextIndex], Toast.LENGTH_SHORT).show();
    }

    private void toggleSelectAll() {
        if (selectedForShuffle.size() == staticItems.size()) {
            selectedForShuffle.clear();
        } else {
            selectedForShuffle.clear();
            for (WallpaperItem si : staticItems) selectedForShuffle.add(si.name);
        }
        WallpaperFolder.setShuffleSelection(this, selectedForShuffle);
        updateShuffleUI();
        if (grid.getAdapter() != null) grid.getAdapter().notifyDataSetChanged();
    }

    private void updateShuffleUI() {
        if (btnToggleShuffle == null) return;
        btnToggleShuffle.setText(shuffleEnabled ? "Randomize: ON" : "Randomize: OFF");
        btnToggleShuffle.setAlpha(shuffleEnabled ? 1.0f : 0.7f);

        String currentLabel = "30m";
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] == currentIntervalMs) {
                currentLabel = INTERVAL_LABELS[i];
                break;
            }
        }
        btnInterval.setText("Every " + currentLabel);

        int count = selectedForShuffle.size();
        btnSelectAll.setText(count == staticItems.size() ? "Deselect All" : "Select All (" + count + "/" + staticItems.size() + ")");
        shuffleSelectionSummary.setText(shuffleEnabled ? "Click wallpapers to select/deselect for shuffle (" + count + " selected)" : "Turn Randomize ON to select which wallpapers cycle");
    }

    private void switchTab(boolean live) {
        showingLive = live;
        tabStatic.setSelected(!live);
        tabLive.setSelected(live);

        tabStatic.setAlpha(!live ? 1.0f : 0.6f);
        tabLive.setAlpha(live ? 1.0f : 0.6f);

        if (shuffleControls != null) {
            shuffleControls.setVisibility(live ? View.GONE : View.VISIBLE);
        }

        if (live) {
            hint.setText("High framerate 4K 60fps live wallpapers. Select one to loop as your dynamic background.");
            grid.setAdapter(new WallAdapter(liveItems, isCurrentLive()));
        } else {
            hint.setText("Curated static wallpapers. Automatically adapts font contrast across the home screen.");
            grid.setAdapter(new WallAdapter(staticItems, getCurrentStatic()));
        }
    }

    private String getCurrentStatic() {
        return WallpaperFolder.load(this).current;
    }

    private String isCurrentLive() {
        File liveActive = new File(getExternalFilesDir(null), "wallpaper.mp4");
        return liveActive.isFile() ? liveActive.getName() : null;
    }

    private void pickItem(WallpaperItem item) {
        try {
            if (item.isLive) {
                // Set as active video wallpaper in private storage
                File dest = new File(getExternalFilesDir(null), "wallpaper.mp4");
                copyFile(item.file, dest);
                // Also clean up any static wallpaper override so live video takes precedence
                File activeStatic = new File(getExternalFilesDir(null), "wallpaper.jpg");
                if (activeStatic.exists()) activeStatic.delete();
                Toast.makeText(this, "Set " + item.displayName + " as Live Wallpaper", Toast.LENGTH_SHORT).show();
            } else {
                // If a video wallpaper was active, disable it so static shows
                File activeVideo = new File(getExternalFilesDir(null), "wallpaper.mp4");
                if (activeVideo.exists()) activeVideo.delete();
                File sdVideo = new File("/sdcard/Wallpapers/wallpaper.mp4");
                if (sdVideo.exists()) sdVideo.delete();

                // Save static rotation state
                List<WallpaperRotation.Picture> pics = new ArrayList<>();
                for (WallpaperItem si : staticItems) pics.add(new WallpaperRotation.Picture(si.name, si.file.lastModified()));
                WallpaperRotation.State state = WallpaperRotation.select(pics, WallpaperFolder.load(this), System.currentTimeMillis(), item.name);
                WallpaperFolder.save(this, state);

                File dest = new File(getExternalFilesDir(null), "wallpaper.jpg");
                copyFile(item.file, dest);
                Toast.makeText(this, "Set " + item.displayName + " as Wallpaper", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Log.w(TAG, "Failed to apply wallpaper", ex);
        }
        finish();
    }

    private static void copyFile(File src, File dst) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    private final class WallAdapter extends RecyclerView.Adapter<WallAdapter.Holder> {
        private final List<WallpaperItem> items;
        private final String current;

        WallAdapter(List<WallpaperItem> items, String current) {
            this.items = items;
            this.current = current;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView label;
            final View badge;
            final ImageView checkMark;

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                label = v.findViewById(R.id.label);
                badge = v.findViewById(R.id.current);
                checkMark = v.findViewById(R.id.checkMark);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WallpaperItem item = items.get(position);
            h.label.setText(item.displayName);
            h.label.setVisibility(View.VISIBLE);
            h.badge.setVisibility(item.name.equals(current) ? View.VISIBLE : View.GONE);
            if (!item.isLive && shuffleEnabled && h.checkMark != null) {
                h.checkMark.setVisibility(selectedForShuffle.contains(item.name) ? View.VISIBLE : View.GONE);
            } else if (h.checkMark != null) {
                h.checkMark.setVisibility(View.GONE);
            }
            h.thumb.setImageDrawable(null);
            h.thumb.setTag(item.file.getPath());

            h.itemView.setOnClickListener(v -> {
                if (shuffleEnabled && !item.isLive) {
                    // In Randomize mode: click toggles selection for shuffle and holds menu open
                    if (selectedForShuffle.contains(item.name)) {
                        selectedForShuffle.remove(item.name);
                        Toast.makeText(WallpaperPickerActivity.this, "Deselected " + item.displayName, Toast.LENGTH_SHORT).show();
                    } else {
                        selectedForShuffle.add(item.name);
                        Toast.makeText(WallpaperPickerActivity.this, "Selected " + item.displayName, Toast.LENGTH_SHORT).show();
                    }
                    WallpaperFolder.setShuffleSelection(WallpaperPickerActivity.this, selectedForShuffle);
                    updateShuffleUI();
                    notifyItemChanged(position);
                } else {
                    // Regular mode or live wallpaper: applies wallpaper and closes picker
                    pickItem(item);
                }
            });
            h.itemView.setOnLongClickListener(v -> {
                if (!item.isLive) {
                    if (selectedForShuffle.contains(item.name)) {
                        selectedForShuffle.remove(item.name);
                        Toast.makeText(WallpaperPickerActivity.this, "Deselected " + item.displayName, Toast.LENGTH_SHORT).show();
                    } else {
                        selectedForShuffle.add(item.name);
                        Toast.makeText(WallpaperPickerActivity.this, "Selected " + item.displayName + " for Randomize", Toast.LENGTH_SHORT).show();
                    }
                    WallpaperFolder.setShuffleSelection(WallpaperPickerActivity.this, selectedForShuffle);
                    updateShuffleUI();
                    notifyItemChanged(position);
                    return true;
                }
                return false;
            });

            decoder.execute(() -> {
                Bitmap bmp = null;
                try {
                    if (item.isLive) {
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(item.file.getPath());
                        bmp = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        mmr.release();
                    } else {
                        bmp = WallpaperFolder.decode(item.file, THUMB_WIDTH_PX, 0);
                    }
                } catch (Exception ignored) {}

                final Bitmap result = bmp;
                main.post(() -> {
                    if (result != null && item.file.getPath().equals(h.thumb.getTag())) {
                        h.thumb.setImageBitmap(result);
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
