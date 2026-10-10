# Logos

The Tchat mark: a T with a dot beneath it, stroked in the `brand` (#4F8CFF) → `brand-violet` (#8B5CF6) gradient on transparent. Copied from the repository's `design/` folder.

- `TchatLogo.svg` is the vector source, on a 96×96 viewBox. Its ink is the gradient above, and it can't inherit a colour.
- `TchatLogo.png` is a 1606×1606 foreground-only raster. It's the source for the Android adaptive icon and the iOS AppIcon set (`design/generate-*-icons.py`).
- Set the mark on `ground` (#0C0F1A), as the launcher tile and splash do. It needs a dark ground, so never place it on `bg` light or `surface`.
- Keep the mark at least 24px, with clear space on every side of at least half its width. Don't redraw, recolour, outline or animate it.
- The upstream Tchat mark is itself marked as a placeholder. When the final mark ships, replace both files here.
