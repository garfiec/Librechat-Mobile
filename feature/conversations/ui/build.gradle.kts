plugins {
    id("librechat.kmp.feature.ui")
    // DrawerFilters is saved in the drawer's saved-state JSON.
    id("librechat.kotlin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lifecycle.runtime.compose.kmp)
        }
    }
}
