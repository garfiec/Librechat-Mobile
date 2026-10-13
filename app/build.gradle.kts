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

    // `play` must keep its own applicationId: both channels are signed with the same key, so on one
    // package either would install over the other as an ordinary update. See docs/RELEASING.md
    // "Distribution channels".
    flavorDimensions += "channel"
    productFlavors {
        create("foss") {
            dimension = "channel"
            isDefault = true
            manifestPlaceholders["distributionChannel"] = "oss"
        }
        create("play") {
            dimension = "channel"
            applicationIdSuffix = ".play"
            manifestPlaceholders["distributionChannel"] = "play"
        }
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

    // AGP stores a Play-encrypted list of our dependencies in the APK signing block
    // (id 0x504B4453). fdroidserver's scanner rejects it outright -- it is in
    // APK_SIGNING_BLOCK_IDS alongside Frosting and Meituan -- and a signing block cannot be
    // whitelisted from the recipe the way a source path can with scanignore. F-Droid's own
    // rebuild is unsigned so it never has the block; only the developer-signed APK we publish
    // as the reproducible-build reference does, which is what gets scanned.
    // Only an APK is ever scanned by F-Droid, so the bundle (Play only) keeps the block: Play reads
    // it to warn about known-bad SDK versions.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    testOptions {
        // Robolectric needs the merged manifest on the unit-test classpath to see the flavor's
        // channel meta-data.
        unitTests.isIncludeAndroidResources = true
    }
}

// playRelease exists only on request, so `assembleRelease` yields exactly one APK: the open-source
// one the release workflow publishes and F-Droid rebuilds. Only play.yml produces a Play release.
val buildPlay = providers.gradleProperty("buildPlay").isPresent
androidComponents {
    beforeVariants(selector().withFlavor("channel" to "play").withBuildType("release")) {
        it.enable = buildPlay
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

    testImplementation(libs.robolectric)
    testImplementation(libs.android.test.core)

    androidTestImplementation(libs.compose.ui.test)
    // Pin espresso ≥3.7.0: the 3.5.x pulled in transitively by ui-test-junit4 injects
    // input via InputManager.getInstance, which no longer exists on API 36+.
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)

    constraints {
        // espresso 3.7.0 needs concurrent-futures 1.2.0, but under compose 1.12 the app's runtime
        // resolves 1.1.0, and AGP holds the test APK to the app's versions: the two are a hard
        // conflict that fails :app:lint. Raising the app's copy lets both agree.
        implementation(libs.androidx.concurrent.futures)
        implementation(libs.androidx.concurrent.futures.ktx)
    }
}
