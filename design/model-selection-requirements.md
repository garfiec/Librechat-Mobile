# Model selection and Image Gen: review and requirements

Requirements for implementing the design in `design/system/Model selection.md` and the
`ModelTrigger` / `ModelPicker` / `Chip` / `Composer` component notes. The reference
implementation is Tchat web's picker (`Tchat/client/src/components/Chat/Menus/Endpoints/Tchat/`):
`owners.ts`, `useModelCatalog.ts`, `useRepairImageModelChat.ts` and `TchatModelSelector.tsx`.

## Review of the design

The design holds up against the web picker and the server. These are the places where it was
silent, conflicted with the app, or needed a concrete rule, and what the implementation does.

| # | Finding | Decision |
| --- | --- | --- |
| D1 | The design says long-press a row to pin it. The old sheet used long-press for drag-select (hold, then slide to pick). | The new picker drops drag-select. Long-press opens a Pin / Unpin menu. |
| D2 | The design calls `image_gen` a per-conversation flag. On mobile, every other tool toggle (code, file search, MCP) is app-wide and persisted in DataStore. | Image Gen follows the app's convention: one persisted selection, shared by the picker, the tools sheet and the chip. The request still sends it per message as `ephemeralAgent.image_gen`. |
| D3 | "The current model is always visible" needs the top bar to show the model. The app's header bubble defaults to the conversation title (Settings → Chat → Header). | Tchat defaults the header to the model trigger. People can still choose the title or nothing. |
| D4 | Liquid Glass bars are drawn from a one-line `BarTitle`, so the trigger's second line can't render there. | Glass shows one line: "GPT-6 Luna", or "GPT-6 Luna · GPT Image" when Image Gen is on. Material shows the two-line trigger. |
| D5 | "If the image tool isn't available to this user" had no concrete test. | Available means `GET /api/agents/tools` lists `image_gen_oai`, the same check as web's `useImageGenAvailable`. |
| D6 | The server ignores ephemeral tools when an agent is selected. The design doesn't cover agents. | While an agent is selected, the chip and the trigger's image line are hidden and `image_gen` isn't sent. The Image Gen group stays in the picker. |
| D7 | Comparison mode's second model can't carry Image Gen (only the primary request has `ephemeralAgent`). | The secondary picker has no Image Gen group. |
| D8 | A new chat seeded from a stale last-used image model would show the repair message before the person did anything. | New chats are repaired silently. The message is shown only when an existing conversation is repaired. |
| D9 | The old sheet had a "Set API key" call to action for endpoints with user-provided keys. Tchat has none (`EXPOSE_PROVIDER_BACKEND_SETTINGS = false`). | The new picker doesn't show it. A send on such an endpoint still gets the existing key-error snackbar. |
| D10 | The design shows letter placeholders for maker marks. | OpenAI, Anthropic and Google use the app's existing logos. Other makers use a monogram tile. |
| D11 | Settings → Chat → "Starred models" (Grouped / Top / Off) has no counterpart in the design. | Off hides the Pinned section. Grouped and Top both show it. |

## Requirements

### Data

- **R1.** Decode `startupConfig.modelSpecs.imageList` as `ImageSpec {name, label, model, protocol, description?, iconURL?, default?}`. A missing list is empty.
- **R2.** Decode `default` on `modelSpecs.list` entries. The default spec's `preset.endpoint` and `preset.model` drive the repair fallback (R16).
- **R3.** `EphemeralAgent` gains `image_gen`, sent as `true` for the default image entry or as an `imageList` entry name. It's omitted when Image Gen is off.
- **R4.** Fetch `GET /api/tchat/models/catalog` (`{free: [id], categories: {id: category}}`) once per app session, with a 5-minute refresh on next use. A failed fetch leaves the catalog unknown and never blocks the picker or sending.
- **R5.** Image Gen is available when `GET /api/agents/tools` lists `image_gen_oai` and `imageList` isn't empty (D5).
- **R6.** The Image Gen selection persists across launches: off, the default entry, or a named entry (D2).

