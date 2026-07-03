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
        profile.getSteps().clear();
        profile.getSteps().add(new ClickStep("a", ClickStep.TYPE_CLICK, 100, 200, 100, 200, 50, 80, 8));
        profile.getSteps().add(new ClickStep("b", ClickStep.TYPE_SWIPE, 10, 20, 300, 400, 90, 500, 12));

        ClickProfile copied = profile.copy("复制方案");

        assertEquals("复制方案", copied.getName());
        assertEquals(3, copied.getLoopCount());
        assertEquals(2, copied.getSteps().size());
        assertEquals(ClickStep.TYPE_CLICK, copied.getSteps().get(0).getType());
        assertTrue(copied.getSteps().get(1).isSwipe());
        assertEquals(300, copied.getSteps().get(1).getEndX());
        assertEquals(12, copied.getSteps().get(1).getRandomRadius());
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
        assertEquals(0, step.getRandomRadius());
    }
}
