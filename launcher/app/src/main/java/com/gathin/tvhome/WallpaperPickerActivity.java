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
    private RecyclerView grid;
    private boolean showingLive = false;

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
        grid = findViewById(R.id.wallpaperGrid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));

        initFolders();
        loadItems();

        tabStatic.setOnClickListener(v -> switchTab(false));
        tabLive.setOnClickListener(v -> switchTab(true));

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

    private void switchTab(boolean live) {
        showingLive = live;
        tabStatic.setSelected(!live);
        tabLive.setSelected(live);

        tabStatic.setAlpha(!live ? 1.0f : 0.6f);
        tabLive.setAlpha(live ? 1.0f : 0.6f);

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

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                label = v.findViewById(R.id.label);
                badge = v.findViewById(R.id.current);
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
            h.thumb.setImageDrawable(null);
            h.thumb.setTag(item.file.getPath());

            h.itemView.setOnClickListener(v -> pickItem(item));

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
