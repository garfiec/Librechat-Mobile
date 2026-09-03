# Design assets

## `TchatLogo.png`

The Tchat mark: 1606×1606 RGBA, **foreground only** on transparency, no background, no corner
mask — same contract `SwitchboardLogo.png` (still present, unused) followed for Switchboard's own
icon. This is the source of truth for both the Android launcher icon and the iOS AppIcon set.

Generated from `TchatLogo.svg` (the T+dot glyph from `Tchat/deploy/tchat/branding/tchat-mark.svg`,
with its rounded-square tile dropped and the glyph itself recoloured to the tile's own gradient —
see the comment at the top of that file for why: a dark-on-transparent glyph would be invisible
against this project's dark launcher background):

```sh
rsvg-convert -w 1606 -h 1606 design/TchatLogo.svg -o design/TchatLogo.png
```

`Tchat/deploy/tchat/branding/tchat-mark.svg` is itself explicitly a placeholder ("Replace with the
real asset"). When a real Tchat mark exists, redo `TchatLogo.svg`/`TchatLogo.png` against it once,
rather than maintaining two generations of placeholder.

## Regenerating the Android launcher icon

```sh
python3 design/generate-android-icons.py    # from the repo root; needs Pillow + NumPy
```

That rewrites the adaptive-icon foreground, the themed-icon monochrome layer, and the legacy
square/round bitmaps across all five densities. It should print `~70%` for the mark's share of
the visible tile; a number far from that means something is wrong with the input.

The **background colour lives in `app/src/main/res/values/ic_launcher_background.xml`**, and
the script reads it from there. The legacy bitmaps bake the colour in, so changing it means
editing that file and re-running the script — otherwise the two disagree. The adaptive
foreground and monochrome layers are colour-independent.

Two decisions in the script that look wrong but are not, and should not be "simplified":

- Only the **solid** artwork is fitted to the safe circle, not the full alpha extent. This was
  written for Switchboard's mark, whose glow is a large part of the image and is meant to bleed
  past the safe zone and fill the layer. `TchatLogo.png` has no such glow, so this is inert on it
  (the "solid" region is essentially the whole glyph) rather than actively load-bearing — kept
  as-is so a future real Tchat mark that *does* want a bleeding glow still works without a rewrite.
- The glow is **feathered at the master's canvas border**, where it is cut off at up to 52%
  alpha. Inset into the safe zone that cut would otherwise show as a hard line. Also inert on the
  current glyph for the same reason.

## Regenerating the iOS AppIcon set

```sh
python3 design/generate-ios-icons.py        # from the repo root; needs Pillow
```

Rewrites all 16 PNGs in `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`, reading their exact
filenames and sizes from that appiconset's own `Contents.json` (never hardcoded, so the two can't
disagree). iOS icons are fully opaque — no alpha channel, no corner mask baked in; the OS applies
its own squircle mask at render time.

This appiconset is a traditional flat-PNG-per-size set, not an Xcode 16+ Icon Composer `.icon`
bundle (its `Contents.json` lists plain image files, not an `.icon` reference), so a script is a
legitimate way to maintain it. It intentionally does **not** try to match Android's safe-circle
percentage — the two platforms have different masking conventions and are not meant to be pixel-
identical; see `generate-ios-icons.py`'s own `GLYPH_FRACTION` constant if the padding ever needs
adjusting.

## `SwitchboardLogo.png`

Superseded by `TchatLogo.png` above; kept only because `generate-android-icons.py`'s git history
and any future upstream-sync diff need it to still exist for comparison. Not read by anything.
