# Auto Clicker Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add execution traces, proportional recording-panel scaling, a circular anime-avatar run button, randomized loop intervals, and a new adaptive launcher icon without restructuring the existing app.

**Architecture:** Keep `MainActivity`, `ClickProfile`, `AutoClickEngine`, `AutoClickAccessibilityService`, and `FloatingControlService` in their current roles. Extend the profile JSON compatibly, reuse the engine's bounded random-duration logic, and make the existing overlay service own the trace animation, scaled panel container, and circular run button.

**Tech Stack:** Android SDK 36.1, Java 11 source compatibility, Android Views/XML, AccessibilityService gestures, WindowManager overlays, JUnit 4, Gradle 9.4.1.

---

## Project Constraints

- Do not commit, stage, push, or otherwise mutate Git history.
- Do not rewrite complete existing files; use minimal patches.
- Do not add UTF-8 BOM.
- Do not change unrelated code or existing warnings.
- Use Android Studio JBR at `D:\noIn\android studio\jbr` only for Gradle commands; do not change the user's Java environment.
- Stop a local Gradle verification if it exceeds two minutes.
- Image generation must use the `imagegen` skill and produce an original image.

## File Map

**Modify**

- `app/src/main/java/com/example/myapplication3/model/ClickProfile.java` — add and persist `loopIntervalRandomMs`.
- `app/src/test/java/com/example/myapplication3/ClickProfileTest.java` — profile compatibility, copy, JSON, and clamping tests.
- `app/src/main/java/com/example/myapplication3/engine/AutoClickEngine.java` — calculate the randomized interval between loops.
- `app/src/test/java/com/example/myapplication3/AutoClickEngineTest.java` — deterministic random interval and stop-during-wait tests.
- `app/src/main/java/com/example/myapplication3/MainActivity.java` — bind and save the new input.
- `app/src/main/res/layout/activity_main.xml` — add the random loop interval input.
- `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java` — trace fade, scaled panel container, and circular run button.
- `app/src/main/res/layout/view_floating_control.xml` — change the resize affordance to a lower-right handle.
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml` — use the new portrait foreground.
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` — use the new portrait foreground.
- `app/src/main/res/drawable/ic_launcher_background.xml` — use the approved purple-blue neon background.

**Create**

- `app/src/main/res/drawable-nodpi/ic_launcher_portrait.png` — generated original black-haired anime portrait.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — unrelated neutral themed-icon mark.
- `app/src/main/res/drawable/bg_profile_avatar.xml` — circular avatar clipping/stroke background.
- `app/src/main/res/drawable/bg_profile_badge_idle.xml` — play badge background.
- `app/src/main/res/drawable/bg_profile_badge_running.xml` — stop badge background.

**Leave unchanged**

- `app/src/main/java/com/example/myapplication3/service/AutoClickAccessibilityService.java` — its existing step callback already forwards final coordinates.
- `app/src/main/java/com/example/myapplication3/model/ClickStep.java` — step timing and coordinate fields are already sufficient.
- Legacy density launcher WebP files — API 33+ resolves the adaptive icon from `mipmap-anydpi`.

### Task 1: Persist the Random Loop Interval

**Files:**

- Modify: `app/src/main/java/com/example/myapplication3/model/ClickProfile.java`
- Test: `app/src/test/java/com/example/myapplication3/ClickProfileTest.java`

- [ ] **Step 1: Write failing model tests**

Add `org.json.JSONObject` import and these assertions/tests:

