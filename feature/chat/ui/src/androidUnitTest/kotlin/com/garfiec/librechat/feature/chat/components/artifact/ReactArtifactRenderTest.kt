package com.garfiec.librechat.feature.chat.components.artifact

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the React-artifact renderer in [ArtifactWebContent.buildHtml].
 *
 * React artifacts are compiled in-browser and loaded as a real ES module; the artifact's
 * own `import`/`export` statements run verbatim against an import map the runner builds
 * over the bundled React/ReactDOM UMD globals. The load-bearing properties: (1) every
 * script the page executes ships with the app, (2) React resolves to a single instance,
 * (3) a package that is not bundled fails by name rather than reaching the network, and
 * (4) the source round-trips through the embedding untouched.
 */
class ReactArtifactRenderTest {

    private val reactType = "application/vnd.react"

    private fun build(content: String): String =
        ArtifactWebContent.buildHtml(content, reactType, isDarkTheme = false)

    /** Recovers the artifact source the runner will execute, from the base64 blob. */
    private fun embeddedSource(html: String): String {
        val b64 = Regex("""atob\('([^']*)'\)""").find(html)!!.groupValues[1]
        return String(Base64.getDecoder().decode(b64))
    }

    @Test
    fun `every script the page loads is bundled with the app`() {
        val html = build("export default function App() { return <div/>; }")
        assertTrue(html.contains("""<script src="react/react.production.min.js">"""))
        assertTrue(html.contains("""<script src="react-dom/react-dom.production.min.js">"""))
        assertTrue(html.contains("""<script src="babel/babel.min.js">"""))
        assertTrue(html.contains("""<script src="tailwind/tailwind.min.js">"""))
    }

