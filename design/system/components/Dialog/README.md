# Dialog

Interrupts to confirm a consequential choice. Its title asks the question, and its buttons answer it.

**Consumer provides:** `title` (a question: "Delete conversation?"), `children` (one or two sentences on the consequence), `confirmLabel` (a verb: "Delete"), optionally `dismissLabel` (default "Cancel"), `icon`, `tone="danger"`, `onConfirm` and `onDismiss`.

- Use `surface-overlay`, `radius-xl` and `elevation-3` over the `scrim`. The title is `title-lg` and the body is `body-md` in `ink-muted`.
- The confirm button is on the right and is the only filled one. Dismiss is a `text` button. Back, a tap on the scrim, and Escape all dismiss.
- Use a dialog only when the action can't be undone. For anything undoable, act immediately and offer Undo in a snackbar.
- Any dialog with a text field applies `clearFocusOnTap` to its content root (see `core/ui/CLAUDE.md`).
- Maps to `AdaptiveAlertDialog` and `DiscardChangesDialog`.
