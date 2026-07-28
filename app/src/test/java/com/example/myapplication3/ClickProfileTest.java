package com.example.myapplication3;

import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClickProfileTest {
    @Test
    public void profileCopyKeepsStepValuesWithNewIdentity() {
        ClickProfile profile = new ClickProfile("测试方案");
        profile.setLoopCount(3);
        profile.setInfiniteLoop(false);
        profile.setLoopIntervalRandomMs(700L);
        profile.getSteps().clear();
        ClickStep timedStep = new ClickStep("a", ClickStep.TYPE_CLICK, 100, 200, 100, 200, 50, 80, 8);
        timedStep.setDelayRandomMs(10L);
        timedStep.setDurationRandomMs(20L);
        profile.getSteps().add(timedStep);
        profile.getSteps().add(new ClickStep("b", ClickStep.TYPE_SWIPE, 10, 20, 300, 400, 90, 500, 12));

        ClickProfile copied = profile.copy("复制方案");

        assertEquals("复制方案", copied.getName());
        assertEquals(3, copied.getLoopCount());
        assertEquals(700L, copied.getLoopIntervalRandomMs());
        assertEquals(2, copied.getSteps().size());
        assertEquals(ClickStep.TYPE_CLICK, copied.getSteps().get(0).getType());
        assertTrue(copied.getSteps().get(1).isSwipe());
        assertEquals(300, copied.getSteps().get(1).getEndX());
        assertEquals(12, copied.getSteps().get(1).getRandomRadius());
        assertEquals(10L, copied.getSteps().get(0).getDelayRandomMs());
        assertEquals(20L, copied.getSteps().get(0).getDurationRandomMs());
        assertTrue(!profile.getId().equals(copied.getId()));
        assertTrue(!profile.getSteps().get(0).getId().equals(copied.getSteps().get(0).getId()));
    }

    @Test
    public void stepConstructorClampsInvalidNegativeValues() {
        ClickStep step = new ClickStep("bad", ClickStep.TYPE_CLICK, -1, -2, -3, -4, -5, -6, -7);

        assertEquals(0, step.getStartX());
        assertEquals(0, step.getStartY());
        assertEquals(0, step.getEndX());
        assertEquals(0, step.getEndY());
        assertEquals(0, step.getDelayMs());
        assertEquals(1, step.getDurationMs());
        assertEquals(0L, step.getDelayRandomMs());
        assertEquals(0L, step.getDurationRandomMs());
        assertEquals(0, step.getRandomRadius());
    }

    @Test
    public void stepClampsInvalidTimeRandomValues() {
        ClickStep step = ClickStep.click();

        step.setDelayRandomMs(-10L);
        step.setDurationRandomMs(-20L);

        assertEquals(0L, step.getDelayRandomMs());
        assertEquals(0L, step.getDurationRandomMs());
    }

    @Test
    public void speedMultiplierClampsToSupportedRange() {
        ClickProfile profile = new ClickProfile("速度范围");

        profile.setSpeedMultiplier(0.1);
        assertEquals(0.5, profile.getSpeedMultiplier(), 0.0);

        profile.setSpeedMultiplier(4.8);
        assertEquals(4.0, profile.getSpeedMultiplier(), 0.0);

        profile.setSpeedMultiplier(2.34);
        assertEquals(2.3, profile.getSpeedMultiplier(), 0.0);
    }

    @Test
    public void loopCountClampsToSupportedRange() {
        ClickProfile profile = new ClickProfile("循环范围");

        profile.setLoopCount(0);
        assertEquals(1, profile.getLoopCount());

        profile.setLoopCount(1200);
        assertEquals(999, profile.getLoopCount());
    }

    @Test
    public void loopIntervalDefaultsToOneSecondAndClampsNegativeValues() {
        ClickProfile profile = new ClickProfile("循环间隔");

        assertEquals(1000L, profile.getLoopIntervalMs());

        profile.setLoopIntervalMs(-1L);
        assertEquals(0L, profile.getLoopIntervalMs());

        profile.setLoopIntervalMs(2500L);
        assertEquals(2500L, profile.getLoopIntervalMs());
    }

    @Test
    public void loopIntervalRandomDefaultsToZeroAndClampsNegativeValues() {
        ClickProfile profile = new ClickProfile("循环随机间隔");

        assertEquals(0L, profile.getLoopIntervalRandomMs());

        profile.setLoopIntervalRandomMs(-1L);
        assertEquals(0L, profile.getLoopIntervalRandomMs());

        profile.setLoopIntervalRandomMs(800L);
        assertEquals(800L, profile.getLoopIntervalRandomMs());
    }

}
