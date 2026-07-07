# Floating Recording and Crosshair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the auto clicker usable over other apps by adding crosshair coordinate picking and transparent overlay recording to the floating control service.

**Architecture:** Keep execution in the existing `AutoClickAccessibilityService` and `AutoClickEngine`. Expand `FloatingControlService` into a small mode controller that manages three overlay surfaces: the compact control panel, an optional draggable crosshair, and an optional full-screen recording layer. Persist newly captured steps through `ClickProfileStore` without changing the existing JSON model.

**Tech Stack:** Android Java, AppCompat/Material Components, `WindowManager.TYPE_APPLICATION_OVERLAY`, `SharedPreferences`, existing Gradle/AGP setup.

---

## File Structure

- Modify `app/src/main/java/com/example/myapplication3/store/ClickProfileStore.java`
  - Add focused helpers to append a step and remove the last step from the selected profile.
  - Keep existing JSON schema and existing public methods.
- Modify `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`
  - Add mode state: execute, crosshair, recording.
  - Manage extra overlay views and their lifecycle.
  - Create click/swipe steps from crosshair and recording coordinates.
- Modify `app/src/main/res/layout/view_floating_control.xml`
  - Add compact mode buttons and action rows for execute, crosshair, and recording modes.
- Create `app/src/main/res/layout/view_crosshair.xml`
  - A small draggable target view with a visible center marker.
- Create `app/src/main/res/layout/view_recording_overlay.xml`
  - A transparent full-screen touch receiver with a compact status panel.
- Modify `app/src/main/res/values/strings.xml`
  - Add user-facing status and action strings used by the floating controls.
- Test `app/src/test/java/com/example/myapplication3/ClickProfileTest.java`
  - Add model-level coverage for generated click/swipe step values where possible.
- Verification
  - Run `:app:testDebugUnitTest`.
  - Run `:app:assembleDebug` with Android Studio JBR.

## Constraints

- Do not commit git changes.
- Do not rewrite whole files unless unavoidable; prefer scoped patches.
- Do not change `ClickStep` or `ClickProfile` JSON keys.
- Do not change `AutoClickEngine` gesture scheduling unless a compile or runtime issue requires it.
- Recording overlay intentionally captures touches and prevents the target app underneath from receiving them.

## Task 1: Store Helpers

**Files:**
- Modify: `app/src/main/java/com/example/myapplication3/store/ClickProfileStore.java`

- [ ] Add `appendStepToSelectedProfile(ClickStep step)` returning `boolean`.
- [ ] Add `removeLastStepFromSelectedProfile()` returning `boolean`.
- [ ] Implement both by loading profiles, resolving selected profile id, falling back to the first profile, mutating only that profile, and calling `saveProfiles`.
- [ ] Preserve current fallback behavior for empty/corrupt profile storage.

Implementation shape:

```java
public boolean appendStepToSelectedProfile(ClickStep step) {
    if (step == null) {
        return false;
    }
    List<ClickProfile> profiles = loadProfiles();
    ClickProfile selected = findSelectedProfile(profiles);
    if (selected == null) {
        return false;
    }
    selected.getSteps().add(step);
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
```

## Task 2: Floating Control Layout

**Files:**
- Modify: `app/src/main/res/layout/view_floating_control.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Add mode buttons with ids `executeModeButton`, `crosshairModeButton`, `recordModeButton`.
- [ ] Add action containers with ids `executeActions`, `crosshairActions`, `recordingActions`.
- [ ] Keep existing ids used by Java: `floatingStatusText`, `floatingStartButton`, `floatingPauseButton`, `floatingStopButton`.
- [ ] Add crosshair action buttons: `addClickPointButton`, `setSwipeStartButton`, `setSwipeEndButton`.
- [ ] Add recording action buttons: `stopRecordingButton`, `undoRecordedStepButton`.
- [ ] Keep panel compact and `wrap_content`, since it sits over other apps.

## Task 3: Crosshair Layout

**Files:**
- Create: `app/src/main/res/layout/view_crosshair.xml`

- [ ] Create a 56dp square overlay.
- [ ] Add a visible center marker using nested `View` elements.
- [ ] Use `@drawable/bg_floating_panel` or existing colors to avoid adding unnecessary drawables.

Expected layout responsibilities:

```xml
<FrameLayout ... android:layout_width="56dp" android:layout_height="56dp">
    <!-- Center marker and cross lines. -->
