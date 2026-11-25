plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.files"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:files:ui"))
            implementation(project(":core:network"))
            implementation(project(":core:logging"))
            implementation(libs.kermit)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}

