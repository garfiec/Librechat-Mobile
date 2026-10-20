# Small-screen reference

The shared knowledge every small-screen agent reads: what to test against, how to judge a finding,
which fix each defect class gets, and what the renders can't show. `SKILL.md` says when to use
each part; the workflows point their agents here.

## The bug class

UI that breaks when the **effective width in dp** is small. Three things shrink it, separately or
together: a narrow screen, a large font scale, and a translation longer than English. Many defects
break in German or French at 1.0x on an ordinary phone, so font scale is not the only axis.

## Test matrix

| Axis | Values |
| --- | --- |
| Width | **320dp** (narrowest supported Android phone), 360dp, **375pt** (iPhone SE). 600–720dp for split panes and the Fold |
| Height | 640dp normal; **569** (320dp hdpi phone with 3-button nav); 667pt (SE); **≈388** (SE with the keyboard up); 521 (320×569 minus the nav bar) |
| Font scale | Android up to **2.0**, and non-linear on API 34+: text up to ~14sp roughly doubles, but titleLarge 22sp lands near 37sp, not 44. iOS Compose maps Dynamic Type to fontScale linearly, capped at **1.8**. Native UIKit views go to ~310%. The common real-world middle is 360–375dp at 1.3–1.5x |
| Locale | de, fr, es, ru, ja, ar (RTL), pt, ko, zh. Always judge with the **real worst translation** of the key, from `worst-strings.tsv` (`scripts/worst_strings.py`; CJK counts as width 2). Never guess a translation |
| Style | `UiStyle.MATERIAL` and `UiStyle.LIQUID_GLASS`. `Adaptive*` components self-theme, so a screen's fit can differ by style. iOS defaults to glass |
| Keyboard, nav bar | Robolectric has no IME. Simulate it with a shorter host height (SE keyboard ≈388pt), or by sending a synthetic IME inset to the compose view. 3-button nav takes 48dp |

**`@PreviewNarrowScreens`** (`core/ui/src/androidMain/.../preview/PreviewNarrowScreens.kt`) is the committed preview matrix: 320 at 1.0x and 2.0x, 375 at 1.0x and 1.5x, and 375 at 1.3x in German. It is Android-only because the common `@Preview` has no `fontScale`. Dialog previews go through `DialogPreviewHost` in the same package.

**Every render case is a pair:**
- The **trigger** (`<id>-<slug>`) renders at the finding's minimum trigger and must show the fix.
- The **control** (`<id>-<slug>__control`) renders at 360×640dp, 1.0x, English, same style. It must look the same before and after; that is the proof normal phones didn't change.

### Arithmetic givens

- Read `core/ui/.../theme/Type.kt` once for the type scale.
- A Latin glyph is about 0.55 × the font size (labelLarge 14sp ≈ 7.7dp per character at 1.0x). Bold and caps are wider.
- Default M3 sizes: Button content padding 24dp horizontal, TextButton 12dp, IconButton 48dp (or whatever size the code sets). Dialogs are 280–560dp wide with 24dp padding.

## Judging a finding

- **Fit depends on the caller.** When a component takes labels or slots, grep its call sites
  (`core/ feature/ shared/ app/`) and judge with the real strings and real container widths.
- **Trace the available width from the screen edge inward**: screen padding, card padding, sibling
  widths. Show the arithmetic: available dp against required dp at the triggering condition.
- **Report the minimum trigger**, the mildest condition that already breaks. "en, 1.0x, 320dp"
  beats "de, 2.0x".
- **Quote the worst string and its length.**

**Not defects:**
- ellipsized *user content* (conversation titles, file names, model names) where ellipsis is intended;
- weighted text that wraps to a reasonable number of lines;
- content inside a scrolling parent;
- deliberate ellipsis on non-essential text.

An ellipsized *fixed UI label* (tab name, button label) **is** a defect, low or medium.

**Verifying a finding:**
- Try to refute it: is the text actually weighted, ellipsized, in a scrolling parent, constrained differently, or given a different string? Is the arithmetic wrong?
- **Default to REFUTED** when the breaking condition can't be shown concretely.
- Use PLAUSIBLE when it plausibly breaks but depends on runtime data or an uncertain measurement.

### Severity

