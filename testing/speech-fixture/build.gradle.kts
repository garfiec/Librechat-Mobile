// A stand-in third-party speech provider (recognizer, TTS engine, recognize-speech activity) for
// scripts/verify-speech-services.py. Installed as its own APK on a no-GMS emulator so the app sees it
// the way it sees Whisper+ or RHVoice on a de-Googled ROM. Nothing depends on it; it never ships.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kover)
}

// compileSdk, targetSdk and the toolchain mirror build-logic's BuildConstants; bump them together.
android {
    namespace = "com.garfiec.librechat.testfixture.speech"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.garfiec.librechat.testfixture.speech"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
}

kotlin {
    jvmToolchain(21)
}
