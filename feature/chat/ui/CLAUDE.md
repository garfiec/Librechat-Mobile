# feature:chat:ui

Stateless chat UI: components, `ChatUiState` and its slices, pure util, prompts UI, `ChatScreenContent`
and the other screen contents, previews and every chat string. The general rules (dependencies, wrapper + Content, file names, `Res`) are in the root `CLAUDE.md`, "Feature UI modules".

- The chat screen's Content takes `ChatActions` (implemented by `ChatViewModel`) and `ChatScreenSlots`
  (composer, top bar, Add to chat, MCP providers, child-thread sheet), all filled by `ChatScreen`.
- Platform rendering (WebViews for math, mermaid and artifacts, players, native PDF, MCP Apps) goes
  through `LocalChatContentRenderers`, provided once in shared's `LibreChatNavHost`; a null slot draws
  the source or nothing. Add a slot there, not an `expect`.
- `nonAndroidMain` (iOS + browser) holds the iOS `MessageBubble`, `MarkdownContent` and `keyboardRising`.
- `:feature:chat` keeps `files/web` (vendored web assets) with its own `Res` in
  `…feature.chat.web.resources`.
