package com.healthtrainer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtrainer.app.camera.CameraPreview
import com.healthtrainer.app.MainViewModel
import com.healthtrainer.app.pose.PoseLandmarkerHelper
import com.healthtrainer.core.exercise.ExerciseType

/**
 * Top-level live training screen: exercise selector, the camera preview with the skeleton overlay, the
 * live rep/hold indicator, the live feedback text, and Start/End-set + Finish controls.
 *
 * Pure presentation: it observes the single immutable [ExerciseUiState] and hoists every event (chip
 * select, set start/end, finish) up to [MainViewModel]. No business logic, no `when(exerciseType)` and
 * no `== ExerciseType.PLANK` branch lives here — the hold indicator reads [ExerciseUiState.isHold] and
 * the live message is derived by the state. The selector iterates [ExerciseType.entries] and asks
 * [ExerciseUiText] for each label, so a new exercise needs no edit to this file.
 *
 * NOTE (requires device): the camera preview, overlay rendering, and live updates are unverified on an
 * SDK-less machine. The composable structure and the ViewModel wiring are inspectable.
 *
 * @param onFinish invoked after the session is built (navigates to the result screen).
 */
@Composable
fun ExerciseScreen(
    viewModel: MainViewModel,
    helper: PoseLandmarkerHelper,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.uiState

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        ExerciseSelector(
            selected = state.selectedExercise,
            enabled = !state.isSetActive,
            onSelect = viewModel::selectExercise,
        )

        // Camera preview + skeleton overlay stacked.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
        ) {
            CameraPreview(helper = helper, modifier = Modifier.fillMaxSize())
            SkeletonOverlay(
                landmarks = state.overlayLandmarks,
                feedback = state.liveFeedback,
                modifier = Modifier.fillMaxSize(),
            )
        }

        LiveStats(text = state.statsLine)
        LiveFeedbackText(message = state.liveMessage, color = state.overlayColor)

        SetControls(
            isSetActive = state.isSetActive,
            onStart = viewModel::startSet,
            onEnd = viewModel::endSet,
            onFinish = {
                viewModel.finishSession()
                onFinish()
            },
        )
    }
}

@Composable
private fun ExerciseSelector(
    selected: ExerciseType,
    enabled: Boolean,
    onSelect: (ExerciseType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Data-driven over the registry's types; the label comes from the presentation catalog.
        ExerciseType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                enabled = enabled,
                onClick = { onSelect(type) },
                label = { Text(ExerciseUiText.label(type)) },
            )
        }
    }
}

@Composable
private fun LiveStats(text: String) {
    Text(
        text = text,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun LiveFeedbackText(message: String, color: Color) {
    Text(
        text = message,
        color = color,
        fontSize = 18.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun SetControls(
    isSetActive: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSetActive) {
            Button(onClick = onEnd, modifier = Modifier.weight(1f)) { Text("세트 종료") }
        } else {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("세트 시작") }
        }
        Button(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("운동 완료") }
    }
}
