package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * then the module cards the owner chose to show (by label), the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, WALLPAPER = 3, PRAYER = 4, FOLDER = 5;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;
        /** Preinstalled (FLAG_SYSTEM): these go in the System folder unless they are in the dock. */
        boolean system;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.18f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;
    static final float DESIGN_SQUIRCLE_DP = 58f;

    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        return tileWidthPx(innerWidthPx, columns, density, false);
    }

    static int tileWidthPx(int innerWidthPx, int columns, float density, boolean isV2) {
        if (isV2) {
            return Math.round(DESIGN_SQUIRCLE_DP * density);
        }
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return tileHeightPx(tileWidthPx, false);
    }

    static int tileHeightPx(int tileWidthPx, boolean isV2) {
        if (isV2) return tileWidthPx;
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return itemHeightPx(tileHeightPx, density, false);
    }

    static int itemHeightPx(int tileHeightPx, float density, boolean isV2) {
        if (isV2) return tileHeightPx + Math.round(8 * density);
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean animations;
    private final boolean advancedEffects;
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    /** Shows the Wallpaper tile, which opens the picker, when a wallpaper folder is set. */
    private final boolean showWallpaperTile;
    /** Shows the Prayer Times tile when prayer times are on. */
    private final boolean showPrayerTile;
    private final List<Entry> items = new ArrayList<>();
    /** The System folder: preinstalled apps not in the dock. */
    private final List<Entry> systemApps = new ArrayList<>();
    private final boolean isV2;
    private int activePage = 0;
    private boolean isLightWallpaper = false;

    public void setLightWallpaper(boolean isLight) {
        if (this.isLightWallpaper != isLight) {
            this.isLightWallpaper = isLight;
            notifyDataSetChanged();
        }
    }

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.isV2 = cfg.isV2();
        this.favourites = cfg.dockApps;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.animations = cfg.animations;
        this.advancedEffects = cfg.animations && cfg.advancedEffects;
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        this.showWallpaperTile = cfg.wallpaperFolder != null;
        this.showPrayerTile = cfg.prayerTimes;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    void reload(List<Cards.Card> cards) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(probe, 0));
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo != null) seen.add(ri.activityInfo.packageName);
        }
        Intent mobileProbe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> mobileFound = pm.queryIntentActivities(mobileProbe, 0);
        for (ResolveInfo ri : mobileFound) {
            if (ri.activityInfo != null && !seen.contains(ri.activityInfo.packageName)) {
                seen.add(ri.activityInfo.packageName);
                found.add(ri);
            }
        }
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> userApps = new ArrayList<>();
        List<Entry> preinstalled = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            Drawable banner = loadBanner(pm, ri);
            Drawable icon = ri.loadIcon(pm);
            Entry entry;
            if (card != null) {
                entry = new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent);
            } else {
                // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
                // redraw the tile name as something else (#117).
                entry = new Entry(Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg), component, banner, icon);
            }
            entry.system = card == null
                    && (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            (entry.system ? preinstalled : userApps).add(entry);
        }

        // Most used first. Favourites start with a head start so a fresh install already fills the dock with them.
        Map<String, Long> minutes = Usage.foregroundMinutes(ctx);
        Map<String, Long> scores = new HashMap<>();
        for (Entry e : userApps) scores.put(e.pkg, scoreOf(e.pkg, minutes));
        for (Entry e : preinstalled) scores.put(e.pkg, scoreOf(e.pkg, minutes));
        // Pinned favourites first in exact specified order, followed by other apps
        Comparator<Entry> byUse = (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                return Integer.compare(fa < 0 ? Integer.MAX_VALUE : fa, fb < 0 ? Integer.MAX_VALUE : fb);
            }
            int c = Long.compare(scores.get(b.pkg), scores.get(a.pkg));
            if (c != 0) return c;
            return a.label.compareToIgnoreCase(b.label);
        };
        Collections.sort(userApps, byUse);
        Collections.sort(preinstalled, byUse);

        // The dock: favourites first, then installed user apps, then preinstalled
        List<Entry> fresh = new ArrayList<>();
        // 1. Add all pinned favourites that are installed (whether user or preinstalled) in exact config order
        for (String favPkg : favourites) {
            for (Entry e : userApps) {
                if (e.pkg.equals(favPkg) && !fresh.contains(e)) { fresh.add(e); break; }
            }
            for (Entry e : preinstalled) {
                if (e.pkg.equals(favPkg) && !fresh.contains(e)) { fresh.add(e); break; }
            }
        }
        // 2. Fill any remaining slots up to dockSize with most used apps
        for (Entry e : userApps) if (fresh.size() < dockSize && !fresh.contains(e)) fresh.add(e);
        for (Entry e : preinstalled) if (fresh.size() < dockSize && !fresh.contains(e)) fresh.add(e);

        // Installed apps not in the dock: alphabetical, as before.
        List<Entry> restUser = new ArrayList<>();
        for (Entry e : userApps) if (!fresh.contains(e)) restUser.add(e);
        if (isV2) {
            // In V2, include all remaining preinstalled/system apps directly in the drawer
            for (Entry e : preinstalled) if (!fresh.contains(e)) restUser.add(e);
        }
        Collections.sort(restUser, (a, b) -> a.label.compareToIgnoreCase(b.label));
        fresh.addAll(restUser);
        if (!isV2) {
            // Whatever preinstalled apps the dock did not take go into the System folder.
            List<Entry> folder = new ArrayList<>();
            for (Entry e : preinstalled) if (!fresh.contains(e)) folder.add(e);
            Collections.sort(folder, (a, b) -> a.label.compareToIgnoreCase(b.label));
            systemApps.clear();
            systemApps.addAll(folder);
            if (!folder.isEmpty()) {
                fresh.add(new Entry(Entry.FOLDER, ctx.getString(R.string.system_folder),
                        new ComponentName(ctx.getPackageName(), SystemFolder.class.getName()), null,
                        folderIcon(folder), null, 0));
            }
        }

        // Retain the optional Default-home tile until dependent tile-ordering work is merged.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        // Prayer Times tile near the end of the grid: today's six times, full screen.
        if (showPrayerTile) {
            fresh.add(new Entry(Entry.PRAYER, ctx.getString(R.string.prayer_times_title),
                    new ComponentName(ctx, PrayerTimesActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_prayer_isha), null, 0));
        }
        // Wallpaper tile at the end of the grid: opens the picker for the wallpaper folder.
        if (showWallpaperTile) {
            fresh.add(new Entry(Entry.WALLPAPER, ctx.getString(R.string.wallpaper),
                    new ComponentName(ctx, WallpaperPickerActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_wallpaper), null, 0));
        }
        items.clear();
        items.addAll(fresh);
        if (activePage >= getPageCount()) activePage = Math.max(0, getPageCount() - 1);
        notifyDataSetChanged();
    }

    public boolean isV2() {
        return isV2;
    }

    public int getActivePage() {
        return activePage;
    }

    public int getPageCount() {
        if (!isV2 || dockSize <= 0) return 1;
        return Math.max(1, (items.size() + dockSize - 1) / dockSize);
    }

    public boolean setActivePage(int page) {
        return setActivePage(page, null);
    }

    public boolean setActivePage(int page, RecyclerView rv) {
        if (page < 0 || page >= getPageCount() || page == activePage) return false;
        activePage = page;
        if (rv != null) {
            int count = getItemCount();
            for (int i = 0; i < dockSize; i++) {
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(i);
                if (vh instanceof Holder) {
                    if (i < count) {
                        vh.itemView.setVisibility(View.VISIBLE);
                        onBindViewHolder((Holder) vh, i);
                    } else {
                        vh.itemView.setVisibility(View.INVISIBLE);
                    }
                }
            }
            return true;
        }
        notifyDataSetChanged();
        return true;
    }

    /** Usage score of one app: foreground minutes and launches from here, plus a head start for favourites. */
    private long scoreOf(String pkg, Map<String, Long> minutes) {
        long score = Usage.score(minutes.containsKey(pkg) ? minutes.get(pkg) : 0L, Usage.launches(ctx, pkg));
        int idx = favourites.indexOf(pkg);
        if (idx >= 0) score += 30L * (favourites.size() - idx);
        return score;
    }

    /** The preinstalled apps the System folder holds, in the order it shows them. */
    List<Entry> systemApps() {
        return new ArrayList<>(systemApps);
    }

    /** A 16:9 picture of up to four of the folder's icons, so the folder tile reads like a phone's folder. */
    private Drawable folderIcon(List<Entry> apps) {
        int w = 320, h = 180;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF2A2A2F);
        c.drawRoundRect(new RectF(0, 0, w, h), 28, 28, bg);
        int cell = 62, gap = 10;
        int left = (w - (2 * cell + gap)) / 2, top = (h - (2 * cell + gap)) / 2;
        for (int i = 0; i < Math.min(4, apps.size()); i++) {
            Drawable icon = apps.get(i).icon;
            if (icon == null) continue;
            int x = left + (i % 2) * (cell + gap), y = top + (i / 2) * (cell + gap);
            icon.setBounds(x, y, x + cell, y + cell);
            icon.draw(c);
        }
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return ri.activityInfo.loadBanner(pm);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density, isV2), h = tileHeightPx(w, isV2);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        if (isV2) {
            card.setBackgroundResource(isLightWallpaper ? R.drawable.tile_v2_light_bg : R.drawable.tile_v2_bg);
            View banner = v.findViewById(R.id.banner);
            View icon = v.findViewById(R.id.icon);
            TextView label = v.findViewById(R.id.label);
            banner.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
            label.setVisibility(View.VISIBLE);

            int iconSize = Math.round(24f * density);
            LinearLayout.LayoutParams iconLp = (LinearLayout.LayoutParams) icon.getLayoutParams();
            iconLp.width = iconSize;
            iconLp.height = iconSize;
            iconLp.gravity = Gravity.CENTER_HORIZONTAL;
            icon.setLayoutParams(iconLp);

            LinearLayout.LayoutParams labelLp = (LinearLayout.LayoutParams) label.getLayoutParams();
            labelLp.topMargin = Math.round(2f * density);
            labelLp.leftMargin = Math.round(1f * density);
            labelLp.rightMargin = Math.round(1f * density);
            labelLp.gravity = Gravity.CENTER_HORIZONTAL;
            label.setLayoutParams(labelLp);

            label.setSingleLine(false);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
            label.setTextColor(isLightWallpaper ? 0xDD111827 : 0xFFFFFFFF);
            label.setAlpha(isLightWallpaper ? 0.70f : 0.75f);
            label.setPadding(0, 0, 0, 0);
        }
        return new Holder(v, listener, animations, advancedEffects, reducedMotion, isV2);
    }

    static Drawable getV2Glyph(Context ctx, String pkg) {
        if (pkg == null) return null;
        if (pkg.contains("youtube.tvmusic")) {
            return ctx.getDrawable(R.drawable.ic_v2_ytmusic);
        } else if (pkg.contains("youtube")) {
            return ctx.getDrawable(R.drawable.ic_v2_youtube);
        } else if (pkg.contains("netflix")) {
            return ctx.getDrawable(R.drawable.ic_v2_netflix);
        } else if (pkg.contains("hotstar")) {
            return ctx.getDrawable(R.drawable.ic_v2_hotstar);
        } else if (pkg.contains("amazonvideo") || pkg.contains("primevideo")) {
            return ctx.getDrawable(R.drawable.ic_v2_prime);
        } else if (pkg.contains("airtel")) {
            return ctx.getDrawable(R.drawable.ic_v2_airtel);
        } else if (pkg.contains("apple.atve") || pkg.contains("appletv")) {
            return ctx.getDrawable(R.drawable.ic_v2_apple);
        } else if (pkg.contains("vending")) {
            return ctx.getDrawable(R.drawable.ic_v2_playstore);
        } else if (pkg.contains("SystemFolder") || pkg.contains("system")) {
            return ctx.getDrawable(R.drawable.ic_v2_system);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        int actualIndex = isV2 ? activePage * dockSize + position : position;
        if (actualIndex < 0 || actualIndex >= items.size()) return;
        Entry e = items.get(actualIndex);
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        if (isV2) {
            h.label.setText(e.label);
            h.label.setTextColor(isLightWallpaper ? 0xDD111827 : 0xFFFFFFFF);

            Drawable glyph = getV2Glyph(ctx, e.pkg);
            h.icon.setImageDrawable(glyph != null ? glyph : (e.icon != null ? e.icon : e.banner));
            if (glyph != null) {
                h.icon.setColorFilter(isLightWallpaper ? 0xDD111827 : 0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
            } else {
                h.icon.clearColorFilter();
            }

            h.card.setBackgroundResource(isLightWallpaper ? R.drawable.tile_v2_light_bg : R.drawable.tile_v2_bg);
            if (focused) {
                h.card.setScaleX(animations ? 1.22f : 1f);
                h.card.setScaleY(animations ? 1.22f : 1f);
                h.icon.setAlpha(1.0f);
                h.label.setAlpha(1.0f);
            } else {
                h.card.setScaleX(1.0f);
                h.card.setScaleY(1.0f);
                h.icon.setAlpha(isLightWallpaper ? 0.90f : 0.85f);
                h.label.setAlpha(isLightWallpaper ? 0.70f : 0.75f);
            }
        } else {
            h.label.setSingleLine(true);
            h.label.setMaxLines(1);
            h.card.setScaleX(focused && animations ? focusScale() : 1f);
            h.card.setScaleY(focused && animations ? focusScale() : 1f);
            h.card.setTranslationY(focused && advancedEffects ? -4f * ctx.getResources().getDisplayMetrics().density : 0f);
            h.card.setTranslationZ(focused && advancedEffects ? 8f * ctx.getResources().getDisplayMetrics().density : 0f);
            h.card.setBackgroundResource(R.drawable.tile_bg);
            h.banner.setScaleX(focused && advancedEffects ? 1.035f : 1f);
            h.banner.setScaleY(focused && advancedEffects ? 1.035f : 1f);
            if (e.banner != null) {
                h.banner.setImageDrawable(e.banner);
                h.banner.setVisibility(View.VISIBLE);
                h.icon.setVisibility(View.GONE);
                h.label.setVisibility(View.GONE);
            } else {
                h.banner.setImageDrawable(null);
                h.banner.setVisibility(View.GONE);
                h.icon.setImageDrawable(e.icon);
                h.icon.setVisibility(View.VISIBLE);
                h.label.setText(e.label);
                h.label.setVisibility(View.VISIBLE);
            }
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        if (e.kind == Entry.FOLDER) {
            SystemFolder.show((Activity) ctx, ctx.getString(R.string.system_folder), systemApps());
            return;
        }
        launchEntry(ctx, e);
    }

    void launchAt(int position) {
        int actualIndex = isV2 ? activePage * dockSize + position : position;
        if (actualIndex >= 0 && actualIndex < items.size()) {
            launch(items.get(actualIndex));
        }
    }

    /** Opens an app tile from anywhere in the launcher (the grid or the System folder). */
    static void launchEntry(Context ctx, Entry e) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (e.kind == Entry.WALLPAPER) {
            ctx.startActivity(new Intent(ctx, WallpaperPickerActivity.class));
            return;
        }
        if (e.kind == Entry.PRAYER) {
            ctx.startActivity(new Intent(ctx, PrayerTimesActivity.class));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            Usage.recordLaunch(ctx, e.pkg);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        if (isV2) {
            int start = activePage * dockSize;
            if (start >= items.size()) return 0;
            return Math.min(dockSize, items.size() - start);
        }
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        int actualIndex = isV2 ? activePage * dockSize + position : position;
        if (actualIndex < 0 || actualIndex >= items.size()) return RecyclerView.NO_ID;
        return items.get(actualIndex).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        int actualIndex = isV2 ? activePage * dockSize + position : position;
        return actualIndex >= 0 && actualIndex < items.size() ? items.get(actualIndex).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) {
                if (isV2) {
                    int pageStart = activePage * dockSize;
                    if (i >= pageStart && i < pageStart + dockSize) return i - pageStart;
                    return -1;
                }
                return i;
            }
        }
        return -1;
    }

    String labelAt(int position) {
        int actualIndex = isV2 ? activePage * dockSize + position : position;
        return actualIndex >= 0 && actualIndex < items.size() ? items.get(actualIndex).label : "";
    }

    float focusScale() {
        return !animations ? 1f : advancedEffects ? 1.22f : FOCUS_SCALE;
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final boolean animations;
        final boolean advancedEffects;
        final boolean reducedMotion;

        final boolean isV2;

        Holder(View v, TileListener listener, boolean animations, boolean advancedEffects,
               boolean reducedMotion, boolean isV2) {
            super(v);
            this.isV2 = isV2;
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            this.animations = animations;
            this.advancedEffects = advancedEffects;
            this.reducedMotion = reducedMotion;

            final float density = v.getResources().getDisplayMetrics().density;
            if (!isV2) {
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                    }
                });
                card.setClipToOutline(true);
            } else {
                card.setClipToOutline(false);
            }

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                if (isV2) {
                    float s = focused && animations ? 1.22f : 1f;
                    view.animate().scaleX(s).scaleY(s).setDuration(200).start();
                    float unfocusedAlpha = isLightWallpaper ? 0.90f : 0.85f;
                    float unfocusedLabelAlpha = isLightWallpaper ? 0.70f : 0.75f;
                    icon.animate().alpha(focused ? 1.0f : unfocusedAlpha).setDuration(200).start();
                    label.animate().alpha(focused ? 1.0f : unfocusedLabelAlpha).setDuration(200).start();
                    int pos = getBindingAdapterPosition();
                    if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                        listener.onTileFocused(pos, view);
                    }
                    return;
                }
                banner.animate().cancel();
                float scale = !animations ? 1f : advancedEffects ? 1.22f : FOCUS_SCALE;
                float s = focused ? scale : 1f;
                if (!animations) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationY(0f);
                    view.setTranslationZ(0f);
                    banner.setScaleX(1f);
                    banner.setScaleY(1f);
                } else if (reducedMotion) {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused ? -6f * density : 0f)
                            .translationZ(0f)
                            .setDuration(focused ? 120 : 80)
                            .setInterpolator(new DecelerateInterpolator())
                            .withLayer().start();
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused ? -6f * density : 0f)
                            .translationZ(0f)
                            .setDuration(advancedEffects ? (focused ? 220 : 170) : (focused ? 260 : 180))
                            .setInterpolator(new OvershootInterpolator(focused ? (advancedEffects ? 0.7f : 1.6f) : 0f))
                            .withLayer().start();
                    if (advancedEffects) {
                        banner.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                                .setDuration(280).start();
                    }
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
