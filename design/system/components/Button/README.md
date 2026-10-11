# Button

Starts an action; the label is a verb that names the result ("Send answer", "Delete conversation").

**Variants, by emphasis.** `filled` (`accent`) is the one primary action on a screen or dialog. `tonal` (`accent-container`) is a secondary action that still matters, such as an empty state's call to action. `outlined` (`border-strong` edge) is an alternative path. `text` is for the low-stakes choice in dialogs ("Cancel") and inline actions. `tone="danger"` recolours any variant with `danger` and is only for actions that destroy data.

**Consumer provides:** `children` (the label, sentence case), `onClick`, optionally `icon` (a leading 20px glyph), `loading`, `disabled`, `size` (`md` 48px by default, `sm` 36px with its hit area padded to `size-touch`) and `fullWidth` (sheet actions on phones).

- Icon-only buttons pass `icon` and `label` (it becomes the accessibility name), with no children.
- Keep a button enabled when it is unavailable for a reason the person can fix, and explain on tap. A disabled button gives no reason.
- `loading` swaps the icon for a spinner and keeps the label, so the width doesn't jump. The button stays focusable with `aria-busy`.
- One `filled` button per view. If a dialog has two, the second becomes `text`.
- Maps to `AdaptiveButton`, `AdaptiveFilledTonalButton`, `AdaptiveOutlinedButton` and `AdaptiveTextButton` in `:core:ui`. In Liquid Glass the same roles become capsules with a glass fill.