| Severity | Rule |
| --- | --- |
| high | An action becomes unreachable or clipped, or essential content unreadable, at ≤1.3x; **or anything that breaks in English at 1.0x on 320dp** |
| medium | Breaks only at 1.8–2.0x, or only in de/fr/ru etc. at a moderate scale |
| low | Cosmetic: an ugly wrap, an ellipsized fixed label, hardcoded English |

### Finding ids and the table

- An id is `S<shard>-<lens><n>`, for example `S04-V4` or `S19-H2`. A verifier's own find is `S<shard>-X<n>`.
- Table columns, in this order: `id | sev | ver | cls | file:line | title | min trigger | prior | batch | root | fix hint`.
- `ver` is C (confirmed) or P (plausible).
- `prior` names a previous audit's row for the same defect at the same site, or N.
- `root` tags the shared component the fix lives in (e.g. DLG-BTN, DLG-TXT, CHOICE, TOPBAR, SHELL, ACTIONROW, PARAM, FIELD). Leave it empty when the fix is local.

## Defect classes and their fixes

Use the helper the repo already has before writing a new one.

| Class | What breaks | Fix |
| --- | --- | --- |
| **C1** | Text with `maxLines` and no overflow: the last line is clipped mid-glyph | Add `overflow = TextOverflow.Ellipsis` |
| **C2** | Text squeezed by unweighted siblings in a Row, a weighted label starved to a sliver, or a control pushed past the edge | The label takes `weight(1f, fill = false)`; the trailing value, chevron or button stays unweighted with `maxLines = 1`. Cap a trailing badge at a share of the row with `Modifier.widthInFraction(max)` (`feature/chat/.../WidthInFraction.kt`), never a fixed dp. When the title must win, measure it in a custom `Layout` (`TitleFirstLine` in `feature/conversations/.../search/HitMeta.kt`) |
| **C3** | A row of buttons, chips or actions that never wraps | `FlowRow(horizontalArrangement = spacedBy(8.dp, Alignment.End))`. For a dialog, confirm comes first: at the end when side by side, on top when stacked (`GlassFloatingDialog`). Or move the primary action to its own line, or move actions into an overflow menu below a width (the message action row) |
| **C4** | A fixed height or size on something that holds text | `heightIn(min = …)`. A glass segment's height comes from the measured text |
| **C5** | Equal cells, tabs or segments that clip or break translated labels mid-word | **Measure, then fall back**: `rememberChoiceLabelFit` + `ChoiceFallback.RADIO_LIST` for settings choices, `TWO_LINES` for strips that switch content (`core/ui/.../ChoiceFit.kt`). `latinWords()` stops a Latin word breaking mid-word. `AdaptiveTabRow` scrolls when a title doesn't fit (`fitTitles` for counts that change) |
| **C6** | A sheet, dialog or screen body that can outgrow the viewport and doesn't scroll, including with the keyboard up on an SE | `verticalScroll` on the caller, or one `LazyColumn`. Dialogs: `AdaptiveAlertDialog` scrolls its text slot by default (`scrollableText`); pass `scrollableText = false` where the slot scrolls itself. The detekt rule `AlertDialogNestedScroll` enforces the opt-out |
| **C6 (insets)** | Content under the keyboard or the system bars | `imePadding()`. `windowInsetsPadding(WindowInsets.safeDrawing)` goes **before** `verticalScroll` on auth roots and full-screen WebViews. Menus are inset by `safeDrawing` |
| **C7** | Fixed pane widths or width breakpoints that leave a too-narrow content pane | Thresholds come from the content, not the window class: the sidebar shows only when width − 320 ≥ `MinContentWidth` (360dp) and height ≥ `MinSidebarWindowHeight` (480dp) (`shared/.../TabletLayout.kt`). Grids use `GridCells.Adaptive` |
| **C8** | `fillMaxWidth(0.x)` or `widthIn(max)` that starves content on a narrow screen | Drop the fraction below a width, or use `widthInFraction` |
| **C9** | iOS native UIKit text that wraps or clips | `titleLineBreakMode = NSLineBreakByTruncatingTail`, a pinned font with the large-content viewer (`NativeGlassTopBar.ios.kt`). Not previewable: say so in the result |
| **C10** | Hardcoded English or hand-rolled plurals | Move to the owning module's `composeResources` in **all 10 locales** with real translations. Use `pluralStringResource` for counts and the locale's number format for numbers. Never reuse a key only because the English matches |
| **C11** | Anything else in this bug class | — |

