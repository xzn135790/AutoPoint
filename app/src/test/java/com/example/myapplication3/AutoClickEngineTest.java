package com.example.myapplication3;

import com.example.myapplication3.engine.AutoClickEngine;
import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;

import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoClickEngineTest {
    @Test
    public void randomizeKeepsPointInsideRadiusAndNonNegative() {
        Random random = new Random(7);

        for (int i = 0; i < 100; i++) {
            int[] point = AutoClickEngine.randomize(4, 5, 10, random);
            assertTrue(point[0] >= 0);
            assertTrue(point[1] >= 0);
            assertTrue(Math.abs(point[0] - 4) <= 10);
            assertTrue(Math.abs(point[1] - 5) <= 10);
        }
    }

    @Test
    public void calculateLoopIntervalStaysWithinConfiguredBounds() {
        Random random = new Random(17);

        for (int i = 0; i < 1000; i++) {
            long actualMs = AutoClickEngine.calculateLoopIntervalMs(3000L, 1000L, random);
            assertTrue(actualMs >= 2000L);
            assertTrue(actualMs <= 4000L);
        }
    }

    @Test
    public void calculateLoopIntervalWithZeroRandomReturnsClampedBase() {
        assertEquals(3000L, AutoClickEngine.calculateLoopIntervalMs(3000L, 0L, new Random(1)));
        assertEquals(0L, AutoClickEngine.calculateLoopIntervalMs(-1L, 0L, new Random(1)));
        assertEquals(3000L, AutoClickEngine.calculateLoopIntervalMs(3000L, -1L, new Random(1)));
    }

    @Test
    public void calculateLoopIntervalClampsUnderflowAndOverflow() {
        Random lowestOffset = new Random() {
            @Override
            public long nextLong() {
                return 0L;
            }
        };
        Random highestOffset = new Random() {
            @Override
            public long nextLong() {
                return 20L;
            }
        };

        assertEquals(0L, AutoClickEngine.calculateLoopIntervalMs(5L, 10L, lowestOffset));
        assertEquals(Long.MAX_VALUE,
                AutoClickEngine.calculateLoopIntervalMs(Long.MAX_VALUE - 5L, 10L, highestOffset));
    }

    @Test
    public void calculateLoopIntervalResamplesRejectedModuloValue() {
        final AtomicInteger callCount = new AtomicInteger();
        Random controlledRandom = new Random() {
            @Override
            public long nextLong() {
                return callCount.getAndIncrement() == 0 ? Long.MAX_VALUE - 7L : 20L;
            }
        };

        assertEquals(20L, AutoClickEngine.calculateLoopIntervalMs(10L, 10L, controlledRandom));
        assertEquals(2, callCount.get());
    }

    @Test
    public void stopDuringLoopIntervalPreventsNextLoop() throws Exception {
        final AtomicInteger dispatchCount = new AtomicInteger();
        final CountDownLatch firstDispatch = new CountDownLatch(1);
        final CountDownLatch workerFinished = new CountDownLatch(1);
        AutoClickEngine engine = new AutoClickEngine(new AutoClickEngine.GestureDispatcher() {
            @Override
            public boolean dispatch(ClickStep step, int startX, int startY, int endX, int endY, long durationMs) {
                dispatchCount.incrementAndGet();
                firstDispatch.countDown();
                return true;
            }
        });
        engine.setStateListener(new AutoClickEngine.StateListener() {
            @Override
            public void onStateChanged(boolean running, boolean paused, String message) {
                if (!running && "执行完成".equals(message)) {
                    workerFinished.countDown();
                }
            }
        });
        ClickProfile profile = new ClickProfile("停止轮间等待测试");
        profile.setLoopCount(2);
        profile.setLoopIntervalMs(5000L);
        profile.setLoopIntervalRandomMs(1000L);
        profile.getSteps().clear();
        profile.getSteps().add(new ClickStep("one", ClickStep.TYPE_CLICK, 1, 1, 1, 1, 0, 1, 0));

        assertTrue(engine.start(profile));
        assertTrue(firstDispatch.await(1, TimeUnit.SECONDS));
        Thread.sleep(250L);
        engine.stop();

        assertTrue(workerFinished.await(1, TimeUnit.SECONDS));
        assertEquals(1, dispatchCount.get());
        engine.shutdown();
    }

    @Test
    public void finiteLoopRunsExpectedNumberOfSteps() throws Exception {
        final AtomicInteger dispatchCount = new AtomicInteger();
        final CountDownLatch finished = new CountDownLatch(1);
        AutoClickEngine engine = new AutoClickEngine(new AutoClickEngine.GestureDispatcher() {
            @Override
            public boolean dispatch(ClickStep step, int startX, int startY, int endX, int endY, long durationMs) {
                dispatchCount.incrementAndGet();
                return true;
            }
        });
        engine.setStateListener(new AutoClickEngine.StateListener() {
            @Override
            public void onStateChanged(boolean running, boolean paused, String message) {
                if (!running && "执行完成".equals(message)) {
                    finished.countDown();
                }
            }
        });
        ClickProfile profile = new ClickProfile("循环测试");
        profile.setLoopCount(2);
        profile.getSteps().clear();
        profile.getSteps().add(new ClickStep("one", ClickStep.TYPE_CLICK, 1, 1, 1, 1, 0, 1, 0));
        profile.getSteps().add(new ClickStep("two", ClickStep.TYPE_CLICK, 2, 2, 2, 2, 0, 1, 0));

        assertTrue(engine.start(profile));
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertEquals(4, dispatchCount.get());
        assertFalse(engine.isRunning());
        engine.shutdown();
    }

    @Test
    public void speedMultiplierScalesGestureDuration() throws Exception {
        final AtomicLong dispatchedDuration = new AtomicLong();
        final CountDownLatch finished = new CountDownLatch(1);
        AutoClickEngine engine = new AutoClickEngine(new AutoClickEngine.GestureDispatcher() {
            @Override
            public boolean dispatch(ClickStep step, int startX, int startY, int endX, int endY, long durationMs) {
                dispatchedDuration.set(durationMs);
                return true;
            }
        });
        engine.setStateListener(new AutoClickEngine.StateListener() {
            @Override
            public void onStateChanged(boolean running, boolean paused, String message) {
                if (!running && "执行完成".equals(message)) {
                    finished.countDown();
                }
            }
        });
        ClickProfile profile = new ClickProfile("速度测试");
        profile.setSpeedMultiplier(2.0);
        profile.getSteps().clear();
        profile.getSteps().add(new ClickStep("one", ClickStep.TYPE_CLICK, 1, 1, 1, 1, 0, 100, 0));

        assertTrue(engine.start(profile));
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertEquals(50, dispatchedDuration.get());
        engine.shutdown();
    }
}
