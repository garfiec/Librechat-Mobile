# core:ui

Material 3 theme and shared Compose components for all feature modules. Purely presentational.

## Module rules
- **No business logic**: no ViewModels, repository calls, use cases, or DI (no Koin modules, no
  injected classes).
- Components take `:core:model` types as parameters and are stateless or hoist state to the caller.
- Dependencies: `:core:model`, `:core:common`, Coil, Compose, Kermit (androidMain, PDF holder).
- `@Preview` on all components.
- During SSE streaming, buffer markdown re-renders to ~100ms intervals.
- No shared markdown renderer lives here: features use `com.mikepenz` multiplatform-markdown-renderer
  (`libs.markdown.renderer.m3`) directly (`feature/chat` CachedMarkdown, `feature/skills`). Lift one
  here only if it is genuinely shared.

## Components (`components/`, `media/`, `theme/`)
- Reuse before writing: `LibreChatTopBar`, `LoadingIndicator`, `ErrorBanner`, `EmptyState` (`action`
  slot), `AvatarImage`, `BannerDisplay`, `FileTypeTile` / `FileTypeChip`, `FilterChipBottomSheet`,
  `OtpVerificationDialog`, `PdfPasswordDialog`, `CustomHeadersEditor`; `media/` `MediaViewerHost`,
  `FileActionsSheet` / `FileActionsMenu`.
- `InitialTile`: the identity mark for every list of user-made things (project, MCP server), so they
  match. Seed with a stable id; `TileStatusDot` goes in `badge`.
- `EndpointIcon` / `EndpointAvatar`: no third-party logo is bundled; see `feature/chat/CLAUDE.md`
  "Brand art".
- `ShareLinkSheet` (`components/share/`): the one conversation share sheet; which controls show is
  decided in `ShareSheetControls`. The host owns the clipboard write (`onCopy`).
- `AclSharingSection` (`components/acl/`): driven by `:core:data` `AclController(AclPolicy)`; the agent
  and skill editors still carry their own copies until they migrate.
- `BookmarkPickerSheet` / `BookmarkEditorSheet` (`components/bookmarks/`): driven by `:core:data`
  `BookmarkPickerController` (each tap saves).

### Editors: `DiscardChangesDialog`
- Every full-screen editor uses it the same way: the ViewModel owns `onBackRequested()` and the dialog
  flag; `PlatformBackHandler` is enabled only while dirty (a clean form keeps predictive back); the top
  bar's back goes through the same check. Dirty = what Save would send differs from what was loaded.
- A sheet editor uses `AdaptiveModalBottomSheet(interceptDismiss = dirty)`: while true, every dismiss
  gesture calls `onDismissRequest` instead; the sheet closes when the caller drops it.
- Pass `secure = true` (both sheet overloads) while a `SecretTextField` is revealed (Android
  `FLAG_SECURE`, ref-counted). iOS has no equivalent: re-mask on ON_PAUSE, not ON_STOP (iOS stops
  drawing before ON_STOP, so the app-switcher snapshot would keep the secret).

### Secrets and autofill
- `SecretTextField`: use it (not a new reveal toggle) for a non-login secret (API keys, tokens, PDF
  passwords). Reveal is hoisted so the caller can re-mask on ON_PAUSE.
- `Modifier.excludeFromAutofill()`: `SecretTextField` applies it; add it only to a masked field outside it (Android only).
- `KeyboardOptions.withIosTextContentType()`: iOS `textContentType` roles; no-op on Android.
- A field whose text must arrive as typed passes `UrlKeyboardOptions` (URLs) or `VerbatimKeyboardOptions`
  (codes, identifiers, JSON); Compose otherwise leaves autocorrect on.

