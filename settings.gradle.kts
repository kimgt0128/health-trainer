pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HealthTrainer"

// :core is pure Kotlin/JVM with zero Android dependencies. Its unit tests run with
// only a JDK (./gradlew :core:test) — this is where the testable domain logic lives.
include(":core")

// :app is an Android module (CameraX, MediaPipe, Compose). The Android Gradle Plugin
// cannot even be configured without an Android SDK, which would otherwise break
// :core:test on SDK-less machines (CI that only validates domain logic, this dev box).
// So include :app only when an SDK is actually present. Export ANDROID_HOME or
// ANDROID_SDK_ROOT (or add a local.properties sdk.dir) to build the app.
val androidSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (androidSdk != null && file(androidSdk).exists()) {
    include(":app")
} else {
    gradle.startParameter.let {
        println("[HealthTrainer] No Android SDK (ANDROID_HOME/ANDROID_SDK_ROOT) — :app excluded; building :core only.")
    }
}
