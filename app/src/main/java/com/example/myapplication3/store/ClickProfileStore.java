package com.example.myapplication3.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;
import com.example.myapplication3.util.AppLogger;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class ClickProfileStore {
    private static final String PREFS_NAME = "click_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_SELECTED_PROFILE_ID = "selected_profile_id";

    private final Context context;
    private final SharedPreferences preferences;

    public ClickProfileStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ClickProfile> loadProfiles() {
        String rawProfiles = preferences.getString(KEY_PROFILES, "");
        if (rawProfiles == null || rawProfiles.trim().isEmpty()) {
            List<ClickProfile> defaults = new ArrayList<>();
            defaults.add(ClickProfile.defaultProfile());
            saveProfiles(defaults, defaults.get(0).getId());
            return defaults;
        }

        try {
            JSONArray array = new JSONArray(rawProfiles);
            List<ClickProfile> profiles = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                profiles.add(ClickProfile.fromJson(array.getJSONObject(i)));
            }
            if (profiles.isEmpty()) {
                profiles.add(ClickProfile.defaultProfile());
            }
            removeSeededPlaceholderStep(profiles);
            return profiles;
        } catch (JSONException e) {
            // Corrupt local JSON should not brick the app; reset to a safe default profile.
            List<ClickProfile> fallback = new ArrayList<>();
            fallback.add(ClickProfile.defaultProfile());
            saveProfiles(fallback, fallback.get(0).getId());
            return fallback;
        }
    }

    public void saveProfiles(List<ClickProfile> profiles, String selectedProfileId) {
        JSONArray array = new JSONArray();
        if (profiles != null) {
            for (ClickProfile profile : profiles) {
                try {
                    array.put(profile.toJson());
                } catch (JSONException ignored) {
                    // Skip one bad profile and keep the rest of the user's saved data.
                }
            }
        }
        boolean saved = preferences.edit()
                .putString(KEY_PROFILES, array.toString())
                .putString(KEY_SELECTED_PROFILE_ID, selectedProfileId)
                .commit();
        AppLogger.i(context, "saveProfiles profiles=" + array.length() + ", selected=" + selectedProfileId + ", saved=" + saved);
    }

    public boolean appendStepToSelectedProfile(ClickStep step) {
        if (step == null) {
            return false;
        }
        List<ClickStep> steps = new ArrayList<>();
        steps.add(step);
        return appendStepsToSelectedProfile(steps);
    }

    public boolean appendStepsToSelectedProfile(List<ClickStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        List<ClickProfile> profiles = loadProfiles();
        ClickProfile selected = findSelectedProfile(profiles);
        if (selected == null) {
            return false;
        }
        selected.getSteps().addAll(steps);
        saveProfiles(profiles, selected.getId());
        return true;
    }

    public boolean removeLastStepFromSelectedProfile() {
        List<ClickProfile> profiles = loadProfiles();
        ClickProfile selected = findSelectedProfile(profiles);
        if (selected == null || selected.getSteps().isEmpty()) {
            return false;
        }
        selected.getSteps().remove(selected.getSteps().size() - 1);
        saveProfiles(profiles, selected.getId());
        return true;
    }

    public String loadSelectedProfileId() {
        return preferences.getString(KEY_SELECTED_PROFILE_ID, "");
    }

    private ClickProfile findSelectedProfile(List<ClickProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        String selectedId = loadSelectedProfileId();
        for (ClickProfile profile : profiles) {
            if (profile.getId().equals(selectedId)) {
                return profile;
            }
        }
        return profiles.get(0);
    }

    private void removeSeededPlaceholderStep(List<ClickProfile> profiles) {
        boolean changed = false;
        for (ClickProfile profile : profiles) {
            if (!"默认方案".equals(profile.getName()) || profile.getSteps().size() != 1) {
                continue;
            }
            ClickStep step = profile.getSteps().get(0);
            if (isSeededPlaceholderStep(step)) {
                profile.getSteps().clear();
                changed = true;
            }
        }
        if (changed) {
            saveProfiles(profiles, loadSelectedProfileId());
        }
    }

    private boolean isSeededPlaceholderStep(ClickStep step) {
        return step != null
                && !step.isSwipe()
                && step.getStartX() == 540
                && step.getStartY() == 960
                && step.getEndX() == 540
                && step.getEndY() == 960
                && step.getDelayMs() == 300
                && step.getDurationMs() == 80
                && step.getRandomRadius() == 0;
    }

}
