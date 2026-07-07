package com.example.myapplication3.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import com.example.myapplication3.R;
import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;
import com.example.myapplication3.store.ClickProfileStore;
import com.example.myapplication3.util.AppLogger;

import java.util.ArrayList;
import java.util.List;

public class FloatingControlService extends Service {
    public static final String EXTRA_START_RECORDING = "start_recording";
    public static final String EXTRA_SHOW_PROFILE_BUBBLE = "show_profile_bubble";

    private static final int MODE_EXECUTE = 0;
    private static final int MODE_CROSSHAIR = 1;
    private static final int MODE_RECORDING = 2;
    private static final int RECORD_SWIPE_THRESHOLD_PX = 24;
    private static final long MIN_RECORDED_CLICK_MS = 50L;
    private static final long MIN_RECORDED_SWIPE_MS = 120L;
    private static final int FLOATING_DEFAULT_WIDTH_DP = 200;
    private static final int FLOATING_DEFAULT_HEIGHT_DP = 260;
    private static final int FLOATING_MIN_WIDTH_DP = 160;
    private static final int FLOATING_MAX_WIDTH_DP = 360;
    private static final int FLOATING_MIN_HEIGHT_DP = 220;
    private static final int FLOATING_MAX_HEIGHT_DP = 520;

    private WindowManager windowManager;
    private View floatingView;
    private View crosshairView;
    private View recordingView;
    private View executionPreviewView;
    private LinearLayout profileBubbleView;
    private TextView profileBubbleText;
    private WindowManager.LayoutParams floatingParams;
    private WindowManager.LayoutParams crosshairParams;
    private WindowManager.LayoutParams profileBubbleParams;
    private TextView statusText;
    private TextView recordingStatusText;
    private Button pauseButton;
    private LinearLayout executeActions;
    private LinearLayout crosshairActions;
    private LinearLayout recordingActions;
    private AutoClickAccessibilityService.StatusCallback statusCallback;
    private AutoClickAccessibilityService.StepCallback stepCallback;
    private int currentMode = MODE_EXECUTE;
    private float touchStartX;
    private float touchStartY;
    private int windowStartX;
    private int windowStartY;
    private int floatingResizeStartWidth;
    private int floatingResizeStartHeight;
    private boolean hasSwipeStart;
    private int swipeStartX;
    private int swipeStartY;
    private boolean profileBubbleExecuting;
    private float recordDownX;
    private float recordDownY;
    private float recordDownLocalX;
    private float recordDownLocalY;
    private long recordDownTime;
    private int recordedStepCount;
    private final List<View> recordingMarkers = new ArrayList<>();
    private final List<Integer> recordingMarkerGroupSizes = new ArrayList<>();
    private final List<ClickStep> pendingRecordedSteps = new ArrayList<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLogger.i(this, "FloatingControlService onStartCommand. startId=" + startId);
        if (!Settings.canDrawOverlays(this)) {
            AppLogger.i(this, "Service stopped: overlay permission missing");
            Toast.makeText(this, R.string.overlay_permission_tip, Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_SHOW_PROFILE_BUBBLE, false)) {
            AppLogger.i(this, "Service received show profile bubble extra");
            showProfileBubble();
            return START_STICKY;
        }
        if (floatingView == null) {
            showFloatingView();
        }
        if (floatingView != null && intent != null && intent.getBooleanExtra(EXTRA_START_RECORDING, false)) {
            AppLogger.i(this, "Service received start recording extra");
            switchMode(MODE_RECORDING);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        AppLogger.i(this, "FloatingControlService onDestroy");
        AutoClickAccessibilityService.removeStatusCallback(statusCallback);
        AutoClickAccessibilityService.removeStepCallback(stepCallback);
        removeProfileBubble();
        removeExecutionPreview();
        removeRecordingView();
        removeCrosshairView();
        removeOverlayView(floatingView);
        floatingView = null;
        super.onDestroy();
    }

