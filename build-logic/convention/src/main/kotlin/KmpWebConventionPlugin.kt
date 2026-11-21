import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds the browser (wasmJs) target, only when the build passes `-Plibrechat.web=true`, so Android and
 * iOS builds never configure or compile it. Code under `src/wasmJsMain` is ignored without the flag.
 */
class KmpWebConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            if (!isWebBuild()) return
            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> {
                    @OptIn(ExperimentalWasmDsl::class)
                    wasmJs { browser() }
                }
            }
        }
    }
}

internal fun Project.isWebBuild(): Boolean = providers.gradleProperty("librechat.web").orNull == "true"
