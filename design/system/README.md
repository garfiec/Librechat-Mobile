Tchat is a native chat client for a self-hosted LibreChat server, on Android and iOS. People open it to ask a question, read a long answer, and come back to the conversation later. The system serves that: the transcript is the product, and everything else gets out of its way.

It keeps the app's two interface styles, **Material** and **Liquid Glass**. Both draw from these tokens, so a screen never branches on style; only the `Adaptive*` components in `:core:ui` do.

## Principles

1. **Reading comes first.** Messages are set in `body-lg` (16/24) on `bg`, at most `size-reading` (720px) wide. Assistant replies have no bubble and run the full width. Only the user's own messages sit in `bubble-user`.
2. **One accent, used for action.** `accent` marks what you can tap to move forward: send, the filled button, a selected chip, an on switch. If two things on a screen are accent-filled, demote one to tonal (`accent-container`).
3. **Legible in every theme.** Every text token is checked at 4.5:1 or better on the surfaces its note names, in light and in dark. Control outlines and the focus ring are checked at 3:1. Glass never lowers these floors.
4. **Thumb-sized and forgiving.** Every hit target is at least `size-touch` (48px). Destructive actions ask once, and an edit always offers a way back.
5. **Calm motion.** Motion shows where something came from and where it went. It never decorates, and it turns off with the system's reduce-motion setting.

## Content fundamentals

- **Sentence case everywhere**: buttons, titles, menu items, settings rows. Write "New chat", "Copy link" and "Save as preset", not "New Chat", "Copy Link" or "Save as Preset". The shipped strings mix both. New copy follows this rule, and old copy is fixed when it's touched.
- **Address the person as "you"**, and call the app "Tchat". Never say "we" unless the server operator is speaking, as in the server banner.
- **Buttons are verbs that name the result**: "Delete conversation", "Keep editing", "Send answer". Avoid "OK" and "Yes". A confirm dialog's title asks the question ("Discard title change?"), and its buttons answer it.
- **Errors say what happened and what to do next**, in that order: "Couldn't reach the server. Check your connection and try again." Don't blame the person, and don't show raw status codes unless a details row is expanded.
- **Empty states have one line of context and one action**: "No conversations yet", then "Start a new chat". The composer placeholder is "Message…", or "Message {agent}" when an agent is selected.
- **Use an ellipsis (…) only when a label opens something that needs more input**, like "Rename…". Write the real character, not three dots.
- **No emoji in the UI chrome.** Emoji in message content render as typed.
- **Accessibility labels describe the action**, not the icon: "Send message", "Stop generation", "Open navigation drawer". They're never "Arrow" or "Square".

## Color

