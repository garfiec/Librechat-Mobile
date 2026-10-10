# ListGroup

A grouped, inset list of rows: the shape of every settings page and of account and data menus.

**Consumer provides:** an optional `title` (a short, sentence-case group name) and `footer` (a one-line explanation), with `ListGroup.Row` children. Each row takes `title`, plus optional `subtitle`, `icon` (24px, `ink-muted`), `leading`, `value` (the current choice, in `ink-muted`), `trailing` (such as a Switch), `chevron` (it opens a page), `onClick` and `tone="danger"`.

- The page is `bg` and cells are `surface` with `radius-md`. Separators are `border` hairlines, inset to the text when the row has an icon.
- Rows are at least `size-control` tall and padded `space-3` × `space-4`. Groups are `space-6` apart.
- A row that navigates shows a chevron. A row that toggles holds a Switch. A row that acts immediately (Sign out) has neither and uses `tone="danger"` if it's destructive.
- Destructive rows sit in their own last group.
- Maps to `AdaptiveGroupedList`, with `GlassControlColors.groupedBackground` and `cell` in both styles.
