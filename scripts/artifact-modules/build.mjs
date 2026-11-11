// Bundles npm packages into one classic script for the React artifact runner.
//
// Run by scripts/vendor-web-assets.py (--sync), never by hand and never at app build time.
// Its output is vendored and hash-locked like every other web asset; CI verifies the lock
// without node.
//
// Why classic scripts and not ES modules: the artifact WebViews load their documents from
// file:///android_asset with file-from-file access off. Chromium fetches a module script in
// CORS mode, and file:// is not a CORS scheme, so `import 'file:///…/x.js'` is refused
// there. A classic <script src> is not, which is how React, ReactDOM and Babel already
// load. So each bundle is an IIFE that registers module namespaces on
// globalThis.__switchboardModules, and the runner re-exports them from blob: modules,
// the same bridge it builds for React.
//
// React is never bundled: every `react` / `react-dom` import resolves to the single UMD
// copy the page already loaded. A second React breaks hooks ("invalid hook call").
//
// Usage: node build.mjs <job.json>
//   job = { id, modules: [specifier...], outDir,
//           shadcn?: { componentsTs, clientArtifactsTs, notice, mountedElsewhere: [path...] } }

import * as esbuild from 'esbuild';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const NODE_MODULES = path.join(HERE, 'node_modules');
const SHADCN_SRC = path.join(HERE, '.shadcn-src');

function die(msg) {
  console.error(`build.mjs: ${msg}`);
  process.exit(1);
}

// React 18 ships no jsx-runtime in its UMD build; libraries compiled with the automatic
// runtime import jsx()/jsxs() from it. createElement keeps `children` from the config when
// no positional children are passed, and pulls `key` and `ref` out of it, so passing the
// props through whole is exact -- spreading children positionally is not (see the runner).
const REACT_SHIMS = {
  react: 'module.exports = window.React;',
  'react-dom': 'module.exports = window.ReactDOM;',
  'react-dom/client':
    'module.exports = { createRoot: window.ReactDOM.createRoot, hydrateRoot: window.ReactDOM.hydrateRoot };',
  'react/jsx-runtime': `
    var R = window.React;
    function jsx(type, props, key) {
      if (key !== undefined) { props = Object.assign({}, props); props.key = key; }
      return R.createElement(type, props);
    }
    module.exports = { Fragment: R.Fragment, jsx: jsx, jsxs: jsx, jsxDEV: jsx };`,
};
REACT_SHIMS['react/jsx-dev-runtime'] = REACT_SHIMS['react/jsx-runtime'];

const reactFromPage = {
  name: 'react-from-page',
  setup(build) {
    const filter = new RegExp(
      '^(' + Object.keys(REACT_SHIMS).map((k) => k.replace(/[/.]/g, '\\$&')).join('|') + ')$',
    );
    build.onResolve({ filter }, (args) => ({ path: args.path, namespace: 'react-from-page' }));
    build.onLoad({ filter: /.*/, namespace: 'react-from-page' }, (args) => ({
      contents: REACT_SHIMS[args.path],
      loader: 'js',
    }));
  },
};

