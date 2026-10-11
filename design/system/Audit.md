# Audit: the shipped theme and what changed

The shipped theme, as defined in `:core:ui` (`theme/`, `glass/` and `components/AvatarColors.kt`), and the fix for each UX problem it has. Values in this section are what the app paints today. Everything else in this system is the redesign.

## Color

The app has no fixed palette. `LibreChatTheme` generates the whole Material 3 scheme from one seed, `#4F8CFF`, with MaterialKolor `PaletteStyle.TonalSpot` (2021 spec). TonalSpot keeps only the seed's hue, so **no role ever paints the brand blue**: the primary renders as a muted slate.

| Role | Light | Dark |
| --- | --- | --- |
| primary / onPrimary | #465d91 / #ffffff | #afc6ff / #132f60 |
| primaryContainer / on | #d9e2ff / #2d4678 | #2d4678 / #d9e2ff |
| secondary | #575e71 | #bfc6dc |
| tertiary | #725573 | #dfbbde |
| error / onError | #ba1a1a / #ffffff | #ffb4ab / #690005 |
| surface / onSurface | #faf9ff / #1a1b20 | #121318 / #e2e2e9 |
| onSurfaceVariant | #44474f | #c5c6d0 |
| surfaceContainerLowest…Highest | #ffffff → #e2e2e9 | #0c0e13 → #33353a |
| outline / outlineVariant | #757780 / #c5c6d0 | #8f9099 / #44474f |

**Problems**

- The brand blue (#4F8CFF) never appears in the UI. The primary is a desaturated #465d91, which weakens both identity and affordance.
- The seed hue tints every neutral, so a user-chosen accent like pink turns every surface pink-grey.
- `outlineVariant` (#c5c6d0 light) is used for hairlines, but it's only 1.6:1 on surface, too faint for a control edge.
- The dark-mode surfaces (#121318) ignore the brand ground #0C0F1A, which the splash and launcher already use, so there's a visible jump at launch.
- `error` and the absent success color both rely on hue alone.
- Glass controls switch to `inversePrimary` in dark mode, which is a third accent value.

**Fix**: fixed semantic tokens (`bg`, `surface*`, `ink*`, `accent*`, status trios), each checked at 4.5:1 (text) or 3:1 (marks) in both themes, with the dark ground set to `ground`. The accent seed now drives only `accent`, `accent-container` and `focus-ring`.

## Typography

`libreChatTypography` overrides 7 of Material 3's 15 styles. The other 8 fall back to the defaults.

| Style | Shipped | Uses in code | Problem | Now |
| --- | --- | --- | --- | --- |
| bodySmall | M3 default 12/16 | **259** | the most-used style is the smallest | `body-sm` 13/18 |
| bodyMedium | 14/20 | 157 | fine, slightly tight for rows | `body-md` 15/22 |
| bodyLarge | 16/24 | 103 | — | `body-lg` 16/24 |
| labelSmall | 12/16 Regular | 89 | badges blend into body | `label-sm` 12/16 Medium |
| titleSmall | 14/20 **Regular** | 76 | identical to bodyMedium, so titles don't out-rank text | `title-sm` 15/20 Semibold |
| labelMedium | M3 default | 50 | — | `label-md` 13/16 Semibold |
| titleMedium | 16/24 SemiBold | 41 | — | `title-md` 17/24 |
| labelLarge | M3 default | 30 | — | `label-lg` 15/20 Semibold |
| headlineMedium | 24/32 Medium | 14 | — | `headline` 24/32 |
| headlineLarge | 30/36 Bold | 3 | — | `display` 32/40 |

There are also literal `fontSize` values in feature code (10, 11, 13, 22sp…). A 10sp value is below any comfortable reading size, so those move to the scale.

## Spacing

Of all the `.dp` literals in `padding`, `spacedBy` and `size`, these are the most frequent: 8 (426), 16 (366), 12 (229), 4 (197), 24 (113), **18 (71)**, 20 (66), **6 (47)**, 32 (42), 2 (32), **10 (13)**, **14 (6)** and **3 (5)**. The bold values are off the 4px grid. **Fix**: an 11-step 4px scale, with each off-grid value mapped to its nearest step.

## Shape

`libreChatShapes` defines 8, 16, 24 and 28, but code also uses `RoundedCornerShape` literals of 2, 4, 6, 8 (59 times), 12 (26), 16, 24, 28, 32 and 999: **nine distinct radii**. Bubbles are 16, menus 16 and glass menus 16. **Fix**: six steps (`radius-xs` to `radius-full`), with bubbles and the composer at `radius-lg` (20).

## Avatars

There are 14 fixed avatar colors with white initials. Five of them fail 4.5:1: #f57c00 (2.7:1), #689f38 (3.2), #0288d1 (3.9), #e64a19 (3.9) and #388e3c (4.1). **Fix**: those five are darkened within their own hue (`avatar-7`, `-9`, `-10`, `-11`, `-12`). The slot count and order are unchanged, so every account keeps its color family.

## Kept as is

- `series-1` to `series-8` (LibreChat web's color-vision-safe series order).
- The glass tint floor (0.4) and the hairline and disabled alpha (0.4 → `opacity-disabled` 0.38).
- The Material / Liquid Glass split through `Adaptive*` components, and `clearFocusOnTap` keyboard dismissal.
- Sheet and dialog corner radius of 28.
