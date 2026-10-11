# SegmentedControl

Picks exactly one of two to four short options, all visible at once. Use it for the theme (System, Light, Dark), the interface style (Material, Liquid Glass) and list sorting.

**Consumer provides:** `options` (`{value, label}[]`, one or two words each), `value`/`onChange` or `defaultValue`, and a `label` for the radio group's accessibility name.

- The selected segment lifts onto `surface-raised` with `elevation-1`, and its text goes from `ink-muted` to `ink`.
- Segments are at least 72px wide and 40px tall, inside a 48px track hit area.
- For five or more options, or long labels, use a ListGroup of radio rows instead.
- Maps to `AdaptiveSegmentedChoice` / `LiquidSegmentedControl`.
