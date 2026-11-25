plugins {
    id("librechat.kmp.feature.ui")
    // ProviderKeyFormState is saved across process death as JSON.
    id("librechat.kotlin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil3.compose)
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.qrose)
            // Release notes on What's new.
            implementation(libs.markdown.renderer.m3)
        }
    }
}
