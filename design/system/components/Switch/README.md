# Switch

Turns a setting on or off immediately, with no Save step.

**Consumer provides:** `label`, optionally a `description`, `checked`/`onChange` or `defaultChecked`, and `disabled`.

- The whole row is the target, not just the track. The row is `size-control` (48px) or taller.
- On fills the track with `accent` and shows a check in the thumb. Off shows a `border-strong` outline on `surface-sunken`. The state reads without colour.
- If the change needs confirmation or a network round trip, show progress in the row and roll back on failure with a Banner.
- Maps to `AdaptiveSwitch`. Liquid Glass draws the iOS-style switch with the same `accent` tint in both themes (no pastel dark variant).
