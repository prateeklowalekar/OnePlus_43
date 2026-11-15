package io.github.cleanyourtv.multiview;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private Button btnLaunchDrastic;
    private Button btnLaunchMyboy;
    private Button btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnLaunchDrastic = findViewById(R.id.btn_launch_drastic);
        btnLaunchMyboy = findViewById(R.id.btn_launch_myboy);
        btnStop = findViewById(R.id.btn_stop);

        setupModeCards();
        setupSourceButtons();
        setupActionButtons();
    }

    private void setupSourceButtons() {
        findViewById(R.id.btn_src_yt_web).setOnClickListener(v -> loadUrl("https://m.youtube.com"));
        findViewById(R.id.btn_src_yt_tv).setOnClickListener(v -> loadUrl("https://www.youtube.com/tv"));
        findViewById(R.id.btn_src_twitch).setOnClickListener(v -> loadUrl("https://m.twitch.tv"));
        findViewById(R.id.btn_src_sign_in).setOnClickListener(v -> loadUrl("https://accounts.google.com/ServiceLogin?service=youtube"));
    }

    private void loadUrl(String url) {
        startMultiView(MultiViewService.currentMode);
        Intent intent = new Intent(MultiViewService.ACTION_LOAD_URL);
        intent.setPackage(getPackageName());
        intent.putExtra(MultiViewService.EXTRA_URL, url);
        sendBroadcast(intent);
        Toast.makeText(this, "Loading: " + url, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (MultiViewService.isRunning) {
            String modeStr;
            switch (MultiViewService.currentMode) {
                case MultiViewService.MODE_PIP:
                    modeStr = "Active (PiP)";
                    break;
                case MultiViewService.MODE_WINDOWED:
                    modeStr = "Active (Windowed)";
                    break;
                case MultiViewService.MODE_SPLIT:
                    modeStr = "Active (Split 50/50)";
                    break;
                case MultiViewService.MODE_FULL:
                    modeStr = "Active (Full Screen)";
                    break;
                default:
                    modeStr = "Active";
            }
            tvStatus.setText("● " + modeStr);
            tvStatus.setTextColor(0xFF38BDF8); // Cyan
        } else {
            tvStatus.setText("Inactive");
            tvStatus.setTextColor(0xFF94A3B8); // Gray
        }
    }

    private void setupModeCards() {
        findViewById(R.id.card_pip).setOnClickListener(v -> startMultiView(MultiViewService.MODE_PIP));
        findViewById(R.id.card_windowed).setOnClickListener(v -> startMultiView(MultiViewService.MODE_WINDOWED));
        findViewById(R.id.card_split).setOnClickListener(v -> startMultiView(MultiViewService.MODE_SPLIT));
        findViewById(R.id.card_full).setOnClickListener(v -> startMultiView(MultiViewService.MODE_FULL));
    }

    private void setupActionButtons() {
        btnLaunchDrastic.setOnClickListener(v -> {
            startMultiView(MultiViewService.MODE_PIP);
            launchApp("com.dsemu.drastic");
        });

        btnLaunchMyboy.setOnClickListener(v -> {
            startMultiView(MultiViewService.MODE_PIP);
            launchApp("com.fastemulator.gba");
        });

        btnStop.setOnClickListener(v -> {
            Intent stopIntent = new Intent(this, MultiViewService.class);
            stopIntent.setAction(MultiViewService.ACTION_STOP);
            startService(stopIntent);
            tvStatus.postDelayed(this::updateStatus, 300);
        });
    }

    private void startMultiView(int mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        Intent serviceIntent = new Intent(this, MultiViewService.class);
        serviceIntent.setAction(MultiViewService.ACTION_START);
        serviceIntent.putExtra(MultiViewService.EXTRA_MODE, mode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        updateStatus();
    }

    private void launchApp(String packageName) {
        Intent launchIntent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "App not installed: " + packageName, Toast.LENGTH_SHORT).show();
        }
    }
}
