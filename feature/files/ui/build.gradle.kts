plugins {
    id("librechat.kmp.feature.ui")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil3.compose)
            implementation(libs.kermit)
        }
    }
}
