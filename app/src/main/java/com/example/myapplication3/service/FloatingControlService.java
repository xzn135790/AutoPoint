package com.example.myapplication3.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private static final long EXECUTION_PREVIEW_HOLD_MS = 200L;
    private static final long EXECUTION_PREVIEW_FADE_MS = 600L;
    private static final int FLOATING_DEFAULT_WIDTH_DP = 200;
    private static final int FLOATING_DEFAULT_HEIGHT_DP = 260;
    private static final float FLOATING_MIN_SCALE = 0.75f;
    private static final float FLOATING_MAX_SCALE = 1.75f;
    private static final int PROFILE_BUBBLE_SIZE_DP = 60;
    private static final int PROFILE_BADGE_SIZE_DP = 22;
    private static final int PROFILE_DRAG_THRESHOLD_DP = 4;
    private static final long PROFILE_LONG_PRESS_MS = 800L;

    private WindowManager windowManager;
    private View floatingWindowView;
    private View floatingPanelView;
    private View crosshairView;
    private View recordingView;
    private View executionPreviewView;
    private FrameLayout profileBubbleView;
    private TextView profileBubbleBadge;
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
    private float floatingScale = 1.0f;
    private float floatingResizeStartScale;
    private boolean hasSwipeStart;
    private int swipeStartX;
    private int swipeStartY;
    private boolean profileBubbleExecuting;
    private final Handler profileBubbleHandler = new Handler(Looper.getMainLooper());
    private Runnable profileBubbleLongPressRunnable;
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
        if (floatingWindowView == null) {
            showFloatingView();
        }
        if (floatingWindowView != null && intent != null && intent.getBooleanExtra(EXTRA_START_RECORDING, false)) {
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
        removeOverlayView(floatingWindowView);
        floatingWindowView = null;
        floatingPanelView = null;
        super.onDestroy();
    }

    private void showFloatingView() {
        AppLogger.i(this, "showFloatingView start");
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            AppLogger.i(this, "Inflating floating control layout");
            floatingWindowView = LayoutInflater.from(this).inflate(R.layout.view_floating_control, null);
            floatingPanelView = floatingWindowView.findViewById(R.id.floatingPanel);
            floatingScale = 1.0f;
            floatingPanelView.setPivotX(0.0f);
            floatingPanelView.setPivotY(0.0f);
            floatingPanelView.setScaleX(floatingScale);
            floatingPanelView.setScaleY(floatingScale);
            AppLogger.i(this, "Floating layout inflated");
            statusText = floatingPanelView.findViewById(R.id.floatingStatusText);
            executeActions = floatingPanelView.findViewById(R.id.executeActions);
            crosshairActions = floatingPanelView.findViewById(R.id.crosshairActions);
            recordingActions = floatingPanelView.findViewById(R.id.recordingActions);
            Button executeModeButton = floatingPanelView.findViewById(R.id.executeModeButton);
            Button crosshairModeButton = floatingPanelView.findViewById(R.id.crosshairModeButton);
            Button recordModeButton = floatingPanelView.findViewById(R.id.recordModeButton);
            Button startButton = floatingPanelView.findViewById(R.id.floatingStartButton);
            pauseButton = floatingPanelView.findViewById(R.id.floatingPauseButton);
            Button stopButton = floatingPanelView.findViewById(R.id.floatingStopButton);
            Button closeButton = floatingPanelView.findViewById(R.id.closeFloatingButton);
            Button addClickPointButton = floatingPanelView.findViewById(R.id.addClickPointButton);
            Button setSwipeStartButton = floatingPanelView.findViewById(R.id.setSwipeStartButton);
            Button setSwipeEndButton = floatingPanelView.findViewById(R.id.setSwipeEndButton);
            Button stopRecordingButton = floatingPanelView.findViewById(R.id.stopRecordingButton);
            Button undoRecordedStepButton = floatingPanelView.findViewById(R.id.undoRecordedStepButton);
            Button useRecordedPlanButton = floatingPanelView.findViewById(R.id.useRecordedPlanButton);
            Button closeRecordingButton = floatingPanelView.findViewById(R.id.closeRecordingButton);
            View resizeHandle = floatingPanelView.findViewById(R.id.resizeHandle);

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

            floatingPanelView.setOnTouchListener(new View.OnTouchListener() {
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
                            constrainFloatingWindowToScreen();
                            windowManager.updateViewLayout(floatingWindowView, floatingParams);
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
                            floatingResizeStartScale = floatingScale;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float widthScaleDelta = (event.getRawX() - touchStartX) / dp(FLOATING_DEFAULT_WIDTH_DP);
                            float heightScaleDelta = (event.getRawY() - touchStartY) / dp(FLOATING_DEFAULT_HEIGHT_DP);
                            // 横向或纵向都能缩放，以相对基准尺寸变化更明显的方向为准。
                            float dominantScaleDelta = Math.abs(widthScaleDelta) >= Math.abs(heightScaleDelta)
                                    ? widthScaleDelta : heightScaleDelta;
                            applyFloatingScale(clamp(floatingResizeStartScale + dominantScaleDelta,
                                    FLOATING_MIN_SCALE, FLOATING_MAX_SCALE));
                            windowManager.updateViewLayout(floatingWindowView, floatingParams);
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
            constrainFloatingWindowToScreen();
            windowManager.addView(floatingWindowView, floatingParams);
            AppLogger.i(this, "Floating control panel added");
        } catch (Throwable e) {
            AppLogger.e(this, "Failed to add floating control panel", e);
            floatingWindowView = null;
            floatingPanelView = null;
            Toast.makeText(this, "悬浮面板显示失败，请查看日志", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void registerServiceCallbacks() {
        if (statusCallback == null) {
            statusCallback = new AutoClickAccessibilityService.StatusCallback() {
                @Override
                public void onStatusChanged(final boolean running, final boolean paused, final String message) {
                    final View anchor = floatingWindowView != null ? floatingWindowView : profileBubbleView;
                    if (anchor == null) {
                        return;
                    }
                    anchor.post(new Runnable() {
                        @Override
                        public void run() {
                            if (floatingWindowView != null) {
                                updateState(running, paused, message);
                            }
                            if (profileBubbleView != null) {
                                profileBubbleExecuting = running;
                                updateProfileBubble(running);
                            }
                            if (!running) {
                                removeExecutionPreview();
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
                    final View anchor = floatingWindowView != null ? floatingWindowView : profileBubbleView;
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
        removeOverlayView(floatingWindowView);
        floatingWindowView = null;
        floatingPanelView = null;
        removeProfileBubble();
        removeRecordingView();
        removeExecutionPreview();
        registerServiceCallbacks();
        final ClickProfile profile = loadSelectedProfile();
        final String name = profile == null ? "方案" : profile.getName();
        profileBubbleView = new FrameLayout(this);
        profileBubbleView.setElevation(dp(8));
        profileBubbleView.setClipChildren(false);

        ImageView avatarView = new ImageView(this);
        avatarView.setImageResource(R.mipmap.ic_launcher);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setBackgroundResource(R.drawable.bg_profile_avatar);
        avatarView.setClipToOutline(true);
        profileBubbleView.addView(avatarView, new FrameLayout.LayoutParams(
                dp(PROFILE_BUBBLE_SIZE_DP),
                dp(PROFILE_BUBBLE_SIZE_DP),
                Gravity.CENTER
        ));

        profileBubbleBadge = new TextView(this);
        profileBubbleBadge.setTextColor(Color.WHITE);
        profileBubbleBadge.setTextSize(11);
        profileBubbleBadge.setGravity(Gravity.CENTER);
        profileBubbleBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        profileBubbleBadge.setElevation(dp(10));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                dp(PROFILE_BADGE_SIZE_DP),
                dp(PROFILE_BADGE_SIZE_DP),
                Gravity.END | Gravity.BOTTOM
        );
        profileBubbleView.addView(profileBubbleBadge, badgeParams);
        updateProfileBubble(false);

        profileBubbleParams = new WindowManager.LayoutParams(
                dp(PROFILE_BUBBLE_SIZE_DP),
                dp(PROFILE_BUBBLE_SIZE_DP),
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
            private boolean longPressTriggered;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelProfileBubbleLongPress();
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downWindowX = profileBubbleParams.x;
                        downWindowY = profileBubbleParams.y;
                        moved = false;
                        longPressTriggered = false;
                        profileBubbleLongPressRunnable = new Runnable() {
                            @Override
                            public void run() {
                                longPressTriggered = true;
                                closeProfileBubbleControls();
                            }
                        };
                        profileBubbleHandler.postDelayed(profileBubbleLongPressRunnable, PROFILE_LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downRawX);
                        int dy = (int) (event.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(PROFILE_DRAG_THRESHOLD_DP)
                                || Math.abs(dy) > dp(PROFILE_DRAG_THRESHOLD_DP)) {
                            moved = true;
                            cancelProfileBubbleLongPress();
                        }
                        if (profileBubbleView == null || profileBubbleParams == null) {
                            return true;
                        }
                        profileBubbleParams.x = downWindowX + dx;
                        profileBubbleParams.y = downWindowY + dy;
                        windowManager.updateViewLayout(profileBubbleView, profileBubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        cancelProfileBubbleLongPress();
                        if (!moved && !longPressTriggered) {
                            toggleProfileBubbleExecution();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        cancelProfileBubbleLongPress();
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

    private void updateProfileBubble(boolean running) {
        if (profileBubbleView == null || profileBubbleBadge == null) {
            return;
        }
        ClickProfile profile = loadSelectedProfile();
        String name = profile == null ? "方案" : profile.getName();
        profileBubbleBadge.setText(running ? "■" : "▶");
        profileBubbleBadge.setBackgroundResource(running
                ? R.drawable.bg_profile_badge_running
                : R.drawable.bg_profile_badge_idle);
        profileBubbleView.setContentDescription(running
                ? name + "正在执行，轻点停止，长按关闭"
                : name + "，轻点启动，长按关闭");
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
        cancelProfileBubbleLongPress();
        removeOverlayView(profileBubbleView);
        profileBubbleView = null;
        profileBubbleBadge = null;
        profileBubbleParams = null;
        profileBubbleExecuting = false;
    }

    private void cancelProfileBubbleLongPress() {
        if (profileBubbleLongPressRunnable == null) {
            return;
        }
        profileBubbleHandler.removeCallbacks(profileBubbleLongPressRunnable);
        profileBubbleLongPressRunnable = null;
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
        if (windowManager == null || floatingWindowView == null || floatingParams == null) {
            AppLogger.i(this, "bringFloatingPanelToFront skipped: missing view or params");
            return;
        }
        removeOverlayView(floatingWindowView);
        try {
            windowManager.addView(floatingWindowView, floatingParams);
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
        final ExecutionPreviewView previewView =
                new ExecutionPreviewView(this, step.isSwipe(), startX, startY, endX, endY, stepIndex);
        executionPreviewView = previewView;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        boolean previewAdded = false;
        try {
            previewView.setAlpha(0f);
            previewView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    previewView.removeOnLayoutChangeListener(this);
                    if (executionPreviewView != previewView || !previewView.isAttachedToWindow()) {
                        return;
                    }
                    // 手势坐标属于屏幕坐标；首次布局后才能取得包含系统栏偏移的悬浮层真实原点。
                    previewView.captureOverlayLocation();
                    previewView.setAlpha(1f);
                    previewView.animate()
                            .alpha(0f)
                            .setStartDelay(EXECUTION_PREVIEW_HOLD_MS)
                            .setDuration(EXECUTION_PREVIEW_FADE_MS)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    removeExecutionPreview(previewView);
                                }
                            })
                            .start();
                }
            });
            windowManager.addView(previewView, params);
            previewAdded = true;
            AppLogger.i(this, "Execution preview added. stepIndex=" + stepIndex);
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to add execution preview", e);
            previewView.animate().cancel();
            if (previewAdded) {
                removeOverlayView(previewView);
            }
            if (executionPreviewView == previewView) {
                executionPreviewView = null;
            }
        }
    }

    private void removeExecutionPreview() {
        View previewView = executionPreviewView;
        executionPreviewView = null;
        if (previewView != null) {
            previewView.animate().cancel();
            removeOverlayView(previewView);
        }
    }

    private void removeExecutionPreview(View expectedView) {
        if (executionPreviewView != expectedView) {
            return;
        }
        executionPreviewView = null;
        removeOverlayView(expectedView);
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

    private void applyFloatingScale(float scale) {
        if (floatingPanelView == null || floatingParams == null) {
            return;
        }
        floatingScale = scale;
        floatingPanelView.setScaleX(scale);
        floatingPanelView.setScaleY(scale);
        // 内层始终按 200x260dp 排版，外层窗口只跟随最终视觉尺寸，避免文字和间距二次布局。
        floatingParams.width = Math.round(dp(FLOATING_DEFAULT_WIDTH_DP) * scale);
        floatingParams.height = Math.round(dp(FLOATING_DEFAULT_HEIGHT_DP) * scale);
        constrainFloatingWindowToScreen();
    }

    private void constrainFloatingWindowToScreen() {
        if (floatingParams == null) {
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxX = Math.max(0, metrics.widthPixels - floatingParams.width);
        int maxY = Math.max(0, metrics.heightPixels - floatingParams.height);
        floatingParams.x = clamp(floatingParams.x, 0, maxX);
        floatingParams.y = clamp(floatingParams.y, 0, maxY);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
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
            linePaint.setColor(Color.argb(120, 37, 99, 235));
            linePaint.setStrokeWidth(6f);
            linePaint.setStyle(Paint.Style.STROKE);
            markerPaint.setColor(Color.argb(135, 249, 115, 22));
            markerPaint.setStyle(Paint.Style.FILL);
            markerStrokePaint.setColor(Color.argb(190, 255, 255, 255));
            markerStrokePaint.setStrokeWidth(3f);
            markerStrokePaint.setStyle(Paint.Style.STROKE);
            textPaint.setColor(Color.argb(220, 255, 255, 255));
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
