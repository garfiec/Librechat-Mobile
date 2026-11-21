import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("librechat.kmp.feature.ui")
}

android {
    testOptions {
        // The Robolectric Compose tests need the merged manifest (ui-test-manifest's activity) and
        // the Compose resources on the host-JVM unit-test classpath.
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach { maxHeapSize = "2g" }

kotlin {
    // nonAndroidMain: the iOS copies of the forked pure-Compose actuals (MessageBubble,
    // MarkdownContent, keyboardRising), shared with the browser.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                group("ios")
                withWasmJs()
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.atomicfu)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil3.compose)
            implementation(libs.ktor.http)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer.coil3)
        }
        getByName("androidUnitTest").dependencies {
            // Compose UI tests on the JVM (Robolectric), for semantics and focus that CI must run.
            implementation(libs.robolectric)
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
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
        iosTest.dependencies {
            implementation(libs.compose.multiplatform.ui.test)
        }
    }
}
