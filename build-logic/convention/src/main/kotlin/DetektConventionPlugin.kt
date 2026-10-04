import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                allRules = false
                parallel = true
            }

            tasks.withType<DetektCreateBaselineTask>().configureEach {
                enabled = false
            }

            // Committed generated sources (e.g. BackendCommitMap) are machine-written; skip lint
            // (large packed literals trip formatting/line-length rules that don't apply to codegen).
            // Scoped to the specific codegen package so hand-written code elsewhere stays linted.
            // Build-time codegen (KSP output such as Room *_Impl classes, picked up by the
            // per-target detekt tasks) is machine-written too and excluded wholesale — by file
            // path, because string patterns match relative to each source root and the codegen
            // roots live under build/generated themselves.
            tasks.withType<Detekt>().configureEach {
                exclude("**/com/garfiec/librechat/core/common/generated/**")
                exclude { element ->
                    element.file.invariantSeparatorsPath.contains("/build/generated/")
                }
            }

            // `detekt` is the gate: one plain pass over every source set (androidMain, iosMain, every
            // test set, :app). Out of the box it reads only the JVM default dirs, which KMP modules
            // don't have, and the per-compilation tasks miss commonTest / iosTest entirely.
            // The type-resolved tasks (detektAndroidDebug, detektDebug, …) stay out of the gate: detekt
            // 1.23's embedded compiler predates the project's Kotlin and reports reachable code as
            // unreachable and real suspend calls as redundant.
            tasks.named<Detekt>("detekt") {
                setSource(fileTree("src") { include("*/kotlin/**/*.kt", "*/kotlin/**/*.kts") })
            }

            dependencies {
                add("detektPlugins", libs.findLibrary("detekt-formatting").get())
                add("detektPlugins", libs.findLibrary("detekt-koin").get())
                add("detektPlugins", libs.findLibrary("detekt-compose").get())
                // Custom row-tenancy ruleset (AccountScopedDao). :detekt-rules deliberately does not
                // apply this convention, so wiring it here can't create a self-analysis cycle.
                add("detektPlugins", project(":detekt-rules"))
            }
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
