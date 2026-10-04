# core:ui

Material 3 theme and shared Compose components used across all feature modules. Purely presentational -- no business logic, no ViewModels, no repositories.

## What This Module Provides

### Theme (`theme/`)
- `Theme.kt`: `LibreChatTheme` wrapping Material 3 `MaterialTheme`. Maps LibreChat web colors to M3 color roles.
- `Color.kt`: Color palette derived from the web app's CSS variables.
- `Type.kt`: Typography scale.
- `Shape.kt`: Corner radius definitions.

### Shared Components (`components/`)
- `LibreChatTopBar` - App bar with optional back navigation and actions.
- `LoadingIndicator` - Centered circular progress.
- `ErrorBanner` - Dismissible error message bar.
- `EmptyState` - Illustration + message for empty lists.
- `AvatarImage` - User/agent/model avatar with Coil image loading and fallback.
- `ConfirmationDialog` - Reusable confirm/cancel dialog.
- `DiscardChangesDialog` - "Discard changes?" for leaving an editor with unsaved edits. Every
  full-screen editor (agent, skill, schedule, prompt, role skills) uses it the same way: the ViewModel
  owns `onBackRequested()` and the dialog flag, `PlatformBackHandler` is enabled only while dirty (a
  clean form keeps predictive back), and the top bar's back goes through the same check, since the
  handler never fires on iOS. Dirty = what Save would send differs from what was loaded.
- `ModelIcon` - Endpoint-specific model icons.
- `EndpointBadge` - Colored badge showing the AI provider.
- `SearchBar` - Reusable search input field.
- `BottomSheetScaffold` - Wrapper for modal bottom sheets.
- `PullToRefresh` - Pull-to-refresh wrapper.
- `BannerDisplay` - The server banner. The server sends at most one and only ever types it
  `banner`, so there is a single visual treatment; a `persistable` banner hides the dismiss control.

### Keyboard dismissal (`components/ClearFocusOnTap.kt`)
`Modifier.clearFocusOnTap()` clears focus — dismissing the keyboard — on a tap nothing else handled.
iOS has no Back key, so without it a focused field's keyboard cannot be dismissed at all. Applies on
both platforms.

- **Rule: every composition root that can host a text field applies it.** The app roots already do
  (`MainActivity`'s root `Surface`, `LibreChatApp`'s root `Box`). Every `AlertDialog` /
  `ModalBottomSheet` / `Dialog` whose content has a text field must apply it too — they render in their
  own window (Android) / platform layer (iOS), which the app root's pointer input never sees. On
  `AlertDialog`/`ModalBottomSheet` pass it as `modifier`; `Dialog` has none, so put it on the content root.
- **Why `clearFocus()`, not `LocalSoftwareKeyboardController.hide()`:** hide leaves the field focused,
  so tapping it again may not reshow the keyboard.
- **Why not `Modifier.clickable { clearFocus() }` on a root Box** (the common snippet): full-screen
  ripple, plus an accessibility "button" node covering the whole app.
- **Why not `detectTapGestures`:** it cancels only when a child consumes the movement, so a drag across
  inert space would count as a tap. The node gives up past touch slop, like a UIKit tap recognizer.
- **Why not a native UIKit tap recognizer on the host view:** it can't hit-test Compose text fields,
  and `endEditing` resigns CMP's hidden input view without clearing Compose focus.

Behaviour, pinned by `ClearFocusOnTapInstrumentedTest` (Android; every row, with a list swipe standing in for the drag gestures) and `ClearFocusOnTapIosTest`
(iOS simulator, `:core:ui:iosSimulatorArm64Test` — CMP's iOS text field has its own gesture code, so
"the field consumes its own tap" must be proven there too). The one rule: a down→up within touch slop
that no child consumed:

| Gesture | Keyboard | Why |
|---|---|---|
| Tap on empty space | dismisses | it's a tap nothing handled |
| Hold still on empty space, then release | dismisses | no duration cap — any still down→up is a tap |
| Tap on message text (`SelectionContainer`) | dismisses | its clear-selection tap doesn't consume |
| Tap on the focused field | stays | the field consumes its own tap |
| Tap on a button (Send, attach, chips) | stays | the button consumes; the button still fires |
| Scroll / fling / pull-to-refresh / sheet drag | stays | a drag is not a tap |
| Drag across non-scrolling space | stays | past touch slop → gesture abandoned |
| Tap that stops a fling | stays | the list consumes the stopping tap |
| Long-press message text | dismisses | **not this modifier**: starting a selection moves focus to the `SelectionContainer`; same without it |