Other patterns that recur:
- **The top bar.** `TitleFirstBar` collapses a `BarAction.Text` to its `compactIcon` only when the title would be squeezed. Plain titles get `maxLines = 2`.
- **Inline if it fits, else stack.** Use a multi-content `Layout`, measuring with `rememberTextMeasurer` on the same text and style the visible `Text` uses (`PausePanelCompactHeader`).
- **The translation is too long even with the fallback.** Shorten the translation, in that one locale.

### Rejected approaches

- **Shrinking the font to make text fit.** The user ruled this out: "this may cause some maintainability issues in the long run."
- **Window-size-class breakpoints for content fit.** Layout follows the content. The window-size-class dependency was removed on purpose.
- **A plain `weight(1f)` label inside a `FlowRow`.** It never wraps, because a weighted item's minimum intrinsic width is its longest word. Give it `widthIn(min = 120–160.dp)` instead.
- **Two weighted siblings (`weight(1f, false)` + `weight(2f, false)`) in a Row.** A Row never gives back the unused part of a weighted child's share.
- **A fixed-dp cap on a badge.** Use a fraction of the row.
- **A `SubcomposeLayout` where a multi-content `Layout` does.** Likewise a duplicated title style just for the fit check.
- **A central `verticalScroll` in a shared sheet.** It crashes the callers that hold a `LazyColumn`. Scroll per caller.
- **A full-screen dialog as a fallback.**

## Rules for every fix

- `Adaptive*` components over raw Material 3 chrome (detekt bans raw chrome).
- UI distances in multiples of 4dp.
- No new `@Suppress` unless it can't be avoided, and then say why.
- Match the surrounding code's comment density and naming.
- **Normal widths must not get worse.**
  - A FlowRow on a wide row still lays out on one line.
  - A weight change doesn't stretch anything that was sized on purpose.
  - Tablets and the Fold keep their layouts.
  - Every behaviour change visible on an ordinary 360dp phone at 1.0x is listed in the result.
- Comments state the rule the code follows, in the present tense ("drops below the label when it doesn't fit"). They never narrate the fix ("now", "used to", "the old height") and never cite audit ids.
- Commit messages: Conventional Commits, no co-author, session or generated-by lines.

## Writing previews

A preview shows a layout at a chosen size, font scale and locale without building or installing the app. Write one for every component or screen a finding names, **before changing production code**. Commit them on their own, so the before renders show the code as it was. Previews are also how a fix is checked while it is written, and the render harness reuses their hosts.

**Where they go.** Put them in a `*Previews.kt` file in the module's `androidMain`, beside what they preview (e.g. `feature/chat/src/androidMain/.../components/PausePanelPreviews.kt`). Use `androidx.compose.ui.tooling.preview.Preview`:
- Only the Android `@Preview` takes `fontScale` and `locale`, which is why `@PreviewNarrowScreens` lives in `androidMain`. Android and iOS share the composables being previewed, so a preview shows what iOS lays out at the same width and scale. The exceptions are `iosMain` code and native UIKit views.
- `commonMain` has a few `org.jetbrains.compose.ui.tooling.preview.Preview` previews (the `Dynamic*` parameter controls). Don't use that one for fit checks.

**The shape.** A preview function is one line: a theme surface, a host, a fixture.
- The **host** is an `@Composable` that holds the component's state with `remember`, the way its real owner hoists it, so interactive mode can tap and page.
- A **fixture** is a `val` of fixed data with the long strings and the state that trigger the finding: the worst translation, a long file name, a full batch.
- Hosts never load anything. Pass the loaded, failed or empty state directly. A host that fetches renders its spinner.

