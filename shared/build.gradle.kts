import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.compose")
    id("librechat.kmp.koin")
    id("librechat.kotlin.serialization")
    alias(libs.plugins.skie)
}

android {
    namespace = "com.garfiec.librechat.shared"
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Export core modules so iOS can see all public types through the single framework
            export(project(":core:common"))
            export(project(":core:model"))
            export(project(":core:network"))
            export(project(":core:data"))
            // TEMPORARY: Kotlin 2.4.10's Release optimization passes exceed the CI heap limit:
            // StaticInitializersOptimization, then DevirtualizationAnalysis's constraint graph.
            // Skipping these optimizations preserves correctness, with possible binary-size and
            // runtime costs. The static-initializer issue is KT-89519, fixed by KT-87570 starting
            // in Kotlin 2.5.0-Beta2; the devirtualization failure needs separate verification.
            // After a compiler upgrade, re-enable each phase only after the CI OOM canary links
            // within its usual time at the unchanged heap limit.
            if (buildType == NativeBuildType.RELEASE) {
                freeCompilerArgs += "-Xdisable-phases=" +
                    "RemoveRedundantCallsToStaticInitializersPhase,DevirtualizationAnalysis"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:model"))
            api(project(":core:network"))
            api(project(":core:data"))
            api(project(":core:ui"))
            implementation(project(":core:notifications"))
            implementation(project(":core:logging"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:chat"))
            implementation(project(":feature:conversations"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:agents"))
            implementation(project(":feature:files"))
            implementation(project(":feature:schedules"))
            implementation(project(":feature:skills"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.kermit)
            implementation(libs.navigation3.ui.kmp)
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.lifecycle.viewmodel.compose.kmp)
            implementation(libs.lifecycle.viewmodel.navigation3.kmp)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
            implementation(libs.coil3.svg)
        }
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

skie {
    features {
        // All features enabled by default:
        // - Sealed classes → Swift enums (onEnum(of:))
        // - Flow → AsyncSequence
        // - Suspend → async/await
        // No configuration needed for defaults
    }
}
