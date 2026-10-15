#!/usr/bin/env python3
"""Capture framed screenshots and recordings from the Pixel 10 Pro Fold emulator.

    scripts/screenshots/android.py demo on|off            # SystemUI demo mode (status bar)
    scripts/screenshots/android.py fold|unfold
    scripts/screenshots/android.py night yes|no           # system dark mode
    scripts/screenshots/android.py shot out.png [--raw raw.png] [--scale 0.5]
    scripts/screenshots/android.py record out.gif --seconds 8 [--width 560]
    scripts/screenshots/android.py encode out.mp4 smaller.gif --posture closed --width 400 \\
        --start 1.5 --duration 12

`shot` and `record` capture whichever display is on (the outer one folded, the inner one
unfolded) and frame it with frame.py. The emulator has two displays, so a capture must name
one: without `-d`, screencap prefixes its PNG with a warning and the file is unreadable.

`record` runs screenrecord for --seconds while you, or a script, drive the app, then frames
every video frame and writes a looping GIF with transparent corners.

Set ANDROID_SERIAL to pick the emulator (default emulator-5554).
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import frame  # noqa: E402

SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
SKIN = frame.ANDROID_SKINS / "pixel_10_pro_fold"
DEMO_CLOCK = "1200"


def adb(*args: str, capture: bool = False, binary: bool = False):
    cmd = ["adb", "-s", SERIAL, *args]
    if capture:
        out = subprocess.run(cmd, check=True, capture_output=True)
        return out.stdout if binary else out.stdout.decode()
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
    return None


def demo(on: bool):
    """SystemUI demo mode: a fixed clock, full battery and Wi-Fi, no mobile or notification
    icons.

    It also turns on the emulator's corner display-cutout emulation. The emulator doesn't know
    the skin's rounded corners or camera hole, so it puts the clock and status icons right
    against the screen edge, where the frame clips the clock's first digit and the camera hole
    covers an icon. The corner cutout makes the status bar taller, which drops its content
    below the curve and beside the camera hole."""
    broadcast = ["shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command"]
    overlay = ["shell", "cmd", "overlay", "enable" if on else "disable",
               "com.android.internal.display.cutout.emulation.corner"]
    if not on:
        adb(*broadcast, "exit")
        adb(*overlay)
        return
    adb(*overlay)
    adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    adb(*broadcast, "enter")
    adb(*broadcast, "clock", "-e", "hhmm", DEMO_CLOCK)
    adb(*broadcast, "battery", "-e", "level", "100", "-e", "plugged", "false")
    adb(*broadcast, "network", "-e", "wifi", "show", "-e", "level", "4", "-e", "fully", "true")
    adb(*broadcast, "network", "-e", "mobile", "hide")
    adb(*broadcast, "network", "-e", "nosim", "hide")
    adb(*broadcast, "notifications", "-e", "visible", "false")
    time.sleep(3)  # the cutout change relayouts the status bar


def displays() -> list[str]:
    out = adb("shell", "dumpsys", "SurfaceFlinger", "--display-id", capture=True)
    return [line.split()[1] for line in out.splitlines() if line.startswith("Display ")]


def active_display() -> tuple[str, str, tuple[int, int]]:
    """(display id, skin posture, size) for the display that's on now."""
    want = {}
    for posture in ("closed", "default"):
        size, _ = frame.android_layout(SKIN / posture)
        want[size] = posture
    for display in displays():
        png = adb("exec-out", "screencap", "-p", "-d", display, capture=True, binary=True)
        with tempfile.NamedTemporaryFile(suffix=".png") as tmp:
            tmp.write(png)
            tmp.flush()
            im = Image.open(tmp.name)
            # A display that's off reads as solid black.
            if im.size in want and im.convert("L").getextrema()[1] > 0:
                return display, want[im.size], im.size
    raise SystemExit("no lit display matches a Pixel 10 Pro Fold skin posture")


def shot(out: Path, raw_out: Path | None, scale: float):
    display, posture, _ = active_display()
    png = adb("exec-out", "screencap", "-p", "-d", display, capture=True, binary=True)
    with tempfile.NamedTemporaryFile(suffix=".png") as tmp:
        tmp.write(png)
        tmp.flush()
        raw = Image.open(tmp.name).convert("RGBA")
        raw.load()
    if raw_out:
        raw.save(raw_out)
    framed = frame.frame_android(raw, posture)
    if scale != 1:
        framed = framed.resize((round(framed.width * scale), round(framed.height * scale)),
                               Image.LANCZOS)
    framed.save(out)
    print(f"{out}: {framed.width}x{framed.height} ({posture})")


