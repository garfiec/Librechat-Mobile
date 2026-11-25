plugins {
    id("librechat.kmp.feature.ui")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Compose-native markdown for the skill detail body; the same renderer the chat feature uses.
            implementation(libs.markdown.renderer.m3)
            // Previews a skill file's raw bytes on the file screen.
            implementation(libs.coil3.compose)
        }
    }
}