**Deliberately not done — scroll-to-dismiss.** iMessage dismisses when the transcript is dragged
(`keyboardDismissMode = .interactive`: the keyboard tracks the finger and can be dragged back). Compose
can't reproduce the interactive variant, and the "dismiss as soon as a scroll starts" approximation is
non-idiomatic on Android, where chat apps keep the keyboard while scrolling history. If ever wanted: iOS
only, message list only, user-initiated scrolls only (`NestedScrollSource.UserInput`) — never the
streaming auto-follow scroll.

### Interface style and Liquid Glass (`theme/UiStyleLocals.kt`, `glass/`, `components/topbar/`)

#### The pattern: self-theming components, one CompositionLocal
The user picks Material or Liquid Glass in Settings. **The theme passes that choice down as a
CompositionLocal; shared components read it and draw one of two variants. Screens never branch.**

```kotlin
// App root (MainActivity / LibreChatApp): the only place the style enters the tree.
LibreChatTheme(darkTheme, accentColor, useDynamicColor, uiStyle = storedUiStyle) { App() }
// → provides LocalGlassLevel (internal): null = Material, else the device's GlassCapability tier.

// Shared component in core/ui: mirrors the M3 signature, consumes the local, dispatches.
@Composable
fun AdaptiveThing(/* exactly the M3 Thing's parameters, same names, order and defaults */) {
    if (!isLiquidGlass) {
        Thing(/* every parameter forwarded unchanged */)   // Material: the M3 control, every parameter forwarded
        return
    }
    // Liquid Glass variant. Colours from GlassControlColors, sizes from GlassDefaults,
    // glass surfaces via rememberGlassStyle() + Modifier.glassSurface(style, shape, LocalGlassBackdrop.current).
}

// Screen code: style-agnostic.
AdaptiveThing(...)
```

Rules — follow these when writing or reviewing UI:
1. **Screens use `Adaptive*` components, never raw M3 chrome.** Detekt `ForbiddenImport`
   (`config/detekt/detekt.yml`) bans the raw M3 versions outside `components/Adaptive*.kt` and
   `components/topbar/`. Note CI's detekt only scans `commonMain`; androidMain/iosMain are on you.
2. **The branch lives inside the component, once.** Two accepted shapes:
   - *early return* (above) when the glass variant is a different control (Switch, Checkbox, Radio,
     TabRow, SegmentedChoice, SectionHeader, TextField, spinner);
   - *one M3 call with per-argument overrides* when glass only restyles it (Buttons, Chips,
     DropdownMenu, FAB): `shape = shape ?: if (glass) GlassShape else M3Defaults.shape`. A value the
     caller passes explicitly still wins (null / M3-default sentinel).
3. **Material path = the M3 call with every parameter forwarded.** M3-only parameters stay and are
   documented as "Material only". The exception is deliberate Material motion: a component may
   replace the M3 call with its own implementation to add motion M3 doesn't expose, provided it
   keeps M3's layout, placement and colours — e.g. `AdaptiveDropdownMenu` → `MaterialMenuOverlay`
   (drawn in `GlassSheetHost` like the glass menu, so it can overshoot its bounds; inside a
   dialog/sheet window it falls back to the `MaterialMenuPopup` window), both placed by a port of
   M3's popup positioning rules.
4. **`isLiquidGlass` is the one public predicate.** `LocalGlassLevel` is internal — only core/ui
   tells the tiers (NATIVE / FULL / SIMULATED / BLUR_ONLY / FLAT) apart, and `glassSurface` /
   `rememberGlassBackdrop` degrade by tier themselves. Never re-derive the flag another way
   (`rememberGlassStyle() != null` as a boolean, comparing styles, reading capability).
5. **Tokens, not literals.** Glass colours: `GlassControlColors` (reads `MaterialTheme.colorScheme`
   + `LocalDarkTheme`, so accent and dark mode carry over). Shared measures: `GlassDefaults`.
6. **Self-gating, not caller-gating.** Adaptive sheets/dialogs call `CoversNativeBars` and provide
   `LocalInSeparateWindow` themselves; `AdaptiveScaffold` provides `LocalGlassBackdrop` to its slots
   (null to its body). Callers set none of this.
