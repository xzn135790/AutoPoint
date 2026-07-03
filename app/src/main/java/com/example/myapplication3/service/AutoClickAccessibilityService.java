package com.example.myapplication3.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import com.example.myapplication3.engine.AutoClickEngine;
import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoClickAccessibilityService extends AccessibilityService {
    public interface StatusCallback {
        void onStatusChanged(boolean running, boolean paused, String message);
    }

    private static AutoClickAccessibilityService instance;
    private static final List<StatusCallback> statusCallbacks = new CopyOnWriteArrayList<>();

    private AutoClickEngine engine;
    private String lastMessage = "服务未连接";

    public static boolean isReady() {
        return instance != null && instance.engine != null;
    }

    public static String getLastMessage() {
        return instance == null ? "无障碍服务未开启" : instance.lastMessage;
    }

    public static void addStatusCallback(StatusCallback callback) {
        if (callback == null || statusCallbacks.contains(callback)) {
            return;
        }
        statusCallbacks.add(callback);
        if (instance != null && callback != null) {
            callback.onStatusChanged(instance.engine != null && instance.engine.isRunning(),
                    instance.engine != null && instance.engine.isPaused(),
                    instance.lastMessage);
        }
    }

    public static void removeStatusCallback(StatusCallback callback) {
        statusCallbacks.remove(callback);
    }

    public static boolean startProfile(ClickProfile profile) {
        if (!isReady()) {
            notifyExternal(false, false, "请先开启无障碍服务");
            return false;
        }
        return instance.engine.start(profile);
    }

    public static void pauseOrResume() {
        if (!isReady()) {
            notifyExternal(false, false, "请先开启无障碍服务");
            return;
        }
        if (instance.engine.isPaused()) {
            instance.engine.resume();
        } else {
            instance.engine.pause();
        }
    }

    public static void stopRunning() {
        if (isReady()) {
            instance.engine.stop();
        } else {
            notifyExternal(false, false, "请先开启无障碍服务");
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        engine = new AutoClickEngine(new AutoClickEngine.GestureDispatcher() {
            @Override
            public boolean dispatch(ClickStep step, int startX, int startY, int endX, int endY) {
                return dispatchStep(step, startX, startY, endX, endY);
            }
        });
        engine.setStateListener(new AutoClickEngine.StateListener() {
            @Override
            public void onStateChanged(boolean running, boolean paused, String message) {
                lastMessage = message;
                notifyExternal(running, paused, message);
            }
        });
        lastMessage = "无障碍服务已连接";
        notifyExternal(false, false, lastMessage);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Gesture-only service; window content is intentionally not read.
    }

    @Override
    public void onInterrupt() {
        if (engine != null) {
            engine.stop();
        }
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.shutdown();
        }
        instance = null;
        lastMessage = "无障碍服务已断开";
        notifyExternal(false, false, lastMessage);
        super.onDestroy();
    }

    private boolean dispatchStep(ClickStep step, int startX, int startY, int endX, int endY) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        Path path = new Path();
        path.moveTo(startX, startY);
        if (step.isSwipe()) {
            path.lineTo(endX, endY);
        }
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                path,
                0,
                Math.max(1, step.getDurationMs())
        );
        GestureDescription description = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        return dispatchGesture(description, null, null);
    }

    private static void notifyExternal(boolean running, boolean paused, String message) {
        for (StatusCallback callback : statusCallbacks) {
            callback.onStatusChanged(running, paused, message);
        }
    }
}
