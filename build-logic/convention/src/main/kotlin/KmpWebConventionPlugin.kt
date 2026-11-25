import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

/**
 * Adds a browser `wasmJs` target, only when the build runs with `-Plibrechat.web=true`. Without the
 * property this plugin does nothing, so Android, iOS, CI and F-Droid builds never declare a web
 * target or download Node, Yarn or Binaryen. Apply it after `librechat.kmp.library`, and only to a
 * module whose every commonMain dependency publishes a wasmJs variant.
 *
 * The toolchain downloads come from the repositories settings.gradle.kts declares for a web build:
 * `FAIL_ON_PROJECT_REPOS` rejects the ones the Kotlin plugin would add itself.
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
            listOf(this, rootProject).distinct().forEach { it.useSettingsToolchainRepositories() }
        }
    }
}

private fun Project.useSettingsToolchainRepositories() {
    fun <T : EnvSpec<*>> Project.noDownloadRepository(spec: Class<T>) {
        extensions.findByType(spec)?.downloadBaseUrl?.set(null as String?)
    }
    plugins.withType(WasmNodeJsPlugin::class.java) { noDownloadRepository(WasmNodeJsEnvSpec::class.java) }
    plugins.withType(WasmYarnPlugin::class.java) { noDownloadRepository(WasmYarnRootEnvSpec::class.java) }
    plugins.withType(BinaryenPlugin::class.java) { noDownloadRepository(BinaryenEnvSpec::class.java) }
}

internal fun Project.isWebBuild(): Boolean = providers.gradleProperty("librechat.web").orNull == "true"
