plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.conversations"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:conversations:ui"))
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
