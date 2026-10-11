# ModelTrigger

Names the model the chat is using, in the top bar, and opens the ModelPicker when tapped.

**Consumer provides:** `label` (the readable name, like "GPT-6 Luna", never the raw id), `owner` (the maker, like "OpenAI"), `mark` (the maker's art; the preview uses a monogram tile), optionally `free`, `imageModel` (the Image Gen model's label when Image Gen is on) and `onClick`.

- Line one is the model in `title-sm`, with a Free tag if it applies, then a chevron. Line two is the maker in `body-sm` `ink-muted`. When Image Gen is on, line two adds an image icon and the image model's name in `accent-ink`. Both models in use are visible without opening anything.
- The whole block is one button at least `size-touch` tall. Its accessibility name reads "Choose model. Current: GPT-6 Luna. Image Gen: GPT Image".
- Long names truncate on line one, and line two never wraps. The raw id is never in the bar; it's in the picker's Current card.
- For an agent, show the agent's name and avatar, with "Agent" as line two.
- Replaces `ModelSelectorButton`, which shows the raw id with a drop-down arrow.