```kotlin
// State is hoisted the way the screen's ViewModel hoists it, so interactive mode works.

@PreviewNarrowScreens
@Preview(name = "SE · keyboard up", widthDp = 375, heightDp = 388, fontScale = 1.8f, showBackground = true)
@Composable
private fun RenameMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { RenameHost(longTitle) }

@PreviewNarrowScreens
@Composable
private fun RenameGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { RenameHost(longTitle) }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun RenameHost(initial: String) {
    var text by remember { mutableStateOf(initial) }
    RenameField(value = text, onValueChange = { text = it }, onSave = {}, onCancel = {})
}

private val longTitle = "Quarterly planning notes for the infrastructure migration"
```

The names are illustrative; `PausePanelPreviews.kt` and `FilePreviewFallbackPreviews.kt` are real files in this shape.

**Sizes:**
- `@PreviewNarrowScreens` gives the standard five (320 at 1.0x and 2.0x, 375 at 1.0x and 1.5x, 375 at 1.3x in German).
- Stack extra `@Preview`s for what a finding needs:
  - a height (`heightDp = 388` for an SE with the keyboard up, `569` for a 320dp phone with 3-button nav);
  - a landscape phone (`widthDp = 640, heightDp = 360`);
  - another locale (`locale = "fr"`);
  - a Fold or tablet width (`widthDp = 600`–`720`).
- UI labels come from `stringResource`, so the `locale` parameter switches them: pick the locale `worst-strings.tsv` names for the key. Only user content (titles, names, messages) goes in a fixture as a literal.

**Both styles** wherever the style changes the layout: dialogs, sheets, segmented controls, tabs, toasts. Glass draws its own versions of these, so one style passing says nothing about the other.

**Dialogs and sheets:**
- Wrap them in `DialogPreviewHost(style, height = …)` (`core/ui/src/androidMain/.../preview/`). Glass portals dialogs and sheets into that host, so its `height` is the room the dialog gets.
- A shorter height stands in for the keyboard.

**Viewing them.** Android Studio renders a `*Previews.kt` file in its Split or Design view as soon as the module compiles. No app build, emulator or device is involved.
- Each `@Preview` is one frame; the five from `@PreviewNarrowScreens` appear under the group "narrow".
- Interactive mode runs the host's state, so taps, paging and collapsing work.
- "Run Preview" installs it on a device: don't use it for fit checks.
- For the before/after record the user reviews, render the same hosts headlessly with `scripts/render.py`. A screenshot of the IDE pane doesn't pair before and after.

**Keep them.** Previews stay committed after the fix, as the regression check for the next change. Use the names `<What><Style>Preview`, and say in the file's first comment what state the hosts reproduce.

## What renders can't show

- **A render is one still frame from the top.** Where a fix makes content scroll, the after frame can show *less* than the before. Render a second, scrolled-to-the-end frame for those cases (`"scrolled": true` in the cases file).
- **Robolectric gaps:**
  - No IME.
  - Popups and dropdown menus can't be captured.
  - WebViews draw nothing (Mermaid, KaTeX, SSO).
  - Glass renders flat.
  - No hyphenation data.
  - Nothing in `iosMain` can be rendered.

  Say which findings these leave unshown.
- **A render waits two seconds on a manual clock, not on real work.** A host has to reach its state without I/O or a coroutine waiting on one. Feed it the loaded or failed state directly; a host that downloads first renders its spinner.
- **Most `*Previews.kt` files declare their own `private fun PreviewSurface`.** Making one `internal` for a render clashes with the others in the module. In a case's `call`, use `LibreChatTheme(darkTheme = true, uiStyle = …) { … }` directly, and make only the host and its fixtures internal.
- **A Material `AlertDialog` with a text field never idles.** The harness runs a manual clock and draws the dialog's window directly when the compose node can't be captured.
- **Two font-scale methods disagree.** A hand-built `Density(fontScale)` is linear. `RuntimeEnvironment.setFontScale` (what the harness uses) goes through the API 34+ non-linear converter, like a device.
- **The locale must be set through the Robolectric qualifier** (the harness does this). `stringResource` follows it; this was checked with real German strings.
- **On a device, an opaque keyboard hides a shrink.** Check IME layouts by uiautomator bounds, not by eye.
- **Platform gaps that no render shows:**
  - `Hyphens.Auto` does nothing on iOS, so long German compounds break without a hyphen.
  - `DeviceConfigurationOverride` throws on iOS (CMP 1.12.1), so don't use it in `commonTest`.
