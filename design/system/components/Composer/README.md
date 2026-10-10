# Composer

The message input at the bottom of the chat: tools on the left, the text in the middle, and one primary action on the right.

**Consumer provides:** `placeholder` ("Message…", or "Message {agent}"), `onSend`, `streaming`, and optionally `defaultValue`.

- The shell is `surface` with a `border-strong` hairline, `radius-lg` and `elevation-1`. It grows to about six lines (160px), then scrolls.
- The right-hand action changes with state, so there is only ever one: voice (empty field, `ink-muted`), send (text present, `accent` disc) or stop (while a reply streams, an `ink` disc). Each has its own accessibility label.
- Attachments and tool toggles stack above the field as Chips. They never push the send button off screen.
- Keep the composer above the keyboard and inside the safe area, at least `space-2` above the home indicator.
- Liquid Glass renders the shell in glass. The send disc stays solid `accent` for contrast.
