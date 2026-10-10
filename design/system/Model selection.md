# Model selection and Image Gen

Choosing a model is the most frequent decision in Tchat after typing a message. The flow mirrors Tchat web's picker (`client/src/components/Chat/Menus/Endpoints/Tchat/`), adapted for phones. On a phone the picker is a bottom sheet, the maker list becomes a horizontal rail, and pinning moves to long-press.

## The current model is always visible

- The top bar's **ModelTrigger** names the model in readable form ("GPT-6 Luna"), its maker ("OpenAI"), and, when Image Gen is on, the image model ("GPT Image") in `accent-ink`. A person can always see what they're paying for before they send.
- The raw id never appears in the bar. It appears in the picker, under each label and in the Current card, for people who need to tell `gpt-5.6-luna` from `gpt-6-luna`.
- When the model changes mid-conversation, the next reply's header shows the new model's badge. Earlier replies keep the badge of the model that wrote them.

## Choosing a chat model

1. Tap the trigger. The **ModelPicker** sheet opens at half height, scrolled to the top, with All selected.
2. The **Current** card confirms what's in use. **Pinned** models come next, so the usual pick is one tap away.
3. Search or tap a maker in the rail to narrow the list. Each maker section lists only models that can hold a chat. Image, embedding and transcription models never appear here, because sending a chat message to one fails.
4. Tapping a row selects it, closes the sheet, and updates the trigger. Nothing else changes: the conversation, tools and Image Gen stay as they were.

## Image Gen

Image models make pictures. They can't chat, so they're never chat models. Image Gen equips the chat with an image tool, and the chat model decides when to call it.

- **Two ways in, one state.** People can turn Image Gen on from the picker's **Image Gen** group, or from the composer's tools menu. Both write the same per-conversation flag, `ephemeralAgent.image_gen`, which is `true` for the default entry or an `imageList` entry name.
- **Picking an image model is a toggle.** Picking one turns Image Gen on with that model, and picking the active one again turns it off. The chat model is untouched either way.
- **While it's on, it shows in two places.** The trigger's second line shows the image model, and an input chip above the composer ("GPT Image" with an image icon and ✕) shows it too. Tapping the chip opens the picker filtered to Image Gen, and ✕ turns it off.
- The list is `startupConfig.modelSpecs.imageList`, in the server's order, with the server's labels ("GPT Image 2 · HD"). If `imageList` is empty or the image tool isn't available to this user, hide the group and the chip.

## Repairing a chat set to an image model

Older chats, and chats synced from a client without these rules, can carry an image model as the chat model. When such a chat opens:

1. Switch the chat model to the default spec's model if it's on the same endpoint, otherwise to that endpoint's first chat model.
2. Turn Image Gen on with the matching `imageList` entry: an exact model match first, then the same family at another price tier (`gpt-image-2-4k` matches `gpt-image-2`), then the default entry.
3. Show a snackbar on `surface-inverse` that says what happened: "GPT Image 2 only makes images, so it now runs through Image Gen. This chat continues on GPT-6 Luna." This is the same string as web's `com_ui_image_model_moved`.

## Data the app needs

| Source | Gives | Status in the app today |
| --- | --- | --- |
| `GET /api/endpoints`, `/api/models` | chat models per endpoint | used |
| `GET /api/tchat/models/catalog` | `{free: [...], categories: {id: "language" \| "image" \| …}}` | **not called yet** |
| `startupConfig.modelSpecs.imageList` | `[{name, label, description, model, default?, protocol?}]` | **not decoded**: `ModelSpecs` has `list` only |
| `ephemeralAgent.image_gen` | `true` or an `imageList` name | **not sent**: `EphemeralAgent` has no field for it |
| favorites API | pinned models and agents | used |
