plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.compose")
    id("librechat.kmp.koin")
}

android {
    namespace = "com.garfiec.librechat.core.notifications"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:data"))
            implementation(project(":core:logging"))
            implementation(libs.kermit)
            implementation(libs.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.activity.compose)
            implementation(libs.work.runtime)
        }
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.android.test.core)
            implementation(libs.work.testing)
        }
    }
}
