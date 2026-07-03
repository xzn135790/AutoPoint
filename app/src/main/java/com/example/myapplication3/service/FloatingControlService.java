package com.example.myapplication3.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication3.R;
import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.store.ClickProfileStore;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class FloatingControlService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private TextView statusText;
    private MaterialButton pauseButton;
    private AutoClickAccessibilityService.StatusCallback statusCallback;
    private float touchStartX;
    private float touchStartY;
    private int windowStartX;
    private int windowStartY;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_tip, Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (floatingView == null) {
            showFloatingView();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        AutoClickAccessibilityService.removeStatusCallback(statusCallback);
        if (windowManager != null && floatingView != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
        super.onDestroy();
    }

    private void showFloatingView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.view_floating_control, null);
        statusText = floatingView.findViewById(R.id.floatingStatusText);
        MaterialButton startButton = floatingView.findViewById(R.id.floatingStartButton);
        pauseButton = floatingView.findViewById(R.id.floatingPauseButton);
        MaterialButton stopButton = floatingView.findViewById(R.id.floatingStopButton);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 180;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        windowStartX = params.x;
                        windowStartY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = windowStartX + (int) (event.getRawX() - touchStartX);
                        params.y = windowStartY + (int) (event.getRawY() - touchStartY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    default:
                        return false;
                }
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickProfile profile = loadSelectedProfile();
                if (profile == null) {
                    updateState(false, false, "没有可执行方案");
                    return;
                }
                AutoClickAccessibilityService.startProfile(profile);
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutoClickAccessibilityService.pauseOrResume();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutoClickAccessibilityService.stopRunning();
            }
        });

        statusCallback = new AutoClickAccessibilityService.StatusCallback() {
            @Override
            public void onStatusChanged(final boolean running, final boolean paused, final String message) {
                if (floatingView == null) {
                    return;
                }
                floatingView.post(new Runnable() {
                    @Override
                    public void run() {
                        updateState(running, paused, message);
                    }
                });
            }
        };
        AutoClickAccessibilityService.addStatusCallback(statusCallback);

        updateState(false, false, AutoClickAccessibilityService.getLastMessage());
        windowManager.addView(floatingView, params);
    }

    private ClickProfile loadSelectedProfile() {
        ClickProfileStore store = new ClickProfileStore(this);
        List<ClickProfile> profiles = store.loadProfiles();
        String selectedId = store.loadSelectedProfileId();
        for (ClickProfile profile : profiles) {
            if (profile.getId().equals(selectedId)) {
                return profile;
            }
        }
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    private void updateState(boolean running, boolean paused, String message) {
        statusText.setText(message);
        pauseButton.setText(paused ? "继续" : "暂停");
        if (!running) {
            pauseButton.setText("暂停");
        }
    }
}