```java
import org.json.JSONObject;

@Test
public void loopIntervalRandomDefaultsToZeroAndClampsNegativeValues() {
    ClickProfile profile = new ClickProfile("循环随机间隔");

    assertEquals(0L, profile.getLoopIntervalRandomMs());

    profile.setLoopIntervalRandomMs(-1L);
    assertEquals(0L, profile.getLoopIntervalRandomMs());

    profile.setLoopIntervalRandomMs(800L);
    assertEquals(800L, profile.getLoopIntervalRandomMs());
}

@Test
public void loopIntervalRandomRoundTripsThroughJsonAndDefaultsForOldJson() throws Exception {
    ClickProfile profile = new ClickProfile("JSON");
    profile.setLoopIntervalMs(3000L);
    profile.setLoopIntervalRandomMs(1000L);

    ClickProfile restored = ClickProfile.fromJson(profile.toJson());
    ClickProfile legacy = ClickProfile.fromJson(new JSONObject()
            .put("name", "旧方案")
            .put("loopIntervalMs", 2500L));

    assertEquals(3000L, restored.getLoopIntervalMs());
    assertEquals(1000L, restored.getLoopIntervalRandomMs());
    assertEquals(0L, legacy.getLoopIntervalRandomMs());
}
```

In `profileCopyKeepsStepValuesWithNewIdentity()`, set and verify the value:

```java
profile.setLoopIntervalRandomMs(700L);
// ...
assertEquals(700L, copied.getLoopIntervalRandomMs());
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:testDebugUnitTest --tests com.example.myapplication3.ClickProfileTest --no-daemon --console=plain
```

Expected: compilation fails because `getLoopIntervalRandomMs()` and `setLoopIntervalRandomMs(...)` do not exist.

- [ ] **Step 3: Add the model field and compatible constructor chain**

Add the field:

```java
private long loopIntervalRandomMs;
```

Keep all existing public constructors source-compatible:

```java
public ClickProfile(String name) {
    this(UUID.randomUUID().toString(), name, 1, false,
            1000L, 0L, 1.0, new ArrayList<ClickStep>());
}

public ClickProfile(String id, String name, int loopCount,
                    boolean infiniteLoop, List<ClickStep> steps) {
    this(id, name, loopCount, infiniteLoop,
            1000L, 0L, 1.0, steps);
}

public ClickProfile(String id, String name, int loopCount,
                    boolean infiniteLoop, double speedMultiplier,
                    List<ClickStep> steps) {
    this(id, name, loopCount, infiniteLoop,
            1000L, 0L, speedMultiplier, steps);
}
```

Change the full constructor to:

```java
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
```

Add accessors:

```java
public long getLoopIntervalRandomMs() {
    return loopIntervalRandomMs;
}

public void setLoopIntervalRandomMs(long loopIntervalRandomMs) {
    this.loopIntervalRandomMs = Math.max(0L, loopIntervalRandomMs);
}
```

- [ ] **Step 4: Update JSON and copy paths**

Use a zero default for old data:

```java
object.optLong("loopIntervalMs", 1000L),
object.optLong("loopIntervalRandomMs", 0L),
object.optDouble("speedMultiplier", 1.0),
```

Write the field:

```java
object.put("loopIntervalRandomMs", loopIntervalRandomMs);
```

Preserve it when copying:

```java
return new ClickProfile(UUID.randomUUID().toString(), newName, loopCount, infiniteLoop,
        loopIntervalMs, loopIntervalRandomMs, speedMultiplier, copiedSteps);
```

- [ ] **Step 5: Run the focused tests**

Run the Task 1 command again.

Expected: `ClickProfileTest` passes.

- [ ] **Step 6: Checkpoint without committing**

Run:

```powershell
git diff --check
git status --short
```

Expected: only Task 1 files plus the approved design/plan documents are changed; do not stage or commit.

### Task 2: Randomize the Interval Between Loops

**Files:**

- Modify: `app/src/main/java/com/example/myapplication3/engine/AutoClickEngine.java`
- Test: `app/src/test/java/com/example/myapplication3/AutoClickEngineTest.java`

- [ ] **Step 1: Write deterministic failing tests**

Add:

