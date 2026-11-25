import com.android.build.gradle.LibraryExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature's stateless UI module (`:feature:<x>:ui`): Android, iOS and, with `-Plibrechat.web=true`,
 * the browser. It may depend only on [ALLOWED_PROJECTS] and other `:feature:*:ui` modules, and never on
 * a [DENIED_EXTERNAL] library; a declaration that breaks this fails the build while it configures.
 *
 * Its `Res` class is public and keeps the feature's resources package, so the feature module (which
 * depends on it with `api`) reads the same strings without an import change.
 */
class KmpFeatureUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            require(isFeatureUiPath(path)) { "librechat.kmp.feature.ui applies only to :feature:<x>:ui, not $path" }
            pluginManager.apply("librechat.kmp.library")
            pluginManager.apply("librechat.kmp.compose")
            pluginManager.apply("librechat.kmp.web")

            val feature = path.removePrefix(":feature:").removeSuffix(":ui")
            extensions.configure<LibraryExtension> {
                namespace = "com.garfiec.librechat.feature.$feature.ui"
            }
            extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
                publicResClass = true
                packageOfResClass = "com.garfiec.librechat.feature.$feature.resources"
            }
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(project(":core:ui"))
                    implementation(project(":core:model"))
                    implementation(project(":core:common"))
                }
            }

            configurations.matching { it.name.matches(CODE_CONFIGURATION) }.configureEach {
                val configuration = name
                dependencies.configureEach { checkUiDependency(this@with, configuration, this) }
            }
        }
    }
}

// The configurations code compiles or runs against; tooling ones (detektPlugins, kover) are exempt.
private val CODE_CONFIGURATION = Regex(".*(Implementation|Api|CompileOnly|RuntimeOnly)|implementation|api|compileOnly|runtimeOnly")

private val ALLOWED_PROJECTS = setOf(":core:ui", ":core:model", ":core:common")

private val DENIED_EXTERNAL = listOf(
    "io.insert-koin:" to "Koin",
    "androidx.lifecycle:lifecycle-viewmodel" to "ViewModel",
    "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel" to "ViewModel",
    "androidx.navigation3:" to "Navigation 3",
    "org.jetbrains.androidx.navigation3:" to "Navigation 3",
    "io.ktor:ktor-client" to "a Ktor client",
    "androidx.room:" to "Room",
    "androidx.datastore:" to "DataStore",
)

private fun isFeatureUiPath(path: String) = path.startsWith(":feature:") && path.endsWith(":ui")

private fun checkUiDependency(project: Project, configuration: String, dependency: Dependency) {
    val problem = when (dependency) {
        is ProjectDependency -> dependency.path.takeUnless { it in ALLOWED_PROJECTS || isFeatureUiPath(it) }
        is ExternalModuleDependency -> {
            val coordinates = "${dependency.group}:${dependency.name}"
            DENIED_EXTERNAL.firstOrNull { (prefix, _) -> coordinates.startsWith(prefix) }
                ?.let { (_, what) -> "$coordinates ($what)" }
        }
        else -> null
    } ?: return
    throw GradleException(
        "${project.path} declares $problem in '$configuration'. A :feature:<x>:ui module may depend only on " +
            "${ALLOWED_PROJECTS.joinToString()} and other :feature:*:ui modules; move the code that needs it to " +
            "the feature module and pass the result in as state or a callback.",
    )
}
