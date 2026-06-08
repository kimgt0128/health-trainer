package com.healthtrainer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.camera.CameraPreview
import com.healthtrainer.app.MainViewModel
import com.healthtrainer.app.pose.PoseLandmarkerHelper
import com.healthtrainer.app.ui.components.PrimaryButton
import com.healthtrainer.app.ui.components.TokenText
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type
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
 * Design (design-system §0–3): this screen is NOT in the result wireframe, but it still obeys the
 * monochrome token set — selector / stats / feedback / controls draw from [Hue]/[Type]/[Dimens], not
 * default Material colors or stray hex/sp/dp. The ONE allowed hue exception is the skeleton overlay's
 * green/yellow/red tint (§0.1), which lives in [SkeletonGraphics] / [SkeletonOverlay]; the live feedback
 * *text* is monochrome ink (the colored skeleton already carries the good/warning/error signal).
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

    Column(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.screenPad)) {
        Spacer(Modifier.height(Dimens.gapLarge))
        TokenText("자세 추적", Type.eyebrow, Hue.muted)
        TokenText("${ExerciseUiText.label(state.selectedExercise)} 자세 확인", Type.h2, Hue.ink)

        Spacer(Modifier.height(Dimens.gap))
        ExerciseSelector(
            selected = state.selectedExercise,
            enabled = !state.isSetActive,
            onSelect = viewModel::selectExercise,
        )

        // Camera preview + skeleton overlay stacked inside the dark "camera" chrome.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = Dimens.gap)
                .clip(RoundedCornerShape(Dimens.radCard)),
        ) {
            CameraPreview(helper = helper, modifier = Modifier.fillMaxSize())
            SkeletonOverlay(
                landmarks = state.overlayLandmarks,
                feedback = state.liveFeedback,
                modifier = Modifier.fillMaxSize(),
            )
        }

        LiveStats(text = state.statsLine)
        Spacer(Modifier.height(Dimens.gapSmall))
        LiveFeedbackText(message = state.liveMessage)
        // Optional ASSIST line from the on-device form model (null unless a confident, non-`correct`
        // verdict exists for the latest rep). Rendered as a secondary, muted hint under the feedback.
        state.modelHint?.let {
            Spacer(Modifier.height(2.dp))
            ModelHintText(message = it)
        }

        Spacer(Modifier.height(Dimens.gap))
        SetControls(
            isSetActive = state.isSetActive,
            onStart = viewModel::startSet,
            onEnd = viewModel::endSet,
            onFinish = {
                viewModel.finishSession()
                onFinish()
            },
        )
        Spacer(Modifier.height(Dimens.gapLarge))
    }
}

/**
 * Exercise selector — a row of monochrome chips (no default Material `FilterChip`). The selected chip
 * uses the `soft` fill + `lineStrong` border (no hue change, §0.1); the rest are white with a 1px line.
 * Data-driven over [ExerciseType.entries]; the label comes from [ExerciseUiText] (open/closed).
 */
@Composable
private fun ExerciseSelector(
    selected: ExerciseType,
    enabled: Boolean,
    onSelect: (ExerciseType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
    ) {
        ExerciseType.entries.forEach { type ->
            val active = selected == type
            val shape = RoundedCornerShape(Dimens.radChip)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (active) Hue.soft else Hue.surface)
                    .border(BorderStroke(Dimens.border, if (active) Hue.lineStrong else Hue.line), shape)
                    .clickable(enabled = enabled) { onSelect(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                TokenText(
                    text = ExerciseUiText.label(type),
                    spec = if (active) Type.labelStrong else Type.label,
                    color = when {
                        active -> Hue.ink
                        enabled -> Hue.muted
                        else -> Hue.faint
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** The big live counter / hold indicator (statValue-ish; bumped to score weight for live legibility). */
@Composable
private fun LiveStats(text: String) {
    TokenText(text, Type.h2, Hue.ink, maxLines = 1, modifier = Modifier.fillMaxWidth())
}

/**
 * The live rule feedback line — monochrome ink (the colored skeleton overlay already carries the
 * good/warning/error signal, so the body text stays neutral per §0.1).
 */
@Composable
private fun LiveFeedbackText(message: String) {
    TokenText(message, Type.bodyStrong, Hue.ink2, keepAll = true, modifier = Modifier.fillMaxWidth())
}

/** The optional model assist hint — smaller + muted so it reads as supplementary to the rule feedback. */
@Composable
private fun ModelHintText(message: String) {
    TokenText("도움말 · $message", Type.label, Hue.muted, keepAll = true, modifier = Modifier.fillMaxWidth())
}

/**
 * Set controls — a monochrome primary action plus a secondary ghost-style action (no default Material
 * `Button`). Start/End is the primary (ink) action; Finish is the secondary outlined action.
 */
@Composable
private fun SetControls(
    isSetActive: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
    ) {
        PrimaryButton(
            text = if (isSetActive) "세트 종료" else "세트 시작",
            onClick = if (isSetActive) onEnd else onStart,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(text = "운동 완료", onClick = onFinish, modifier = Modifier.weight(1f))
    }
}

/** White outlined secondary action (design-system §Components: white bg, line border, ink text). */
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.radButton)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.buttonHeight)
            .clip(shape)
            .background(Hue.surface)
            .border(BorderStroke(Dimens.border, Hue.line), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TokenText(text, Type.bodyStrong, Hue.ink, maxLines = 1)
    }
}
