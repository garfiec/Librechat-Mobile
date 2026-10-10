# Banner

An inline message about the state of the app or server, pinned above the content it affects.

**Consumer provides:** `tone` (`info`, `success`, `warning` or `danger`), `children` (one or two sentences: what happened, then what to do), and optionally `title`, `action`/`onAction` (one text action) and `dismissible`.

- Each tone uses its container pair (`danger-container` / `on-danger-container`, and so on) plus its own icon, so meaning never relies on colour.
- `danger` banners are announced immediately (`role="alert"`). The others are announced politely.
- Use a banner for things that persist (offline, server notice, an update). Use a snackbar for one-off confirmations ("Copied"), which disappear by themselves.
- The server banner (`BannerDisplay`) uses `warning`, and hides dismiss when the server marks it persistable.