```java
@Test
public void loopIntervalRandomStaysInsideConfiguredBounds() {
    Random random = new Random(19);

    for (int i = 0; i < 100; i++) {
        long interval = AutoClickEngine.calculateLoopIntervalMs(3000L, 1000L, random);
        assertTrue(interval >= 2000L);
        assertTrue(interval <= 4000L);
    }
}

@Test
public void loopIntervalRandomClampsAtZeroAndSupportsZeroRange() {
    assertEquals(2500L,
            AutoClickEngine.calculateLoopIntervalMs(2500L, 0L, new Random(2)));

    for (int i = 0; i < 50; i++) {
        long interval = AutoClickEngine.calculateLoopIntervalMs(100L, 1000L, new Random(i));
        assertTrue(interval >= 0L);
        assertTrue(interval <= 1100L);
    }
}

@Test
public void stopPreventsNextLoopDuringLongInterval() throws Exception {
    final AtomicInteger dispatchCount = new AtomicInteger();
    final CountDownLatch firstDispatch = new CountDownLatch(1);
    AutoClickEngine engine = new AutoClickEngine(new AutoClickEngine.GestureDispatcher() {
        @Override
        public boolean dispatch(ClickStep step, int startX, int startY,
                                int endX, int endY, long durationMs) {
            dispatchCount.incrementAndGet();
            firstDispatch.countDown();
            return true;
        }
    });
    ClickProfile profile = new ClickProfile("停止轮次等待");
    profile.setLoopCount(2);
    profile.setLoopIntervalMs(5000L);
    profile.setLoopIntervalRandomMs(0L);
    profile.getSteps().add(new ClickStep(
            "one", ClickStep.TYPE_CLICK, 1, 1, 1, 1, 0, 1, 0));

    assertTrue(engine.start(profile));
    assertTrue(firstDispatch.await(1, TimeUnit.SECONDS));
    Thread.sleep(150L);
    engine.stop();
    Thread.sleep(200L);

    assertEquals(1, dispatchCount.get());
    assertFalse(engine.isRunning());
    engine.shutdown();
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:testDebugUnitTest --tests com.example.myapplication3.AutoClickEngineTest --no-daemon --console=plain
```

Expected: compilation fails because `calculateLoopIntervalMs(...)` does not exist.

- [ ] **Step 3: Add a deterministic, overflow-safe calculation entry point**

Add:

```java
public static long calculateLoopIntervalMs(long baseMs, long randomMs, Random random) {
    long safeBase = Math.max(0L, baseMs);
    long safeRandom = Math.min(Math.max(0L, randomMs), Long.MAX_VALUE / 4L);
    if (safeRandom == 0L) {
        return safeBase;
    }
    long offset = nextLongInclusive(safeRandom * 2L, random) - safeRandom;
    if (offset > 0L && safeBase > Long.MAX_VALUE - offset) {
        return Long.MAX_VALUE;
    }
    return Math.max(0L, safeBase + offset);
}

private static long nextLongInclusive(long boundInclusive, Random random) {
    if (boundInclusive <= 0L) {
        return 0L;
    }
    long candidate;
    do {
        candidate = random.nextLong() & Long.MAX_VALUE;
    } while (candidate > Long.MAX_VALUE
            - (Long.MAX_VALUE % (boundInclusive + 1L)));
    return candidate % (boundInclusive + 1L);
}
```

Make the existing instance `nextLongInclusive(...)` delegate to the static method so the step timing behavior remains unchanged:

```java
private long nextLongInclusive(long boundInclusive) {
    return nextLongInclusive(boundInclusive, random);
}
```

- [ ] **Step 4: Use the random interval only between real loops**

Replace the fixed wait inside `runProfile(...)` with:

```java
waitIfPaused();
long loopIntervalMs = calculateLoopIntervalMs(
        profile.getLoopIntervalMs(),
        profile.getLoopIntervalRandomMs(),
        random
);
sleepInterruptibly(loopIntervalMs);
```

Do not apply `speedMultiplier` to this value.

- [ ] **Step 5: Run the focused tests**

Run the Task 2 command again.

