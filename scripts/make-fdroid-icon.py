#!/usr/bin/env python3
"""Build the F-Droid listing icon from the Switchboard logo master.

Writes `fastlane/metadata/android/en-US/images/icon.png` — see `fastlane/README.md`.

Two things here are load-bearing:

  * The mark is flattened onto an opaque background. F-Droid composites a transparent icon
    over its own theme colour, and a dark mark vanishes on a dark theme. (`sips --padColor`
    cannot do this; it only pads outside the image bounds.)
  * The crop is derived from the mark's *measured* alpha bounding box and may expand past the
    source edges, filling with background. The master is full-bleed, so a fixed crop clips the
    glow, and measuring stays correct if the master is ever redrawn with margins.

Pillow is deliberately not a dependency, hence the `zlib`/`struct` PNG codec below.

Usage:  python3 scripts/make-fdroid-icon.py [SOURCE_PNG]
"""

from __future__ import annotations

import pathlib
import struct
import sys
import zlib

# app/src/main/res/values/ic_launcher_background.xml — keep in step with the adaptive icon.
BACKGROUND = (0x1C, 0x1C, 0x1E)
# Fraction of the final canvas the mark should span: the 72/108 Android adaptive-icon safe zone.
MARK_FRACTION = 72 / 108
SIZE = 512

DEFAULT_SOURCE = pathlib.Path.home() / "Desktop" / "SwitchboardLogo.png"
# Anchored to the repo, not the cwd: a relative destination writes a stray fastlane/ tree
# wherever the script was run from and reports success, leaving the real icon untouched.
REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST = REPO_ROOT / "fastlane/metadata/android/en-US/images/icon.png"


def decode_png(data: bytes) -> tuple[int, int, bytearray]:
    """Return (width, height, RGBA rows) for an 8-bit truecolour PNG."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("not a PNG")
    pos, idat, ihdr = 8, bytearray(), None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos : pos + 4])
        kind = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + length]
        if kind == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", chunk)
        elif kind == b"IDAT":
            idat += chunk
        elif kind == b"IEND":
            break
        pos += 12 + length
    if ihdr is None:
        raise SystemExit("PNG has no IHDR")
    width, height, depth, colour, _comp, _filt, interlace = ihdr
    if depth != 8 or colour not in (2, 6) or interlace:
        raise SystemExit(
            f"unsupported PNG: depth={depth} colourtype={colour} interlace={interlace}; "
            "expected 8-bit RGB or RGBA, non-interlaced"
        )

    src_bpp = 4 if colour == 6 else 3
    stride = width * src_bpp
    raw = zlib.decompress(bytes(idat))
    expected = height * (stride + 1)
    if len(raw) != expected:
        raise SystemExit(f"decompressed {len(raw)} bytes, expected {expected}")

    out = bytearray(width * height * 4)
    prev = bytearray(stride)
    for y in range(height):
        base = y * (stride + 1)
        ftype = raw[base]
        line = bytearray(raw[base + 1 : base + 1 + stride])
        if ftype == 1:
            for i in range(src_bpp, stride):
                line[i] = (line[i] + line[i - src_bpp]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                left = line[i - src_bpp] if i >= src_bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                a = line[i - src_bpp] if i >= src_bpp else 0
                b = prev[i]
                c = prev[i - src_bpp] if i >= src_bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        elif ftype != 0:
            raise SystemExit(f"row {y}: unknown filter type {ftype}")
        prev = line

        o = y * width * 4
        if src_bpp == 4:
            out[o : o + stride] = line
        else:
            for x in range(width):
                out[o + x * 4 : o + x * 4 + 3] = line[x * 3 : x * 3 + 3]
                out[o + x * 4 + 3] = 255
    return width, height, out


def alpha_bbox(width: int, height: int, rgba: bytearray, threshold: int = 8):
    """Bounding box of pixels whose alpha exceeds `threshold`."""
    min_x, min_y, max_x, max_y = width, height, -1, -1
    for y in range(height):
        row = y * width * 4
        for x in range(width):
            if rgba[row + x * 4 + 3] > threshold:
                if x < min_x:
                    min_x = x
                if x > max_x:
                    max_x = x
                if y < min_y:
                    min_y = y
                if y > max_y:
                    max_y = y
    if max_x < 0:
        raise SystemExit("source image is fully transparent")
    return min_x, min_y, max_x, max_y


def encode_png(width: int, height: int, rgb: bytearray) -> bytes:
    """Encode 8-bit truecolour RGB (no alpha) with filter 0 rows."""
    raw = bytearray()
    stride = width * 3
    for y in range(height):
        raw.append(0)
        raw += rgb[y * stride : (y + 1) * stride]

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def main() -> None:
    source = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SOURCE
    if not source.is_file():
        raise SystemExit(f"source not found: {source}")

    width, height, rgba = decode_png(source.read_bytes())
    print(f"source      {width}x{height}  {source}")

    min_x, min_y, max_x, max_y = alpha_bbox(width, height, rgba)
    mark_w, mark_h = max_x - min_x + 1, max_y - min_y + 1
    print(f"mark bbox   {mark_w}x{mark_h} at ({min_x},{min_y})")

    side = max(mark_w, mark_h) / MARK_FRACTION
    cx, cy = (min_x + max_x + 1) / 2, (min_y + max_y + 1) / 2
    left, top = cx - side / 2, cy - side / 2
    print(f"crop        {side:.0f}x{side:.0f} at ({left:.0f},{top:.0f})  "
          f"mark spans {MARK_FRACTION:.0%} of the canvas")

    br, bg, bb = BACKGROUND
    out = bytearray(SIZE * SIZE * 3)
    step = side / SIZE

    for oy in range(SIZE):
        y0 = top + oy * step
        y1 = y0 + step
        sy0, sy1 = int(y0 // 1), max(int(y0 // 1) + 1, int(-(-y1 // 1)))
        for ox in range(SIZE):
            x0 = left + ox * step
            x1 = x0 + step
            sx0, sx1 = int(x0 // 1), max(int(x0 // 1) + 1, int(-(-x1 // 1)))

            # Area-average the source box, treating outside-the-image as fully transparent.
            rs = gs = bs = as_ = 0
            n = 0
            for sy in range(sy0, sy1):
                if sy < 0 or sy >= height:
                    n += sx1 - sx0
                    continue
                row = sy * width * 4
                for sx in range(sx0, sx1):
                    n += 1
                    if sx < 0 or sx >= width:
                        continue
                    i = row + sx * 4
                    a = rgba[i + 3]
                    # Weight colour by alpha so transparent pixels don't drag in stray RGB.
                    rs += rgba[i] * a
                    gs += rgba[i + 1] * a
                    bs += rgba[i + 2] * a
                    as_ += a
            if n == 0:
                n = 1

            alpha = as_ / (n * 255)
            if as_ == 0:
                r = g = b = 0
            else:
                r, g, b = rs / as_, gs / as_, bs / as_

            o = (oy * SIZE + ox) * 3
            out[o] = min(255, max(0, round(r * alpha + br * (1 - alpha))))
            out[o + 1] = min(255, max(0, round(g * alpha + bg * (1 - alpha))))
            out[o + 2] = min(255, max(0, round(b * alpha + bb * (1 - alpha))))

    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_bytes(encode_png(SIZE, SIZE, out))
    print(f"wrote       {DEST}  {SIZE}x{SIZE}  {DEST.stat().st_size} bytes  "
          f"background #{br:02X}{bg:02X}{bb:02X}, no alpha")


if __name__ == "__main__":
    main()
