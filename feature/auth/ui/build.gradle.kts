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

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lifecycle.runtime.compose.kmp)
            // QR code for the required 2FA setup screen, same library as Settings' enrollment.
            implementation(libs.qrose)
            // The server's terms are Markdown; same renderer as Settings' release notes.
            implementation(libs.markdown.renderer.m3)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}
