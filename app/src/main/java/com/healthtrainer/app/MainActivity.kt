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
import com.healthtrainer.app.ui.ResultScreen
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    HealthTrainerApp(viewModel)
                }
            }
        }
    }
}

private enum class Screen { EXERCISE, RESULT, REPLAY }

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
            val session = viewModel.session
            if (session == null) {
                screen = Screen.EXERCISE
            } else {
                ResultScreen(
                    session = session,
                    replayFrames = viewModel.replayFrames,
                    onReplay = { screen = Screen.REPLAY },
                    onRestart = {
                        viewModel.resetSession()
                        screen = Screen.EXERCISE
                    },
                )
            }
        }

        Screen.REPLAY -> Skeleton3DViewer(
            frames = viewModel.replayFrames,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CameraPermissionRationale(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Button(onClick = onRequest) { Text("카메라 권한을 허용해 주세요") }
    }
}
