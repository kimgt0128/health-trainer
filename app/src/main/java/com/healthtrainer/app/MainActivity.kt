package com.healthtrainer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.healthtrainer.app.pose.PoseLandmarkerHelper
import com.healthtrainer.app.replay.SkeletonReplayStore
import com.healthtrainer.app.ui.ExerciseScreen
import com.healthtrainer.app.ui.ReportPresentation
import com.healthtrainer.app.ui.ResultScreen
import com.healthtrainer.app.ui.SetDetailScreen
import com.healthtrainer.app.replay.Skeleton3DViewer

/**
 * App entry point. Hosts the [MainViewModel], requests the CAMERA permission, wires the
 * [PoseLandmarkerHelper] to the ViewModel's per-frame entry point, and navigates between the live
 * [ExerciseScreen], the [ResultScreen], and the [Skeleton3DViewer].
 *
 * Navigation is a tiny in-Activity enum (no nav library this slice — skeleton scope).
 *
 * NOTE (requires device): the permission flow, MediaPipe helper, camera, and all rendering are
 * unverified on an SDK-less machine. Only the structure + `:core`/helper wiring is inspectable.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = com.healthtrainer.app.ui.theme.Hue.bg) {
                    HealthTrainerApp(viewModel)
                }
            }
        }
    }
}

private enum class Screen { EXERCISE, RESULT, SET_DETAIL, REPLAY }

@Composable
private fun HealthTrainerApp(viewModel: MainViewModel) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // MediaPipe helper: results are routed straight into the ViewModel's per-frame pipeline.
    // requires device: setup()/inference unverified here.
    val helper = remember {
        PoseLandmarkerHelper(
            context = context,
            onResult = { result, ts -> viewModel.onPoseResult(result, ts) },
            onError = { /* requires device: surface inference errors in UI later */ },
        ).also { it.setup() }
    }
    val replayStore = remember { SkeletonReplayStore(context) }

    var screen by remember { mutableStateOf(Screen.EXERCISE) }
    // The set the user drilled into (drives SET_DETAIL + which rep the REPLAY shows). 1-based.
    var selectedSetNo by remember { mutableIntStateOf(0) }

    if (!hasCameraPermission) {
        CameraPermissionRationale(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    when (screen) {
        Screen.EXERCISE -> ExerciseScreen(
            viewModel = viewModel,
            helper = helper,
            onFinish = {
                val session = viewModel.session
                if (session != null) {
                    // Persist replay frames for this session. requires device: file IO unverified.
                    replayStore.save(session.exerciseType, session.startedAtMs, viewModel.replayFrames)
                }
                screen = Screen.RESULT
            },
        )

        Screen.RESULT -> {
            val summary = viewModel.summary
            if (summary == null) {
                screen = Screen.EXERCISE
            } else {
                ResultScreen(
                    summary = summary,
                    onOpenSet = { setNo ->
                        selectedSetNo = setNo
                        screen = Screen.SET_DETAIL
                    },
                    onRestart = {
                        viewModel.resetSession()
                        screen = Screen.EXERCISE
                    },
                )
            }
        }

        Screen.SET_DETAIL -> {
            val summary = viewModel.summary
            if (summary == null) {
                screen = Screen.EXERCISE
            } else {
                SetDetailScreen(
                    summary = summary,
                    setNo = selectedSetNo,
                    onBack = { screen = Screen.RESULT },
                    onReplay = { screen = Screen.REPLAY },
                )
            }
        }

        Screen.REPLAY -> {
            val summary = viewModel.summary
            // The rep to replay = this set's problem rep (first invalid, else lowest-scoring).
            val repScore = summary?.sets
                ?.firstOrNull { it.setNo == selectedSetNo }
                ?.let { ReportPresentation.repToReplay(it) }
            Skeleton3DViewer(
                frames = replayFramesForSet(viewModel.replayFrames, selectedSetNo, repScore?.repNo),
                repScore = repScore,
                onBack = { screen = Screen.SET_DETAIL },
                onSummary = { screen = Screen.RESULT },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Narrow the captured replay frames to the set (and, if known, the specific rep) being replayed, so
 * the viewer scrubs just that rep's motion. Falls back to the whole buffer when nothing matches (so
 * the replay is never empty if frames exist).
 *
 * Invariant: [SkeletonReplayFrame.setNo]/[repNo] and the [repNo] picked from `SetScore`
 * ([ReportPresentation.repToReplay]) both derive from the SAME `SetTracker` rep numbering, so they
 * align by construction. The `ifEmpty { all }` fallback only guards a genuinely missing capture — it
 * is not meant to paper over a numbering drift; if these two producers ever diverge, fix the source.
 */
private fun replayFramesForSet(
    all: List<com.healthtrainer.app.replay.SkeletonReplayFrame>,
    setNo: Int,
    repNo: Int?,
): List<com.healthtrainer.app.replay.SkeletonReplayFrame> {
    val byRep = if (repNo != null) all.filter { it.setNo == setNo && it.repNo == repNo } else emptyList()
    if (byRep.isNotEmpty()) return byRep
    val bySet = all.filter { it.setNo == setNo }
    return bySet.ifEmpty { all }
}

@Composable
private fun CameraPermissionRationale(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Button(onClick = onRequest) { Text("카메라 권한을 허용해 주세요") }
    }
}
