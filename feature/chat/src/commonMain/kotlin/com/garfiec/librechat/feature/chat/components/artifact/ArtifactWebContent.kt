package com.garfiec.librechat.feature.chat.components.artifact

import com.garfiec.librechat.core.model.TextFormat
import com.garfiec.librechat.feature.chat.components.web.VendoredAdvisory
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Builds the HTML document for an artifact preview. Used by both the
 * [ArtifactViewer] and the [InlineArtifactView]. Set [inline] to true when
 * embedding inside a chat message: SVG/Markdown/Plain shrink their body
 * padding, Mermaid additionally disables htmlLabels so the SVG can round-trip
 * through Coil's SvgDecoder for the cache-harvest path. HTML and React
 * templates ignore the flag — the surrounding Compose Surface supplies their
 * padding.
 */
object ArtifactWebContent {

    /**
     * [allowedLibraries] are the flagged libraries ([libraryAdvisories]) the user agreed to load.
     * Any other flagged library a React artifact imports gets a stub that says so instead of the
     * bundle, so a caller that never asked (and passes nothing) loads none of them.
     */
    fun buildHtml(
        content: String,
        type: String,
        isDarkTheme: Boolean,
        inline: Boolean = false,
        allowedLibraries: Set<String> = emptySet(),
    ): String {
        val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
        val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"

        // Office-doc previews must NOT route through the generic HTML path: that
        // would inject `content` as raw HTML unconditionally, bypassing the
        // textFormat==html security gate. The office card pre-builds the safe,
        // complete document via [buildOfficePreviewHtml] (which honors the gate)
        // and passes it here as `content`; return it unchanged.
        // SECURITY: office-MIME artifacts are pre-gated by OfficePreviewCard (the only
        // producer; previews arrive as attachments, not :::artifact directives). Any
        // future raw-text office-MIME artifact MUST gate via buildOfficePreviewHtml
        // before reaching here, or it would pass through unescaped.
        if (ArtifactType.isOfficePreviewMime(type)) {
            return content
        }

        return when (ArtifactType.from(type)) {
            // htmlLabels = !inline: only the inline cache-harvest path needs htmlLabels
            // disabled so the resulting SVG renders correctly through Coil 3's SvgDecoder
            // (no foreignObject). The artifact viewer renders via the live
            // WebView+mermaid runtime so it keeps mermaid's default htmlLabels=true for
            // label fidelity (bold/italic, <br/>, nested spans).
            ArtifactType.MERMAID -> MermaidWebContent.buildHtml(content, isDarkTheme, inline, htmlLabels = !inline)
            ArtifactType.MARKDOWN -> MarkdownWebContent.buildHtml(content, isDarkTheme, inline)
            // Escaped, never through marked: marked hands raw HTML in the text straight to
            // innerHTML, so a file's or a reply's "plain text" would render as live markup.
            ArtifactType.PLAIN, ArtifactType.CODE -> buildPlainHtml(content, bgColor, fgColor, inline)
            ArtifactType.REACT -> buildReactHtml(content, bgColor, fgColor, allowedLibraries)
            ArtifactType.SVG -> buildSvgHtml(content, bgColor, inline)
            ArtifactType.HTML -> buildEnhancedHtml(content, bgColor, fgColor)
        }
    }

    /**
     * The flagged libraries (unmaintained, deprecated or vulnerable; `advisory` in
     * scripts/web-assets.json) a React artifact imports, in first-import order. The host asks
     * the user about each before building the page. Empty for every other type.
     */
    internal fun libraryAdvisories(content: String, type: String): List<VendoredAdvisory> =
        if (!ArtifactType.isOfficePreviewMime(type) && ArtifactType.from(type) == ArtifactType.REACT) {
            ReactArtifactImports.advisories(content)
        } else {
            emptyList()
        }

