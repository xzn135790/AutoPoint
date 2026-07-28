package com.example.myapplication3.engine;

import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoClickEngine {
    public interface GestureDispatcher {
        boolean dispatch(ClickStep step, int startX, int startY, int endX, int endY, long durationMs);
    }

    public interface StateListener {
        void onStateChanged(boolean running, boolean paused, String message);
    }

    public interface StepListener {
        void onStepDispatch(ClickStep step, int startX, int startY, int endX, int endY, int stepIndex);
    }

    private final GestureDispatcher dispatcher;
    private final Random random;
    private final ExecutorService executorService;
    private final Object pauseLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private StateListener stateListener;
    private StepListener stepListener;

    public AutoClickEngine(GestureDispatcher dispatcher) {
        this(dispatcher, new Random());
    }

    AutoClickEngine(GestureDispatcher dispatcher, Random random) {
        this.dispatcher = dispatcher;
        this.random = random;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void setStateListener(StateListener stateListener) {
        this.stateListener = stateListener;
    }

    public void setStepListener(StepListener stepListener) {
        this.stepListener = stepListener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean start(final ClickProfile profile) {
        if (profile == null || profile.getSteps().isEmpty()) {
            notifyState(false, false, "方案没有步骤");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            notifyState(true, paused.get(), "已有任务正在执行");
            return false;
        }
        paused.set(false);
        notifyState(true, false, "开始执行：" + profile.getName());
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                runProfile(profile);
            }
        });
        return true;
    }

    public void pause() {
        if (running.get()) {
            paused.set(true);
            notifyState(true, true, "已暂停");
        }
    }

    public void resume() {
        if (running.get()) {
            synchronized (pauseLock) {
                paused.set(false);
                pauseLock.notifyAll();
            }
            notifyState(true, false, "继续执行");
        }
    }

    public void stop() {
        running.set(false);
        synchronized (pauseLock) {
            paused.set(false);
            pauseLock.notifyAll();
        }
        notifyState(false, false, "已停止");
    }

    public void shutdown() {
        stop();
        executorService.shutdownNow();
    }

    private void runProfile(ClickProfile profile) {
        int completedLoops = 0;
        try {
            while (running.get() && (profile.isInfiniteLoop() || completedLoops < profile.getLoopCount())) {
                runSteps(profile.getSteps(), profile.getSpeedMultiplier());
                completedLoops++;
                if (running.get() && (profile.isInfiniteLoop() || completedLoops < profile.getLoopCount())) {
                    waitIfPaused();
                    sleepInterruptibly(calculateLoopIntervalMs(
                            profile.getLoopIntervalMs(),
                            profile.getLoopIntervalRandomMs(),
                            random));
                }
            }
            running.set(false);
            paused.set(false);
            notifyState(false, false, "执行完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            paused.set(false);
            notifyState(false, false, "执行被中断");
        }
    }

    private void runSteps(List<ClickStep> steps, double speedMultiplier) throws InterruptedException {
        for (int i = 0; i < steps.size(); i++) {
            ClickStep step = steps.get(i);
            if (!running.get()) {
                return;
            }
            waitIfPaused();
            sleepInterruptibly(scaleDuration(randomizeDuration(step.getDelayMs(), step.getDelayRandomMs(), 0L), speedMultiplier));
            if (!running.get()) {
                return;
            }
            int[] start = randomize(step.getStartX(), step.getStartY(), step.getRandomRadius());
            int[] end = step.isSwipe()
                    ? randomize(step.getEndX(), step.getEndY(), step.getRandomRadius())
                    : new int[]{start[0], start[1]};
            long actualDurationMs = randomizeDuration(step.getDurationMs(), step.getDurationRandomMs(), 1L);
            long scaledDurationMs = Math.max(1, scaleDuration(actualDurationMs, speedMultiplier));
            notifyStep(step, start[0], start[1], end[0], end[1], i + 1);
            boolean accepted = dispatcher.dispatch(step, start[0], start[1], end[0], end[1], scaledDurationMs);
            if (!accepted) {
                running.set(false);
                notifyState(false, false, "系统拒绝执行手势");
                return;
            }
            sleepInterruptibly(scaleDuration(actualDurationMs + 80, speedMultiplier));
        }
    }

    private long randomizeDuration(long baseMs, long randomMs, long minMs) {
        long safeBase = Math.max(minMs, baseMs);
        long safeRandom = Math.min(Math.max(0L, randomMs), Long.MAX_VALUE / 4L);
        if (safeRandom == 0L) {
            return safeBase;
        }
        long offset = nextLongInclusive(safeRandom * 2L, random) - safeRandom;
        return Math.max(minMs, safeBase + offset);
    }

    public static long calculateLoopIntervalMs(long baseMs, long randomMs, Random random) {
        long safeBase = Math.max(0L, baseMs);
        long safeRandom = Math.min(Math.max(0L, randomMs), Long.MAX_VALUE / 4L);
        if (safeRandom == 0L) {
            return safeBase;
        }
        if (random == null) {
            throw new IllegalArgumentException("random 不能为空");
        }

        long offset = nextLongInclusive(safeRandom * 2L, random) - safeRandom;
        if (offset > 0L && safeBase > Long.MAX_VALUE - offset) {
            return Long.MAX_VALUE;
        }
        if (offset < 0L && safeBase < -offset) {
            return 0L;
        }
        return safeBase + offset;
    }

    private static long nextLongInclusive(long boundInclusive, Random random) {
        if (boundInclusive <= 0L) {
            return 0L;
        }
        long range = boundInclusive + 1L;
        long bits;
        long value;
        do {
            bits = random.nextLong() & Long.MAX_VALUE;
            value = bits % range;
        } while (bits - value + (range - 1L) < 0L);
        return value;
    }

    private long scaleDuration(long millis, double speedMultiplier) {
        double safeMultiplier = Math.max(0.5, Math.min(4.0, speedMultiplier));
        return Math.max(0, Math.round(millis / safeMultiplier));
    }

    private void waitIfPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (running.get() && paused.get()) {
                pauseLock.wait();
            }
        }
    }

    private void sleepInterruptibly(long millis) throws InterruptedException {
        long remaining = Math.max(0, millis);
        while (running.get() && remaining > 0) {
            long chunk = Math.min(remaining, 100);
            Thread.sleep(chunk);
            remaining -= chunk;
            waitIfPaused();
        }
    }

    private int[] randomize(int x, int y, int radius) {
        return randomize(x, y, radius, random);
    }

    public static int[] randomize(int x, int y, int radius, Random random) {
        int safeRadius = Math.max(0, radius);
        if (safeRadius == 0) {
            return new int[]{Math.max(0, x), Math.max(0, y)};
        }
        int offsetX = random.nextInt(safeRadius * 2 + 1) - safeRadius;
        int offsetY = random.nextInt(safeRadius * 2 + 1) - safeRadius;
        return new int[]{Math.max(0, x + offsetX), Math.max(0, y + offsetY)};
    }

    private void notifyState(boolean isRunning, boolean isPaused, String message) {
        if (stateListener != null) {
            stateListener.onStateChanged(isRunning, isPaused, message);
        }
    }

    private void notifyStep(ClickStep step, int startX, int startY, int endX, int endY, int stepIndex) {
        if (stepListener != null) {
            stepListener.onStepDispatch(step, startX, startY, endX, endY, stepIndex);
        }
    }
}
