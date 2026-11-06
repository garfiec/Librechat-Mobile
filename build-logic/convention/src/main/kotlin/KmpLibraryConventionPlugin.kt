import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("librechat.detekt")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(BuildConstants.JVM_TOOLCHAIN_VERSION)
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-opt-in=kotlin.time.ExperimentalTime",
                    )
                }
                androidTarget()
                iosArm64()
                iosSimulatorArm64()

                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                }
                sourceSets.named("androidUnitTest").configure {
                    dependencies {
                        implementation(libs.findBundle("testing").get())
                    }
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = BuildConstants.COMPILE_SDK
                defaultConfig {
                    minSdk = BuildConstants.MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    isCoreLibraryDesugaringEnabled = true
                }
                lint {
                    disable += "NullSafeMutableLiveData"
                }
                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            dependencies.add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk").get())

            // One JVM runs a module's whole unit-test task, and MockK's inline mocks stay reachable
            // across test classes: :feature:chat's suite sat at Gradle's default 512 MB cap,
            // full-GC thrashing, and a few more tests turned that into OutOfMemoryErrors (or a
            // test that never finished) far from the test that tipped it.
            tasks.withType(Test::class.java).configureEach { maxHeapSize = "2g" }
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