- **Grounds.** Use `bg` behind screens, `surface` for cells, cards and the composer, `surface-sunken` for wells (search, segmented tracks), `surface-raised` for menus, and `surface-overlay` for dialogs and sheets. In dark mode, each step is lighter than the one before. In light mode the steps are white and shadow (`elevation-*`) does the separating.
- **Text.** Set primary text in `ink`, secondary text in `ink-muted`, and placeholders in `ink-subtle`. The subtle tone still passes 4.5:1, so a placeholder is never the only legible hint. Links and accent text use `accent-ink`, never `accent`.
- **Accent.** `accent` and `on-accent` mark filled actions. `accent-container` and `on-accent-container` mark selected or tonal states. In dark mode `on-accent` is the dark ink, because white fails on the lighter accent.
- **Status.** Use `danger`, `success` and `warning`, each with a `-container` and `on-…-container` pair for banners. **Status never relies on color alone**: each one carries an icon (error, check, warning triangle) and a word. `success` is a bluish green, but `danger` and `success` are close in lightness, so the icon is what tells them apart for people with color-vision deficiency.
- **Lines.** `border` is decorative: separators and card hairlines. `border-strong` marks the edge of a control: inputs, outlined buttons, the off switch. It meets 3:1.
- **Identity.** `brand` (#4F8CFF), `brand-violet` and `ground` are for the logo, splash and launcher only. `brand` is 3.2:1 on white, so it never carries text in light mode. The accent is `brand` adjusted for contrast.
- **Charts.** Use `series-1` to `series-8` in order. Never pick colors out of order: the order is what keeps adjacent series distinguishable.
- **Avatars.** Letter avatars use `avatar-1` to `avatar-14`, chosen by a stable hash of the account id and identical in both themes. White initials pass 4.5:1 on all fourteen.
- **User-chosen accents.** The accent picker stays, but a chosen seed feeds `accent`, `accent-container` and `focus-ring` only, with the tone clamped so `on-accent` keeps 4.5:1. Neutrals stay neutral and don't pick up the hue.

## Typography

The system faces are SF Pro on iOS and Roboto on Android. They're tuned for each screen, they ship Dynamic Type and font scaling, and they cost nothing to load. Sizes are in sp/pt and **scale with the user's font-size setting up to 200%**. Layouts wrap and grow; they never truncate a message.

| Style | Size / line | Weight | Use |
| --- | --- | --- | --- |
| `display` | 32 / 40 | 700 | Empty-state and onboarding hero, one per screen |
| `headline` | 24 / 32 | 600 | Large screen titles |
| `title-lg` | 20 / 28 | 600 | Top bar, dialog and sheet titles |
| `title-md` | 17 / 24 | 600 | Section headings, card titles |
| `title-sm` | 15 / 20 | 600 | Conversation titles, the primary line of a row |
| `body-lg` | 16 / 24 | 400 | Chat messages, the composer |
| `body-md` | 15 / 22 | 400 | Rows, descriptions, dialog body |
| `body-sm` | 13 / 18 | 400 | Meta, timestamps, helper and error text |
| `label-lg` | 15 / 20 | 600 | Buttons |
| `label-md` | 13 / 16 | 600 | Chips, tabs, segments |
| `label-sm` | 12 / 16 | 500 | Badges, endpoint tags |
| `code` | 14 / 20 | 400 mono | Code blocks, inline code |

- Use **at most three styles per component**. Build hierarchy from weight and color (`ink` vs `ink-muted`) before size.
- **Nothing is smaller than 12px.**
- Don't stretch line length: a message column caps at `size-reading`.
- Numbers that change in place, like token counts and timers, use tabular figures.

## Spacing and layout

- **4px grid**: `space-1` (4), `space-2` (8), `space-3` (12), `space-4` (16), `space-5` (20), `space-6` (24), `space-8` (32), `space-10` (40), `space-12` (48) and `space-16` (64). Use `space-0-5` (2) only for optical nudges. Values off the grid aren't allowed.
- **Gutters.** Phones use `space-4`. Tablets use `space-6` and center the content column at `size-reading`.
- **Rhythm in the transcript.** Leave `space-3` between messages from the same sender and `space-5` when the sender changes. Bubbles are padded `space-2` vertically and `space-3` horizontally.
- **Lists.** Rows are at least `size-control` (48) tall and padded `space-3` × `space-4`. Groups are separated by `space-6`, and a group's heading sits `space-2` above it.
- **Reachability.** Primary actions sit in the bottom half of the screen: the composer, sheet actions and the FAB. Top bars hold navigation and secondary actions.
- **Adaptive layout.** On compact widths, use a single pane with a drawer. From 600dp, use a list–detail pair: the conversation list next to the transcript. The tablet layout must never stretch the phone layout across the screen.

## Shape

- Use `radius-xs` (4) for inline code and badges, `radius-sm` (8) for chips and code blocks, `radius-md` (12) for inputs, cards, cells, menus and banners, `radius-lg` (20) for bubbles and the composer, `radius-xl` (28) for dialogs and sheet tops, and `radius-full` for pills, switches, avatars and the send button.
- **Nest smaller inside larger**: inner radius = outer radius − padding. Nine ad-hoc radii in the code collapse into these six.

## Elevation and Liquid Glass

- In light mode, `elevation-1` goes on cards and the composer, `elevation-2` on menus and snackbars, and `elevation-3` on dialogs and sheets. In dark mode, elevation comes from the surface step, and the shadows are near-invisible on purpose.
- **Liquid Glass** is a material for floating chrome, never for content: bars, the composer shell, menus, sheets and segmented controls. Glass tints sampled content at `opacity-glass-tint` (0.4) and adds a 0.5pt `border` hairline. Text on glass uses the same `ink` tokens. If the backdrop is busy enough to drop contrast, the panel thickens; it never thins.
- When the system's **Reduce Transparency** setting is on, or the device can't blur (tier FLAT), glass falls back to a solid `surface-raised` with `stroke-hairline` `border`. That fallback is the contract, not a degradation.

## Interaction states

- **Pressed**: an `ink` overlay at `opacity-pressed` (0.12), or `on-accent` over filled controls. Material keeps its ripple. Glass scales to 0.97 with a spring (the press bounce).
- **Focus**: a solid `stroke-focus` (2px) `focus-ring` ring, 2px outside the control and following its radius. It's shown for keyboard, switch access and D-pad input, never on touch.
- **Disabled**: the whole control at `opacity-disabled` (0.38), never recolored. If an action is unavailable for a reason the person can fix, keep it enabled and explain on tap.
- **Selected**: `accent-container` fill with `on-accent-container` content, plus a check icon on chips. Selection never depends on color alone.
- **Loading**: when the wait is known, show progress in `accent`. Use skeleton rows in `surface-sunken` for lists, and keep a spinner only for under ~2 seconds of unknown wait. While a reply streams, the send button becomes "Stop generation".

## Motion

- **Durations**: 100ms for micro feedback (press, toggle), 200ms for small transitions (chips, menus), 300ms for screen transitions and sheets, and never above 400ms.
- **Easing**: standard (0.2, 0, 0, 1) for things moving on screen, decelerate (0, 0, 0, 1) when entering, and accelerate (0.3, 0, 1, 1) when exiting. Glass controls use springs (damping ≈ 0.8).
- **Streaming text** appears as it arrives, without a per-token fade. The transcript auto-follows only while the person is already at the bottom; once they scroll up, show a "Scroll to bottom" pill and stop following.
- **Reduce motion**: replace movement with cross-fades at ≤150ms, and turn off the press bounce and glass highlight tilt.

## Iconography

- Use **Material Symbols (Filled, 24dp grid)**: `Icons.Filled.*`, plus `AutoMirrored` for anything directional, so arrows flip in right-to-left languages. Use Outlined only for the unselected half of a toggle pair.
- On iOS, Liquid Glass bars render **SF Symbols** through `BarIcons`. Keep each Material icon mapped to its SF Symbol counterpart there.
- Sizes are `size-icon-sm` (16) in chips, `size-icon-md` (20) in buttons and fields, and `size-icon-lg` (24) standalone. An icon's hit target is always `size-touch`.
- Icons take `ink`, `ink-muted` or `on-*` colors, and a meaningful icon meets 3:1.
- **Icon-only buttons need an accessibility label.** If the action isn't obvious (anything beyond send, close, back or more), add a text label or a tooltip on long-press.
- Logos live under **Logos**. The mark is the T+dot glyph in the `brand` → `brand-violet` gradient on `ground`. Don't redraw it, recolor it, or set it on light grounds.

## Accessibility checklist

- Text meets 4.5:1, and large text, icons and control borders meet 3:1, in both themes. The token notes name the pairs that were checked.
- Hit targets are 48×48 or larger, with at least 8px between neighboring targets.
- Layout works at 200% font scale and in landscape. Messages never truncate.
- Every icon-only control has a label. Message bubbles announce the sender, time and content as one node.
- Status is shown with an icon and text as well as color. Selection is shown with a check as well as a fill.
- Reduce Motion and Reduce Transparency are honored on both platforms.
- Streaming replies announce politely at completion ("Response complete"), not token by token.