7. **Bars are data.** `AdaptiveTopBar(AdaptiveTopBarSpec(...))`, because the iOS 26 renderer is a
   UIKit `UINavigationBar` that can't host composables; Compose-only extras go in `belowBar`.
8. **Escape hatch, used sparingly.** Truly bespoke chrome (chat composer, chat top bar, drawer /
   sidebar, chat backdrop plumbing) may branch on `isLiquidGlass` directly. If the same branch shows
   up a second time, it belongs in a new `Adaptive*` component.

Known gaps (stay M3 in glass until someone adds a wrapper): `ExposedDropdownMenuBox` menus, `Slider`,
`LinearProgressIndicator`, scrolling tab rows. `ForbiddenImport` also misses a fully-qualified call
(`androidx.compose.material3.AlertDialog(…)` with no import) — write the import and use the wrapper.

**Adding a new style-aware control:** write `AdaptiveX` in `components/` with the M3 signature,
pick a shape from rule 2, put the Material branch first, take glass values from
`GlassControlColors`/`GlassDefaults`, add a `ForbiddenImport` entry for the raw M3 `X`, and list it in
the controls bullet below.

#### Details
- `LibreChatTheme(uiStyle = …)` (null = the platform default) provides `LocalGlassLevel` (null =
  Material, else the device's `GlassCapability`; internal), `LocalDarkTheme` and the app-wide
  `LocalNativeOverlayGate`. **Prefer an `Adaptive*` component to branching.** When a screen genuinely
  must branch, ask `isLiquidGlass` — the one public predicate; only core/ui tells the tiers apart.
- **Top bars are data.** Screens describe a bar with `AdaptiveTopBarSpec` and draw it with
  `AdaptiveTopBar`, which renders a plain M3 `TopAppBar` in Material, the system's glass
  `UINavigationBar` on iOS 26+, and a simulated glass bar elsewhere. Every bar icon is a `BarIcon`
  from `BarIcons` (Material vector + SF Symbol); `BarIconsSymbolTest` in `:shared` iosTest fails on a
  symbol UIKit does not know. Use `AdaptiveScaffold` for screens with an adaptive bar or FAB.
- **Native bars sit above the whole Compose canvas**, so a Compose surface meant to cover them (the
  drawer, a sheet or dialog scrim) draws *under* them. Such surfaces call `CoversNativeBars(active)`;
  the bar hides while any are up. `AdaptiveModalBottomSheet`, `AdaptiveAlertDialog` and
  `AdaptiveDialog` do this themselves — never gate a surface drawn by one of them again — so only
  canvas overlays that aren't (the drawer, the chat media/PDF overlays) call it; it is a no-op off
  the native tier, and detekt bans the raw M3 versions (plus `DropdownMenu`, for
  `AdaptiveDropdownMenu`) outside core/ui's adaptive files. Sheets, dialogs and menus open in their
  own window, which the backdrop cannot sample — so in glass mode `AdaptiveAlertDialog`,
  `AdaptiveModalBottomSheet` and `AdaptiveDropdownMenu` don't use a window: they portal
  (`GlassPortal`) into `GlassSheetHost`, which `LibreChatNavHost` wraps around **every** layout,
  including a platform's own `content` (Android's) — a layout outside the host silently gets opaque
  M3 sheets and menus. The host draws its overlays over the whole app canvas and records the app as
  their backdrop only while one is open. The opener's composition locals are captured and
  re-provided, so content reads the same theme/ViewModels as in place; overlay parameters go through
  `rememberUpdatedState` so an opener's recomposition doesn't recompose the open overlay. The menu
  (`GlassFloatingMenu`) anchors to the caller's parent layout, as the M3 popup does, opens below it
  (above when there's no room) aligned to its nearer edge, and dismisses on an outside tap or back.
  The sheet (`GlassFloatingSheet`)
  re-implements the slide, scrim, back, drag/fling-to-dismiss and nested-scroll pull, and provides
  `onSurface` as the content colour; `sheetState` and `shape` apply to Material only. The dialog
  (`GlassFloatingDialog`) keeps the M3 layout (icon, title, text, buttons at the end), honours
  `dismissOnBackPress` / `dismissOnClickOutside`, and rises above the keyboard. The panel is glass
  and the buttons stay plain `TextButton`s — one glass layer, as on an iOS alert. `AdaptiveDialog` (arbitrary full content) still opens a window and
  stays opaque. `AdaptiveCard` is a glass panel over `LocalGlassBackdrop` (cards floating over
  content, e.g. the tool-approval / ask-user panels above the composer). The adaptive
  dialogs and the M3 sheet provide `LocalInSeparateWindow`: inside their own window a sheet falls back
  to the opaque M3 sheet and a top bar stays in Compose, with nothing for the caller to set.
- **Controls look like iOS in glass mode**, drawn in Compose: `AdaptiveSwitch`, `AdaptiveRadioButton`
  (a checkmark), `AdaptiveCheckbox`, `AdaptiveDivider` (inset hairline), `AdaptiveButton` (flat
  capsule) and `AdaptiveOutlinedButton`/`AdaptiveFilledTonalButton` (borderless capsule on
  `GlassControlColors.fill`, accent label unless the caller picked a colour),
  `AdaptiveOutlinedTextField` (iOS rounded filled field; the chat composer keeps its own field),
  `AdaptiveSegmentedChoice` and `AdaptiveTabRow` (the liquid segmented control in glass; the tab
  row takes a `backdrop` only in a bar slot), `AdaptivePillChoice` (a full-width sliding-pill
  toggle in Material, like the drawer's Chats/Projects switch, for a few short options; the
  liquid segmented control in glass), `AdaptiveFloatingActionButton` (`small` for the M3
  small FAB), `AdaptiveSectionHeader`, the four
  `Adaptive*Chip`s (pills), `AdaptiveSnackbarHost` (a glass capsule toast that samples the
  `AdaptiveScaffold` backdrop) and `AdaptiveCircularProgressIndicator` (iOS activity indicator when
  indeterminate). Each mirrors the M3 signature and passes straight through in Material, and detekt
  bans the raw M3 control. Scrolling tab rows have no glass counterpart and stay M3. Settings
  pages group rows with `adaptiveSection { row(key) { … } }` inside `AdaptiveGroupedPage`; a row's own
  `Surface` takes `adaptiveRowColor`; title a section with `AdaptiveSectionHeader`. A cell is a
  Column, because a lazy item stacks several root children vertically.
- **Glass primitives** (`glass/`): the backdrop library is an `implementation` dependency, so only this
  module can touch it. `rememberGlassBackdrop()` returns null in Material and on the flat tier —
  recording costs a redraw of the content every frame, so never record unconditionally. Apply
  `glassBackdropSource` to content that is a *sibling* of the glass, never an ancestor (a surface
  sampling its own ancestor feeds back into itself); `AdaptiveScaffold` hides the backdrop from its
  own body for that reason. Shared measures live in `GlassDefaults`. The backdrop paints the screen colour first:
  recorded content is usually transparent, and a blurred transparent copy lets the sharp original
  show through. Don't add `vibrancy()` back without measuring; it was the entire cost of glass while
  streaming.

### Markdown
- core/ui does NOT provide a shared markdown renderer. Features render markdown
  directly with the `com.mikepenz` multiplatform-markdown-renderer
  (`libs.markdown.renderer.m3`) — see `feature/chat` (CachedMarkdown, streaming-
  tuned) and `feature/skills` (plain static `Markdown(...)`). `feature/chat`
  also has its own WebView-based artifact/LaTeX rendering. If a genuinely shared
  renderer is ever needed, lift it here; until then there is nothing in core/ui
  to depend on.

### Message Components (`message/`)
- `MessageBubble` - Shared message rendering used by `:feature:chat`.
- `ToolCallCard` - Expandable tool call display.
- `FileAttachmentChip` - File reference chip.
- `FeedbackButtons` - Thumbs up/down.

### PDF (`pdf/`, androidMain only)
- `PdfDocumentHolder` - Owns the `PdfRenderer`/fd; mutex-serialized on-demand `renderPage` with
  dimension caps and a page-count bound. Created from bytes, closed by the owning composable.
- `PdfPageContent` - One page rendered when it scrolls into a LazyColumn window and recycled when
  it scrolls out. Shared by `:feature:chat`'s viewer (adds per-page pinch-zoom via the modifier
  slot) and `:feature:files`' preview (adds page labels). Fix render/recycle logic HERE, not in the
  feature copies — there are none.