    @Test
    fun `the page may not reach any remote origin`() {
        // Not a style preference: a remote <script src> here is what F-Droid's inclusion
        // policy forbids, and it is invisible in a passing render because the CDN answers.
        val html = build(
            """
            import { LineChart } from 'recharts';
            export default function App() { return <LineChart/>; }
            """.trimIndent(),
        )
        val csp = Regex("""content="(default-src[^"]*)"""").find(html)!!.groupValues[1]
        val scriptSrc = csp.split(";").map { it.trim() }.first { it.startsWith("script-src") }
        assertFalse(scriptSrc.contains("http"), "CSP still allows remote script: $scriptSrc")
        assertFalse(html.contains("https://cdn."), "page references a CDN")
        assertFalse(html.contains("esm.sh"), "page references the ESM CDN")
        assertFalse(html.contains("unpkg.com"), "page references unpkg")
    }

    @Test
    fun `react resolves to the single bundled instance`() {
        val html = build("export default function App() { return <div/>; }")
        // Both come off the same window.React / window.ReactDOM the UMD scripts defined,
        // so there is no way for a second copy to exist and break hooks.
        assertTrue(html.contains("'react': globalModule('React', window.React)"))
        assertTrue(html.contains("'react-dom': globalModule('ReactDOM', window.ReactDOM)"))
        assertTrue(html.contains("'react-dom/client': moduleUrl(DOM_CLIENT)"))
        assertTrue(html.contains("'react/jsx-runtime': moduleUrl(JSX_RUNTIME)"))
    }

    /** The runner's generated table `var NAME = …;`, as embedded. */
    private fun table(html: String, name: String): String =
        Regex("""var $name = (.*);""").find(html)!!.groupValues[1]

    @Test
    fun `an unbundled package fails by name instead of loading from a cdn`() {
        val html = build(
            """
            import { motion } from 'framer-motion';
            import confetti from 'canvas-confetti';
            import * as Icons from '@radix-ui/react-icons';
            export default function App() { return <div/>; }
            """.trimIndent(),
        )
        // Each specifier carries the names it is imported under. Those names are what the stub
        // declares as exports, and declaring them is what lets the explanation run at all:
        // named imports are resolved while the module graph links, before any code executes, so
        // a stub exporting nothing is rejected with "does not provide an export named 'X'" and
        // the package is never named. A namespace import binds to whatever exists, so
        // @radix-ui/react-icons correctly needs none.
        assertEquals(
            """[["framer-motion","framer-motion",["motion"],null],""" +
                """["canvas-confetti","canvas-confetti",["default"],null],""" +
                """["@radix-ui/react-icons","@radix-ui/react-icons",[],null]]""",
            table(html, "STUBS"),
        )
        assertTrue(html.contains("which is not ' +"), "stub carries no explanation")
    }

    @Test
    fun `a bundled package loads its vendored script and is re-exported`() {
        val html = build(
            """
            import { Plus } from 'lucide-react';
            import { LineChart, XAxis } from 'recharts';
            export default function App() { return <Plus/>; }
            """.trimIndent(),
        )
        assertTrue(html.contains("""<script src="lucide-react/index.js"></script>"""))
        assertTrue(html.contains("""<script src="recharts/index.js"></script>"""))
        assertEquals(
            """[["lucide-react","lucide-react",["Plus"]],["recharts","recharts",["LineChart","XAxis"]]]""",
            table(html, "BUNDLED"),
        )
        assertEquals("[]", table(html, "STUBS"))
        // A bundle loads only when imported: three is vendored, but this page has no use for it.
        assertFalse(html.contains("three/index.js"))
    }

    @Test
    fun `bundle scripts load after react and before the runner`() {
        // Bundles resolve `react` to window.React when they execute, so React must already exist.
        val html = build("import { Plus } from 'lucide-react';\nexport default () => <Plus/>;")
        val react = html.indexOf("react-dom/react-dom.production.min.js")
        val bundle = html.indexOf("lucide-react/index.js")
        assertTrue(react in 1 until bundle, "bundle script precedes ReactDOM")
        assertTrue(bundle < html.indexOf("var BUNDLED"), "bundle script loads after the runner")
    }

    @Test
    fun `shadcn path imports alias to one key and load the shadcn bundle once`() {
        val html = build(
            """
            import { Button } from '/components/ui/button';
            import { buttonVariants } from './components/ui/button.tsx';
            import { Card } from '@/components/ui/card';
            import { cn } from '/lib/utils';
            export default function App() { return <Button/>; }
            """.trimIndent(),
        )
        assertEquals(1, Regex("""shadcn-ui/index\.js""").findAll(html).count(), "shadcn bundle loaded more than once")
        assertEquals(
            """{"/components/ui/button":"@switchboard/shadcn/components/ui/button",""" +
                """"./components/ui/button.tsx":"@switchboard/shadcn/components/ui/button",""" +
                """"@/components/ui/card":"@switchboard/shadcn/components/ui/card",""" +
                """"/lib/utils":"@switchboard/shadcn/lib/utils"}""",
            table(html, "ALIASES"),
        )
        assertTrue(
            table(html, "BUNDLED").contains(
                """["@switchboard/shadcn/components/ui/button","/components/ui/button",["Button","buttonVariants"]]""",
            ),
            "two spellings of one component should merge into one entry",
        )
    }

    @Test
    fun `a shadcn component web does not mount fails by name`() {
        val html = build("import { Form } from '/components/ui/form';\nexport default () => <div/>;")
        assertEquals(
            """[["@switchboard/shadcn/components/ui/form","/components/ui/form",["Form"],null]]""",
            table(html, "STUBS"),
        )
        assertEquals("""{"/components/ui/form":"@switchboard/shadcn/components/ui/form"}""", table(html, "ALIASES"))
    }

    private val flaggedSource = """
        import { Drawer } from '/components/ui/drawer';
        import * as dat from 'dat.gui';
        export default function App() { return <div/>; }
    """.trimIndent()

    @Test
    fun `only allowed flagged libraries load, the rest are stubbed and their bundles never load`() {
        val html = ArtifactWebContent.buildHtml(flaggedSource, reactType, isDarkTheme = false, allowedLibraries = setOf("dat.gui"))
        assertFalse(html.contains("vaul/index.js"), "a library the user didn't allow still loads")
        assertTrue(html.contains("dat-gui/index.js"), "an allowed flagged library should load")
        assertEquals(
            """[["@switchboard/shadcn/components/ui/drawer","/components/ui/drawer",["Drawer"],"vaul"]]""",
            table(html, "STUBS"),
        )
    }

    @Test
    fun `a caller that never asked loads no flagged library`() {
        // Fail closed: consent is opt-in, so a host that skips ArtifactLibraryGate gets stubs.
        val html = build(flaggedSource)
        assertFalse(html.contains("vaul/index.js"))
        assertFalse(html.contains("dat-gui/index.js"))
        assertTrue(table(html, "STUBS").contains(""""dat.gui","dat.gui",[],"dat.gui"]"""))
    }

    @Test
    fun `flagged libraries are reported once each, in import order, for react only`() {
        val source = """
            import * as dat from 'dat.gui';
            import { Drawer } from '/components/ui/drawer';
            import { Drawer as D } from 'vaul';
            import { Plus } from 'lucide-react';
        """.trimIndent()
        assertEquals(
            listOf("dat.gui", "vaul"),
            ArtifactWebContent.libraryAdvisories(source, reactType).map { it.library },
        )
        assertEquals(emptyList(), ArtifactWebContent.libraryAdvisories(source, "text/html"))
    }

    @Test
    fun `typescript compiles alongside jsx`() {
        // Web runs artifacts in Sandpack's react-ts template, so typed artifacts are normal.
        val html = build("export default function App(): JSX.Element { return <div/>; }")
        assertTrue(html.contains("['typescript', { isTSX: true, allExtensions: true }]"))
        assertTrue(html.contains("filename: 'artifact.tsx'"))
    }

    @Test
    fun `relative and url imports are left for the browser to resolve`() {
        val html = build(
            """
            import { helper } from './utils';
            import data from 'https://example.com/data.js';
            import './styles.css';
            export default function App() { return <div/>; }
            """.trimIndent(),
        )
        val generated = table(html, "STUBS") + table(html, "BUNDLED") + table(html, "ALIASES")
        assertFalse(generated.contains("./utils"), "relative import should not be mapped")
        assertFalse(generated.contains("example.com"), "url import should not be mapped")
        assertFalse(generated.contains("./styles.css"), "relative css should not be mapped")
    }

    @Test
    fun `a specifier that is not a package name is dropped rather than embedded`() {
        // The specifier tables are interpolated into a script, and their contents come from
        // model output. Anything outside the npm name charset never reaches the page.
        val html = build(
            """
            import x from 'evil'/*"];window.stolen=1;//*/;
            import y from '/components/ui/x"];window.stolen=2;//';
            export default function App() { return <div/>; }
            """.trimIndent(),
        )
        val generated = table(html, "STUBS") + table(html, "BUNDLED") + table(html, "ALIASES")
        assertFalse(generated.contains("window.stolen"), "injected script escaped the specifier tables")
    }

    @Test
    fun `artifact source round-trips through the embedding verbatim`() {
        // Includes characters that would break naive string embedding.
        val source = """
            import { useState } from 'react';
            export default function App() {
              const html = `<script>alert(1)</script>`;
              const t = `total: ${'$'}{1 + 2}`;
              return <div>{html}{t}</div>;
            }
        """.trimIndent()
        assertEquals(source, embeddedSource(build(source)), "source must survive embedding unchanged")
    }

    @Test
    fun `source is not rewritten - imports and exports are preserved`() {
        // The whole point of the module approach: no regex surgery on the source.
        val source = "import { useState } from 'react';\nexport default function App() { return <div/>; }"
        val recovered = embeddedSource(build(source))
        assertTrue(recovered.contains("import { useState } from 'react';"), "import was altered")
        assertTrue(recovered.contains("export default function App()"), "export was altered")
    }

    @Test
    fun `runner compiles jsx and loads the artifact as a module`() {
        val html = build("export default function App() { return <div/>; }")
        assertTrue(html.contains("Babel.transform("), "jsx is not compiled")
        assertTrue(html.contains("runtime: 'automatic'"), "automatic runtime lets JSX work without importing React")
        assertTrue(html.contains("createRoot(root).render"), "component is not mounted")
        // The import map has to be in the document before the first dynamic import, which
        // is the whole reason the runner is a classic script wrapping an async IIFE.
        val mapAt = html.indexOf("mapTag.type = 'importmap'")
        val importAt = html.indexOf("await import('react')")
        assertTrue(mapAt in 1 until importAt, "import map is injected after the first import")
    }
}
