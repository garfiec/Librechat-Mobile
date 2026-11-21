# feature:auth:ui

Stateless sign-in UI: every screen's `…Content`, gate and policy sections, dialogs, `AuthRefusal`,
the view state and all of the feature's strings. The general rules (dependencies, wrapper + Content, file names, `Res`) are in the root `CLAUDE.md`, "Feature UI modules".

- Slots, each defaulting to nothing: `diagnosticLogLink` (export link, a ViewModel per entry), `webView`
  on `SsoLoginContent` and `GatePageSignInFrame` (platform WebViews), `gatePage` on `ServerUrlContent`.