## Rules

- **No business logic.** No ViewModels, no repository calls, no use cases.
- Components accept domain models from `:core:model` as parameters and render them.
- All components must be stateless or hoist state to the caller.
- Dependencies: `:core:model`, `:core:common`, Coil for image loading, Compose libraries, Kermit logging (androidMain, for the PDF holder).
- Convention plugins: `librechat.mobile.library` + `librechat.mobile.compose`.
- No DI in this module (no Koin modules, no injected classes).
- Use `@Preview` annotations on all components for Android Studio preview support.
- During SSE streaming, buffer markdown re-renders to ~100ms intervals to avoid frame drops.

### Dynamic Parameters (`components/Dynamic*.kt`)
- `DynamicParameterPanel` dispatches `List<ParameterDefinition>` to typed controls (slider/dropdown/checkbox/input/textarea)
- `ParameterDefinition` contains: key, type, label, description, default, min, max, step, options
- `DynamicSlider` snaps to step increments; calculates stepCount from range
- All controls are stateless — caller manages values via `Map<String, String>`
- `ModelParameterContent` falls back to existing fixed params when no schema available
- **Gotcha**: Slider step count calculated as `((max - min) / step).toInt()` — ensure step divides range evenly to avoid drift

## Compose Performance Rules

These rules apply to ALL composables across feature modules, not just core:ui.