</FrameLayout>
```

## Task 4: Recording Overlay Layout

**Files:**
- Create: `app/src/main/res/layout/view_recording_overlay.xml`

- [ ] Create a full-screen `FrameLayout` that receives touch events.
- [ ] Add a small status panel aligned near the top with id `recordingStatusText`.
- [ ] Keep the background transparent enough to see the target app.
- [ ] Do not add buttons here; recording buttons remain in the floating control panel to avoid duplicated handlers.

## Task 5: Floating Service Mode Controller

**Files:**
- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] Add an enum or integer constants for `MODE_EXECUTE`, `MODE_CROSSHAIR`, `MODE_RECORDING`.
- [ ] Add fields for `crosshairView`, `recordingView`, their `LayoutParams`, selected swipe start coordinates, and recording down event data.
- [ ] Add `switchMode(int mode)` that updates visible action rows and creates/removes extra overlay views.
- [ ] Ensure `onDestroy()` removes `floatingView`, `crosshairView`, and `recordingView` if present.
- [ ] Ensure `stopRecording` is called before switching away from recording mode.

Important lifecycle rule:

```java
private void removeOverlayView(View view) {
    if (windowManager != null && view != null) {
        windowManager.removeView(view);
    }
}
```

Use null checks around `removeView` because overlays may already be absent during service shutdown.

## Task 6: Crosshair Capture

**Files:**
- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] Implement `showCrosshair()` with `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
- [ ] Make the crosshair draggable by updating its `x` and `y` from raw touch deltas.
- [ ] Implement `getCrosshairCenter()` using `params.x + crosshairView.getWidth() / 2` and `params.y + crosshairView.getHeight() / 2`.
- [ ] Implement `addClickFromCrosshair()` that creates `ClickStep.click()`, sets start/end coordinates to the center, and persists it.
- [ ] Implement `setSwipeStartFromCrosshair()` that stores the center in fields.
- [ ] Implement `addSwipeEndFromCrosshair()` that validates the start exists, creates `ClickStep.swipe()`, sets start/end coordinates, and persists it.

Status messages:

- Click saved: `已添加点击：x,y`
- Swipe start saved: `已设置滑动起点：x,y`
- Swipe saved: `已添加滑动：x1,y1 -> x2,y2`
- Missing swipe start: `请先设置滑动起点`

## Task 7: Recording Capture

**Files:**
- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] Implement `startRecording()` to show `view_recording_overlay`.
- [ ] Attach `OnTouchListener` to recording view.
- [ ] On `ACTION_DOWN`, store raw down x/y and event time.
- [ ] On `ACTION_UP`, compare with up x/y.
- [ ] If distance is less than 24px, create a click step.
- [ ] If distance is at least 24px, create a swipe step with duration at least 120ms.
- [ ] Save each step immediately through `ClickProfileStore`.
- [ ] Implement `undoLastRecordedStep()` by calling `removeLastStepFromSelectedProfile()`.
- [ ] Implement `stopRecording()` that removes recording overlay and updates mode/status.

Coordinate and duration constants:

```java
private static final int RECORD_SWIPE_THRESHOLD_PX = 24;
private static final long MIN_RECORDED_CLICK_MS = 50L;
private static final long MIN_RECORDED_SWIPE_MS = 120L;
```

## Task 8: Wire Buttons and Status

**Files:**
- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] Bind new mode and action buttons in `showFloatingView()`.
- [ ] Route execute mode buttons to existing `AutoClickAccessibilityService` methods.
- [ ] Route crosshair buttons to crosshair capture methods.
- [ ] Route recording buttons to recording methods.
- [ ] Update pause button text through existing status callback.
- [ ] Update action row visibility on mode switch.

Visibility behavior:

```java
executeActions.setVisibility(currentMode == MODE_EXECUTE ? View.VISIBLE : View.GONE);
crosshairActions.setVisibility(currentMode == MODE_CROSSHAIR ? View.VISIBLE : View.GONE);
recordingActions.setVisibility(currentMode == MODE_RECORDING ? View.VISIBLE : View.GONE);
```

## Task 9: Verification

**Files:**
- Modify only if failures reveal a local code issue.

- [ ] Run unit tests:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] Run debug build:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] Manual device checks:
  - Open floating controls over another app.
  - Crosshair mode can add a click and a swipe.
  - Recording mode captures a tap as click and drag as swipe.
  - Stop recording removes the transparent overlay.
  - Execute mode still starts the selected profile.

## Self-Review

- Spec coverage: execution mode, crosshair mode, recording mode, persistence, Android overlay boundary, and verification are covered.
- Placeholder scan: no `TODO`, `TBD`, or unresolved implementation placeholders remain.
- Type consistency: planned method names and ids are consistent across tasks.
- Git behavior: no commit step is included because project instructions prohibit git commits.
