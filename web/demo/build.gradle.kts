import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.security.MessageDigest

// The live demo: the app's real drawer and chat screens in the browser, with sample data. Included only
// in a `-Plibrechat.web=true` build (settings.gradle.kts). `assembleSite` builds the site with it.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("librechat.kmp.web")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("switchboard-demo")
        browser {
            commonWebpackConfig {
                outputFileName = "switchboard-demo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":feature:chat:ui"))
            implementation(project(":feature:conversations:ui"))
            implementation(project(":feature:files:ui"))
            @Suppress("DEPRECATION")
            implementation(compose.runtime)
            @Suppress("DEPRECATION")
            implementation(compose.foundation)
            @Suppress("DEPRECATION")
            implementation(compose.material3)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)
            @Suppress("DEPRECATION")
            implementation(compose.ui)
        }
    }
}

// index.html's loading bar divides the bytes it has streamed by the .wasm files' total size, and the
// site's prefetch.js downloads the files files.json lists ahead of the click.
val distribution = tasks.named("wasmJsBrowserDistribution") {
    val distDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    doLast {
        val dir = distDir.get().asFile
        val wasm = dir.listFiles { file -> file.extension == "wasm" }.orEmpty().sortedBy { it.name }
        // The .wasm names already carry a hash; the script's name doesn't, so it gets one here, or a
        // cached old script would load .wasm files that are gone.
        val script = "switchboard-demo.js?v=" + ContentHash.of(dir.resolve("switchboard-demo.js"))
        val index = dir.resolve("index.html")
        index.writeText(
            index.readText()
                .replace("__WASM_BYTES__", wasm.sumOf { it.length() }.toString())
                .replace("\"switchboard-demo.js\"", "\"$script\""),
        )
        val files = listOf(script) + wasm.map { it.name }
        dir.resolve("files.json").writeText(files.joinToString(",", "[", "]\n") { "\"$it\"" })
    }
}

/**
 * The site as it deploys: `site/` with the demo under `demo/`. Pages link style.css and prefetch.js
 * with a hash of their content, so a browser holding an old copy fetches the new one with the new page.
 */
tasks.register<Sync>("assembleSite") {
    val siteDir = rootProject.layout.projectDirectory.dir("site")
    val versioned = listOf("style.css", "prefetch.js")
    inputs.files(versioned.map { siteDir.file(it) })
    val hashes by lazy { versioned.associateWith { name -> ContentHash.of(siteDir.file(name).asFile) } }
    from(siteDir) {
        filesMatching("*.html") {
            filter { line ->
                hashes.entries.fold(line) { text, (name, hash) -> text.replace("\"$name\"", "\"$name?v=$hash\"") }
            }
        }
    }
    from(distribution) {
        into("demo")
        exclude("**/*.map", "**/*.gz")
    }
    // The app's vendored libraries, which an artifact's page loads (ArtifactFrame).
    from(rootProject.layout.projectDirectory.dir("feature/chat/src/commonMain/composeResources/files/web")) {
        into("demo/web")
    }
    into(layout.buildDirectory.dir("site"))
    finalizedBy("siteContent")
}

// What's new, Privacy and Compatibility are filled from GitHub releases, docs/PRIVACY.md and the
// release tags (scripts/site-pages.py). It reads the network and git, so it always runs; without
// them the pages keep their link to GitHub, unless -Psite.strict (the deploy) makes that fail.
tasks.register<Exec>("siteContent") {
    val siteOut = layout.buildDirectory.dir("site")
    val strict = providers.gradleProperty("site.strict").isPresent
    workingDir = rootProject.projectDir
    commandLine(
        listOf("python3", "scripts/site-pages.py", siteOut.get().asFile.path) + if (strict) listOf("--strict") else emptyList(),
    )
    outputs.upToDateWhen { false }
}

// An object, not a script function: a task action that calls a script function can't be configuration-cached.
object ContentHash {
    fun of(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).take(4).joinToString("") { "%02x".format(it) }
}
