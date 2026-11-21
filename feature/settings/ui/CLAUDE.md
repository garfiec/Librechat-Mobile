# feature:settings:ui

Stateless Settings UI: every page's `…Content`, rows, cards, dialogs and sheets, the UI state
(`viewmodel/*UiState.kt`, `state/`), view data (`model/`), `SettingsPage` and all Settings strings.
The general rules (dependencies, wrapper + Content, file names, `Res`) are in the root `CLAUDE.md`, "Feature UI modules".

- `SettingsPage` is the panes' plain type; `:feature:settings` maps it to and from the Nav 3 routes, which
  keep their saved format.
- Filled by the entry points: the avatar picker, the provider-key file picker, the home-screen shortcut
  check and the Set key dialog slot.
