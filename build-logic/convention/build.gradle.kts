plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "librechat.mobile.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "librechat.mobile.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "librechat.mobile.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "librechat.mobile.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "librechat.mobile.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidKoin") {
            id = "librechat.mobile.koin"
            implementationClass = "AndroidKoinConventionPlugin"
        }
        register("kmpWeb") {
            id = "librechat.kmp.web"
            implementationClass = "KmpWebConventionPlugin"
        }
        register("kmpFeatureUi") {
            id = "librechat.kmp.feature.ui"
            implementationClass = "KmpFeatureUiConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "librechat.kotlin.serialization"
            implementationClass = "KotlinSerializationConventionPlugin"
        }
        register("detekt") {
            id = "librechat.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("kmpLibrary") {
            id = "librechat.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "librechat.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpKoin") {
            id = "librechat.kmp.koin"
            implementationClass = "KmpKoinConventionPlugin"
        }
        register("kmpRoom") {
            id = "librechat.kmp.room"
            implementationClass = "KmpRoomConventionPlugin"
        }
        register("kmpFeature") {
            id = "librechat.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
