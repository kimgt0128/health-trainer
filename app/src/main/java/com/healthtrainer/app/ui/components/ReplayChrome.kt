package com.healthtrainer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue

/**
 * §4 ReplayCanvas — the dark "camera" chrome for the replay: a vertical camera-gradient surface with
 * a white tracking pill at the top-left (ink 12sp/720), wrapping the skeleton content passed in.
 *
 * The skeleton itself (Canvas + stable projection) is the [content] — this only restyles the chrome
 * (design-system §0.1 keeps the report monochrome; the skeleton's own normal/warning color is the one
 * allowed exception and lives in SkeletonGraphics).
 *
 * @param pill the tracking-pill text (e.g. "1세트 2회차").
 */
@Composable
fun ReplayCanvas(
    pill: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.radCard)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Hue.camera, Hue.camera2))),
    ) {
        Box(modifier = Modifier.fillMaxSize()) { content() }

        // Tracking pill (white, ink label) — top-left.
        Box(
            modifier = Modifier
                .padding(Dimens.cardPad)
                .clip(RoundedCornerShape(50))
                .background(Hue.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = pill, color = Hue.ink, fontSize = 12.sp, fontWeight = FontWeight(720))
        }
    }
}
