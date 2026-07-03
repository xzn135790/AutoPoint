# 自动连点器 MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local Android 13+ auto clicker MVP with profile storage, click/swipe steps, delay/random offset controls, accessibility gesture execution, floating controls, and a polished Material UI.

**Architecture:** Use a native Android Java implementation. Keep data models, persistence, execution, accessibility service, floating control service, and UI separate so each part can be tested or changed independently.

**Tech Stack:** Android SDK 36, Java 11 source compatibility, AppCompat, Material Components, SharedPreferences, Android AccessibilityService, WindowManager overlay.

---

## File Structure

- Create `app/src/main/java/com/example/myapplication3/model/ClickStep.java`: click/swipe step model, validation helpers, JSON conversion.
- Create `app/src/main/java/com/example/myapplication3/model/ClickProfile.java`: saved profile model and JSON conversion.
- Create `app/src/main/java/com/example/myapplication3/store/ClickProfileStore.java`: SharedPreferences persistence and default profile seeding.
- Create `app/src/main/java/com/example/myapplication3/engine/AutoClickEngine.java`: delayed loop execution, random offset calculation, pause/stop state.
- Create `app/src/main/java/com/example/myapplication3/service/AutoClickAccessibilityService.java`: system gesture dispatch entry point.
- Create `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`: overlay start/pause/stop controls.
- Create `app/src/main/java/com/example/myapplication3/MainActivity.java`: profile and step editor UI binding.
- Create XML layouts under `app/src/main/res/layout/`: main screen, step item, edit dialog, floating panel.
- Create drawable resources for the visual theme.
- Modify `app/src/main/AndroidManifest.xml`: activities, services, permissions.
- Create `app/src/main/res/xml/auto_click_accessibility_service.xml`: accessibility service metadata.
- Modify `app/src/main/res/values/*.xml`: strings, colors, themes.
- Create unit tests for model serialization and engine random offset bounds.

## Tasks

### Task 1: Android Entry Points and Theme

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/xml/auto_click_accessibility_service.xml`

- [ ] Add `SYSTEM_ALERT_WINDOW`, `MainActivity`, `AutoClickAccessibilityService`, and `FloatingControlService`.
- [ ] Add accessibility service metadata with gesture capability.
- [ ] Define app name, permission text, and Material theme colors.
- [ ] Run manifest merge through `:app:assembleDebug`.

### Task 2: Data Models and Persistence

**Files:**
- Create: `app/src/main/java/com/example/myapplication3/model/ClickStep.java`
- Create: `app/src/main/java/com/example/myapplication3/model/ClickProfile.java`
- Create: `app/src/main/java/com/example/myapplication3/store/ClickProfileStore.java`
- Test: `app/src/test/java/com/example/myapplication3/ClickProfileTest.java`

- [ ] Implement click/swipe models with explicit fields for delay, duration, and random radius.
- [ ] Implement JSON serialization with `org.json` to avoid new dependencies.
- [ ] Store profile list and selected profile id in SharedPreferences.
- [ ] Add tests for click and swipe serialization round trips.

### Task 3: Execution Engine

**Files:**
- Create: `app/src/main/java/com/example/myapplication3/engine/AutoClickEngine.java`
- Test: `app/src/test/java/com/example/myapplication3/AutoClickEngineTest.java`

- [ ] Add `GestureDispatcher` interface so tests can validate execution without Android services.
- [ ] Implement delay, finite loop, infinite loop, pause/resume, stop, and random offset.
- [ ] Clamp randomized coordinates at zero to avoid invalid gestures.
- [ ] Add tests for random offset bounds and finite loop count.

### Task 4: Accessibility and Floating Services

**Files:**
- Create: `app/src/main/java/com/example/myapplication3/service/AutoClickAccessibilityService.java`
- Create: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`
- Create: `app/src/main/res/layout/view_floating_control.xml`

- [ ] Connect `AutoClickEngine` to `dispatchGesture`.
- [ ] Expose static safe methods for start, pause, resume, and stop.
- [ ] Build a compact overlay panel with start/pause/stop buttons.
- [ ] Handle missing service instance by returning an error state instead of crashing.

### Task 5: Main UI

**Files:**
- Create: `app/src/main/java/com/example/myapplication3/MainActivity.java`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/item_click_step.xml`
- Create: `app/src/main/res/layout/dialog_step_editor.xml`
- Create drawable resources under `app/src/main/res/drawable/`

- [ ] Build a polished Material main screen with status header, profile controls, step list, and action bar.
- [ ] Implement profile add/copy/delete/save flows.
- [ ] Implement click and swipe edit dialogs with field validation.
- [ ] Wire permission buttons to Android accessibility and overlay settings.
- [ ] Start floating service only when overlay permission is granted.

### Task 6: Verification

**Files:**
- Modify only if failures require local fixes.

- [ ] Run unit tests with `:app:testDebugUnitTest`.
- [ ] Run debug build with `:app:assembleDebug`.
- [ ] If Gradle requires Java 21, run with Android Studio JBR from `D:\noIn\android studio\jbr`.
- [ ] Report any device-only verification that still needs manual checking on the Redmi phone.
