# Health Trainer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a mobile fitness posture-checking MVP that extracts human pose landmarks in real time, judges squat/push-up/plank form with explainable rules, records invalid reps per set, and replays saved landmarks as a 3D skeleton.

**Architecture:** Use Android Native as the first implementation target because CameraX, MediaPipe, and real-time rendering are easier to stabilize there than through a cross-platform bridge. The app should split pose inference, landmark normalization, exercise rule evaluation, rep/set tracking, persistence, and UI rendering into small modules so each part can be tested without a camera. The initial posture evaluator is rule-based, with a later upgrade path for a learned good-form/bad-form classifier.

**Tech Stack:** Android Kotlin, Jetpack Compose, CameraX, MediaPipe Pose Landmarker, Kotlin serialization, Room or local JSON storage, JUnit, optional OpenCV for offline prototype/debug tooling, optional Three.js WebView or native OpenGL/SceneView for 3D skeleton replay.

---

## Product Scope

The first version should support three exercises:

- Squat: rep counting plus depth, knee alignment, torso stability checks.
- Push-up: rep counting plus elbow depth and body-line checks.
- Plank: timed hold plus body-line and shoulder/elbow placement checks.

The app should show real-time camera preview, skeleton overlay, current exercise state, rep count, and immediate feedback. After a set, it should show a result screen listing invalid movements such as `1세트 2회차: 스쿼트 깊이 부족`. The result screen should include a 3D skeleton replay based on stored world landmarks, with failed frames or failed joints highlighted.

## Why Rule-Based First

The project needs two model layers, but only one must be trained or shipped at the start:

- Pose landmark model: required. MediaPipe Pose Landmarker extracts 33 body landmarks and includes both normalized image coordinates and world coordinates.
- Correct-form model: optional at MVP stage. The MVP can judge posture with explicit angle, distance, confidence, and state-transition rules.

Rule-based posture evaluation is better for a term project MVP because it is explainable, testable, and does not require a labeled exercise dataset. A learned classifier can be added later after collecting valid/invalid rep samples.

## Commercial-Quality MVP Direction

The first version should optimize for a believable coaching product rather than a perfect trainer-grade correctness model. The app should avoid overconfident medical or professional claims and instead provide posture signals, form scores, invalid rep records, and replay evidence.

The recommended product strategy is:

- Use a proven pose estimator for landmarks.
- Use deterministic state machines for rep counting.
- Use rule-based scoring for form quality.
- Use model training only for bounded support tasks at first: exercise recognition, phase recognition, or limited error classification.
- Show feedback as "needs attention" rather than absolute pass/fail when confidence is low.
- Gate all posture feedback behind camera-angle and landmark-confidence checks.
- Make the result screen polished: invalid rep list, metric readout, and 3D skeleton replay.

This direction is more commercially plausible for an early product because the app can be useful even when it is not perfectly accurate. Users still get workout logs, repeated-motion counting, visible posture trends, and replay-based self-review.

### Demo Quality Principles

For the term-project demo, prioritize flows that are reliable under controlled conditions:

- Side-view squat: normal rep, shallow rep, normal rep.
- Side-view push-up: normal rep, shallow rep, body-line broken rep.
- Side-view plank: valid hold, hips-low warning, confidence warning when the user leaves the frame.

The demo should say:

```text
This is not a medical-grade posture judge.
It is a pose-landmark based fitness feedback prototype.
It detects movement phases, counts reps, records suspicious reps, and shows replay evidence.
```

### Model Training Positioning

If the project includes model training, train a lightweight model for one of these bounded tasks:

- Exercise recognition: `squat`, `push_up`, `plank`, `unknown`.
- Phase recognition: `top`, `bottom`, `hold`, `transition`.
- Limited error classification: `correct`, `shallow`, `forward_lean`, `body_line_broken`.

Do not make the trained model the only source of truth for correct posture in the MVP. Use it as a secondary signal next to rule-based metrics. This makes the final system easier to explain, easier to debug, and more robust during live demos.

