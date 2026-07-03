package com.example.myapplication3.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication3.model.ClickProfile;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class ClickProfileStore {
    private static final String PREFS_NAME = "click_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_SELECTED_PROFILE_ID = "selected_profile_id";

    private final SharedPreferences preferences;

    public ClickProfileStore(Context context) {
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
        preferences.edit()
                .putString(KEY_PROFILES, array.toString())
                .putString(KEY_SELECTED_PROFILE_ID, selectedProfileId)
                .apply();
    }

    public String loadSelectedProfileId() {
        return preferences.getString(KEY_SELECTED_PROFILE_ID, "");
    }
}
