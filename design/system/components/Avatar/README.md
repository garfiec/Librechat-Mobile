# Avatar

Shows a person, agent or model as a photo, or as initials on a stable colour.

**Consumer provides:** `name` (the accessibility name and the source of the initials), optionally `src` (an image URL), `seed` (a stable id for the colour; it defaults to the name, but pass the account id) and `size` (`sm` 28px beside messages, `md` 40px in lists, `lg` 64px in profiles).

- Initials are `on-avatar` on `avatar-1` to `avatar-14`, picked by `avatarSlot(seed)`, the same 31-based hash as `avatarColorForSeed`. All fourteen pass 4.5:1.
- Seed with a stable id, never with a display name the person can edit, or their colour will change.
- Model and endpoint icons (`ModelIcon`, `EndpointBadge`) are logos, not avatars, so they keep their own artwork.
