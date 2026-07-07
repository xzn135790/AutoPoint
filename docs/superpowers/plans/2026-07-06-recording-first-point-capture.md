# Recording-First Point Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make recording point capture the primary workflow so the user can open another app, tap target locations, see numbered markers, and replay those coordinates.

**Architecture:** Keep the existing accessibility execution path unchanged. Simplify the floating panel around recording and execution, use the transparent overlay as the coordinate collector, and add numbered marker views directly inside the recording overlay. Persist points immediately to the selected profile through `ClickProfileStore`.

**Tech Stack:** Android Java, `WindowManager.TYPE_APPLICATION_OVERLAY`, Material buttons, existing SharedPreferences profile store, Gradle/AGP Android app.

---

## Tasks

### Task 1: Version Bump

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] Change `versionCode = 1` to `versionCode = 2`.
- [ ] Change `versionName = "1.0"` to `versionName = "1.1"`.

### Task 2: Simplify Floating Panel

**Files:**
- Modify: `app/src/main/res/layout/view_floating_control.xml`

- [ ] Make `executeModeButton` display `录制取点`.
- [ ] Hide `crosshairModeButton` by setting `android:visibility="gone"`.
- [ ] Hide `recordModeButton` by setting `android:visibility="gone"`.
- [ ] Keep existing execute buttons, but make the main entry obvious: record first, then start execution.
- [ ] Keep recording action row with `停止录制` and `撤销上一步`.

### Task 3: Marker Drawable

**Files:**
- Create: `app/src/main/res/drawable/bg_record_marker.xml`

- [ ] Create an oval marker background using `@color/app_warning`.
- [ ] Add a white stroke so markers stay visible on mixed app backgrounds.

### Task 4: Recording Overlay Markers

**Files:**
- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] Add `List<View> recordingMarkers = new ArrayList<>();`.
- [ ] Change the `executeModeButton` click handler to start recording mode.
- [ ] Keep start/pause/stop execution buttons in execute action row.
- [ ] After each recorded click/swipe, add a numbered marker at the recorded start coordinate.
- [ ] On undo, remove the last marker and remove the last stored step.
- [ ] On stop recording, clear marker state and remove the recording overlay.

Marker behavior:

```java
TextView marker = new TextView(this);
marker.setText(String.valueOf(recordedStepCount));
marker.setBackgroundResource(R.drawable.bg_record_marker);
```

### Task 5: Verification

**Files:**
- Modify only if failures reveal local issues.

- [ ] Run `:app:testDebugUnitTest`.
- [ ] Run `:app:assembleDebug`.
- [ ] Manual check on device: tap several positions in recording mode and confirm numbered points appear where tapped.

## Self-Review

- Spec coverage: version bump, recording-first workflow, visual recorded markers, undo, execution retention are covered.
- Placeholder scan: no unresolved placeholders.
- Git behavior: no commit step is included because project instructions prohibit git commits.