Expected: `AutoClickEngineTest` passes, including stop-during-wait.

- [ ] **Step 6: Checkpoint without committing**

Run `git diff --check` and inspect `git status --short`. Do not stage or commit.

### Task 3: Add the Random Interval Input

**Files:**

- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/example/myapplication3/MainActivity.java`

- [ ] **Step 1: Add the labeled numeric input**

Insert immediately after `loopIntervalInput`:

```xml
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="10dp"
    android:text="间隔随机 ±（毫秒）"
    android:textColor="@color/app_text_secondary"
    android:textSize="13sp" />

<EditText
    android:id="@+id/loopIntervalRandomInput"
    android:layout_width="match_parent"
    android:layout_height="48dp"
    android:layout_marginTop="4dp"
    android:background="@drawable/bg_input"
    android:hint="默认 0"
    android:inputType="number"
    android:maxLines="1"
    android:textColor="@color/app_text_primary"
    android:textColorHint="@color/app_text_secondary" />
```

- [ ] **Step 2: Bind the view**

Add the field:

```java
private EditText loopIntervalRandomInput;
```

In `bindViews()`:

```java
loopIntervalRandomInput = findViewById(R.id.loopIntervalRandomInput);
```

- [ ] **Step 3: Bind and save profile values**

In `bindCurrentProfile()`:

```java
loopIntervalRandomInput.setText(
        String.valueOf(currentProfile.getLoopIntervalRandomMs()));
```

In `saveCurrentProfileFromInputs()`:

```java
currentProfile.setLoopIntervalRandomMs(
        parsePositiveLong(loopIntervalRandomInput, 0L));
```

Keep `parsePositiveLong(...)` unchanged because it already handles empty, invalid, and negative input.

- [ ] **Step 4: Update the save-button text**

Change:

```xml
android:text="保存循环和倍速"
```

to:

```xml
android:text="保存循环、间隔和倍速"
```

- [ ] **Step 5: Compile resources and Java**

Run:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:compileDebugJavaWithJavac --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`. Stop if it exceeds two minutes.

### Task 4: Restore and Fade the Execution Trace

**Files:**

- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] **Step 1: Add explicit animation constants**

Add:

```java
private static final long EXECUTION_PREVIEW_HOLD_MS = 200L;
private static final long EXECUTION_PREVIEW_FADE_MS = 600L;
```

- [ ] **Step 2: Remove the root-cause guard**

In the `StepCallback` UI runnable, remove:

```java
if (profileBubbleView != null) {
    return;
}
```

Always call:

```java
showCurrentExecutionPreview(step, startX, startY, endX, endY, stepIndex);
```

- [ ] **Step 3: Animate and identity-guard the current preview**

After adding the preview view to `WindowManager`, retain the exact instance:

```java
final View preview = executionPreviewView;
preview.setAlpha(1.0f);
preview.animate()
        .alpha(0.0f)
        .setStartDelay(EXECUTION_PREVIEW_HOLD_MS)
        .setDuration(EXECUTION_PREVIEW_FADE_MS)
        .withEndAction(new Runnable() {
            @Override
            public void run() {
                if (executionPreviewView == preview) {
                    removeExecutionPreview();
                }
            }
        })
        .start();
bringProfileBubbleToFront();
```

Before removing a live preview:

```java
if (executionPreviewView != null) {
    executionPreviewView.animate().cancel();
}
```

Then use the existing safe `removeOverlayView(...)`.

- [ ] **Step 4: Make the trace visibly淡色**

Use lower-alpha paint values:

```java
linePaint.setColor(Color.argb(135, 37, 99, 235));
markerPaint.setColor(Color.argb(155, 249, 115, 22));
markerStrokePaint.setColor(Color.argb(190, 255, 255, 255));
textPaint.setColor(Color.argb(220, 255, 255, 255));
```

Keep the existing step number, swipe endpoint label, coordinates, and non-touchable window flags.

