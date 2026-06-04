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

// :core is pure Kotlin/JVM with zero Android dependencies. Its unit tests run with only a
// JDK (./gradlew :core:test) — this is where the testable domain logic lives.
include(":core")

// :app is an Android module (CameraX, MediaPipe, Compose). The Android Gradle Plugin cannot be
// configured without an Android SDK, which would otherwise break :core:test on SDK-less machines
// (CI that only validates domain logic). So include :app only when an SDK is actually discoverable.
// Detection order — covers CI, a plain shell, AND Android Studio:
//   1. ANDROID_HOME / ANDROID_SDK_ROOT env vars
//   2. local.properties `sdk.dir`  (what Android Studio writes on first open — the common case)
//   3. the default macOS SDK location (~/Library/Android/sdk)
val androidSdkDir: String? = sequenceOf(
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
    file("local.properties").takeIf { it.exists() }?.let { lp ->
        java.util.Properties().apply { lp.inputStream().use { load(it) } }.getProperty("sdk.dir")
    },
    "${System.getProperty("user.home")}/Library/Android/sdk",
).filterNotNull().firstOrNull { file(it).exists() }

if (androidSdkDir != null) {
    include(":app")
} else {
    println("[HealthTrainer] No Android SDK found (env / local.properties / default path) — :app excluded; building :core only.")
}
