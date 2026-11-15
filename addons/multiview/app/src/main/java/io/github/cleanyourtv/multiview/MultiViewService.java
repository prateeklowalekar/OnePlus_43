package io.github.cleanyourtv.multiview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import java.io.ByteArrayInputStream;

public class MultiViewService extends Service {

    public static final String ACTION_START = "io.github.cleanyourtv.multiview.ACTION_START";
    public static final String ACTION_STOP = "io.github.cleanyourtv.multiview.ACTION_STOP";
    public static final String ACTION_SET_MODE = "io.github.cleanyourtv.multiview.ACTION_SET_MODE";
    public static final String ACTION_TOGGLE_FOCUS = "io.github.cleanyourtv.multiview.ACTION_TOGGLE_FOCUS";
    public static final String ACTION_LOAD_URL = "io.github.cleanyourtv.multiview.ACTION_LOAD_URL";

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_URL = "extra_url";

    public static final int MODE_PIP = 1;
    public static final int MODE_WINDOWED = 2;
    public static final int MODE_SPLIT = 3;
    public static final int MODE_FULL = 4;

    public static final int FOCUS_YOUTUBE = 0;
    public static final int FOCUS_BROWSER = 1;
    public static final int FOCUS_GAME = 2;

    private static final String DEFAULT_URL_YOUTUBE = "https://m.youtube.com";
    private static final String DEFAULT_URL_BROWSER = "https://www.google.com";

    public static boolean isRunning = false;
    public static int currentMode = MODE_PIP;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams layoutParams;

    private View leftPane;
    private View splitDivider;
    private View rightPane;
    private WebView webViewLeft;
    private WebView webViewRight;

    private View hudBar;
    private Button btnFocus;