### Keyboard dismissal (`components/ClearFocusOnTap.kt`)
`Modifier.clearFocusOnTap()` clears focus on a tap nothing else handled (both platforms). iOS has no Back key, so without
it a keyboard cannot be dismissed.
- **Every composition root that can host a text field applies it.** The app roots do (`MainActivity`'s
  root `Surface`, `LibreChatApp`'s root `Box`). Every `AlertDialog` / `ModalBottomSheet` / `Dialog` with
  a text field must apply it too (own window/layer, unseen by the app root): pass it as `modifier`, or on
  `Dialog` (no modifier) put it on the content root.
- Don't swap the mechanism for `keyboardController.hide()` (field stays focused; a re-tap may not
  reshow the keyboard), `clickable { clearFocus() }` (full-screen ripple + a11y button),
  `detectTapGestures` (a drag over inert space counts as a tap) or a UIKit tap recognizer (can't
  hit-test Compose fields; `endEditing` doesn't clear Compose focus).
- Behaviour: a down→up within touch slop that no child consumed.

| Gesture | Keyboard | Why |
|---|---|---|
| Tap on empty space | dismisses | it's a tap nothing handled |
| Hold still on empty space, then release | dismisses | no duration cap — any still down→up is a tap |
| Tap on selectable text (`SelectionContainer`) with no clickable around it | dismisses | its clear-selection tap doesn't consume |
| Tap anywhere in the chat thread (message, text, thinking card, buttons in it) | dismisses | **not this modifier**: they consume the tap, so `MessageList` watches the Initial pass itself; not while focus is inside the list (inline edit) |
| Tap on the focused field | stays | the field consumes its own tap |
| Tap on a button (Send, attach, chips) | stays | the button consumes; the button still fires |
| Scroll / fling / pull-to-refresh / sheet drag | stays | a drag is not a tap |
| Drag across non-scrolling space | stays | past touch slop → gesture abandoned |
| Tap that stops a fling | stays, except in the chat thread | the list consumes the stopping tap |
| Long-press message text | dismisses | **not this modifier**: starting a selection moves focus to the `SelectionContainer`; same without it |

Scroll-to-dismiss is deliberately not done (non-idiomatic on Android; iOS's interactive variant can't be
reproduced). If ever added: iOS only, message list only, `NestedScrollSource.UserInput` only, never the
streaming auto-follow scroll.

## Interface style and Liquid Glass (`theme/UiStyleLocals.kt`, `glass/`, `components/topbar/`)

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
        Thing(/* every parameter forwarded unchanged */)
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
   `components/topbar/`; it misses a fully-qualified call with no import, so import and use the wrapper.
2. **The branch lives inside the component, once**, in one of two shapes: *early return* when glass is
   a different control (Switch, Checkbox, Radio, TabRow, SegmentedChoice, SectionHeader, TextField,
   spinner); *one M3 call with per-argument overrides* when glass only restyles it (Buttons, Chips,
   DropdownMenu, FAB): `shape = shape ?: if (glass) GlassShape else M3Defaults.shape`, so an explicit
   caller value still wins (null / M3-default sentinel).
3. **Material path = the M3 call with every parameter forwarded**; M3-only parameters stay, documented
   "Material only". Exception: a component may replace the M3 call to add Material motion M3 doesn't
   expose, if it keeps M3's layout, placement and colours (`AdaptiveDropdownMenu` →
   `MaterialMenuOverlay`, or the `MaterialMenuPopup` window inside a dialog/sheet window). Menus round
   to `AdaptiveMenuShape` (16dp) in both styles; callers pass no `shape` for that.
4. **`isLiquidGlass` is the one public predicate.** `LocalGlassLevel` is internal; only core/ui tells
   the tiers (NATIVE / FULL / SIMULATED / BLUR_ONLY / FLAT) apart, and `glassSurface` /
   `rememberGlassBackdrop` degrade by tier themselves. Never re-derive the flag (`rememberGlassStyle()
   != null`, comparing styles, reading capability). `UiStylePreview(style)` / `ThemePreview(...)` set
   style for a picker's previews only, never to restyle part of a real screen.
5. **Tokens, not literals.** Glass colours from `GlassControlColors` (follows accent and dark mode),
   measures from `GlassDefaults`.
6. **Self-gating, not caller-gating.** Adaptive sheets/dialogs call `CoversNativeBars` and provide
   `LocalInSeparateWindow` themselves; `AdaptiveScaffold` provides `LocalGlassBackdrop` to its slots
   (null to its body). Callers set none of this.
7. **Bars are data.** `AdaptiveTopBar(AdaptiveTopBarSpec(...))`: the iOS 26 renderer is a UIKit
   `UINavigationBar` that can't host composables; Compose-only extras go in `belowBar`. A status label
   is `BarAction.Text(...)`; the native bar draws no dot, so its `accessibilityLabel` must carry the
   state.
8. **Escape hatch, used sparingly.** Bespoke chrome (chat composer, chat top bar, drawer / sidebar,
   chat backdrop plumbing) may branch on `isLiquidGlass`. A branch that shows up a second time belongs
   in a new `Adaptive*` component.

**Known gaps** (stay M3 in glass until wrapped): `Slider`, scrolling tab rows, indeterminate
`LinearProgressIndicator`. Raw `LinearProgressIndicator` and `TextButton` aren't banned yet (existing
call sites); prefer `AdaptiveLinearProgressIndicator` (determinate) and `AdaptiveTextButton`.

**Adding a style-aware control:** `AdaptiveX` in `components/` with the M3 signature, a shape from
rule 2, Material branch first, glass values from `GlassControlColors`/`GlassDefaults`, a
`ForbiddenImport` entry for raw `X`, and list it under Controls below.

#### Details
- **Top bars.** Every bar icon is a `BarIcon` from `BarIcons` (Material vector + SF Symbol);
  `BarIconsSymbolTest` (`:shared` iosTest) fails on an unknown symbol. Use `AdaptiveScaffold` for
  screens with an adaptive bar or FAB.
