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
    private final List<ClickStep> steps;

    public ClickProfile(String name) {
        this(UUID.randomUUID().toString(), name, 1, false, new ArrayList<ClickStep>());
    }

    public ClickProfile(String id, String name, int loopCount, boolean infiniteLoop, List<ClickStep> steps) {
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "默认方案" : name.trim();
        this.loopCount = Math.max(1, loopCount);
        this.infiniteLoop = infiniteLoop;
        this.steps = steps == null ? new ArrayList<ClickStep>() : steps;
    }

    public static ClickProfile defaultProfile() {
        ClickProfile profile = new ClickProfile("默认方案");
        profile.getSteps().add(ClickStep.click());
        return profile;
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
                parsedSteps
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("loopCount", loopCount);
        object.put("infiniteLoop", infiniteLoop);
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
                    step.getRandomRadius()
            ));
        }
        return new ClickProfile(UUID.randomUUID().toString(), newName, loopCount, infiniteLoop, copiedSteps);
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
        this.loopCount = Math.max(1, loopCount);
    }

    public boolean isInfiniteLoop() {
        return infiniteLoop;
    }

    public void setInfiniteLoop(boolean infiniteLoop) {
        this.infiniteLoop = infiniteLoop;
    }

    public List<ClickStep> getSteps() {
        return steps;
    }
}
