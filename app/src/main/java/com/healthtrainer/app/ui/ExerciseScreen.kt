package com.healthtrainer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import com.healthtrainer.core.exercise.FeedbackCode

/**
 * Top-level live training screen: exercise selector (squat / push-up / plank), the camera preview
 * with the skeleton overlay, the live rep counter, the live feedback text, and Start/End-set +
 * Finish controls. Observes [MainViewModel] state.
 *
 * NOTE (requires device): the camera preview, overlay rendering, and live updates are unverified on
 * an SDK-less machine. The composable structure and the ViewModel wiring are inspectable.
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
    val selected = viewModel.selectedExercise
    val feedback = viewModel.liveFeedback
    val overlay = viewModel.overlayLandmarks
    val repCount = viewModel.repCount
    val isSetActive = viewModel.isSetActive

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        ExerciseSelector(
            selected = selected,
            enabled = !isSetActive,
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
                landmarks = overlay,
                feedback = feedback,
                modifier = Modifier.fillMaxSize(),
            )
        }

        LiveStats(repCount = repCount, isPlank = selected == ExerciseType.PLANK)
        LiveFeedbackText(viewModel = viewModel)

        SetControls(
            isSetActive = isSetActive,
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
        ExerciseType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                enabled = enabled,
                onClick = { onSelect(type) },
                label = { Text(exerciseLabel(type)) },
            )
        }
    }
}

@Composable
private fun LiveStats(repCount: Int, isPlank: Boolean) {
    // Plank has no rep count (single hold); show a hold indicator instead.
    val text = if (isPlank) "플랭크 유지 중" else "반복 횟수: $repCount"
    Text(
        text = text,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun LiveFeedbackText(viewModel: MainViewModel) {
    val feedback = viewModel.liveFeedback
    val color: Color = SkeletonGraphics.overlayColor(feedback)

    // Show the most relevant live message: hard failure > soft warning > low-confidence > OK.
    val message = when {
        feedback == null -> "자세를 인식하는 중..."
        feedback.hardFailures.isNotEmpty() ->
            feedback.hardFailures.joinToString("·") { FeedbackText.label(it) }
        (feedback.softWarnings - FeedbackCode.LOW_CONFIDENCE).isNotEmpty() ->
            (feedback.softWarnings - FeedbackCode.LOW_CONFIDENCE)
                .joinToString("·") { FeedbackText.label(it) }
        viewModel.isLowConfidence(feedback) -> FeedbackText.label(FeedbackCode.LOW_CONFIDENCE)
        else -> "좋은 자세입니다"
    }

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

private fun exerciseLabel(type: ExerciseType): String = when (type) {
    ExerciseType.SQUAT -> "스쿼트"
    ExerciseType.PUSH_UP -> "푸쉬업"
    ExerciseType.PLANK -> "플랭크"
}
