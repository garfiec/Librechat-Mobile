plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.settings"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:settings:ui"))
            implementation(project(":core:network"))
            implementation(project(":core:logging"))
            implementation(project(":core:notifications"))
            implementation(libs.coil3.network.ktor)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
