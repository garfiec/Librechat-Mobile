package com.garfiec.librechat.feature.chat.components.artifact

import com.garfiec.librechat.feature.chat.components.web.VendoredAdvisory
import com.garfiec.librechat.feature.chat.components.web.VendoredWebAssets

/**
 * How each module a React artifact imports is satisfied, decided before the page is built.
 *
 * Four outcomes per specifier:
 * - the bundled React/ReactDOM globals ([CORE_IMPORTS]);
 * - a vendored bundle ([VendoredWebAssets.MODULES]): its script is added to the page and an
 *   import-map entry re-exports what it registered;
 * - a stub that throws naming the module, when nothing bundled provides it, or when it
 *   belongs to a flagged library ([VendoredWebAssets.ADVISORIES]) the user has not allowed;
 * - left alone for the browser (relative files, URLs).
 *
 * shadcn/ui components are imported by path (`/components/ui/button`), the way web mounts
 * them into Sandpack. A path can't be an import-map key here: the artifact runs from a
 * `blob:` URL, against which `/components/...` does not resolve, so the import never
 * reaches the map. Such imports are rewritten (by a Babel visitor, never by text) to a bare
 * key under [SHADCN_KEY_PREFIX], and [ReactArtifactImports.aliases] says which.
 */
internal class ReactArtifactImports(
    /** Bundle scripts the page loads, in first-import order. */
    val scripts: List<String>,
    /** Import-map key -> the specifier its bundle registered it under. */
    val bundled: List<Bundled>,
    val stubs: List<Stub>,
    /** Specifier as written -> import-map key, for imports that can't be keys themselves. */
    val aliases: Map<String, String>,
    /** Flagged libraries the artifact imports, allowed or not, in first-import order. */
    val advisories: List<VendoredAdvisory>,
) {
    class Bundled(val key: String, val registered: String, val names: List<String>)

    /** [withheldLibrary]: the flagged library this module belongs to, when it was not allowed to load. */
    class Stub(val key: String, val shown: String, val names: List<String>, val withheldLibrary: String?)

    companion object {
        /** Captures the module specifier of every `import ... from 'X'`, `export ... from 'X'`
         *  and bare side-effect `import 'X'`. */
        private val MODULE_SPECIFIER = Regex("""(?:from|import)\s*['"]([^'"]+)['"]""")

        /** Specifiers the runner satisfies from the bundled UMD globals. */
        val CORE_IMPORTS = setOf("react", "react/jsx-runtime", "react-dom", "react-dom/client")

        /** npm package names only. Anything else is dropped rather than embedded: these
         *  strings come from model output and are interpolated into a script. */
        private val SAFE_SPECIFIER = Regex("""^[A-Za-z0-9@][A-Za-z0-9@/._-]*$""")

        /**
         * The ways an artifact names a shadcn module: web's `/components/ui/x` (what the prompt
         * asks for), `./components/ui/x` (the same file from /App.tsx), and the `@/` alias models
         * write anyway, each with or without an extension. The charset doubles as the embedding
         * guard for these, which [SAFE_SPECIFIER] would reject for their leading `.` or `/`.
         */
        private val SHADCN_IMPORT =
            Regex("""^(?:\.?/|@/)(components/ui/[A-Za-z0-9-]+|lib/utils)(?:\.(?:tsx|ts|jsx|js))?$""")

        const val SHADCN_KEY_PREFIX = "@switchboard/shadcn"

        /** Captures the import clause and specifier of `import <clause> from 'X'`. */
        private val IMPORT_CLAUSE = Regex("""import\s+([^'"]+?)\s+from\s*['"]([^'"]+)['"]""")

        /** A binding inside `{ ... }`, keeping the imported name rather than the local alias. */
        private val NAMED_BINDING = Regex("""([A-Za-z_$][A-Za-z0-9_$]*)(?:\s+as\s+[A-Za-z_$][A-Za-z0-9_$]*)?""")

        /** One import as written, the specifier a bundle would register it under, and its import-map key. */
        private class Resolved(val spec: String, val registered: String, val key: String)

        /** Every import the runner maps, in first-import order; relative files and URLs are skipped. */
        private fun resolve(content: String): List<Resolved> =
            MODULE_SPECIFIER.findAll(content).map { it.groupValues[1] }.distinct().mapNotNull { spec ->
                val shadcn = SHADCN_IMPORT.matchEntire(spec)
                when {
                    shadcn != null -> ("/" + shadcn.groupValues[1]).let { Resolved(spec, it, SHADCN_KEY_PREFIX + it) }
                    isBarePackage(spec) -> Resolved(spec, spec, spec)
                    else -> null
                }
            }.toList()

        /** The flagged libraries [content] imports, once each, in first-import order. */
        fun advisories(content: String): List<VendoredAdvisory> =
            resolve(content).mapNotNull { VendoredWebAssets.ADVISORIES[it.registered] }.distinctBy { it.library }

        /**
         * A flagged library loads only when it is in [allowedLibraries]; otherwise it is stubbed. So a
         * caller that never asked the user (and passes nothing) gets the stub, not the library.
         */
        fun plan(content: String, allowedLibraries: Set<String>): ReactArtifactImports {
            val names = importedNames(content)
            val scripts = LinkedHashSet<String>()
            val bundled = LinkedHashMap<String, Bundled>()
            val stubs = LinkedHashMap<String, Stub>()
            val aliases = LinkedHashMap<String, String>()
            val advisories = LinkedHashMap<String, VendoredAdvisory>()

            for (import in resolve(content)) {
                val (spec, registered, key) = Triple(import.spec, import.registered, import.key)
                if (key != spec) aliases[spec] = key
                val specNames = names[spec].orEmpty()
                val advisory = VendoredWebAssets.ADVISORIES[registered]
                if (advisory != null) advisories.getOrPut(advisory.library) { advisory }
                val script = VendoredWebAssets.MODULES[registered]
                // Two spellings of one shadcn path share a key: merge what each imports.
                when {
                    key in bundled -> bundled[key] = bundled.getValue(key).let {
                        Bundled(key, registered, (it.names + specNames).distinct())
                    }
                    key in stubs -> stubs[key] = stubs.getValue(key).let {
                        Stub(key, it.shown, (it.names + specNames).distinct(), it.withheldLibrary)
                    }
                    script == null -> stubs[key] = Stub(key, spec, specNames, withheldLibrary = null)
                    advisory != null && advisory.library !in allowedLibraries ->
                        stubs[key] = Stub(key, spec, specNames, withheldLibrary = advisory.library)
                    else -> {
                        scripts += script
                        bundled[key] = Bundled(key, registered, specNames)
                    }
                }
            }
            return ReactArtifactImports(
                scripts = scripts.toList(),
                bundled = bundled.values.toList(),
                stubs = stubs.values.toList(),
                aliases = aliases,
                advisories = advisories.values.toList(),
            )
        }

        /** A bare package specifier the import map may carry, excluding the React core. */
        private fun isBarePackage(spec: String): Boolean =
            spec.isNotBlank() &&
                !spec.startsWith(".") &&
                !spec.startsWith("/") &&
                !spec.contains(":") &&
                spec !in CORE_IMPORTS &&
                SAFE_SPECIFIER.matches(spec)

        /**
         * The export names each specifier is imported under.
         *
         * A stub module has to *declare* these even though it only ever throws. A module's
         * named imports are resolved during linking, which happens before any of its code
         * runs, so a stub that exports nothing is rejected with "does not provide an export
         * named 'X'" and the message explaining why the module is missing -- the whole point
         * of the stub -- never gets the chance to execute. A bundle whose script failed to
         * register falls back to a stub, so bundled specifiers carry their names too.
         *
         * Namespace imports (`import * as X`) need no entry; they bind to whatever the module
         * exports, including nothing.
         */
        private fun importedNames(content: String): Map<String, List<String>> {
            val exports = mutableMapOf<String, MutableSet<String>>()
            for (match in IMPORT_CLAUSE.findAll(content)) {
                val names = exports.getOrPut(match.groupValues[2]) { mutableSetOf() }
                val clause = match.groupValues[1]
                val braces = clause.substringAfter('{', "").substringBefore('}', "")
                if (braces.isNotBlank()) {
                    NAMED_BINDING.findAll(braces).forEach { names += it.groupValues[1] }
                }
                // Anything before the brace or the `* as` is a default import.
                val head = clause.substringBefore('{').substringBefore('*').trim().trimEnd(',').trim()
                if (head.isNotEmpty() && head != "type") names += "default"
            }
            return exports.mapValues { (_, names) -> names.toList() }
        }
    }
}