def record(out: Path, seconds: int, width: int, fps: int):
    """Record the lit display, keep the raw video beside the GIF (out.mp4), and encode."""
    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg is needed for recordings")
    display, posture, _ = active_display()
    remote = "/sdcard/capture.mp4"
    print(f"recording {posture} for {seconds}s …", flush=True)
    adb("shell", "screenrecord", "--display-id", display, "--time-limit", str(seconds),
        "--bit-rate", "20000000", remote)
    video = out.with_suffix(".mp4")
    adb("pull", remote, str(video))
    adb("shell", "rm", remote)
    encode(video, out, posture, width, fps)


def encode(video: Path, out: Path, posture: str, width: int, fps: int,
           start: float = 0, duration: float | None = None):
    """Frame a raw screen recording and write a looping GIF with transparent corners."""
    skin = SKIN / posture
    size, (x, y) = frame.android_layout(skin)
    back = Image.open(skin / "back.webp")
    height = round(back.height * width / back.width)
    # Trim inside the graph, after resampling to a constant rate: screenrecord writes one
    # keyframe and only the frames where the screen changed, so a still stretch is a single
    # frame. An input seek (-ss) decodes from the wrong place, and trimming before `fps` drops
    # that single frame and the whole stretch with it.
    end = f":end={start + duration}" if duration else ""
    trim = f"trim=start={start}{end},setpts=PTS-STARTPTS,"
    # Device image, then the video at the screen offset, then the corner/camera mask; scale,
    # then a palette that keeps the transparent outside of the frame.
    graph = (
        f"[1:v]fps={fps},{trim}scale={size[0]}:{size[1]}[screen];"
        f"[0:v][screen]overlay={x}:{y}:shortest=1[framed];"
        f"[framed][2:v]overlay={x}:{y}:shortest=1,"
        f"scale={width}:{height}:flags=lanczos,split[a][b];"
        "[a]palettegen=reserve_transparent=1:stats_mode=diff[p];"
        "[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle:alpha_threshold=128"
    )
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error",
        "-loop", "1", "-framerate", str(fps), "-i", str(skin / "back.webp"),
        "-i", str(video),
        "-loop", "1", "-framerate", str(fps), "-i", str(skin / "mask.webp"),
        "-filter_complex", graph, "-loop", "0", str(out),
    ], check=True)
    print(f"{out}: {width}x{height}, {out.stat().st_size / 1e6:.1f} MB ({posture})")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("demo").add_argument("state", choices=["on", "off"])
    sub.add_parser("fold")
    sub.add_parser("unfold")
    sub.add_parser("night").add_argument("state", choices=["yes", "no"])
    s = sub.add_parser("shot")
    s.add_argument("out", type=Path)
    s.add_argument("--raw", type=Path, help="also save the unframed screenshot")
    s.add_argument("--scale", type=float, default=0.5)
    r = sub.add_parser("record")
    r.add_argument("out", type=Path)
    r.add_argument("--seconds", type=int, default=8)
    r.add_argument("--width", type=int, default=560, help="GIF width in pixels")
    r.add_argument("--fps", type=int, default=15)
    e = sub.add_parser("encode", help="re-encode a kept recording (out.mp4) without re-recording")
    e.add_argument("video", type=Path)
    e.add_argument("out", type=Path)
    e.add_argument("--posture", choices=["closed", "default"], required=True)
    e.add_argument("--width", type=int, default=560)
    e.add_argument("--fps", type=int, default=15)
    e.add_argument("--start", type=float, default=0, help="seconds to skip")
    e.add_argument("--duration", type=float, help="seconds to keep")
    args = p.parse_args()

    if args.cmd == "demo":
        demo(args.state == "on")
    elif args.cmd in ("fold", "unfold"):
        # A posture change turns the screen off and locks it; wake it and dismiss the
        # (PIN-less) keyguard so the app is back in front.
        adb("emu", args.cmd)
        time.sleep(3)
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        time.sleep(1)
        adb("shell", "wm", "dismiss-keyguard")
        time.sleep(2)
    elif args.cmd == "night":
        adb("shell", "cmd", "uimode", "night", args.state)
    elif args.cmd == "shot":
        shot(args.out, args.raw, args.scale)
    elif args.cmd == "record":
        record(args.out, args.seconds, args.width, args.fps)
    elif args.cmd == "encode":
        encode(args.video, args.out, args.posture, args.width, args.fps, args.start,
               args.duration)
    return 0


if __name__ == "__main__":
    sys.exit(main())
