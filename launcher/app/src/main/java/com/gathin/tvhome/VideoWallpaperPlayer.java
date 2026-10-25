package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileInputStream;

/** A silent, local background video player. Plays wallpaper.mp4 or loops video wallpapers. */
final class VideoWallpaperPlayer implements TextureView.SurfaceTextureListener {
    static final String FILE_NAME = "wallpaper.mp4";
    private static final String TAG = "TvHome.Video";
    private final Context context;
    private final TextureView video;
    private MediaPlayer player;
    private Surface surface;
    private boolean active, prepared, closed, screenOn;
    private int position, videoWidth, videoHeight;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            if (screenOn) prepare(); else release();
        }
    };

    VideoWallpaperPlayer(Context context, FrameLayout root) {
        this.context = context;
        screenOn = context.getSystemService(PowerManager.class).isInteractive();
        video = new TextureView(context);
        video.setOpaque(false);
        video.setAlpha(0f);
        video.setFocusable(false);
        video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        
        int skyIndex = root.indexOfChild(root.findViewById(R.id.sky));
        int index = Math.max(0, skyIndex);
        root.addView(video, index, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        video.setSurfaceTextureListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(screenReceiver, filter);
        }
    }

    private File getSourceFile() {
        File dir = context.getExternalFilesDir(null);
        File localMp4 = dir != null ? new File(dir, FILE_NAME) : null;
        if (localMp4 != null && localMp4.isFile() && localMp4.length() > 0) {
            return localMp4;
        }
        File sdMp4 = new File("/sdcard/Wallpapers/" + FILE_NAME);
        if (sdMp4.isFile() && sdMp4.length() > 0) {
            return sdMp4;
        }
        return null;
    }

    boolean hasVideo() {
        return getSourceFile() != null;
    }

    void start() {
        active = true;
        prepare();
    }

    void stop() {
        active = false;
        release();
    }

    void close() {
        closed = true;
        stop();
        try { context.unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        video.setSurfaceTextureListener(null);
        if (surface != null) surface.release();
        surface = null;
    }

    private void prepare() {
        File source = getSourceFile();
        if (!active || closed || !screenOn || surface == null || player != null
                || source == null || !source.isFile()) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        try (FileInputStream input = new FileInputStream(source)) {
            next.setDataSource(input.getFD());
            next.setSurface(surface);
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setScreenOnWhilePlaying(false);
            next.setOnVideoSizeChangedListener((media, width, height) -> {
                if (media != player) return;
                videoWidth = width;
                videoHeight = height;
                transform();
            });
            next.setOnPreparedListener(media -> {
                if (media != player || !active || closed || !screenOn) return;
                prepared = true;
                if (position > 0) media.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                media.start();
                Log.i(TAG, "Playing live video background; resumeMs=" + position);
            });
            next.setOnInfoListener((media, what, extra) -> {
                if (media == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    video.setAlpha(1f);
                    Log.i(TAG, "Video wallpaper frame rendered");
                }
                return false;
            });
            next.setOnErrorListener((media, what, extra) -> {
                if (media == player) {
                    Log.w(TAG, "Video wallpaper error: " + what + "/" + extra);
                    release();
                }
                return true;
            });
            next.prepareAsync();
        } catch (Exception error) {
            Log.w(TAG, "Video wallpaper playback failed", error);
            release();
        }
    }

    private void release() {
        MediaPlayer old = player;
        player = null;
        if (old != null) {
            if (prepared) {
                try { position = old.getCurrentPosition(); } catch (IllegalStateException ignored) {}
            }
            old.release();
        }
        prepared = false;
        video.setAlpha(0f);
    }

    private void transform() {
        float[] scale = VideoAspect.centerCrop(video.getWidth(), video.getHeight(), videoWidth, videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(scale[0], scale[1], video.getWidth() / 2f, video.getHeight() / 2f);
        video.setTransform(matrix);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        transform();
        prepare();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        transform();
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        if (surface != null) surface.release();
        surface = null;
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