- **Scrolling under the bars.** A list screen that shows content through the status and top bar passes
  `MaterialBarStyle(translucent = true)`, wraps the list in
  `UnderBarsPullToRefreshBox(barPadding = innerPadding)` instead of padding it, and adds `innerPadding`
  to the list's `contentPadding`. Non-scrolling states (empty, error, loading) still pad themselves. In
  Material `belowBar` sits inside the scrim, so give it a transparent container
  (`AdaptiveTabRow(containerColor = …)`).
- **Native bars sit above the whole Compose canvas**, so a Compose surface meant to cover them draws
  under them. Such surfaces call `CoversNativeBars(active)` (no-op off the native tier). The adaptive
  sheet and dialogs already do — never gate them again; only other canvas overlays (drawer, chat
  media/PDF overlays) call it.
- **Overlays in glass.** A separate window can't be sampled by the backdrop, so in glass
  `AdaptiveAlertDialog`, `AdaptiveModalBottomSheet` and `AdaptiveDropdownMenu` portal (`GlassPortal`)
  into `GlassSheetHost`, which `LibreChatNavHost` must wrap around **every** layout, including a
  platform's own `content`; a layout outside it silently gets opaque M3 sheets and menus. The host
  re-provides the opener's composition locals and passes overlay parameters through
  `rememberUpdatedState`, so an opener's recomposition doesn't recompose the overlay. In glass,
  `sheetState` and `shape` apply to Material only; the glass dialog's buttons stay plain `TextButton`s
  (one glass layer). `AdaptiveDialog` (arbitrary content) still opens a window and stays opaque. Inside
  a separate window (`LocalInSeparateWindow`) a sheet falls back to the M3 sheet and a top bar stays in
  Compose, automatically.
- `AdaptiveCard`'s `onClick` overload is the tappable card; don't add `Modifier.clickable` to the plain
  one.
- **Controls** (M3 signature, straight through in Material, raw M3 banned): `AdaptiveSwitch`
  (+ `AdaptiveSwitchPlaceholder` for a value not known yet), `AdaptiveRadioButton`, `AdaptiveCheckbox`,
  `AdaptiveDivider`, `AdaptiveButton`, `AdaptiveOutlinedButton`, `AdaptiveFilledTonalButton`,
  `AdaptiveTextButton`, `AdaptiveOutlinedTextField` (not the chat composer), `AdaptiveSegmentedChoice`,
  `AdaptiveTabRow` (`backdrop` only in a bar slot), `AdaptivePillChoice`,
  `AdaptiveFloatingActionButton`, `AdaptiveExtendedFloatingActionButton`, `AdaptiveSectionHeader`, the
  four `Adaptive*Chip`s (`overlayAssistChipColors(tint)` over scrolling content;
  `overlayFilterChipColors()` for a toggle chip there, opaque when off in Material),
  `AdaptiveSnackbarHost`, `AdaptiveCircularProgressIndicator`, `AdaptiveLinearProgressIndicator`
  (determinate only), `AdaptiveExposedDropdownMenuBox` + `AdaptiveExposedDropdownMenu` (opaque M3
  popup in glass, not portalled).
- **Settings pages** group rows with `adaptiveSection { row(key) { … } }` inside `AdaptiveGroupedPage`;
  a row's own `Surface` takes `adaptiveRowColor`; title sections with `AdaptiveSectionHeader`. A cell is
  a Column, because a lazy item stacks several root children vertically.
- **Glass primitives** (`glass/`): the backdrop library is an `implementation` dependency, so only this
  module touches it.
  - `rememberGlassBackdrop()` is null in Material and on the flat tier; recording redraws the content
    every frame, so never record unconditionally.
  - Apply `glassBackdropSource` to a *sibling* of the glass, never an ancestor (it would sample itself);
    hence `AdaptiveScaffold` hides the backdrop from its own body.
  - The backdrop paints the screen colour first: recorded content is usually transparent, and a blurred
    transparent copy lets the sharp original show through.
  - Don't add `vibrancy()` without measuring: it costs more than the rest of glass while streaming.

## Other packages
- **Status colours** (`theme/StatusColors.kt`): `statusColors()` (`success`, `info`, `warning`,
  `neutral`, `error`) instead of colour literals. Always pair a status colour with a label or icon.
- **Context usage** (`contextusage/`): `ContextBreakdownCard` is the one renderer for the chat gauge and
  the Settings preview; row math lives in `:core:model` `usage/`.
- **MCP status** (`mcp/McpStatusPresentation.kt`): `mcpStatusLabel` / `mcpStatusColor` alone name and
  colour an `McpDisplayStatus`, in Settings and in chat alike.
- **Scroll fades**: a container fades toward an edge with more to scroll through
  `Modifier.scrollFadingEdges` (RTL-aware).