### Picker content (port of `owners.ts`)

- **R7.** Group chat models by maker from the model id, using web's owner table in its order: OpenAI · GPT, Anthropic · Claude, Google · Gemini, xAI · Grok, DeepSeek, Moonshot · Kimi, Qwen, Zhipu · GLM, MiniMax, Mistral, Meta · Llama, NVIDIA · Nemotron, then Other. The first matching pattern wins.
- **R8.** Show each model under a readable label (`gpt-5.6-terra` → "GPT-5.6 Terra", `claude-opus-4-8` → "Claude Opus 4.8", `moonshotai/kimi-k3` → "Kimi K3"), with the raw id under it in monospace.
- **R9.** Never list a model that can't chat. The catalog's category decides. For a model the catalog doesn't list, only an unmistakable id (image, embedding, transcription, audio) is hidden, so a catalog that lags behind never hides a working chat model.
- **R10.** A model served by two endpoints is listed once, on the endpoint whose models all share its maker (TensorGrid-Claude for Claude).
- **R11.** Mark models the catalog prices at zero with a "Free" tag.
- **R12.** Group order: Pinned (All only, no search), the makers, then Image Gen, then My Agents.

### Picker behavior

- **R13.** The sheet has a title ("Choose a model"), a search field, a horizontally scrolling maker rail ("All n", "OpenAI n", …, "Image Gen n", "Agents n"), a Current card (All only, no search) and the sections. Section headers stick while scrolling. The rail pill for the group holding the current pick has a dot.
- **R14.** Search is a case-insensitive substring match over label, id, maker, family and the word "Free" for free models. With no matches the sheet says what was searched and, when a maker filter is on, offers "Search all makers".
- **R15.** Tapping a chat model selects it and closes the sheet. Nothing else changes. Reopening starts again at All with no search.
- **R16.** Tapping an Image Gen row turns Image Gen on with that entry. Tapping the active one turns it off. The chat model never changes. The active row shows "On", and the group opens with a one-line intro saying this.
- **R17.** Long-press a model or agent row for Pin / Unpin (D1). Pinned rows show a pin glyph.
- **R18.** The send-block banner the old sheet showed (no model selected, readiness timeout) still appears at the top of the picker.

### Current model and Image Gen indicators

- **R19.** The top bar trigger shows the readable label with a Free tag and a chevron on line one, and the maker on line two. When Image Gen is on, line two adds an image icon and the image entry's label. For an agent, it shows the agent's name with "Agent" as line two. Its accessibility label is "Choose model. Current: {model}. Image Gen: {image}".
- **R20.** While Image Gen is on (and no agent is selected), an input chip above the composer shows the image entry's label with an image icon. Tapping the chip opens the picker on the Image Gen filter. Its ✕ turns Image Gen off.
- **R21.** The composer's tools sheet has an Image Gen switch that writes the same selection: on means the default entry.

### Repair

- **R22.** When the selected chat model is an image model (by the catalog, once it has answered), switch the chat model to the default spec's model if that spec is on the same endpoint, otherwise to that endpoint's first chat model.
- **R23.** Turn Image Gen on with the matching `imageList` entry: an exact model match, then the same family at another price tier (`gpt-image-2-4k` → `gpt-image-2`), then the default entry. Skip this when Image Gen isn't available.
- **R24.** For an existing conversation, show a snackbar: "{image} only makes images, so it now runs through Image Gen. This chat continues on {model}." (web's `com_ui_image_model_moved`). Repair runs at most once per endpoint and model per screen.

### Out of scope

- Per-message model badges in the transcript (unchanged).
- Model specs other than the default as picker rows (web lists them as Presets; Tchat's two specs name models already in the list).
- The agent marketplace row (Tchat disables the marketplace).
