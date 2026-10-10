# Chip

A compact toggle or shortcut, shown in rows: tool toggles above the composer, filters on the conversation list, and suggested prompts.

**Consumer provides:** `children` (one to three words), and either `kind="filter"` (default, a toggle with `selected`/`defaultSelected`/`onChange`) or `kind="assist"` (an action, with an optional `icon`).

- Selected filter chips fill with `accent-container` and gain a check icon, so selection never relies on colour.
- The chip is 36px tall, but its hit area extends to `size-touch` vertically. Leave `space-2` between chips.
- Use a chip row only when people pick several options or none. For exactly one of 2–4 options, use SegmentedControl.
- Maps to `AdaptiveFilterChip` and `AdaptiveAssistChip`.