## File Structure

Create this Android project layout:

```text
health_trainer/
  settings.gradle.kts
  build.gradle.kts
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/assets/pose_landmarker_lite.task
    src/main/java/com/healthtrainer/
      MainActivity.kt
      camera/CameraPreview.kt
      pose/PoseLandmark.kt
      pose/PoseFrame.kt
      pose/PoseLandmarkerHelper.kt
      pose/LandmarkNormalizer.kt
      geometry/AngleCalculator.kt
      exercise/ExerciseType.kt
      exercise/ExerciseRule.kt
      exercise/ExerciseFeedback.kt
      exercise/ExerciseSession.kt
      exercise/SquatRule.kt
      exercise/PushUpRule.kt
      exercise/PlankRule.kt
      tracker/RepStateMachine.kt
      tracker/SetTracker.kt
      replay/SkeletonReplayFrame.kt
      replay/SkeletonReplayStore.kt
      replay/Skeleton3DViewer.kt
      ui/ExerciseScreen.kt
      ui/ResultScreen.kt
      ui/SkeletonOverlay.kt
    src/test/java/com/healthtrainer/
      geometry/AngleCalculatorTest.kt
      exercise/SquatRuleTest.kt
      exercise/PushUpRuleTest.kt
      exercise/PlankRuleTest.kt
      tracker/RepStateMachineTest.kt
      tracker/SetTrackerTest.kt
```

Module responsibilities:

- `pose`: wraps MediaPipe output and converts raw landmarks into normalized app data.
- `geometry`: provides pure math utilities for angles, distances, and alignment.
- `exercise`: defines posture rules and feedback reasons.
- `tracker`: converts frame-level states into reps, sets, and invalid rep records.
- `replay`: stores landmark frames and renders a 3D skeleton replay.
- `ui`: Compose screens and camera/skeleton presentation.

## Posture Criteria

All thresholds are initial MVP defaults. Store them in each rule class so they are easy to tune after testing real videos.

### Squat

Recommended camera angle: side view first.

- Standing state: knee angle is at least `160` degrees.
- Bottom state: knee angle enters `70..110` degrees.
- Depth failure: minimum knee angle during the rep stays above `120` degrees.
- Torso warning: shoulder-hip-ankle angle drops below `145` degrees during the descent.
- Rep completion: state changes from `standing` to `bottom` to `standing`.
- Valid rep: completed state transition and no hard failure.

### Push-Up

Recommended camera angle: side view.

- Up state: elbow angle is at least `155` degrees.
- Down state: elbow angle enters `70..100` degrees.
- Depth failure: minimum elbow angle during the rep stays above `105` degrees.
- Body-line failure: shoulder-hip-ankle angle is below `160` degrees for more than `30%` of frames in the rep.
- Rep completion: state changes from `up` to `down` to `up`.

### Plank

Recommended camera angle: side view.

- Body-line valid: shoulder-hip-ankle angle is at least `160` degrees.
- Elbow placement valid: elbow is horizontally close to the shoulder in normalized coordinates.
- Hold success: valid posture is maintained for at least `70%` of analyzed frames.
- Feedback reasons: hips too low, hips too high, shoulder/elbow misalignment, landmark confidence too low.

## Landmark Data Model

Use world landmarks for 3D replay and normalized image landmarks for overlay.

```kotlin
enum class LandmarkName {
    NOSE,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
    LEFT_HEEL,
    RIGHT_HEEL,
    LEFT_FOOT_INDEX,
    RIGHT_FOOT_INDEX
}

data class PoseLandmark(
    val name: LandmarkName,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

data class PoseFrame(
    val timestampMs: Long,
    val landmarks: Map<LandmarkName, PoseLandmark>
)
```

Normalize each frame around the hip center and scale by shoulder width:

