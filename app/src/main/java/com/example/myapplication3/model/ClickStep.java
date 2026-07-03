package com.example.myapplication3.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ClickStep {
    public static final String TYPE_CLICK = "click";
    public static final String TYPE_SWIPE = "swipe";

    private final String id;
    private String type;
    private int startX;
    private int startY;
    private int endX;
    private int endY;
    private long delayMs;
    private long durationMs;
    private int randomRadius;

    public ClickStep(String type) {
        this(UUID.randomUUID().toString(), type, 540, 960, 540, 960, 300, 80, 0);
    }

    public ClickStep(
            String id,
            String type,
            int startX,
            int startY,
            int endX,
            int endY,
            long delayMs,
            long durationMs,
            int randomRadius
    ) {
        this.id = id;
        this.type = TYPE_SWIPE.equals(type) ? TYPE_SWIPE : TYPE_CLICK;
        this.startX = Math.max(0, startX);
        this.startY = Math.max(0, startY);
        this.endX = Math.max(0, endX);
        this.endY = Math.max(0, endY);
        this.delayMs = Math.max(0, delayMs);
        this.durationMs = Math.max(1, durationMs);
        this.randomRadius = Math.max(0, randomRadius);
    }

    public static ClickStep click() {
        return new ClickStep(TYPE_CLICK);
    }

    public static ClickStep swipe() {
        return new ClickStep(UUID.randomUUID().toString(), TYPE_SWIPE, 320, 1200, 760, 1200, 300, 450, 0);
    }

    public static ClickStep fromJson(JSONObject object) throws JSONException {
        return new ClickStep(
                object.optString("id", UUID.randomUUID().toString()),
                object.optString("type", TYPE_CLICK),
                object.optInt("startX", 540),
                object.optInt("startY", 960),
                object.optInt("endX", 540),
                object.optInt("endY", 960),
                object.optLong("delayMs", 300),
                object.optLong("durationMs", 80),
                object.optInt("randomRadius", 0)
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("type", type);
        object.put("startX", startX);
        object.put("startY", startY);
        object.put("endX", endX);
        object.put("endY", endY);
        object.put("delayMs", delayMs);
        object.put("durationMs", durationMs);
        object.put("randomRadius", randomRadius);
        return object;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = TYPE_SWIPE.equals(type) ? TYPE_SWIPE : TYPE_CLICK;
    }

    public boolean isSwipe() {
        return TYPE_SWIPE.equals(type);
    }

    public int getStartX() {
        return startX;
    }

    public void setStartX(int startX) {
        this.startX = Math.max(0, startX);
    }

    public int getStartY() {
        return startY;
    }

    public void setStartY(int startY) {
        this.startY = Math.max(0, startY);
    }

    public int getEndX() {
        return endX;
    }

    public void setEndX(int endX) {
        this.endX = Math.max(0, endX);
    }

    public int getEndY() {
        return endY;
    }

    public void setEndY(int endY) {
        this.endY = Math.max(0, endY);
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(1, durationMs);
    }

    public int getRandomRadius() {
        return randomRadius;
    }

    public void setRandomRadius(int randomRadius) {
        this.randomRadius = Math.max(0, randomRadius);
    }
}