### UI State Architecture

1. **Single UI state data class per screen.** Each ViewModel exposes ONE `StateFlow<XyzUiState>` — never 5+ individual StateFlows that each trigger recomposition independently.

2. **UI state contains only display-ready data.** The ViewModel maps domain models (e.g., `Conversation` with 28 fields) to minimal display data classes (e.g., `DrawerConversationDisplayData` with 7 fields). Composables should never receive full domain models.

3. **Mark UI state classes `@Immutable`.** This lets the Compose compiler skip recomposition when the reference hasn't changed. All fields must be `val` with stable types.

4. **Collect state at the narrowest scope.** If only `DrawerContent` uses drawer state, collect inside `DrawerContent` — not in the parent `PhoneLayout` which also owns the NavDisplay. State changes should only recompose the composable that reads them.

5. **Use `SharingStarted.Eagerly`** for UI state that should be ready before the composable subscribes (avoids empty→populated two-phase render jank on first frame).

### Stability

6. **All types passed to composables must be stable.** Primitives, `String`, `@Immutable` data classes, and enums are stable. Standard `List`/`Set`/`Map` are NOT stable by default — they are declared stable in `compose-stability.conf` at the project root.

7. **Never pass lambdas that capture unstable references.** Prefer method references (`viewModel::doThing`) or `remember`'d lambdas over inline lambdas that capture changing state.

### LazyColumn / LazyList

8. **Always provide `key`** on `items()` calls. Keys must be unique, stable identifiers (e.g., `conversationId`).

9. **Always provide `contentType`** when a LazyColumn has mixed item types (headers, content rows, loading indicators). This enables Compose to reuse compositions across items of the same type.

10. **One element per `item {}` block.** Multiple composables in one block prevents independent recycling.

11. **No nested same-direction scrollables.** Never put `LazyColumn` inside `verticalScroll` — combine into one `LazyColumn` using `item {}` blocks.

### Avoiding Unnecessary Work

12. **Move data transformations to the ViewModel.** Sorting, filtering, grouping, and model mapping happen in the ViewModel — not in `remember` blocks in composables.

13. **Cache expensive objects as top-level constants.** `RoundedCornerShape`, `Regex`, `DateTimeFormatter` — allocating these per-item per-frame is wasteful.

14. **Use `background(color, shape)` instead of `clip(shape).background(color)`.** The `clip` modifier creates a persistent clip layer per composable; `background` with a shape parameter draws the clipped background without the layer overhead.

15. **Avoid `copy(alpha = ...)` on colors.** Alpha-blended colors force GPU compositing. Use opaque colors from the theme where possible.

16. **Defer state reads to later phases.** Use lambda-based modifiers (`Modifier.offset { }`, `Modifier.drawBehind { }`) instead of value-based ones (`Modifier.offset(x, y)`, `Modifier.background(animatedColor)`) for scroll/animation state.
