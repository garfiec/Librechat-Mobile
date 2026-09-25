plugins {
    id("librechat.mobile.application")
    id("librechat.mobile.compose")
    id("librechat.mobile.koin")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.garfiec.librechat"

    defaultConfig {
        applicationId = "com.garfiec.librechat"
    }

    buildTypes {
        debug {
            // Release must keep the bare id — Obtainium tracks updates by package name.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Do not strip: stripping needs an NDK and AGP silently skips it when none is
            // installed, so CI (has one) and F-Droid's buildserver image (has none) would
            // package different .so bytes for the same tag and fail reproducible-build
            // verification. We compile no native code; every .so comes prebuilt from Maven.
            // Costs ~577 KB — do not reclaim it by pinning an NDK instead: two pins that
            // must never drift, and strip output can itself vary by NDK version.
            keepDebugSymbols += setOf("**/*.so")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:conversations"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:agents"))
    implementation(project(":feature:files"))
    implementation(project(":feature:schedules"))
    implementation(project(":feature:skills"))

    implementation(libs.activity.compose)
    implementation(libs.navigation3.ui.kmp)
    implementation(libs.lifecycle.viewmodel.navigation3.kmp)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.viewmodel.navigation)
    implementation(libs.compose.material3.wsc)
    implementation(libs.bundles.lifecycle)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor)
    implementation(libs.coil3.svg)
    implementation(libs.kermit)

    debugImplementation(libs.leakcanary)

    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
