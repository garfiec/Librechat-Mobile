plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.auth"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":core:brand")) // TCHAT: ServerPinning for ServerUrlViewModel
        }
        named("androidUnitTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.test)
        }
    }
}
