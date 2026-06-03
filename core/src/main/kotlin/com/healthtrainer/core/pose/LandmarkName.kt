package com.healthtrainer.core.pose

/**
 * The fixed set of body landmarks the app reasons about (17 entries).
 *
 * Defined verbatim per the health-trainer-conventions skill. The :app MediaPipe adapter maps the
 * upstream pose indices onto these names; :core never sees raw indices.
 */
enum class LandmarkName {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX
}