    /**
     * Builds the document for a deferred office-doc preview (`TFilePreview`).
     *
     * SECURITY (load-bearing): the server's `text` is injected as live HTML
     * ONLY when [textFormat] is exactly `"html"` (the backend produced a
     * sanitized full-document preview). For `"text"`, null, or any other value
     * the content is plain text and is rendered through the escaping monospace
     * path — it is NEVER injected as HTML. Upstream's `TFile.textFormat` doc
     * explicitly warns against injecting the `text` format as HTML.
     *
     * The HTML is passed through [localizeOfficePreviewHtml] first: the server's document loads
     * its renderers from a CDN, which F-Droid forbids, so they are pointed at vendored copies.
     */
    fun buildOfficePreviewHtml(
        text: String,
        textFormat: String?,
        isDarkTheme: Boolean,
        inline: Boolean = false,
    ): String {
        val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
        val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"
        return if (textFormat == TextFormat.HTML) {
            buildEnhancedHtml(localizeOfficePreviewHtml(text), bgColor, fgColor)
        } else {
            buildPlainHtml(text, bgColor, fgColor, inline)
        }
    }

    // Security note: HTML artifacts intentionally render unsanitized HTML content.
    // This is by design — HTML artifacts are meant to be rendered as-is. The WebView
    // is sandboxed with a Content Security Policy restricting script/resource origins.
    private fun buildEnhancedHtml(content: String, bgColor: String, fgColor: String): String {
        val hasHtmlTag = content.contains("<html", ignoreCase = true) ||
            content.contains("<!DOCTYPE", ignoreCase = true)

        if (hasHtmlTag) {
            // font-src: an office preview carries this policy beside its own, and both are enforced,
            // so without it docx-preview's embedded (blob:) fonts fall to default-src and are blocked.
            val themeStyle = """
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline' file:; style-src 'unsafe-inline'; img-src data: blob: https:; font-src data: blob:; form-action 'none';">
                <style>:root { --bg: $bgColor; --fg: $fgColor; } html, body { max-width: 100%; overflow-x: hidden; } body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; } img, svg, video, iframe { max-width: 100%; height: auto; }</style>
                <script src="tailwind/tailwind.min.js"></script>
            """.trimIndent()
            return if (content.contains("<head>", ignoreCase = true)) {
                content.replaceFirst(
                    Regex("<head>", RegexOption.IGNORE_CASE),
                    "<head>$themeStyle",
                )
            } else if (content.contains("<head ", ignoreCase = true)) {
                val headMatch = Regex("<head\\s[^>]*>", RegexOption.IGNORE_CASE).find(content)
                if (headMatch != null) {
                    content.replaceRange(headMatch.range.last + 1, headMatch.range.last + 1, themeStyle)
                } else {
                    "$themeStyle\n$content"
                }
            } else {
                "$themeStyle\n$content"
            }
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline' file:; style-src 'unsafe-inline'; img-src data: blob: https:; form-action 'none';">
                <script src="tailwind/tailwind.min.js"></script>
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
                    img, svg, video, iframe { max-width: 100%; height: auto; }
                </style>
            </head>
            <body>$content</body>
            </html>
        """.trimIndent()
    }

    // Security note: SVG content is rendered unsanitized because SVG artifacts are
    // designed to display user-provided vector graphics. CSP restricts script execution.
    private fun buildSvgHtml(content: String, bgColor: String, inline: Boolean): String {
        val padding = if (inline) "4px" else "16px"
        val minHeight = if (inline) "0" else "100vh"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:;">
                <style>
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body {
                        margin: 0;
                        padding: $padding;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: $minHeight;
                        background: $bgColor;
                    }
                    svg, .svg-container {
                        width: 100%;
                        height: auto;
                        max-width: 100%;
                    }
                </style>
            </head>
            <body><div class="svg-container">$content</div></body>
            </html>
        """.trimIndent()
    }

    // Security note: React artifacts intentionally render unsanitized, model-generated
    // JSX/TSX. The preview runs in a sandboxed WebView under a CSP that permits no remote
    // origin at all: React, ReactDOM, Babel, Tailwind and every npm module an artifact may
    // import are the copies bundled with the app, referenced relative to the document base
    // URL the platform host supplies.
    //
    // React 18 publishes no browser-ready ESM build, only CommonJS and UMD, so the UMD
    // builds load as plain scripts and the import map points at small generated modules
    // re-exporting those globals. The npm modules (lucide-react, recharts, shadcn/ui, ...)
    // follow the same bridge: each vendored bundle is a classic script registering module
    // namespaces on `__switchboardModules`, re-exported the same way. See
    // `feature/chat/CLAUDE.md`, "React's module problem". Versions are not named here: they
    // are whatever scripts/web-assets.json pins.

    @Suppress("LongMethod") // debt: one HTML template
    @OptIn(ExperimentalEncodingApi::class)
    private fun buildReactHtml(
        content: String,
        bgColor: String,
        fgColor: String,
        allowedLibraries: Set<String>,
    ): String {
        val imports = ReactArtifactImports.plan(content, allowedLibraries)

        // Every string below is a module specifier that passed the plan's charset guard, an
        // identifier from NAMED_BINDING, or a path from the generated manifest, so none can
        // carry a quote or a backslash out of its JSON literal.
        fun quoted(s: String) = "\"" + s + "\""

        fun names(list: List<String>) = list.joinToString(",", "[", "]") { quoted(it) }

        val bundledJson = imports.bundled.joinToString(",", "[", "]") {
            "[" + quoted(it.key) + "," + quoted(it.registered) + "," + names(it.names) + "]"
        }
        val stubsJson = imports.stubs.joinToString(",", "[", "]") {
            "[" + quoted(it.key) + "," + quoted(it.shown) + "," + names(it.names) + "," +
                (it.withheldLibrary?.let(::quoted) ?: "null") + "]"
        }
        val aliasesJson = imports.aliases.entries.joinToString(",", "{", "}") { (spec, key) ->
            quoted(spec) + ":" + quoted(key)
        }
        val moduleScripts = imports.scripts.joinToString("\n") { """<script src="$it"></script>""" }
        // Embed the source as base64 so arbitrary JSX -- including `</script>`,
        // backticks, or `${'$'}{...}` -- round-trips with zero HTML/JS escaping
        // hazards. The runner decodes, compiles it, and imports it as a real ES module so
        // the artifact's own `import`/`export` statements run against the import map. The
        // one rewrite is of shadcn import paths (see ReactArtifactImports), done on Babel's
        // AST, never on the text.
        val sourceB64 = Base64.encode(content.encodeToByteArray())

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob: file:; style-src 'unsafe-inline'; img-src data: blob: https:; font-src data: file:; form-action 'none';">
                <script src="react/react.production.min.js"></script>
                <script src="react-dom/react-dom.production.min.js"></script>
                $moduleScripts
                <script src="babel/babel.min.js"></script>
                <script src="tailwind/tailwind.min.js"></script>
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
                    img, svg, video, iframe { max-width: 100%; height: auto; }
                    #error-display {
                        display: none;
                        padding: 16px;
                        margin: 16px;
                        background: #B3261E22;
                        border: 1px solid #B3261E;
                        border-radius: 8px;
                        font-family: monospace;
                        font-size: 13px;
                        white-space: pre-wrap;
                        color: $fgColor;
                    }
                </style>
            </head>
            <body>
                <div id="root"></div>
                <div id="error-display"></div>
                <script>
                (function () {
                    var root = document.getElementById('root');
                    var errDiv = document.getElementById('error-display');
                    function showError(msg) {
                        if (root.hasChildNodes()) return;
                        errDiv.style.display = 'block';
                        errDiv.textContent = 'Component failed to render:\n' + msg;
                    }
                    window.addEventListener('error', function(e) { showError(e.message || 'Unknown error'); });
                    window.addEventListener('unhandledrejection', function(e) {
                        showError((e.reason && e.reason.message) || String(e.reason));
                    });

                    // Module code is strict mode, so the strict-only reserved words are
                    // just as fatal here as the ordinary ones: a single
                    // `export const static = …` is a syntax error that fails the whole
                    // module, not only that one binding.
                    var RESERVED = ['default','class','function','const','let','var','import','export',
                        'new','delete','typeof','in','of','do','if','else','return','switch','case',
                        'break','continue','for','while','with','try','catch','finally','throw','this',
                        'super','void','yield','await','enum','null','true','false','instanceof',
                        'extends','debugger','implements','interface','package','private','protected',
                        'public','static','arguments','eval'];

                    // Only plain ASCII identifiers can be re-exported by name; anything
                    // else stays reachable through the module's default export.
                    function isSafeName(k) {
                        if (!k || RESERVED.indexOf(k) !== -1) return false;
                        for (var i = 0; i < k.length; i++) {
                            var c = k.charCodeAt(i);
                            var ok = (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || c === 95 ||
                                (i > 0 && c >= 48 && c <= 57);
                            if (!ok) return false;
                        }
                        return true;
                    }

                    function moduleUrl(source) {
                        return URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
                    }

                    // Re-exports an object as an ES module, naming each export so
                    // `import { useState } from 'react'` binds like it would from npm.
                    function objectModule(accessor, obj) {
                        var lines = ['const m = ' + accessor + ';',
                            'export default (m.default !== undefined ? m.default : m);'];
                        Object.keys(obj).forEach(function (k) {
                            if (isSafeName(k)) lines.push('export const ' + k + ' = m.' + k + ';');
                        });
                        return moduleUrl(lines.join('\n'));
                    }

                    function globalModule(globalName, obj) {
                        return objectModule('window.' + globalName, obj);
                    }

                    // The stub declares the names the artifact imports from it, then throws.
                    // Declaring them is not decoration: named imports are resolved while the
                    // module graph links, before any code runs, so a stub that exports nothing
                    // is rejected with "does not provide an export named 'X'" and the message
                    // below -- the entire reason the stub exists -- never executes.
                    function stubModule(names, message) {
                        var lines = [];
                        names.forEach(function (n) {
                            if (n === 'default') lines.push('export default undefined;');
                            else if (isSafeName(n)) lines.push('export const ' + n + ' = undefined;');
                        });
                        lines.push('throw new Error(' + JSON.stringify(message) + ');');
                        return moduleUrl(lines.join('\n'));
                    }

                    function missingModule(spec, names) {
                        return stubModule(names, 'This artifact imports "' + spec + '", which is not ' +
                            'bundled with the app, so it cannot be loaded.');
                    }

                    function withheldModule(spec, names, library) {
                        var named = spec === library ? '"' + spec + '"' : '"' + spec + '" (' + library + ')';
                        return stubModule(names, 'This artifact imports ' + named +
                            ', which was not loaded: it needs your permission.');
                    }

                    // A bundle's script registers its modules as it runs. One that didn't (a
                    // missing or broken file) gets a stub saying so, rather than a link error
                    // about some export the artifact happened to import first.
                    function bundledModule(spec, names) {
                        var mods = window.__switchboardModules || {};
                        if (!mods[spec]) {
                            return stubModule(names, 'The bundled module "' + spec + '" failed to load.');
                        }
                        return objectModule('window.__switchboardModules[' + JSON.stringify(spec) + ']', mods[spec]);
                    }

                    // Babel's automatic runtime compiles JSX to jsx()/jsxs() imported from
                    // react/jsx-runtime, which React 18's UMD build does not expose.
                    //
                    // `children` is handed to createElement as ONE argument, never spread
                    // into positional ones. Spreading looks equivalent and is not: an array
                    // of length 1 (a `.map()` over a single-item list) collapses to a lone
                    // child, which React reconciles by position instead of by key, so the
                    // element remounts and loses its state the moment the list grows to two
                    // — and a large generated list overflows the argument limit outright.
                    var JSX_RUNTIME = [
                        'const R = window.React;',
                        'export const Fragment = R.Fragment;',
                        'export function jsx(type, props, key) {',
                        '  const p = Object.assign({}, props);',
                        '  const children = p.children;',
                        '  delete p.children;',
                        '  if (key !== undefined) p.key = key;',
                        '  if (children === undefined) return R.createElement(type, p);',
                        '  return R.createElement(type, p, children);',
                        '}',
                        'export const jsxs = jsx;',
                        'export const jsxDEV = jsx;'
                    ].join('\n');

                    var DOM_CLIENT = [
                        'const m = window.ReactDOM;',
                        'export const createRoot = m.createRoot;',
                        'export const hydrateRoot = m.hydrateRoot;',
                        'export default { createRoot: m.createRoot, hydrateRoot: m.hydrateRoot };'
                    ].join('\n');

                    var imports = {
                        'react': globalModule('React', window.React),
                        'react/jsx-runtime': moduleUrl(JSX_RUNTIME),
                        'react-dom': globalModule('ReactDOM', window.ReactDOM),
                        'react-dom/client': moduleUrl(DOM_CLIENT)
                    };
                    var BUNDLED = $bundledJson;
                    var STUBS = $stubsJson;
                    var ALIASES = $aliasesJson;
                    BUNDLED.forEach(function (e) { imports[e[0]] = bundledModule(e[1], e[2]); });
                    STUBS.forEach(function (e) {
                        imports[e[0]] = e[3] ? withheldModule(e[1], e[2], e[3]) : missingModule(e[1], e[2]);
                    });

                    // Points shadcn path imports at their import-map keys (see ALIASES' builder).
                    // Rewritten on the AST: a regex over the source would also hit strings and
                    // comments that merely contain the path.
                    function aliasImports() {
                        function rewrite(path) {
                            var s = path.node.source;
                            if (s && Object.prototype.hasOwnProperty.call(ALIASES, s.value)) {
                                s.value = ALIASES[s.value];
                                delete s.extra;
                            }
                        }
                        return { visitor: {
                            ImportDeclaration: rewrite,
                            ExportNamedDeclaration: rewrite,
                            ExportAllDeclaration: rewrite
                        } };
                    }

                    // Injected before the first dynamic import, which is the only ordering
                    // requirement -- no module has been resolved yet at this point.
                    var mapTag = document.createElement('script');
                    mapTag.type = 'importmap';
                    mapTag.textContent = JSON.stringify({ imports: imports });
                    document.head.appendChild(mapTag);

                    (async function () {
                        try {
                            var ReactNS = await import('react');
                            var React = ReactNS.default || ReactNS;
                            var createRoot = (await import('react-dom/client')).createRoot;
                            var source = new TextDecoder().decode(
                                Uint8Array.from(atob('$sourceB64'), function(c) { return c.charCodeAt(0); })
                            );
                            // TypeScript as well as JSX: web runs artifacts in Sandpack's react-ts
                            // template, and the shadcn components invite typed code. The preset
                            // also drops imports used only as types, which the bundles do not
                            // export at runtime.
                            var compiled = Babel.transform(source, {
                                presets: [
                                    ['typescript', { isTSX: true, allExtensions: true }],
                                    ['react', { runtime: 'automatic', development: false }]
                                ],
                                plugins: [aliasImports],
                                filename: 'artifact.tsx',
                                sourceType: 'module',
                            }).code;
                            var mod = await import(moduleUrl(compiled));
                            var Component = mod.default ||
                                Object.values(mod).find(function(v) { return typeof v === 'function'; });
                            if (!Component) {
                                throw new Error('No React component is exported. Add `export default`.');
                            }
                            createRoot(root).render(React.createElement(Component));
                        } catch (e) {
                            showError((e && e.message) || String(e));
                        }
                    })();
                })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildPlainHtml(content: String, bgColor: String, fgColor: String, inline: Boolean): String {
        val padding = if (inline) "8px" else "16px"
        val escaped = escapeHtml(content)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline';">
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: $padding; font-family: monospace; font-size: 13px; }
                    pre { white-space: pre-wrap; word-wrap: break-word; margin: 0; }
                </style>
            </head>
            <body><pre>$escaped</pre></body>
            </html>
        """.trimIndent()
    }

    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