/** The shadcn/ui sources web injects into every Sandpack, keyed by the path artifacts import. */
async function loadShadcn(job) {
  const { componentsTs, clientArtifactsTs } = job.shadcn;
  // packages/data-provider/src/artifacts.ts: template-literal sources plus an enum. Compiled
  // and imported rather than parsed, so escapes and any interpolation evaluate exactly.
  const js = (await esbuild.transform(readFileSync(componentsTs, 'utf8'), { loader: 'ts', format: 'esm' })).code;
  rmSync(SHADCN_SRC, { recursive: true, force: true });
  mkdirSync(SHADCN_SRC, { recursive: true });
  const modPath = path.join(SHADCN_SRC, 'components.mjs');
  writeFileSync(modPath, js);
  const { shadcnComponents, artifactUIComponents } = await import(pathToFileURL(modPath).href);
  if (!shadcnComponents) die('upstream artifacts.ts no longer exports shadcnComponents');

  // client/src/utils/artifacts.ts sharedFiles mounts some sources literally ('/lib/utils.ts':
  // shadcnComponents.utils) and spreads artifactUIComponents through a `/components/ui/${name}.tsx`
  // template. Read the template rather than hardcode it, so a moved mount fails the coverage check.
  const client = readFileSync(clientArtifactsTs, 'utf8');
  const mounts = {};
  for (const m of client.matchAll(/['"](\/(?:components\/ui|lib)\/[\w-]+)\.tsx?['"]\s*:\s*shadcnComponents\.(\w+)/g)) {
    mounts[m[1]] = [`shadcnComponents.${m[2]}`, shadcnComponents[m[2]]];
  }
  if (/\bartifactUIComponents\b/.test(client)) {
    if (!artifactUIComponents) die('client artifacts.ts spreads artifactUIComponents, which data-provider no longer exports');
    const template = client.match(/`(\/[\w/-]+\/)\$\{(?!string\})\w+\}\.tsx?`/);
    if (!template) die('found no `/<dir>/${name}.tsx` mount template for artifactUIComponents in client artifacts.ts');
    for (const [name, source] of Object.entries(artifactUIComponents)) {
      mounts[`${template[1]}${name}`] = [`artifactUIComponents['${name}']`, source];
    }
  }
  if (!Object.keys(mounts).length) die('found no shadcn component mounts in sharedFiles');

  // A component can live in another bundle (the Drawer ships with vaul, so declining vaul
  // keeps its code from running at all); those count as listed for the coverage check.
  const wanted = job.modules.filter((s) => s.startsWith('/'));
  const elsewhere = job.shadcn.mountedElsewhere ?? [];
  const extra = Object.keys(mounts).filter((p) => !wanted.includes(p) && !elsewhere.includes(p));
  const gone = wanted.filter((p) => !(p in mounts));
  if (extra.length || gone.length) {
    die(
      'the registry and upstream sharedFiles disagree.\n' +
        (extra.length ? `  upstream mounts, registry omits: ${extra.join(', ')}\n` : '') +
        (gone.length ? `  registry lists, upstream no longer mounts: ${gone.join(', ')}\n` : '') +
        '  Update the shadcn bundle entries in scripts/web-assets.json to match, then --sync.',
    );
  }

  const files = {};
  for (const [mount, [from, source]] of Object.entries(mounts)) {
    if (typeof source !== 'string') die(`${from} (mounted at ${mount}) is not a source string`);
    const file = path.join(SHADCN_SRC, mount.slice(1) + (mount.startsWith('/lib/') ? '.ts' : '.tsx'));
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, source);
    files[mount] = file;
  }
  return files;
}

/** package dir -> { name, version, license, text } for every package the bundle drew from. */
function notices(metafile) {
  const dirs = new Set();
  for (const input of Object.keys(metafile.inputs)) {
    const abs = path.resolve(HERE, input);
    const at = abs.lastIndexOf(`${path.sep}node_modules${path.sep}`);
    if (at === -1) continue;
    const rest = abs.slice(at + 14).split(path.sep);
    const name = rest[0].startsWith('@') ? `${rest[0]}/${rest[1]}` : rest[0];
    dirs.add(path.join(abs.slice(0, at + 14), name));
  }
  return [...dirs]
    .map((dir) => {
      const pkg = JSON.parse(readFileSync(path.join(dir, 'package.json'), 'utf8'));
      const licenseFile = readdirSync(dir).find((f) => /^(licen[cs]e|copying)(\.(md|txt|markdown))?$/i.test(f));
      return {
        name: pkg.name,
        version: pkg.version,
        license: typeof pkg.license === 'string' ? pkg.license : JSON.stringify(pkg.license ?? 'UNKNOWN'),
        text: licenseFile ? readFileSync(path.join(dir, licenseFile), 'utf8').trim() : null,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

async function main() {
  const job = JSON.parse(readFileSync(process.argv[2], 'utf8'));
  if (!existsSync(NODE_MODULES)) die('node_modules missing -- run npm ci in scripts/artifact-modules first');

  const shadcnFiles = job.shadcn ? await loadShadcn(job) : {};
  const lines = [];
  job.modules.forEach((spec, i) => {
    const target = spec.startsWith('/') ? shadcnFiles[spec] : spec;
    if (!target) die(`no source for ${spec}`);
    lines.push(`import * as m${i} from ${JSON.stringify(target)};`);
  });
  lines.push('var r = globalThis.__switchboardModules || (globalThis.__switchboardModules = {});');
  job.modules.forEach((spec, i) => lines.push(`r[${JSON.stringify(spec)}] = m${i};`));

  const result = await esbuild.build({
    stdin: { contents: lines.join('\n'), resolveDir: HERE, sourcefile: `${job.id}-entry.js`, loader: 'js' },
    absWorkingDir: HERE,
    nodePaths: [NODE_MODULES],
    bundle: true,
    format: 'iife',
    platform: 'browser',
    target: 'es2020',
    minify: true,
    legalComments: 'none',
    // ASCII (escape everything else): the React page declares no charset and iOS loads it from
    // a file, so raw UTF-8 bytes in a bundle would depend on the WebView's default encoding.
    charset: 'ascii',
    jsx: 'automatic',
    define: { 'process.env.NODE_ENV': '"production"', global: 'globalThis' },
    plugins: [reactFromPage],
    metafile: true,
    write: false,
    logLevel: 'error',
  });

  mkdirSync(job.outDir, { recursive: true });
  const code = result.outputFiles[0].contents;
  writeFileSync(path.join(job.outDir, 'index.js'), code);

  const parts = [
    `Third-party software in ${job.id}/index.js`,
    'Bundled by scripts/artifact-modules/build.mjs from the exact versions in package-lock.json.',
    '',
  ];
  if (job.shadcn) parts.push(job.shadcn.notice.trim(), '');
  for (const n of notices(result.metafile)) {
    parts.push('='.repeat(78), `${n.name}@${n.version} (${n.license})`, '='.repeat(78));
    parts.push(n.text ?? '(the package ships no license file; license per its package.json)', '');
  }
  writeFileSync(path.join(job.outDir, 'THIRD_PARTY_NOTICES.txt'), parts.join('\n'));
  if (job.shadcn) rmSync(SHADCN_SRC, { recursive: true, force: true });
  console.log(JSON.stringify({ id: job.id, bytes: code.length }));
}

main().catch((e) => die(e.stack || String(e)));
