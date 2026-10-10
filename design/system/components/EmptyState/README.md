# EmptyState

Fills a list or screen that has nothing to show yet, with one line of context and one way forward.

**Consumer provides:** `title` (what's empty: "No conversations yet"), optionally `children` (one sentence on why, or what will appear), `icon`, and `action`/`onAction` (one tonal button).

- The icon sits in an `accent-container` disc. Don't use illustrations or emoji.
- The title is `title-md` and the line below is `body-md` `ink-muted`. The block is centred, at most 320px wide, with `space-10` above it.
- A search with no results says what was searched ("No results for "kotlin"") and offers to clear the filters, not to create something.
- Errors aren't empty states. When loading fails, show a `danger` Banner with Retry instead.