- [ ] **Step 5: Compile**

Run the Task 3 compile command.

Expected: `BUILD SUCCESSFUL`.

### Task 5: Scale the Recording Panel as One Unit

**Files:**

- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`
- Modify: `app/src/main/res/layout/view_floating_control.xml`

- [ ] **Step 1: Replace independent min/max constants with scale bounds**

Use:

```java
private static final float FLOATING_MIN_SCALE = 0.75f;
private static final float FLOATING_MAX_SCALE = 1.75f;
```

Add fields:

```java
private View floatingPanelView;
private float floatingScale = 1.0f;
private float floatingResizeStartScale;
```

- [ ] **Step 2: Put the unchanged-size panel inside a scaled window container**

Inflate the XML into `floatingPanelView`, then create the overlay root:

```java
floatingPanelView = LayoutInflater.from(this)
        .inflate(R.layout.view_floating_control, null);
FrameLayout floatingContainer = new FrameLayout(this);
floatingContainer.setClipChildren(false);
floatingContainer.addView(floatingPanelView, new FrameLayout.LayoutParams(
        dp(FLOATING_DEFAULT_WIDTH_DP),
        dp(FLOATING_DEFAULT_HEIGHT_DP)
));
floatingView = floatingContainer;
floatingPanelView.setPivotX(0f);
floatingPanelView.setPivotY(0f);
```

Resolve all child IDs from `floatingPanelView`. Keep `floatingView` as the object added to and removed from `WindowManager`.

- [ ] **Step 3: Add one scale application method**

```java
private void applyFloatingScale(float scale) {
    floatingScale = Math.max(FLOATING_MIN_SCALE,
            Math.min(FLOATING_MAX_SCALE, scale));
    floatingPanelView.setScaleX(floatingScale);
    floatingPanelView.setScaleY(floatingScale);
    floatingParams.width = Math.round(
            dp(FLOATING_DEFAULT_WIDTH_DP) * floatingScale);
    floatingParams.height = Math.round(
            dp(FLOATING_DEFAULT_HEIGHT_DP) * floatingScale);
    clampFloatingPositionToScreen();
    windowManager.updateViewLayout(floatingView, floatingParams);
}

private void clampFloatingPositionToScreen() {
    int screenWidth = getResources().getDisplayMetrics().widthPixels;
    int screenHeight = getResources().getDisplayMetrics().heightPixels;
    floatingParams.x = clamp(floatingParams.x, 0,
            Math.max(0, screenWidth - floatingParams.width));
    floatingParams.y = clamp(floatingParams.y, 0,
            Math.max(0, screenHeight - floatingParams.height));
}
```

The inner panel stays at the baseline size; Android's uniform view transform scales text, buttons, padding, margins, and touch coordinates together.

- [ ] **Step 4: Calculate one scale from either drag axis**

On resize `ACTION_DOWN`:

```java
touchStartX = event.getRawX();
touchStartY = event.getRawY();
floatingResizeStartScale = floatingScale;
return true;
```

On `ACTION_MOVE`:

```java
float dxScale = (event.getRawX() - touchStartX)
        / dp(FLOATING_DEFAULT_WIDTH_DP);
float dyScale = (event.getRawY() - touchStartY)
        / dp(FLOATING_DEFAULT_HEIGHT_DP);
float scaleDelta = Math.abs(dxScale) >= Math.abs(dyScale)
        ? dxScale : dyScale;
applyFloatingScale(floatingResizeStartScale + scaleDelta);
return true;
```

Initialize the panel once with:

```java
applyFloatingScale(1.0f);
```

after `windowManager.addView(...)`, because updating layout requires the view to be attached.

- [ ] **Step 5: Make the resize handle visually lower-right**

Change the handle to:

```xml
<TextView
    android:id="@+id/resizeHandle"
    android:layout_width="40dp"
    android:layout_height="32dp"
    android:layout_gravity="end"
    android:gravity="center"
    android:text="↘"
    android:textColor="@color/app_text_secondary"
    android:textSize="18sp"
    android:textStyle="bold" />
