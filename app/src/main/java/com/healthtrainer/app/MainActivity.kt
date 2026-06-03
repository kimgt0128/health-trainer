package com.healthtrainer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * App entry point.
 *
 * The full exercise-selection / camera / result UI is built in the mvp-4-app-skeleton
 * slice. This is a minimal placeholder so the :app module is coherent from scaffolding on.
 *
 * NOTE: :app requires the Android SDK and a physical device. It is not built or run on
 * SDK-less machines — domain logic is verified in :core instead (./gradlew :core:test).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthTrainerApp()
        }
    }
}

@Composable
private fun HealthTrainerApp() {
    MaterialTheme {
        Surface {
            Text("Health Trainer") // replaced by ExerciseScreen in mvp-4
        }
    }
}
