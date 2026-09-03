# Tchat: Native Mobile Client

[![LibreChat](https://img.shields.io/badge/LibreChat-v0.8.4_–_v0.8.8--rc1-blue)](https://github.com/danny-avila/LibreChat/releases/tag/v0.8.8-rc1)

The native Android/iOS client for **Tchat**, TensorGrid's self-hosted deployment of
[LibreChat](https://www.librechat.ai/) at `chat.tensorgrid.space`. There is no server field to
fill in — the app is pinned to Tchat and opens straight to sign-in.

This is a rebrand of [Switchboard](https://github.com/garfiec/Librechat-Mobile), a third-party
native LibreChat client, kept as a fork so it can keep pulling in Switchboard's fixes and
features. See [TCHAT.md](TCHAT.md) for exactly what this fork changes and why.

> **Backend compatibility:** Tested against LibreChat **v0.8.4 – v0.8.8-rc1** — Tchat itself
> currently runs v0.8.8-rc1.

## Why use this instead of the web app?

- **Smoother and faster** — a real native app instead of a website in a wrapper, so scrolling, typing, and watching responses stream in all feel snappier.
- **Feels like a mobile app, not a shrunk-down website** — familiar gestures like swipe to switch accounts and swipe back, comfortable tap targets, and a layout that adapts to tablets and foldables.
- **Plays nicely with your phone** — share text, images, and files into the app from anywhere *(Android)*, pin your favorite models or generated content to your home screen, open chat links straight into the right screen, and save or share images through your phone's normal menus.
- **Works without a connection** — your chat list and any conversations you've opened are saved on your phone, so you can keep reading with spotty or no signal.
- **Multiple accounts, one app** — sign in to more than one Tchat account and switch between them instantly, no re-entering passwords.
- **Dictation stays on your phone** — voice typing runs on-device instead of through a server.

## Features

- **Multi-Account** — Sign in to more than one Tchat account and switch between them instantly, no re-login. Swipe the account chip to switch; storage is fully scoped per account. All accounts are Tchat accounts — the server is fixed and there is no in-app way to point at a different one, in any build (see [TCHAT.md](TCHAT.md)).
- **Pin Artifacts to Home Screen** — Turn any generated artifact (HTML, React, SVG, Mermaid, Markdown) into a home-screen launcher icon that opens straight into a full-screen viewer — works on cold start, while logged out, and even after the source chat is deleted. *(Android)*
- **Quick Actions** — Your most-used models become home-screen shortcuts (Android) and long-press quick actions (iOS) that deep-link into a new chat with that model preselected.
- **Rich Media Viewer** — Full-screen, Google-Photos-style viewer with pinch-to-zoom and swipe between images across a branch or grid, shared by chat and files.
- **Native Upload, Share & Save** — Attach from the device or reuse existing server files without re-uploading, save images to your gallery, and share out through the system share sheet with original format preserved.
- **Themes** — Pick any accent color and the whole app regenerates a matching Material 3 palette — choose from presets or dial in a custom color (HSV or hex). System/light/dark modes, plus Material You wallpaper-based colors on Android 12+.
- **Chat** — Real-time streaming (SSE), message branching & sibling navigation, stop/regenerate/continue, markdown with syntax highlighting, LaTeX math rendering, code blocks with copy, image display, file attachments, tool call progress cards
- **Inline Artifacts** — Render Mermaid, SVG, HTML, React, and Markdown artifacts directly in chat messages at full message width with content-fit height. Per-type toggle in Settings → Chat → Artifacts.
- **Model Selection** — Searchable bottom sheet grouped by endpoint, model comparison mode
- **Agents** — Marketplace with search and categories, MCP server configuration
- **Conversations** — Paginated list with date grouping, tags, search, rename, archive, delete, share, fork, duplicate, export/import
- **Voice** — On-device live dictation (Android & iOS) with cloud fallback, plus text-to-speech playback
- **Mobile & Tablet** — Single adaptive UI: single-pane on phones, dual-pane layout (600dp+) with a persistent sidebar on tablets and foldables, with native predictive-back gestures
- **Settings** — In-app language switching, account management, data controls
- **Accessibility** — Semantic headings, content descriptions, 48dp touch targets, live regions

## Screenshots

| Feature | Phone | Tablet / Foldable |
|---|---|---|
| **Home Screen** — Clean welcome screen with voice input and quick access | <img src="docs/screenshots/home-phone.png" width="280"> | <img src="docs/screenshots/home-tablet.png" width="380"> |
| **Conversations Sidebar** — Swipe to open your chat history with search, tags, and date grouping | <img src="docs/screenshots/sidebar-phone.gif" width="280"> | <img src="docs/screenshots/sidebar-tablet.gif" width="380"> |
| **Predictive Back** — Native Android back gesture with peek animation | <img src="docs/screenshots/predictive-back-phone.gif" width="280"> | <img src="docs/screenshots/predictive-back-tablet.gif" width="380"> |
| **Mermaid Diagrams** — Interactive flowcharts and diagrams rendered in-chat | <img src="docs/screenshots/mermaid-phone.png" width="280"> | <img src="docs/screenshots/mermaid-tablet.png" width="380"> |
| **LaTeX Math** — Beautifully typeset equations and formulas | <img src="docs/screenshots/latex-phone.png" width="280"> | <img src="docs/screenshots/latex-tablet.png" width="380"> |
| **Code Blocks** — Syntax-highlighted code with language badge and copy button | <img src="docs/screenshots/code-phone.png" width="280"> | <img src="docs/screenshots/code-tablet.png" width="380"> |
| **Tables** — Clean, scrollable data tables | <img src="docs/screenshots/table-phone.png" width="280"> | <img src="docs/screenshots/table-tablet.png" width="380"> |
| **Extended Thinking** — See the model's reasoning process | <img src="docs/screenshots/thinking-phone.png" width="280"> | <img src="docs/screenshots/thinking-tablet.png" width="380"> |
| **Chat Media** — Browse all media shared in the current chat in one place | <img src="docs/screenshots/chat-media-phone.png" width="280"> | <img src="docs/screenshots/chat-media-tablet.png" width="380"> |
| **Chat Options** — Attach files, switch models, toggle tools, and tune parameters | <img src="docs/screenshots/chat-options-phone.png" width="280"> | <img src="docs/screenshots/chat-options-tablet.png" width="380"> |
| **Model Selection** — Searchable bottom sheet with models grouped by provider | <img src="docs/screenshots/model-selection-phone.png" width="280"> | <img src="docs/screenshots/model-selection-tablet.png" width="380"> |
| **Settings** — Theme, language, layout, and personalization options | <img src="docs/screenshots/settings-phone.png" width="280"> | <img src="docs/screenshots/settings-tablet.png" width="380"> |

## Install

Tchat-Mobile is currently built and distributed as internal/debug builds only — there is no
public release channel (Obtainium, Play Store, App Store) yet. See
[TCHAT.md](TCHAT.md#deferred-internaldebug-builds-only-for-now) for what each of those needs when
that changes, and `TCHAT.md`'s top section for how to build a debug APK yourself in the meantime.

## Requirements

| Tool | Version |
|------|---------|
| JDK | 17+ |
| Android Studio or IntelliJ IDEA | Latest stable (recommended IDE for all code editing) |
| Xcode | 15+ (iOS only, Apple Silicon Mac required — IDE not needed, CLI only) |
| iOS Deployment Target | 16.0+ |
| Gradle | 9.5.1 (via wrapper) |
| Kotlin | 2.4.10 |

## Building from Source

### Android

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build:

```bash
./gradlew assembleRelease
```

### iOS

Xcode builds the Kotlin framework itself via a build phase (direct integration), so there is no separate `link*Framework` step to run by hand.

**Simulator:**

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath iosApp/build build
```

**Physical device:**

```bash
open iosApp/iosApp.xcodeproj
```

Then in Xcode: select your device, set your Team under Signing & Capabilities, and press ⌘R.

See [iosApp/README.md](iosApp/README.md) for full build and launch instructions.

### Tech Stack

- Kotlin Multiplatform (KMP) with shared business logic
- Jetpack Compose (Android) + Compose Multiplatform (iOS)
- Koin (dependency injection)
- Ktor Client (OkHttp on Android, Darwin on iOS)
- Kotlinx Serialization
- Room (cache), DataStore (preferences), EncryptedSharedPreferences / Keychain (tokens)
- Kotlin 2.4.10, compileSdk 36, minSdk 26

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, and PR guidelines.

## License

This project is licensed under the [MIT License](LICENSE).
