# TextField

A labelled single- or multi-line input with helper and error text.

**Consumer provides:** `label` (always visible, never only a placeholder), `value`/`onChange` or `defaultValue`, and optionally `placeholder` (an example, in `ink-subtle`), `helper`, `error`, `icon`, `type`, `multiline` and `disabled`.

- The label sits above the box and stays put, so it never collides with typed text at large font sizes.
- The box is `size-control` (48px) tall, with a `radius-md` corner and a `border-strong` outline (3:1). Focus thickens it to 2px `accent`.
- `error` replaces `helper` and draws the outline and label in `danger`, with an error icon. Validate on blur or submit, never on each keystroke. Say how to fix it: "Use at least 8 characters".
- Text inside is `body-lg` (16px), which also stops iOS from zooming in on focus.
- Maps to `AdaptiveOutlinedTextField`. Liquid Glass keeps the same anatomy on a `surface-sunken` well with no outline.