```

- [ ] **Step 6: Compile**

Run the Task 3 compile command.

Expected: `BUILD SUCCESSFUL`.

### Task 6: Generate and Wire the Original Anime Portrait

**Files:**

- Create: `app/src/main/res/drawable-nodpi/ic_launcher_portrait.png`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`

- [ ] **Step 1: Invoke the image generation skill**

Use this exact creative brief:

```text
Create an original square 1024x1024 Android app icon portrait of an adult
black-haired anime woman with a cool, confident expression. Head-and-shoulders
composition, face centered within the middle 60 percent safe area, vivid
purple and electric-blue neon background, polished high-detail anime
illustration, strong silhouette at small sizes. No text, watermark, phone,
finger, cursor, automation symbol, click effect, or existing copyrighted
character. Keep all facial features clear under circular and rounded-square
launcher masks.
```

Save the accepted generated PNG as:

```text
app/src/main/res/drawable-nodpi/ic_launcher_portrait.png
```

- [ ] **Step 2: Point adaptive icons to the portrait**

Use in both adaptive icon XML files:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_portrait" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

- [ ] **Step 3: Use a neon background and unrelated monochrome mark**

Keep the background simple and non-business-related:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="45"
        android:startColor="#24104F"
        android:centerColor="#5B21B6"
        android:endColor="#0EA5E9" />
</shape>
```

Create `ic_launcher_monochrome.xml` as a centered neutral four-point sparkle:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,24 L61,47 L84,54 L61,61 L54,84 L47,61 L24,54 L47,47 Z" />
</vector>
```

- [ ] **Step 4: Compile Android resources**

Run the Task 3 compile command.

Expected: resource compilation and Java compilation succeed.

### Task 7: Replace the Long Bubble with the Circular Avatar Button

**Files:**

- Create: `app/src/main/res/drawable/bg_profile_avatar.xml`
- Create: `app/src/main/res/drawable/bg_profile_badge_idle.xml`
- Create: `app/src/main/res/drawable/bg_profile_badge_running.xml`
- Modify: `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`

- [ ] **Step 1: Create the three small oval backgrounds**

`bg_profile_avatar.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#FF111827" />
    <stroke android:width="2dp" android:color="#E6FFFFFF" />
</shape>
```

`bg_profile_badge_idle.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#FF2563EB" />
    <stroke android:width="2dp" android:color="#FFFFFFFF" />
</shape>
```

`bg_profile_badge_running.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#FFEF4444" />
    <stroke android:width="2dp" android:color="#FFFFFFFF" />
</shape>
```

- [ ] **Step 2: Replace text-bubble fields with avatar fields**

Add these imports:

```java
import android.os.Handler;
import android.os.Looper;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
```

Use:

```java
private static final int PROFILE_BUBBLE_SIZE_DP = 60;
private static final long PROFILE_BUBBLE_LONG_PRESS_MS = 800L;
private FrameLayout profileBubbleView;
private TextView profileBubbleBadge;
private final Handler mainHandler = new Handler(Looper.getMainLooper());
private Runnable profileBubbleLongPressRunnable;
private boolean profileBubbleLongPressed;
```

Remove `profileBubbleText`, `shortBubbleName(...)`, and `buildBubbleText(...)`.

- [ ] **Step 3: Build the avatar and badge programmatically**

In `showProfileBubble()`:

```java
profileBubbleView = new FrameLayout(this);
profileBubbleView.setElevation(dp(8));

ImageView avatarView = new ImageView(this);
avatarView.setImageResource(R.drawable.ic_launcher_portrait);
avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
avatarView.setBackgroundResource(R.drawable.bg_profile_avatar);
avatarView.setClipToOutline(true);
avatarView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
profileBubbleView.addView(avatarView, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
));

profileBubbleBadge = new TextView(this);
profileBubbleBadge.setGravity(Gravity.CENTER);
profileBubbleBadge.setTextColor(Color.WHITE);
profileBubbleBadge.setTextSize(10f);
profileBubbleBadge.setTypeface(Typeface.DEFAULT_BOLD);
FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
        dp(22), dp(22), Gravity.END | Gravity.BOTTOM);
profileBubbleView.addView(profileBubbleBadge, badgeParams);

profileBubbleParams = new WindowManager.LayoutParams(
        dp(PROFILE_BUBBLE_SIZE_DP),
        dp(PROFILE_BUBBLE_SIZE_DP),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
);
```

- [ ] **Step 4: Update state through the badge**

Replace `updateProfileBubble(...)` content with:

```java
private void updateProfileBubble(boolean running) {
    if (profileBubbleBadge == null) {
        return;
    }
    profileBubbleBadge.setText(running ? "■" : "▶");
    profileBubbleBadge.setBackgroundResource(running
            ? R.drawable.bg_profile_badge_running
            : R.drawable.bg_profile_badge_idle);
}
```

- [ ] **Step 5: Separate tap, drag, and long press**

On `ACTION_DOWN`, reset state and post:

```java
profileBubbleLongPressed = false;
profileBubbleLongPressRunnable = new Runnable() {
    @Override
    public void run() {
        profileBubbleLongPressed = true;
        closeProfileBubbleControls();
    }
};
mainHandler.postDelayed(
        profileBubbleLongPressRunnable,
        PROFILE_BUBBLE_LONG_PRESS_MS
);
```

When movement exceeds `dp(4)`, mark `moved = true` and call:

```java
cancelProfileBubbleLongPress();
```

On `ACTION_UP`:

```java
cancelProfileBubbleLongPress();
if (!moved && !profileBubbleLongPressed) {
    toggleProfileBubbleExecution();
}
return true;
```

On `ACTION_CANCEL`, only cancel the pending long press.

Add:

```java
private void cancelProfileBubbleLongPress() {
    if (profileBubbleLongPressRunnable != null) {
        mainHandler.removeCallbacks(profileBubbleLongPressRunnable);
        profileBubbleLongPressRunnable = null;
    }
}
```

Call this method from `removeProfileBubble()` and `onDestroy()`.

- [ ] **Step 6: Compile**

Run the Task 3 compile command.

Expected: `BUILD SUCCESSFUL`.

### Task 8: Full Verification and Manual Handoff

**Files:**

- Verify all files listed above.
- Do not modify unrelated files while addressing failures.

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` and all tests pass.

- [ ] **Step 2: Build the Debug APK**

Run:

```powershell
$env:JAVA_HOME='D:\noIn\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\lenovo\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` and APK at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Inspect the final diff**

Run:

```powershell
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors, no UTF-8 BOM, no staged files, and no unrelated modifications.

- [ ] **Step 4: Perform device checks**

On an Android 13+ device with overlay and accessibility permissions:

1. Start a multi-step profile from the circular avatar button.
2. Verify each click shows its actual step number and fades within about 0.8 seconds.
3. Verify a swipe shows a line, start marker, end marker, and step number.
4. Verify the trace never blocks touches.
5. Resize the recording panel using mostly horizontal movement, then mostly vertical movement.
6. Verify the aspect ratio and all text/control proportions remain stable.
7. Verify avatar tap starts/stops, drag moves, and 0.8-second long press closes.
8. Configure `3000ms ± 1000ms` and confirm successive loop waits vary within `2000～4000ms`.
9. Confirm an old saved profile still loads with random interval `0`.
10. Inspect the launcher icon under circle and rounded-square masks.

- [ ] **Step 5: Report results without committing**

Report:

- changed files,
- test/build outcomes,
- APK path,
- any manual checks that still require the user's physical device.

Do not run `git add`, `git commit`, or `git push`.
