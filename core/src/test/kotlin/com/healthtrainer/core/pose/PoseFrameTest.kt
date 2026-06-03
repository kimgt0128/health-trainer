package com.healthtrainer.core.pose

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the landmark enum and the [PoseFrame.point] accessor that the normalizer and rule
 * engines use to read a landmark as a geometry [com.healthtrainer.core.geometry.Point3].
 */
class PoseFrameTest {

    @Test
    fun landmarkName_hasSeventeenEntries() {
        // The model is fixed at 17 landmarks per the health-trainer-conventions skill.
        assertThat(LandmarkName.entries).hasSize(17)
    }

    @Test
    fun point_presentLandmark_returnsItsCoordinates() {
        val frame = PoseFrame(
            timestampMs = 42L,
            landmarks = mapOf(
                LandmarkName.LEFT_HIP to PoseLandmark(LandmarkName.LEFT_HIP, 1f, 2f, 3f, 0.9f),
            ),
        )
        val p = frame.point(LandmarkName.LEFT_HIP)
        assertThat(p).isNotNull()
        assertThat(p!!.x).isWithin(1e-4f).of(1f)
        assertThat(p.y).isWithin(1e-4f).of(2f)
        assertThat(p.z).isWithin(1e-4f).of(3f)
    }

    @Test
    fun point_missingLandmark_returnsNull() {
        val frame = PoseFrame(timestampMs = 0L, landmarks = emptyMap())
        assertThat(frame.point(LandmarkName.NOSE)).isNull()
    }
}
