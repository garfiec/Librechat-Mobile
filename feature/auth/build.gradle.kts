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
            implementation(project(":core:logging"))
            // QR code for the required 2FA setup screen, same library as Settings' enrollment.
            implementation(libs.qrose)
            // The server's terms are Markdown; same renderer as Settings' release notes.
            implementation(libs.markdown.renderer.m3)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.test)
        }
    }
}