- **MCP OAuth sign-in** (`mcp/`): `McpOAuthSignInDialog` is the one sign-in page, used by Settings, the
  chat's Sign in, and Settings' pull request account connect (same cookies and `/oauth/success|error`
  pages, so it reuses the dialog unchanged under its MCP names). Takes `McpOAuthLaunch.InApp`, reports
  one `McpOAuthOutcome`; the bind is the caller's.
  - `McpOAuthWebView` must finish seeding the bind's cookies for every origin in
    `mcpOAuthCookieOrigins` **before** loading the provider URL (an earlier load can reach the callback
    unbound): Android chains the `setCookie` callbacks and loads from the last; iOS awaits every
    completion on a fresh `nonPersistentDataStore`. Android wipes the jar before, when the flow ends and
    on release, so the binding and provider session don't outlive it.
  - `McpOAuthNavigationGate` (pure, commonTest) ends the flow at `/oauth/success` or `/oauth/error` on
    either server host, and refuses a main-frame `http://` page, the first load included, unless it is on one of those servers'
    own origins (scheme, host, port) or loopback: `Error("insecure_connection")`.
  - User-Agent: `web/EmbeddedBrowserUserAgent.kt` (shared with SSO).
- **Date & time** (`datetime/`): `ProvideDateTimeFormat` sits once inside `AppLocale` in
  `LibreChatNavHost`, not at the activity root (Android's language override only reaches the
  configuration there). It provides `LocalDateTimeFormat` and `LocalRelativeTimeReference` (the
  app-wide minute ticker).
  - Show a date via `rememberAbsoluteTimestamp(iso)` (unparseable shows raw, never blank), or compute a
    `TimestampLabel` inside `remember(…, format, reference)` and call `.resolve()` outside it: plural
    resources only resolve in composition.
  - `rememberSystemIs24Hour()` reads the device clock setting, not the locale convention.
- **PDF** (`pdf/`, androidMain): `PdfDocumentHolder` (closed by the owning composable) and
  `PdfPageContent` serve both `:feature:chat`'s viewer and `:feature:files`' preview: fix render/recycle
  logic here; there are no feature copies.
- **Dynamic parameters** (`components/Dynamic*.kt`): `ModelParameterContent` dispatches
  `ParameterDefinition`s to stateless controls. `DynamicSlider` step count is
  `((max - min) / step).toInt()`: the step must divide the range evenly or values drift.

## Compose Performance Rules

These rules apply to ALL composables across feature modules, not just core:ui.

### UI State Architecture

1. **Single UI state data class per screen.** Each ViewModel exposes ONE `StateFlow<XyzUiState>`, not many StateFlows that each trigger recomposition.

2. **UI state contains only display-ready data.** The ViewModel maps domain models (`Conversation`) to minimal display classes (`DrawerConversationDisplayData`); composables should never receive full domain models.

3. **Mark UI state classes `@Immutable`.** All fields `val` with stable types, so Compose can skip recomposition when the reference is unchanged.

4. **Collect state at the narrowest scope**: in the composable that reads it (`DrawerContent`), not a parent that also owns the NavDisplay.

5. **Use `SharingStarted.Eagerly`** for UI state that should be ready before the composable subscribes (no empty→populated first frame).

### Stability

6. **All types passed to composables must be stable.** Primitives, `String`, `@Immutable` data classes and enums are; `List`/`Set`/`Map` are declared stable in `compose-stability.conf` at the project root.

7. **Never pass lambdas that capture unstable references.** Prefer method references (`viewModel::doThing`) or `remember`'d lambdas.

### LazyColumn / LazyList

8. **Always provide `key`** on `items()`: unique, stable identifiers (e.g. `conversationId`).

9. **Always provide `contentType`** when a list mixes item types (headers, rows, loading indicators).

10. **One element per `item {}` block**, so items recycle independently.

11. **No nested same-direction scrollables.** Never put `LazyColumn` inside `verticalScroll`; use one `LazyColumn` with `item {}` blocks.

### Avoiding Unnecessary Work

12. **Move data transformations to the ViewModel.** Sorting, filtering, grouping and model mapping happen there, not in `remember` blocks in composables.

13. **Cache expensive objects as top-level constants** (`RoundedCornerShape`, `Regex`, `DateTimeFormatter`), not per item per frame.

14. **Use `background(color, shape)` instead of `clip(shape).background(color)`**: `clip` adds a persistent clip layer.

15. **Avoid `copy(alpha = ...)` on colors.** Alpha blending forces GPU compositing; use opaque theme colors where possible.

16. **Defer state reads to later phases.** Use lambda modifiers (`Modifier.offset { }`, `Modifier.drawBehind { }`) instead of value ones (`Modifier.offset(x, y)`, `Modifier.background(animatedColor)`) for scroll/animation state.
