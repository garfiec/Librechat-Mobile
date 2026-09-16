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
            // Ship the .so files exactly as their AARs publish them, symbols and all.
            //
            // We compile no native code — every .so here comes prebuilt from Maven
            // (androidx sqlite-bundled, datastore, graphics-path). Stripping them needs an
            // NDK, and AGP silently skips it when none is installed: GitHub's runners have
            // one, F-Droid's buildserver image does not, so the same tag produced different
            // bytes depending on where it was built. That breaks reproducible-build
            // verification, which is how our developer-signed APK gets published.
            //
            // Not stripping removes the toolchain from the path entirely: the packaged file
            // is byte-for-byte the artifact Maven serves, so every environment agrees. The
            // alternative — pinning one NDK in CI and in the fdroiddata recipe — keeps the
            // APK ~577 KB smaller but makes reproducibility depend on two pins never
            // drifting, and strip output can itself vary by NDK version.
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
