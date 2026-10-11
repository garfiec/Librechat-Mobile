# ModelPicker

The bottom sheet for choosing the chat model, an agent and the Image Gen model. Models are grouped by the company that makes them, as on Tchat web.

**Consumer provides:** `groups` (an ordered list of `{id, label, caption?, mark, kind?, intro?, rows}`), `selected` (the chat model id), `image` (the active image entry or `null`), and `onSelect` and `onImage` callbacks. Each row takes `{id, label, model?, detail?, free?, pinned?}`. `kind` is `"image"` for the Image Gen group and `"agent"` for agents.

**Building the groups** (port of the web fork's `owners.ts`):
- Group chat models by maker from their id (`gpt`/`o1`/`openai/` → OpenAI · GPT, `claude` → Anthropic · Claude, `deepseek`, `kimi`/`moonshot`, `qwen`, `minimax`, `nemotron`/`nvidia/` → NVIDIA, …, with the rest under "Other"). Keep the web's order: makers first, then Image Gen, then agents.
- A model served by both TensorGrid endpoints is listed once, on the maker's dedicated endpoint (TensorGrid-Claude for Claude).
- Labels are formatted (`gpt-5.6-terra` → "GPT-5.6 Terra", `claude-opus-4-8` → "Claude Opus 4.8"), and the raw id sits underneath in mono `ink-muted`, so nothing is hidden.
- **Drop anything that can't hold a chat** (image, embedding, transcription and audio models) using `/api/tchat/models/catalog` categories, falling back to the id patterns in `guessModelCategory`. The same endpoint supplies the Free flags.
- Image Gen rows come from `startupConfig.modelSpecs.imageList`, never from `/v1/models`.

**Layout, top to bottom**
1. A drag handle, then "Choose a model" (`title-lg`).
2. A search field (`surface-sunken`, 48px). It matches label, id, maker, family and "free".
3. A **maker rail**: horizontally scrolling pills reading "All 27", "OpenAI 9", "Anthropic 8"…, then "Image Gen 6". The selected pill is solid `ink`. A pill containing the current pick has an `accent` dot.
4. A **Current** card on `accent-container` showing the label, Free tag, "Maker · raw-id", the Image Gen model if one is on, and a "Current" check. It's shown only under All with no search.
5. **Pinned**, then one section per maker with a sticky header (mark, name, "family · count"). Rows are 56px tall: a label (`body-md` 500), the mono id, a Free tag, and a check in `accent-ink` when selected.

**Image Gen behaves as a toggle, not a model switch.** Picking an image model turns Image Gen on with that model and leaves the chat model unchanged. Picking the active one again turns it off, and the active row shows "On". The group opens with a one-line intro saying exactly that.

- A pick closes the sheet. Reopening it starts again at All with no search.
- Long-press a row for Pin / Unpin. Pinned rows show a pin glyph and also appear under Pinned. There's no always-visible star to tap by mistake.
- With no matches, say what was searched. If a maker filter is on, offer "Search all makers".
- Replaces `ModelSelectorSheet`, which groups by endpoint, lists image and embedding models as chat models, and shows raw ids only.
