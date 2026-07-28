package com.example.myapplication3.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClickProfile {
    private final String id;
    private String name;
    private int loopCount;
    private boolean infiniteLoop;
    private long loopIntervalMs;
    private long loopIntervalRandomMs;
    private double speedMultiplier;
    private final List<ClickStep> steps;

    public ClickProfile(String name) {
        this(UUID.randomUUID().toString(), name, 1, false,
                1000L, 0L, 1.0, new ArrayList<ClickStep>());
    }

    public ClickProfile(String id, String name, int loopCount, boolean infiniteLoop, List<ClickStep> steps) {
        this(id, name, loopCount, infiniteLoop, 1000L, 0L, 1.0, steps);
    }

    public ClickProfile(String id, String name, int loopCount, boolean infiniteLoop, double speedMultiplier, List<ClickStep> steps) {
        this(id, name, loopCount, infiniteLoop, 1000L, 0L, speedMultiplier, steps);
    }

    public ClickProfile(String id, String name, int loopCount, boolean infiniteLoop,
                        long loopIntervalMs, double speedMultiplier, List<ClickStep> steps) {
        this(id, name, loopCount, infiniteLoop, loopIntervalMs, 0L, speedMultiplier, steps);
    }

    public ClickProfile(String id, String name, int loopCount, boolean infiniteLoop,
                        long loopIntervalMs, long loopIntervalRandomMs,
                        double speedMultiplier, List<ClickStep> steps) {
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "默认方案" : name.trim();
        setLoopCount(loopCount);
        this.infiniteLoop = infiniteLoop;
        setLoopIntervalMs(loopIntervalMs);
        setLoopIntervalRandomMs(loopIntervalRandomMs);
        setSpeedMultiplier(speedMultiplier);
        this.steps = steps == null ? new ArrayList<ClickStep>() : steps;
    }

    public static ClickProfile defaultProfile() {
        return new ClickProfile("默认方案");
    }

    public static ClickProfile fromJson(JSONObject object) throws JSONException {
        JSONArray stepArray = object.optJSONArray("steps");
        List<ClickStep> parsedSteps = new ArrayList<>();
        if (stepArray != null) {
            for (int i = 0; i < stepArray.length(); i++) {
                parsedSteps.add(ClickStep.fromJson(stepArray.getJSONObject(i)));
            }
        }
        return new ClickProfile(
                object.optString("id", UUID.randomUUID().toString()),
                object.optString("name", "默认方案"),
                object.optInt("loopCount", 1),
                object.optBoolean("infiniteLoop", false),
                object.optLong("loopIntervalMs", 1000L),
                object.optLong("loopIntervalRandomMs", 0L),
                object.optDouble("speedMultiplier", 1.0),
                parsedSteps
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("loopCount", loopCount);
        object.put("infiniteLoop", infiniteLoop);
        object.put("loopIntervalMs", loopIntervalMs);
        object.put("loopIntervalRandomMs", loopIntervalRandomMs);
        object.put("speedMultiplier", speedMultiplier);
        JSONArray stepArray = new JSONArray();
        for (ClickStep step : steps) {
            stepArray.put(step.toJson());
        }
        object.put("steps", stepArray);
        return object;
    }

    public ClickProfile copy(String newName) {
        List<ClickStep> copiedSteps = new ArrayList<>();
        for (ClickStep step : steps) {
            copiedSteps.add(new ClickStep(
                    UUID.randomUUID().toString(),
                    step.getType(),
                    step.getStartX(),
                    step.getStartY(),
                    step.getEndX(),
                    step.getEndY(),
                    step.getDelayMs(),
                    step.getDurationMs(),
                    step.getDelayRandomMs(),
                    step.getDurationRandomMs(),
                    step.getRandomRadius()
            ));
        }
        return new ClickProfile(UUID.randomUUID().toString(), newName, loopCount, infiniteLoop,
                loopIntervalMs, loopIntervalRandomMs, speedMultiplier, copiedSteps);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.trim().isEmpty() ? "未命名方案" : name.trim();
    }

    public int getLoopCount() {
        return loopCount;
    }

    public void setLoopCount(int loopCount) {
        this.loopCount = Math.max(1, Math.min(999, loopCount));
    }

    public boolean isInfiniteLoop() {
        return infiniteLoop;
    }

    public void setInfiniteLoop(boolean infiniteLoop) {
        this.infiniteLoop = infiniteLoop;
    }

    public long getLoopIntervalMs() {
        return loopIntervalMs;
    }

    public void setLoopIntervalMs(long loopIntervalMs) {
        this.loopIntervalMs = Math.max(0L, loopIntervalMs);
    }

    public long getLoopIntervalRandomMs() {
        return loopIntervalRandomMs;
    }

    public void setLoopIntervalRandomMs(long loopIntervalRandomMs) {
        this.loopIntervalRandomMs = Math.max(0L, loopIntervalRandomMs);
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        if (speedMultiplier < 0.5) {
            this.speedMultiplier = 0.5;
        } else if (speedMultiplier > 4.0) {
            this.speedMultiplier = 4.0;
        } else {
            this.speedMultiplier = Math.round(speedMultiplier * 10.0) / 10.0;
        }
    }

    public List<ClickStep> getSteps() {
        return steps;
    }
}