```text
hipCenter = midpoint(leftHip, rightHip)
shoulderWidth = distance(leftShoulder, rightShoulder)
normalized = (landmark - hipCenter) / shoulderWidth
```

Skip rule evaluation for a frame when required landmarks have visibility below `0.55`.

## Task 1: Scaffold Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/healthtrainer/MainActivity.kt`

- [ ] **Step 1: Create Gradle settings**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HealthTrainer"
include(":app")
```

- [ ] **Step 2: Create root Gradle build file**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

- [ ] **Step 3: Create app Gradle build file**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.healthtrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.healthtrainer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui:1.7.7")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mediapipe:tasks-vision:0.10.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
}
```

- [ ] **Step 4: Add camera permission manifest**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:label="Health Trainer"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 5: Build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Add Pose Model and Pose Wrapper

**Files:**
- Create: `app/src/main/assets/pose_landmarker_lite.task`
- Create: `app/src/main/java/com/healthtrainer/pose/PoseLandmark.kt`
- Create: `app/src/main/java/com/healthtrainer/pose/PoseFrame.kt`
- Create: `app/src/main/java/com/healthtrainer/pose/PoseLandmarkerHelper.kt`

- [ ] **Step 1: Download the MediaPipe model bundle**

Run:

```bash
mkdir -p app/src/main/assets
curl -L "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task" -o app/src/main/assets/pose_landmarker_lite.task
```

Expected: `app/src/main/assets/pose_landmarker_lite.task` exists and is larger than `1 MB`.

- [ ] **Step 2: Define app landmark types**

Use the `LandmarkName`, `PoseLandmark`, and `PoseFrame` model shown in the Landmark Data Model section.

- [ ] **Step 3: Initialize MediaPipe in live stream mode**

`PoseLandmarkerHelper` should use:

```kotlin
val baseOptions = BaseOptions.builder()
    .setModelAssetPath("pose_landmarker_lite.task")
    .build()

val options = PoseLandmarker.PoseLandmarkerOptions.builder()
    .setBaseOptions(baseOptions)
    .setRunningMode(RunningMode.LIVE_STREAM)
    .setNumPoses(1)
    .setMinPoseDetectionConfidence(0.5f)
    .setMinPosePresenceConfidence(0.5f)
    .setMinTrackingConfidence(0.5f)
    .setResultListener(resultListener)
    .setErrorListener(errorListener)
    .build()
```

- [ ] **Step 4: Verify pose output on device**

Run the debug app on a physical Android device and print the number of world landmarks per detected pose.

Expected: when one full body is visible, each pose has `33` landmarks.

## Task 3: Geometry and Normalization

**Files:**
- Create: `app/src/main/java/com/healthtrainer/geometry/AngleCalculator.kt`
- Create: `app/src/main/java/com/healthtrainer/pose/LandmarkNormalizer.kt`
- Create: `app/src/test/java/com/healthtrainer/geometry/AngleCalculatorTest.kt`

- [ ] **Step 1: Write angle tests**

```kotlin
@Test
fun rightAngle_returns90Degrees() {
    val angle = AngleCalculator.angleDegrees(
        Point3(1f, 0f, 0f),
        Point3(0f, 0f, 0f),
        Point3(0f, 1f, 0f)
    )

    assertThat(angle).isWithin(0.5f).of(90f)
}

@Test
fun straightLine_returns180Degrees() {
    val angle = AngleCalculator.angleDegrees(
        Point3(-1f, 0f, 0f),
        Point3(0f, 0f, 0f),
        Point3(1f, 0f, 0f)
    )

    assertThat(angle).isWithin(0.5f).of(180f)
}
```

- [ ] **Step 2: Implement geometry utilities**