    private void showFloatingView() {
        AppLogger.i(this, "showFloatingView start");
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            AppLogger.i(this, "Inflating floating control layout");
            floatingView = LayoutInflater.from(this).inflate(R.layout.view_floating_control, null);
            AppLogger.i(this, "Floating layout inflated");
            statusText = floatingView.findViewById(R.id.floatingStatusText);
            executeActions = floatingView.findViewById(R.id.executeActions);
            crosshairActions = floatingView.findViewById(R.id.crosshairActions);
            recordingActions = floatingView.findViewById(R.id.recordingActions);
            Button executeModeButton = floatingView.findViewById(R.id.executeModeButton);
            Button crosshairModeButton = floatingView.findViewById(R.id.crosshairModeButton);
            Button recordModeButton = floatingView.findViewById(R.id.recordModeButton);
            Button startButton = floatingView.findViewById(R.id.floatingStartButton);
            pauseButton = floatingView.findViewById(R.id.floatingPauseButton);
            Button stopButton = floatingView.findViewById(R.id.floatingStopButton);
            Button closeButton = floatingView.findViewById(R.id.closeFloatingButton);
            Button addClickPointButton = floatingView.findViewById(R.id.addClickPointButton);
            Button setSwipeStartButton = floatingView.findViewById(R.id.setSwipeStartButton);
            Button setSwipeEndButton = floatingView.findViewById(R.id.setSwipeEndButton);
            Button stopRecordingButton = floatingView.findViewById(R.id.stopRecordingButton);
            Button undoRecordedStepButton = floatingView.findViewById(R.id.undoRecordedStepButton);
            Button useRecordedPlanButton = floatingView.findViewById(R.id.useRecordedPlanButton);
            Button closeRecordingButton = floatingView.findViewById(R.id.closeRecordingButton);
            View resizeHandle = floatingView.findViewById(R.id.resizeHandle);

            floatingParams = new WindowManager.LayoutParams(
                    dp(FLOATING_DEFAULT_WIDTH_DP),
                    dp(FLOATING_DEFAULT_HEIGHT_DP),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            floatingParams.gravity = Gravity.TOP | Gravity.START;
            floatingParams.x = 24;
            floatingParams.y = 180;

            floatingView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartX = event.getRawX();
                            touchStartY = event.getRawY();
                            windowStartX = floatingParams.x;
                            windowStartY = floatingParams.y;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            floatingParams.x = windowStartX + (int) (event.getRawX() - touchStartX);
                            floatingParams.y = windowStartY + (int) (event.getRawY() - touchStartY);
                            windowManager.updateViewLayout(floatingView, floatingParams);
                            return true;
                        default:
                            return false;
                    }
                }
            });

            resizeHandle.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartX = event.getRawX();
                            touchStartY = event.getRawY();
                            floatingResizeStartWidth = floatingParams.width;
                            floatingResizeStartHeight = floatingParams.height;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int nextWidth = floatingResizeStartWidth + (int) (event.getRawX() - touchStartX);
                            int nextHeight = floatingResizeStartHeight + (int) (event.getRawY() - touchStartY);
                            floatingParams.width = clamp(nextWidth, dp(FLOATING_MIN_WIDTH_DP), dp(FLOATING_MAX_WIDTH_DP));
                            floatingParams.height = clamp(nextHeight, dp(FLOATING_MIN_HEIGHT_DP), dp(FLOATING_MAX_HEIGHT_DP));
                            windowManager.updateViewLayout(floatingView, floatingParams);
                            return true;
                        default:
                            return true;
                    }
                }
            });

            executeModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_RECORDING);
            }
            });
            crosshairModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_CROSSHAIR);
            }
            });
            recordModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_RECORDING);
            }
            });
            startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useSelectedProfile();
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
                removeExecutionPreview();
            }
            });
            closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFloatingControls();
            }
            });
            addClickPointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addClickFromCrosshair();
            }
            });
            setSwipeStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSwipeStartFromCrosshair();
            }
            });
            setSwipeEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSwipeEndFromCrosshair();
            }
            });
            stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePendingRecordingSteps();
            }
            });
            undoRecordedStepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoLastRecordedStep();
            }
            });
            useRecordedPlanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useSelectedProfile();
            }
            });
            closeRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFloatingControls();
            }
            });

            registerServiceCallbacks();

            updateState(false, false, AutoClickAccessibilityService.getLastMessage());
            updateModeViews();
            windowManager.addView(floatingView, floatingParams);
            AppLogger.i(this, "Floating control panel added");
        } catch (Throwable e) {
            AppLogger.e(this, "Failed to add floating control panel", e);
            floatingView = null;
            Toast.makeText(this, "悬浮面板显示失败，请查看日志", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void registerServiceCallbacks() {
        if (statusCallback == null) {
            statusCallback = new AutoClickAccessibilityService.StatusCallback() {
                @Override
                public void onStatusChanged(final boolean running, final boolean paused, final String message) {
                    final View anchor = floatingView != null ? floatingView : profileBubbleView;
                    if (anchor == null) {
                        return;
                    }
                    anchor.post(new Runnable() {
                        @Override
                        public void run() {
                            if (floatingView != null) {
                                updateState(running, paused, message);
                            }
                            if (!running) {
                                removeExecutionPreview();
                                profileBubbleExecuting = false;
                                updateProfileBubble(false);
                            }
                        }
                    });
                }
            };
            AutoClickAccessibilityService.addStatusCallback(statusCallback);
        }
        if (stepCallback == null) {
            stepCallback = new AutoClickAccessibilityService.StepCallback() {
                @Override
                public void onStepDispatch(final ClickStep step, final int startX, final int startY,
                                           final int endX, final int endY, final int stepIndex) {
                    final View anchor = floatingView != null ? floatingView : profileBubbleView;
                    if (anchor == null) {
                        return;
                    }
                    anchor.post(new Runnable() {
                        @Override
                        public void run() {
                            showCurrentExecutionPreview(step, startX, startY, endX, endY, stepIndex);
                        }
                    });
                }
            };
            AutoClickAccessibilityService.addStepCallback(stepCallback);
        }
    }

    private void showProfileBubble() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        removeOverlayView(floatingView);
        floatingView = null;
        removeProfileBubble();
        removeRecordingView();
        removeExecutionPreview();
        registerServiceCallbacks();
        ClickProfile profile = loadSelectedProfile();
        String name = profile == null ? "方案" : profile.getName();
        profileBubbleView = new LinearLayout(this);
        profileBubbleView.setOrientation(LinearLayout.HORIZONTAL);
        profileBubbleView.setGravity(Gravity.CENTER);
        profileBubbleView.setBackgroundResource(R.drawable.bg_profile_bubble_idle);
        profileBubbleView.setElevation(dp(8));
        profileBubbleView.setPadding(dp(12), 0, dp(8), 0);

        profileBubbleText = new TextView(this);
        profileBubbleText.setText(buildBubbleText(false, name));
        profileBubbleText.setTextColor(Color.WHITE);
        profileBubbleText.setTextSize(13);
        profileBubbleText.setGravity(Gravity.CENTER);
        profileBubbleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        profileBubbleView.addView(profileBubbleText, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));

        TextView closeBubbleView = new TextView(this);
        closeBubbleView.setText("×");
        closeBubbleView.setTextColor(Color.WHITE);
        closeBubbleView.setTextSize(18);
        closeBubbleView.setGravity(Gravity.CENTER);
        closeBubbleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        closeBubbleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeProfileBubbleControls();
            }
        });
        profileBubbleView.addView(closeBubbleView, new LinearLayout.LayoutParams(
                dp(28),
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        profileBubbleParams = new WindowManager.LayoutParams(
                dp(142),
                dp(48),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        profileBubbleParams.gravity = Gravity.TOP | Gravity.START;
        profileBubbleParams.x = 32;
        profileBubbleParams.y = 260;
        profileBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int downWindowX;
            private int downWindowY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downWindowX = profileBubbleParams.x;
                        downWindowY = profileBubbleParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downRawX);
                        int dy = (int) (event.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                            moved = true;
                        }
                        profileBubbleParams.x = downWindowX + dx;
                        profileBubbleParams.y = downWindowY + dy;
                        windowManager.updateViewLayout(profileBubbleView, profileBubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            toggleProfileBubbleExecution();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        try {
            windowManager.addView(profileBubbleView, profileBubbleParams);
            AppLogger.i(this, "Profile bubble added. name=" + name);
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to add profile bubble", e);
            profileBubbleView = null;
        }
    }

    private String shortBubbleName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "方案";
        }
        String trimmed = name.trim();
        return trimmed.length() > 4 ? trimmed.substring(0, 4) : trimmed;
    }

    private String buildBubbleText(boolean running, String name) {
        return running ? "停止" : "启动 " + shortBubbleName(name);
    }

    private void updateProfileBubble(boolean running) {
        if (profileBubbleView == null || profileBubbleText == null) {
            return;
        }
        ClickProfile profile = loadSelectedProfile();
        String name = profile == null ? "方案" : profile.getName();
        profileBubbleText.setText(buildBubbleText(running, name));
        profileBubbleView.setBackgroundResource(running
                ? R.drawable.bg_profile_bubble_running
                : R.drawable.bg_profile_bubble_idle);
    }

    private void closeProfileBubbleControls() {
        AppLogger.i(this, "Profile bubble close requested");
        AutoClickAccessibilityService.stopRunning();
        removeExecutionPreview();
        removeProfileBubble();
        stopSelf();
    }

    private void toggleProfileBubbleExecution() {
        if (profileBubbleExecuting) {
            AppLogger.i(this, "Profile bubble tap: stop execution");
            AutoClickAccessibilityService.stopRunning();
            removeExecutionPreview();
            profileBubbleExecuting = false;
            updateProfileBubble(false);
            return;
        }
        ClickProfile profile = loadSelectedProfile();
        if (profile == null || profile.getSteps().isEmpty()) {
            Toast.makeText(this, "当前方案没有步骤", Toast.LENGTH_SHORT).show();
            AppLogger.i(this, "Profile bubble tap ignored: no steps");
            return;
        }
        boolean started = AutoClickAccessibilityService.startProfile(profile);
        if (!started) {
            Toast.makeText(this, AutoClickAccessibilityService.getLastMessage(), Toast.LENGTH_SHORT).show();
            AppLogger.i(this, "Profile bubble execution failed: " + AutoClickAccessibilityService.getLastMessage());
            return;
        }
        profileBubbleExecuting = true;
        updateProfileBubble(true);
        AppLogger.i(this, "Profile bubble started execution. steps=" + profile.getSteps().size());
    }

    private void removeProfileBubble() {
        removeOverlayView(profileBubbleView);
        profileBubbleView = null;
        profileBubbleText = null;
        profileBubbleParams = null;
        profileBubbleExecuting = false;
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

    private void useSelectedProfile() {
        if (currentMode == MODE_RECORDING) {
            AppLogger.i(this, "Use selected profile from recording. recordedStepCount=" + recordedStepCount + ", pending=" + pendingRecordedSteps.size());
            if (!pendingRecordedSteps.isEmpty()) {
                Toast.makeText(this, "请先保存录制步骤", Toast.LENGTH_SHORT).show();
                return;
            }
            removeRecordingView();
            currentMode = MODE_EXECUTE;
            updateModeViews();
        }
        ClickProfile profile = loadSelectedProfile();
        if (profile == null || profile.getSteps().isEmpty()) {
            updateState(false, false, "没有可执行方案");
            AppLogger.i(this, "useSelectedProfile skipped: no executable steps");
            return;
        }
        AutoClickAccessibilityService.startProfile(profile);
    }

    private void closeFloatingControls() {
        AppLogger.i(this, "Close floating controls requested");
        AutoClickAccessibilityService.stopRunning();
        stopSelf();
    }

    private void switchMode(int mode) {
        AppLogger.i(this, "Switch floating mode to " + mode);
        if (currentMode == MODE_RECORDING && mode != MODE_RECORDING) {
            AppLogger.i(this, "Stop recording. recordedStepCount=" + recordedStepCount);
            removeRecordingView();
        }
        if (mode == MODE_RECORDING) {
            removeExecutionPreview();
        }
        currentMode = mode;
        hasSwipeStart = mode == MODE_CROSSHAIR && hasSwipeStart;
        if (mode == MODE_CROSSHAIR) {
            showCrosshair();
            updateState(false, false, "取点模式：拖动准星后添加步骤");
        } else {
            removeCrosshairView();
        }
        if (mode == MODE_RECORDING) {
            startRecording();
        }
        updateModeViews();
    }

    private void updateModeViews() {
        if (executeActions == null || crosshairActions == null || recordingActions == null) {
            return;
        }
        executeActions.setVisibility(currentMode == MODE_EXECUTE ? View.VISIBLE : View.GONE);
        crosshairActions.setVisibility(currentMode == MODE_CROSSHAIR ? View.VISIBLE : View.GONE);
        recordingActions.setVisibility(currentMode == MODE_RECORDING ? View.VISIBLE : View.GONE);
    }

    private void showCrosshair() {
        if (crosshairView != null) {
            return;
        }
        crosshairView = LayoutInflater.from(this).inflate(R.layout.view_crosshair, null);
        crosshairParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        crosshairParams.gravity = Gravity.TOP | Gravity.START;
        crosshairParams.x = 360;
        crosshairParams.y = 720;
        crosshairView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        windowStartX = crosshairParams.x;
                        windowStartY = crosshairParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        crosshairParams.x = windowStartX + (int) (event.getRawX() - touchStartX);
                        crosshairParams.y = windowStartY + (int) (event.getRawY() - touchStartY);
                        windowManager.updateViewLayout(crosshairView, crosshairParams);
                        return true;
                    default:
                        return true;
                }
            }
        });
        windowManager.addView(crosshairView, crosshairParams);
    }

    private void removeCrosshairView() {
        removeOverlayView(crosshairView);
        crosshairView = null;
        crosshairParams = null;
        hasSwipeStart = false;
    }

    private int[] getCrosshairCenter() {
        if (crosshairView == null || crosshairParams == null) {
            return null;
        }
        int width = crosshairView.getWidth() > 0 ? crosshairView.getWidth() : dp(56);
        int height = crosshairView.getHeight() > 0 ? crosshairView.getHeight() : dp(56);
        return new int[]{Math.max(0, crosshairParams.x + width / 2), Math.max(0, crosshairParams.y + height / 2)};
    }

    private void addClickFromCrosshair() {
        int[] point = getCrosshairCenter();
        if (point == null) {
            updateState(false, false, "准星未显示");
            return;
        }
        ClickStep step = ClickStep.click();
        step.setStartX(point[0]);
        step.setStartY(point[1]);
        step.setEndX(point[0]);
        step.setEndY(point[1]);
        if (saveCapturedStep(step)) {
            updateState(false, false, "已添加点击：" + point[0] + "," + point[1]);
        }
    }

    private void setSwipeStartFromCrosshair() {
        int[] point = getCrosshairCenter();
        if (point == null) {
            updateState(false, false, "准星未显示");
            return;
        }
        swipeStartX = point[0];
        swipeStartY = point[1];
        hasSwipeStart = true;
        updateState(false, false, "已设置滑动起点：" + swipeStartX + "," + swipeStartY);
    }

    private void addSwipeEndFromCrosshair() {
        if (!hasSwipeStart) {
            updateState(false, false, "请先设置滑动起点");
            return;
        }
        int[] point = getCrosshairCenter();
        if (point == null) {
            updateState(false, false, "准星未显示");
            return;
        }
        ClickStep step = ClickStep.swipe();
        step.setStartX(swipeStartX);
        step.setStartY(swipeStartY);
        step.setEndX(point[0]);
        step.setEndY(point[1]);
        if (saveCapturedStep(step)) {
            hasSwipeStart = false;
            updateState(false, false, "已添加滑动：" + swipeStartX + "," + swipeStartY + " -> " + point[0] + "," + point[1]);
        }
    }

    private void startRecording() {
        if (recordingView != null) {
            AppLogger.i(this, "startRecording ignored: recordingView already exists");
            return;
        }
        AppLogger.i(this, "startRecording create recording overlay");
        recordedStepCount = 0;
        recordingMarkers.clear();
        recordingMarkerGroupSizes.clear();
        pendingRecordedSteps.clear();
        recordingView = LayoutInflater.from(this).inflate(R.layout.view_recording_overlay, null);
        recordingStatusText = recordingView.findViewById(R.id.recordingStatusText);
        recordingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleRecordingTouch(event);
            }
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(recordingView, params);
            AppLogger.i(this, "Recording overlay added");
            bringFloatingPanelToFront();
            updateState(false, false, "录制中：点击或拖动屏幕");
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to add recording overlay", e);
            Toast.makeText(this, "录制层显示失败，请查看日志", Toast.LENGTH_LONG).show();
            removeRecordingView();
        }
    }

    private boolean handleRecordingTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                recordDownX = event.getRawX();
                recordDownY = event.getRawY();
                recordDownLocalX = event.getX();
                recordDownLocalY = event.getY();
                recordDownTime = event.getEventTime();
                return true;
            case MotionEvent.ACTION_UP:
                addRecordedStep(event);
                return true;
            default:
                return true;
        }
    }

    private void addRecordedStep(MotionEvent upEvent) {
        int startX = Math.max(0, Math.round(recordDownX));
        int startY = Math.max(0, Math.round(recordDownY));
        int endX = Math.max(0, Math.round(upEvent.getRawX()));
        int endY = Math.max(0, Math.round(upEvent.getRawY()));
        long duration = Math.max(0, upEvent.getEventTime() - recordDownTime);
        double distance = Math.hypot(endX - startX, endY - startY);
        ClickStep step;
        if (distance < RECORD_SWIPE_THRESHOLD_PX) {
            step = ClickStep.click();
            step.setStartX(startX);
            step.setStartY(startY);
            step.setEndX(startX);
            step.setEndY(startY);
            step.setDurationMs(Math.max(MIN_RECORDED_CLICK_MS, duration));
        } else {
            step = ClickStep.swipe();
            step.setStartX(startX);
            step.setStartY(startY);
            step.setEndX(endX);
            step.setEndY(endY);
            step.setDurationMs(Math.max(MIN_RECORDED_SWIPE_MS, duration));
        }
        pendingRecordedSteps.add(step);
        recordedStepCount++;
        String message = step.isSwipe()
                ? "已录制滑动：" + startX + "," + startY + " -> " + endX + "," + endY
                : "已录制点击：" + startX + "," + startY;
        if (step.isSwipe()) {
            addRecordingSwipeTrace(startX, startY, endX, endY);
        } else {
            addRecordingMarkerGroup(startX, startY);
        }
        updateRecordingStatus(message + "（未保存）");
        updateState(false, false, message + "（未保存）");
        AppLogger.i(this, message + " pending=" + pendingRecordedSteps.size());
    }

    private void undoLastRecordedStep() {
        if (recordedStepCount <= 0 || pendingRecordedSteps.isEmpty()) {
            updateState(false, false, "没有可撤销的录制步骤");
            return;
        }
        pendingRecordedSteps.remove(pendingRecordedSteps.size() - 1);
        recordedStepCount--;
        removeLastRecordingMarkerGroup();
        String message = "已撤销上一步，剩余未保存 " + pendingRecordedSteps.size() + " 步";
        updateRecordingStatus(message);
        updateState(false, false, message);
        AppLogger.i(this, message);
    }

    private void removeRecordingView() {
        removeOverlayView(recordingView);
        recordingView = null;
        recordingStatusText = null;
        recordedStepCount = 0;
        recordingMarkers.clear();
        recordingMarkerGroupSizes.clear();
        pendingRecordedSteps.clear();
    }

    private void savePendingRecordingSteps() {
        if (pendingRecordedSteps.isEmpty()) {
            updateState(false, false, "没有需要保存的录制步骤");
            return;
        }
        ClickProfileStore store = new ClickProfileStore(this);
        if (!store.appendStepsToSelectedProfile(new ArrayList<>(pendingRecordedSteps))) {
            updateState(false, false, "保存失败，请返回主界面检查方案");
            AppLogger.i(this, "savePendingRecordingSteps failed. pending=" + pendingRecordedSteps.size());
            return;
        }
        String message = "已保存 " + pendingRecordedSteps.size() + " 步";
        AppLogger.i(this, message);
        updateRecordingStatus(message);
        updateState(false, false, message);
        pendingRecordedSteps.clear();
        removeRecordingView();
        currentMode = MODE_EXECUTE;
        updateModeViews();
    }

    private boolean saveCapturedStep(ClickStep step) {
        ClickProfileStore store = new ClickProfileStore(this);
        if (!store.appendStepToSelectedProfile(step)) {
            AppLogger.i(this, "saveCapturedStep failed");
            updateState(false, false, "保存失败，请返回主界面检查方案");
            return false;
        }
        return true;
    }

    private void updateRecordingStatus(String message) {
        if (recordingStatusText != null) {
            recordingStatusText.setText(message);
        }
    }

    private void addRecordingMarkerGroup(int x, int y) {
        int[] localPoint = toRecordingLocalPoint(x, y);
        addRecordingMarker(localPoint[0], localPoint[1], String.valueOf(recordedStepCount));
        recordingMarkerGroupSizes.add(1);
    }

    private void addRecordingSwipeTrace(int startX, int startY, int endX, int endY) {
        if (!(recordingView instanceof FrameLayout)) {
            return;
        }
        int[] localStart = toRecordingLocalPoint(startX, startY);
        int[] localEnd = toRecordingLocalPoint(endX, endY);
        RecordingLineView lineView = new RecordingLineView(this, localStart[0], localStart[1], localEnd[0], localEnd[1]);
        ((FrameLayout) recordingView).addView(lineView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        recordingMarkers.add(lineView);
        addRecordingMarker(localStart[0], localStart[1], String.valueOf(recordedStepCount));
        addRecordingMarker(localEnd[0], localEnd[1], recordedStepCount + "终");
        recordingMarkerGroupSizes.add(3);
    }

    private void addRecordingMarker(int x, int y, String label) {
        if (!(recordingView instanceof FrameLayout)) {
            return;
        }
        FrameLayout marker = new FrameLayout(this);
        marker.setBackgroundResource(R.drawable.bg_record_marker);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(getColor(R.color.white));
        labelView.setTextSize(label.length() > 2 ? 10 : 13);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        View centerDot = new View(this);
        centerDot.setBackgroundColor(Color.WHITE);

        int size = label.length() > 2 ? dp(38) : dp(34);
        int dotSize = dp(6);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER);
        marker.addView(labelView, labelParams);
        marker.addView(centerDot, dotParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = Math.max(0, x - size / 2);
        params.topMargin = Math.max(0, y - size / 2);
        ((FrameLayout) recordingView).addView(marker, params);
        recordingMarkers.add(marker);
    }

    private void removeLastRecordingMarkerGroup() {
        if (!(recordingView instanceof FrameLayout) || recordingMarkers.isEmpty() || recordingMarkerGroupSizes.isEmpty()) {
            return;
        }
        int groupSize = recordingMarkerGroupSizes.remove(recordingMarkerGroupSizes.size() - 1);
        for (int i = 0; i < groupSize && !recordingMarkers.isEmpty(); i++) {
            View marker = recordingMarkers.remove(recordingMarkers.size() - 1);
            ((FrameLayout) recordingView).removeView(marker);
        }
    }

    private void bringFloatingPanelToFront() {
        if (windowManager == null || floatingView == null || floatingParams == null) {
            AppLogger.i(this, "bringFloatingPanelToFront skipped: missing view or params");
            return;
        }
        removeOverlayView(floatingView);
        try {
            windowManager.addView(floatingView, floatingParams);
            AppLogger.i(this, "Floating panel brought to front");
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to bring floating panel to front", e);
        }
    }

    private void showCurrentExecutionPreview(ClickStep step, int startX, int startY, int endX, int endY, int stepIndex) {
        removeExecutionPreview();
        if (windowManager == null || step == null) {
            AppLogger.i(this, "Execution preview skipped: missing window manager or step");
            return;
        }
        executionPreviewView = new ExecutionPreviewView(this, step.isSwipe(), startX, startY, endX, endY, stepIndex);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(executionPreviewView, params);
            if (executionPreviewView instanceof ExecutionPreviewView) {
                ((ExecutionPreviewView) executionPreviewView).captureOverlayLocation();
            }
            AppLogger.i(this, "Execution preview added. stepIndex=" + stepIndex);
            bringProfileBubbleToFront();
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to add execution preview", e);
            executionPreviewView = null;
        }
    }

    private void removeExecutionPreview() {
        removeOverlayView(executionPreviewView);
        executionPreviewView = null;
    }

    private void bringProfileBubbleToFront() {
        if (windowManager == null || profileBubbleView == null || profileBubbleParams == null) {
            return;
        }
        removeOverlayView(profileBubbleView);
        try {
            windowManager.addView(profileBubbleView, profileBubbleParams);
            AppLogger.i(this, "Profile bubble brought to front");
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to bring profile bubble to front", e);
        }
    }

    private int[] toRecordingLocalPoint(int rawX, int rawY) {
        if (recordingView == null) {
            return new int[]{Math.max(0, rawX), Math.max(0, rawY)};
        }
        int[] location = new int[2];
        recordingView.getLocationOnScreen(location);
        return new int[]{Math.max(0, rawX - location[0]), Math.max(0, rawY - location[1])};
    }

    private void removeOverlayView(View view) {
        if (windowManager == null || view == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException ignored) {
            // Overlay may already have been removed during mode changes or service shutdown.
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class RecordingLineView extends View {
        private final int startX;
        private final int startY;
        private final int endX;
        private final int endY;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RecordingLineView(android.content.Context context, int startX, int startY, int endX, int endY) {
            super(context);
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            linePaint.setColor(Color.argb(230, 37, 99, 235));
            linePaint.setStrokeWidth(8f);
            linePaint.setStyle(Paint.Style.STROKE);
            pointPaint.setColor(Color.argb(230, 249, 115, 22));
            pointPaint.setStyle(Paint.Style.FILL);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawLine(startX, startY, endX, endY, linePaint);
            canvas.drawCircle(startX, startY, 10f, pointPaint);
            canvas.drawCircle(endX, endY, 10f, pointPaint);
        }
    }

    private static class ExecutionPreviewView extends View {
        private final boolean swipe;
        private final int startX;
        private final int startY;
        private final int endX;
        private final int endY;
        private final int stepIndex;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int overlayX;
        private int overlayY;

        ExecutionPreviewView(android.content.Context context, boolean swipe, int startX, int startY,
                             int endX, int endY, int stepIndex) {
            super(context);
            this.swipe = swipe;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.stepIndex = Math.max(1, stepIndex);
            linePaint.setColor(Color.argb(220, 37, 99, 235));
            linePaint.setStrokeWidth(8f);
            linePaint.setStyle(Paint.Style.STROKE);
            markerPaint.setColor(Color.argb(235, 249, 115, 22));
            markerPaint.setStyle(Paint.Style.FILL);
            markerStrokePaint.setColor(Color.WHITE);
            markerStrokePaint.setStrokeWidth(4f);
            markerStrokePaint.setStyle(Paint.Style.STROKE);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(32f);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            setWillNotDraw(false);
        }

        void captureOverlayLocation() {
            int[] location = new int[2];
            getLocationOnScreen(location);
            overlayX = location[0];
            overlayY = location[1];
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int localStartX = toLocalX(startX);
            int localStartY = toLocalY(startY);
            if (swipe) {
                int localEndX = toLocalX(endX);
                int localEndY = toLocalY(endY);
                canvas.drawLine(localStartX, localStartY, localEndX, localEndY, linePaint);
                drawMarker(canvas, localStartX, localStartY, String.valueOf(stepIndex));
                drawMarker(canvas, localEndX, localEndY, stepIndex + "终");
            } else {
                drawMarker(canvas, localStartX, localStartY, String.valueOf(stepIndex));
            }
        }

        private int toLocalX(int rawX) {
            return Math.max(0, rawX - overlayX);
        }

        private int toLocalY(int rawY) {
            return Math.max(0, rawY - overlayY);
        }

        private void drawMarker(Canvas canvas, int x, int y, String label) {
            float radius = label.length() > 2 ? 24f : 20f;
            canvas.drawCircle(x, y, radius, markerPaint);
            canvas.drawCircle(x, y, radius, markerStrokePaint);
            canvas.drawCircle(x, y, 5f, markerStrokePaint);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = y - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(label, x, baseline, textPaint);
        }
    }
}
