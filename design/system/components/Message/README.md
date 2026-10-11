# Message

One turn in the transcript. The person's own messages are bubbles on the right. The assistant's replies run full-width with no bubble, so long answers, code and tables have room.

**Consumer provides:** `role` (`user` or `assistant`), `children` (the rendered markdown), and optionally `sender` and `model` (assistant header), `time`, `streaming` and `error`.

- Body text is `body-lg` in `ink`, with code in `code` on `code-surface`. User bubbles are `bubble-user` with `on-bubble-user`, `radius-lg`, and a `radius-xs` tail corner. They're at most 80% wide.
- Leave `space-3` between turns from the same sender and `space-5` when the sender changes. The column caps at `size-reading`.
- While `streaming`, a caret in `accent` follows the text and the timestamp is hidden. Don't fade tokens in.
- An `error` sits under the message in `danger` with an icon and a retry path. It never replaces what was already received.
- Each message is one accessibility node: sender, time, then content. Actions (copy, regenerate, feedback) appear on long-press or below the last reply, not on every message.
