package com.healthtrainer.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single source of the result-report visual language (rules/design-system.md §1–3): the
 * monochrome color tokens, the type scale, and the radius/spacing scale. Screens and components
 * reference these — NO scattered hex / sp / dp literals in the UI files.
 *
 * Monochrome by law (§0.1): hue never carries information; emphasis is weight / fill / background
 * lightness / label. The ONE exception is the replay skeleton's normal-vs-warning color, which lives
 * in [com.healthtrainer.app.ui.SkeletonGraphics], not here.
 */

/** §1 color tokens — grayscale only. */
object Hue {
    val bg = Color(0xFFF7F7F5)         // screen background
    val surface = Color(0xFFFFFFFF)    // cards / sheets
    val ink = Color(0xFF101211)        // primary text / button fill / filled bars
    val ink2 = Color(0xFF2A2D2B)       // secondary text
    val muted = Color(0xFF737872)      // supporting text / eyebrow / axis labels
    val faint = Color(0xFF9DA39C)      // weaker text
    val soft = Color(0xFFF1F2F0)       // selected card/tab background / track
    val soft2 = Color(0xFFE9EBE8)      // empty-bar track
    val line = Color(0xFFE0E3DF)       // 1px borders / dividers
    val lineStrong = Color(0xFFCFD4CE) // emphasis border (selected card)
    val camera = Color(0xFF121614)     // replay dark-canvas gradient (top)
    val camera2 = Color(0xFF1E2420)    // replay dark-canvas gradient (bottom)
    val onCamera = Color(0xFFF2F3F1)   // text on the dark replay canvas
}

/**
 * §2 type scale. Each token is (sp, weight); color is applied at the call site from [Hue] per the
 * table so a token can be reused on light and dark surfaces.
 */
object Type {
    data class Spec(val size: androidx.compose.ui.unit.TextUnit, val weight: FontWeight)

    val score = Spec(76.sp, FontWeight(820))     // hero number
    val h1 = Spec(30.sp, FontWeight(780))        // screen title
    val h2 = Spec(26.sp, FontWeight(780))        // secondary title
    val statValue = Spec(20.sp, FontWeight(700)) // mini-stat value
    val body = Spec(15.sp, FontWeight(500))      // body
    val bodyStrong = Spec(15.sp, FontWeight(600))
    val bodySmall = Spec(14.sp, FontWeight(400))
    val eyebrow = Spec(13.sp, FontWeight(400))   // one line above a title
    val section = Spec(12.sp, FontWeight(780))   // section header (muted)
    val label = Spec(12.sp, FontWeight(600))     // stat / axis label
    val labelStrong = Spec(13.sp, FontWeight(600))
    val noteTitle = Spec(14.sp, FontWeight(600)) // coach-note title
    val noteBullet = Spec(13.sp, FontWeight(400))// coach-note bullet row
    val axisTick = Spec(11.sp, FontWeight(600))  // trend-chart end labels
}

/** §3 shape / spacing. */
object Dimens {
    // radii
    val radCard = 14.dp
    val radChip = 11.dp
    val radTab = 9.dp
    val radButton = 12.dp

    // borders
    val border = 1.dp

    // spacing
    val screenPad = 22.dp
    val cardPad = 14.dp
    val gap = 10.dp
    val gapSmall = 8.dp
    val gapLarge = 16.dp

    // components
    val buttonHeight = 52.dp
    val ghostSize = 38.dp
    val barTrack = 6.dp
    val metricLabelWidth = 72.dp
}