```kotlin
data class Point3(val x: Float, val y: Float, val z: Float)

object AngleCalculator {
    fun angleDegrees(a: Point3, b: Point3, c: Point3): Float {
        val ab = Point3(a.x - b.x, a.y - b.y, a.z - b.z)
        val cb = Point3(c.x - b.x, c.y - b.y, c.z - b.z)
        val dot = ab.x * cb.x + ab.y * cb.y + ab.z * cb.z
        val abLength = kotlin.math.sqrt(ab.x * ab.x + ab.y * ab.y + ab.z * ab.z)
        val cbLength = kotlin.math.sqrt(cb.x * cb.x + cb.y * cb.y + cb.z * cb.z)
        val cosine = (dot / (abLength * cbLength)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosine).toDouble()).toFloat()
    }
}
```

- [ ] **Step 3: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthtrainer.geometry.AngleCalculatorTest"
```

Expected: both tests pass.

## Task 4: Exercise Rule Engine

**Files:**
- Create: `app/src/main/java/com/healthtrainer/exercise/ExerciseType.kt`
- Create: `app/src/main/java/com/healthtrainer/exercise/ExerciseFeedback.kt`
- Create: `app/src/main/java/com/healthtrainer/exercise/ExerciseRule.kt`
- Create: `app/src/main/java/com/healthtrainer/exercise/SquatRule.kt`
- Create: `app/src/main/java/com/healthtrainer/exercise/PushUpRule.kt`
- Create: `app/src/main/java/com/healthtrainer/exercise/PlankRule.kt`
- Create: `app/src/test/java/com/healthtrainer/exercise/SquatRuleTest.kt`
- Create: `app/src/test/java/com/healthtrainer/exercise/PushUpRuleTest.kt`
- Create: `app/src/test/java/com/healthtrainer/exercise/PlankRuleTest.kt`

- [ ] **Step 1: Define rule result types**

```kotlin
enum class ExerciseType {
    SQUAT,
    PUSH_UP,
    PLANK
}

enum class FeedbackCode {
    LOW_CONFIDENCE,
    SQUAT_DEPTH_NOT_ENOUGH,
    SQUAT_TORSO_LEAN,
    PUSH_UP_DEPTH_NOT_ENOUGH,
    PUSH_UP_BODY_LINE_BROKEN,
    PLANK_HIPS_LOW,
    PLANK_HIPS_HIGH,
    PLANK_ELBOW_MISALIGNED
}

enum class MovementPhase {
    READY,
    TOP,
    BOTTOM,
    HOLD,
    UNKNOWN
}

data class ExerciseFeedback(
    val phase: MovementPhase,
    val hardFailures: Set<FeedbackCode>,
    val softWarnings: Set<FeedbackCode>,
    val metrics: Map<String, Float>
)

