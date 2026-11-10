plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.files"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":core:logging"))
            implementation(libs.kermit)
            implementation(libs.coil3.compose)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
