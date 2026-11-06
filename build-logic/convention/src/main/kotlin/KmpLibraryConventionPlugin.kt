import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
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
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