interface ExerciseRule {
    fun evaluate(frame: PoseFrame): ExerciseFeedback
}
```

- [ ] **Step 2: Implement squat rule**

Use the side-view criteria from the Posture Criteria section. Required landmarks are hip, knee, ankle, and shoulder for the side closest to the camera. Use the average of left and right angles if both sides have visibility `>= 0.55`.

- [ ] **Step 3: Implement push-up rule**

Use elbow angle for top/bottom phase and shoulder-hip-ankle angle for body-line failure. Evaluate both sides when visible, otherwise use the visible side.

- [ ] **Step 4: Implement plank rule**

Use shoulder-hip-ankle angle for body-line validity. Use shoulder and elbow coordinates to detect whether the elbow is underneath the shoulder within normalized horizontal distance `0.25`.

- [ ] **Step 5: Test synthetic frames**

Each rule test should construct synthetic `PoseFrame` values. Include these assertions:

```kotlin
assertThat(result.phase).isEqualTo(MovementPhase.BOTTOM)
assertThat(result.hardFailures).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
assertThat(result.hardFailures).contains(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
assertThat(result.softWarnings).contains(FeedbackCode.PLANK_ELBOW_MISALIGNED)
```

- [ ] **Step 6: Run rule tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthtrainer.exercise.*"
```

Expected: squat, push-up, and plank rule tests pass.

## Task 5: Rep, Set, and Failure Tracking

**Files:**
- Create: `app/src/main/java/com/healthtrainer/exercise/ExerciseSession.kt`
- Create: `app/src/main/java/com/healthtrainer/tracker/RepStateMachine.kt`
- Create: `app/src/main/java/com/healthtrainer/tracker/SetTracker.kt`
- Create: `app/src/test/java/com/healthtrainer/tracker/RepStateMachineTest.kt`
- Create: `app/src/test/java/com/healthtrainer/tracker/SetTrackerTest.kt`

- [ ] **Step 1: Define session records**

```kotlin
data class RepRecord(
    val setNo: Int,
    val repNo: Int,
    val valid: Boolean,
    val failures: Set<FeedbackCode>,
    val startTimestampMs: Long,
    val endTimestampMs: Long
)

data class SetRecord(
    val setNo: Int,
    val reps: List<RepRecord>
)

data class ExerciseSession(
    val exerciseType: ExerciseType,
    val startedAtMs: Long,
    val sets: List<SetRecord>
)
```

- [ ] **Step 2: Implement rep state transition**

For squat and push-up, count one rep only after the sequence completes:

```text
TOP -> BOTTOM -> TOP
```

For plank, create one hold record when the user stops the timer.

- [ ] **Step 3: Aggregate failures over a rep**

A rep is invalid when any hard failure appears in the completed rep window. For push-up body-line failure, invalidate the rep when broken frames exceed `30%` of visible frames.

- [ ] **Step 4: Test invalid rep indexing**

Test with a synthetic sequence where the second rep fails depth:

```kotlin
assertThat(records[1].setNo).isEqualTo(1)
assertThat(records[1].repNo).isEqualTo(2)
assertThat(records[1].valid).isFalse()
assertThat(records[1].failures).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
```

- [ ] **Step 5: Run tracker tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthtrainer.tracker.*"
```

Expected: rep completion and invalid rep indexing tests pass.

## Task 6: Camera, Overlay, and Real-Time UI

**Files:**
- Create: `app/src/main/java/com/healthtrainer/camera/CameraPreview.kt`
- Create: `app/src/main/java/com/healthtrainer/ui/ExerciseScreen.kt`
- Create: `app/src/main/java/com/healthtrainer/ui/SkeletonOverlay.kt`
- Modify: `app/src/main/java/com/healthtrainer/MainActivity.kt`

- [ ] **Step 1: Build camera preview**

Use CameraX `PreviewView` inside Compose through `AndroidView`. Bind `Preview` and `ImageAnalysis` to the activity lifecycle. Send frames to `PoseLandmarkerHelper`.

- [ ] **Step 2: Draw skeleton overlay**

Draw circles for joints and lines for bone connections. Use green for valid posture, yellow for soft warnings, and red for hard failures.

- [ ] **Step 3: Create exercise screen**

Show:

- Exercise selector for squat, push-up, plank.
- Live camera preview.
- Skeleton overlay.
- Current rep count.
- Current feedback text.
- Start set and end set buttons.

- [ ] **Step 4: Verify on device**

Run:

```bash
./gradlew :app:installDebug
```

Expected: physical device opens the app, shows camera preview after permission grant, and displays skeleton overlay when one person is visible.

## Task 7: 3D Skeleton Replay

**Files:**
- Create: `app/src/main/java/com/healthtrainer/replay/SkeletonReplayFrame.kt`
- Create: `app/src/main/java/com/healthtrainer/replay/SkeletonReplayStore.kt`
- Create: `app/src/main/java/com/healthtrainer/replay/Skeleton3DViewer.kt`
- Create: `app/src/main/java/com/healthtrainer/ui/ResultScreen.kt`

- [ ] **Step 1: Save replay frames**

Store normalized world landmarks with timestamp and current rep index:

```kotlin
data class SkeletonReplayFrame(
    val timestampMs: Long,
    val setNo: Int,
    val repNo: Int,
    val landmarks: Map<LandmarkName, PoseLandmark>,
    val failures: Set<FeedbackCode>
)
```

- [ ] **Step 2: Implement local replay store**

Use Kotlin serialization to save each finished session to app-private storage as JSON. Name files with `exerciseType-startedAtMs.json`.

- [ ] **Step 3: Render 3D skeleton**

First implementation can use native Compose Canvas with simple pseudo-3D projection:

```text
screenX = centerX + x * scale + z * depthScale
screenY = centerY - y * scale
```

Add a horizontal slider for frame index. Failed joints and failed bone segments should render red for frames with failure codes.

- [ ] **Step 4: Result screen**

Show:

- Total reps.
- Successful reps.
- Invalid rep list grouped by set.
- 3D replay slider.
- Metric readout for the selected frame, such as knee angle or elbow angle.

- [ ] **Step 5: Manual verification**

Record a short squat set with one deliberately shallow rep.

Expected: result screen lists `1세트 2회차` as invalid and the 3D replay highlights the failed rep frames.

## Task 8: Documentation and Demo Preparation

**Files:**
- Modify: `READ.md`
- Modify: `docs/plan.md`
- Create: `docs/demo-script.md`

- [ ] **Step 1: Add demo script**

Create `docs/demo-script.md` with this flow:

```markdown
# Demo Script

1. 앱 실행 후 스쿼트를 선택한다.
2. 측면 카메라 구도로 선다.
3. 정상 스쿼트 1회, 깊이가 부족한 스쿼트 1회, 정상 스쿼트 1회를 수행한다.
4. 결과 화면에서 `1세트 2회차` 실패 기록을 확인한다.
5. 3D 리플레이에서 실패 구간이 빨간색으로 표시되는지 보여준다.
6. 푸쉬업 또는 플랭크 화면으로 전환해 같은 rule engine이 다른 운동에도 적용되는 구조를 설명한다.
```

- [ ] **Step 2: Final test command**

Run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

## Risks and Mitigations

- Camera angle sensitivity: start with side-view-only guidance and document supported camera angle.
- Landmark noise: skip low-confidence frames and smooth landmarks with a 5-frame moving average.
- Body differences: normalize by hip center and shoulder width; add calibration later.
- Real 3D accuracy: present 3D replay as posture visualization, not medical-grade motion capture.
- Flutter complexity: keep Android Native as MVP; add Flutter wrapper only after core logic is stable.
- Overconfident posture claims: phrase output as form signals and attention points, not medical advice or guaranteed trainer feedback.
- Dataset mismatch: use public datasets for baseline experiments, then add a small self-recorded demo set under the same camera conditions used in the final presentation.

## Data and Model Baseline

Use public datasets to prove that landmark-based exercise modeling is feasible, but do not depend on them for the live demo. Public data often differs in camera angle, lighting, subject body type, and labeling policy.

Recommended baseline path:

1. Use a public squat or push-up dataset to train a small landmark-sequence classifier.
2. Convert every sample into the same feature format used by the app: normalized landmarks plus selected joint angles.
3. Train a compact model such as LSTM, TCN, 1D CNN, or a small Transformer.
4. Export the model to TensorFlow Lite only if it improves the demo.
5. Keep rule-based evaluation active even when the model is present.

For the final presentation, collect a small controlled validation set:

- 5 normal squat clips.
- 5 shallow squat clips.
- 5 normal push-up clips.
- 5 shallow or body-line-broken push-up clips.
- 3 plank clips with valid hold and 3 with visible hip-position issues.

This small dataset is enough to tune thresholds and show before/after behavior without pretending to solve general-purpose fitness coaching.

## References

- MediaPipe Pose Landmarker: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker
- MediaPipe Android guide: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android
- ML Kit Pose Detection: https://developers.google.com/ml-kit/vision/pose-detection/android
- TensorFlow Lite Pose Estimation: https://www.tensorflow.org/lite/examples/pose_estimation/overview
