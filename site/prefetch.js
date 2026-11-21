// Downloads the live demo into the browser's cache once the page has loaded and the browser is idle,
// so "Start the demo" opens from the cache. Skipped when the visitor asks to save data, on a slow
// connection, or in a browser that can't run the demo. Each file is fetched one at a time at low
// priority, and only once per visit.
(function () {
  var DONE = 'switchboard-demo-prefetched';

  function store(action) {
    try { return action(window.sessionStorage); } catch (e) { return null; }
  }

  function allowed() {
    var connection = navigator.connection;
    if (connection && (connection.saveData || /2g|3g/.test(connection.effectiveType || ''))) return false;
    if (window.matchMedia && matchMedia('(prefers-reduced-data: reduce)').matches) return false;
    if (typeof WebAssembly !== 'object' || !window.fetch || !window.ReadableStream) return false;
    // Needs WasmGC, as the demo does.
    return WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0]));
  }

  // Reads the body to the end without keeping it: the cache keeps the file only once it's complete.
  function drain(response) {
    if (!response.ok || !response.body) throw new Error('prefetch ' + response.status);
    var reader = response.body.getReader();
    return (function next() {
      return reader.read().then(function (part) { return part.done ? undefined : next(); });
    })();
  }

  function prefetch() {
    if (store(function (s) { return s.getItem(DONE); }) || !allowed()) return;
    var base = new URL('demo/', document.baseURI);
    fetch(new URL('files.json', base), { cache: 'no-cache' })
      .then(function (response) { return response.json(); })
      .then(function (files) {
        return files.reduce(function (previous, file) {
          return previous.then(function () {
            return fetch(new URL(file, base), { priority: 'low' }).then(drain);
          });
        }, Promise.resolve());
      })
      .then(function () {
        store(function (s) { s.setItem(DONE, '1'); });
        document.dispatchEvent(new Event('switchboard-demo-prefetched'));
      })
      .catch(function () {});
  }

  function whenIdle() {
    if (window.requestIdleCallback) requestIdleCallback(prefetch, { timeout: 5000 });
    else setTimeout(prefetch, 2000);
  }

  if (document.readyState === 'complete') whenIdle();
  else window.addEventListener('load', whenIdle);
})();
