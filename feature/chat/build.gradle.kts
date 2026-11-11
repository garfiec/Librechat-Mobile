plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.chat"
    testOptions {
        // The Robolectric Compose tests need the merged manifest (ui-test-manifest's activity) and
        // the Compose resources on the host-JVM unit-test classpath.
        unitTests.isIncludeAndroidResources = true
    }
}

// One JVM runs the whole suite and MockK's inline mocks stay reachable across classes: 512 MB OOMs.
tasks.withType<Test>().configureEach { maxHeapSize = "2g" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":core:logging"))
            implementation(project(":core:notifications"))
            implementation(libs.atomicfu)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer.coil3)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.webkit)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.datasource)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.koin.test)
            // A real SseClient over a mocked transport, for tests of what the stream client and the
            // ViewModel do together — a stubbed event flow cannot show how a connection ends.
            implementation(libs.ktor.client.mock)
            // Compose UI tests on the JVM (Robolectric), for semantics and focus that CI must run.
            implementation(libs.robolectric)
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
        }
        iosTest.dependencies {
            implementation(libs.compose.multiplatform.ui.test)
        }
        named("androidInstrumentedTest").dependencies {
            implementation(libs.junit)
            implementation(libs.android.test.runner)
            implementation(libs.android.test.ext.junit)
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
            // Pin espresso ≥3.7.0: the 3.5.x pulled in transitively by ui-test-junit4 injects
            // input via InputManager.getInstance, which no longer exists on API 36+.
            implementation(libs.espresso.core)
        }
    }
}