    private int currentFocus = FOCUS_YOUTUBE;
    private int cornerPosition = 0; // 0=Top-Right, 1=Bottom-Right, 2=Bottom-Left, 3=Top-Left

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (ACTION_SET_MODE.equals(action)) {
                int mode = intent.getIntExtra(EXTRA_MODE, currentMode);
                setMode(mode);
            } else if (ACTION_TOGGLE_FOCUS.equals(action)) {
                cycleFocus();
            } else if (ACTION_LOAD_URL.equals(action)) {
                String url = intent.getStringExtra(EXTRA_URL);
                if (url != null && webViewRight != null) {
                    webViewRight.loadUrl(url);
                }
            } else if (ACTION_STOP.equals(action)) {
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceNotification();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initOverlayView();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_MODE);
        filter.addAction(ACTION_TOGGLE_FOCUS);
        filter.addAction(ACTION_LOAD_URL);
        filter.addAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }

        isRunning = true;
    }

    private void startForegroundServiceNotification() {
        String channelId = "multiview_overlay";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "MultiView Overlay",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Provides simultaneous video and gaming overlay");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("MultiView Active")
                .setContentText("Overlay running")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .build();

        startForeground(1001, notification);
    }

    private void initOverlayView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.overlay_multiview, null);

        leftPane = overlayView.findViewById(R.id.left_pane);
        splitDivider = overlayView.findViewById(R.id.split_divider);
        rightPane = overlayView.findViewById(R.id.right_pane);

        webViewLeft = overlayView.findViewById(R.id.webview_left);
        webViewRight = overlayView.findViewById(R.id.webview_right);

        hudBar = overlayView.findViewById(R.id.hud_bar);
        btnFocus = overlayView.findViewById(R.id.hud_focus);

        setupWebView(webViewRight);
        setupWebView(webViewLeft);

        setupHudButtons();

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        layoutParams = new WindowManager.LayoutParams(
                640,
                360,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );

        applyModeParams(MODE_PIP);
        windowManager.addView(overlayView, layoutParams);

        // Remote key interceptor
        overlayView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == 83 || keyCode == 4077) {
                        cycleFocus();
                        return true;
                    }
                }
                return false;
            }
        });

        // Initial loads
        webViewRight.loadUrl(DEFAULT_URL_YOUTUBE);
        webViewLeft.loadUrl(DEFAULT_URL_BROWSER);
    }

    private static boolean isAdUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("doubleclick.net")
                || lower.contains("googleads")
                || lower.contains("/pagead/")
                || lower.contains("adservice.google.")
                || lower.contains("youtube.com/api/stats/ads")
                || lower.contains("youtube.com/pagead/")
                || lower.contains("youtubei/v1/att/get")
                || lower.contains("youtube.com/ptracking")
                || lower.contains("googlesyndication.com");
    }

    private void setupWebView(WebView wv) {
        if (wv == null) return;
        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(wv, true);
        }

        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        wv.setBackgroundColor(Color.BLACK);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (isAdUrl(url)) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String adblockJs = "javascript:(function() {" +
                        "try {" +
                        "  var style = document.getElementById('mv-ad-style');" +
                        "  if (!style) {" +
                        "    style = document.createElement('style');" +
                        "    style.id = 'mv-ad-style';" +
                        "    style.innerHTML = '.video-ads, .ytp-ad-module, ytd-ad-slot-renderer, ytd-promoted-sparkles-web-renderer, #player-ads, .ad-showing, .ad-container, .ytp-ad-overlay-container, ytd-in-feed-ad-layout-renderer { display: none !important; }';" +
                        "    document.head.appendChild(style);" +
                        "  }" +
                        "  if (!window._adBlockInterval) {" +
                        "    window._adBlockInterval = setInterval(function() {" +
                        "      var video = document.querySelector('video');" +
                        "      var ad = document.querySelector('.ad-showing, .ad-interrupting');" +
                        "      if (ad && video && !isNaN(video.duration)) {" +
                        "        video.currentTime = video.duration;" +
                        "      }" +
                        "      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');" +
                        "      if (skip) { skip.click(); }" +
                        "    }, 500);" +
                        "  }" +
                        "} catch(e) {}" +
                        "})()";
                view.evaluateJavascript(adblockJs, null);
            }
        });

        wv.setWebChromeClient(new WebChromeClient());
    }

    private void setupHudButtons() {
        overlayView.findViewById(R.id.hud_pip).setOnClickListener(v -> setMode(MODE_PIP));
        overlayView.findViewById(R.id.hud_win).setOnClickListener(v -> setMode(MODE_WINDOWED));
        overlayView.findViewById(R.id.hud_split).setOnClickListener(v -> setMode(MODE_SPLIT));
        overlayView.findViewById(R.id.hud_full).setOnClickListener(v -> setMode(MODE_FULL));

        overlayView.findViewById(R.id.hud_corner).setOnClickListener(v -> cycleCorner());

        btnFocus.setOnClickListener(v -> cycleFocus());

        overlayView.findViewById(R.id.hud_close).setOnClickListener(v -> stopSelf());
    }

    public void setMode(int mode) {
        currentMode = mode;
        applyModeParams(mode);
        try {
            windowManager.updateViewLayout(overlayView, layoutParams);
        } catch (Exception ignored) {}
    }

    private void applyModeParams(int mode) {
        if (mode == MODE_PIP) {
            if (leftPane != null) leftPane.setVisibility(View.GONE);
            if (splitDivider != null) splitDivider.setVisibility(View.GONE);
            if (rightPane != null) rightPane.setVisibility(View.VISIBLE);
            layoutParams.width = 640;
            layoutParams.height = 360;
            applyCornerGravity();
        } else if (mode == MODE_WINDOWED) {
            if (leftPane != null) leftPane.setVisibility(View.GONE);
            if (splitDivider != null) splitDivider.setVisibility(View.GONE);
            if (rightPane != null) rightPane.setVisibility(View.VISIBLE);
            layoutParams.width = 1120;
            layoutParams.height = 630;
            layoutParams.gravity = Gravity.CENTER;
            layoutParams.x = 0;
            layoutParams.y = 0;
        } else if (mode == MODE_SPLIT) {
            // Full 1920x1080 screen with 50/50 dual pane (Left: Browser / Right: YouTube)
            if (leftPane != null) leftPane.setVisibility(View.VISIBLE);
            if (splitDivider != null) splitDivider.setVisibility(View.VISIBLE);
            if (rightPane != null) rightPane.setVisibility(View.VISIBLE);
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.CENTER;
            layoutParams.x = 0;
            layoutParams.y = 0;
        } else if (mode == MODE_FULL) {
            if (leftPane != null) leftPane.setVisibility(View.GONE);
            if (splitDivider != null) splitDivider.setVisibility(View.GONE);
            if (rightPane != null) rightPane.setVisibility(View.VISIBLE);
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.CENTER;
            layoutParams.x = 0;
            layoutParams.y = 0;
        }
    }

    private void applyCornerGravity() {
        int margin = 32;
        switch (cornerPosition) {
            case 0: // Top-Right
                layoutParams.gravity = Gravity.TOP | Gravity.END;
                layoutParams.x = margin;
                layoutParams.y = margin;
                break;
            case 1: // Bottom-Right
                layoutParams.gravity = Gravity.BOTTOM | Gravity.END;
                layoutParams.x = margin;
                layoutParams.y = margin;
                break;
            case 2: // Bottom-Left
                layoutParams.gravity = Gravity.BOTTOM | Gravity.START;
                layoutParams.x = margin;
                layoutParams.y = margin;
                break;
            case 3: // Top-Left
                layoutParams.gravity = Gravity.TOP | Gravity.START;
                layoutParams.x = margin;
                layoutParams.y = margin;
                break;
        }
    }

    private void cycleCorner() {
        if (currentMode != MODE_PIP) {
            setMode(MODE_PIP);
            return;
        }
        cornerPosition = (cornerPosition + 1) % 4;
        applyCornerGravity();
        try {
            windowManager.updateViewLayout(overlayView, layoutParams);
        } catch (Exception ignored) {}
    }

    private void cycleFocus() {
        if (currentMode == MODE_SPLIT) {
            if (currentFocus == FOCUS_YOUTUBE) {
                currentFocus = FOCUS_BROWSER;
            } else if (currentFocus == FOCUS_BROWSER) {
                currentFocus = FOCUS_GAME;
            } else {
                currentFocus = FOCUS_YOUTUBE;
            }
        } else {
            if (currentFocus == FOCUS_YOUTUBE) {
                currentFocus = FOCUS_GAME;
            } else {
                currentFocus = FOCUS_YOUTUBE;
            }
        }
        applyFocusState();
    }

    private void applyFocusState() {
        if (currentFocus == FOCUS_GAME) {
            // Passthrough all D-pad and controller keys to background game
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            btnFocus.setText("Game");
            btnFocus.setTextColor(0xFF10B981); // Emerald Green
            try {
                windowManager.updateViewLayout(overlayView, layoutParams);
            } catch (Exception ignored) {}
        } else if (currentFocus == FOCUS_BROWSER) {
            // Focus Browser pane
            layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            btnFocus.setText("Web");
            btnFocus.setTextColor(0xFFF59E0B); // Amber
            try {
                windowManager.updateViewLayout(overlayView, layoutParams);
                if (webViewLeft != null) webViewLeft.requestFocus();
            } catch (Exception ignored) {}
        } else {
            // Focus YouTube pane
            layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            btnFocus.setText("YT");
            btnFocus.setTextColor(0xFF38BDF8); // Sky Blue
            try {
                windowManager.updateViewLayout(overlayView, layoutParams);
                if (webViewRight != null) webViewRight.requestFocus();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            int mode = intent.getIntExtra(EXTRA_MODE, currentMode);
            setMode(mode);
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null && webViewRight != null) {
                webViewRight.loadUrl(url);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            unregisterReceiver(controlReceiver);
        } catch (Exception ignored) {}

        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
        }

        if (webViewRight != null) {
            webViewRight.destroy();
            webViewRight = null;
        }
        if (webViewLeft != null) {
            webViewLeft.destroy();
            webViewLeft = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}