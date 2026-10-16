#!/usr/bin/env python3
"""Capture framed screenshots and recordings from an iOS Simulator.

    scripts/screenshots/ios.py "iPhone 17 Pro" statusbar on|off
    scripts/screenshots/ios.py "iPhone 17 Pro" shot out.png [--raw raw.png] [--scale 0.5]
    scripts/screenshots/ios.py "iPad Pro 13-inch (M5)" record out.gif --seconds 8
    scripts/screenshots/ios.py "iPhone 17 Pro" login --env-file scripts/demo-seed/.state/local.env

The device is a booted simulator's name; its device type picks the frame (frame.py). The status
bar override is Apple's 9:41 with full battery, Wi-Fi and signal and no carrier name, which is
also what the App Store marketing guidelines ask for.

Screenshots use --mask=alpha so the screen's corners and camera cutout are transparent and the
frame shows through. Video can't carry that mask (simctl renders it black), so recordings take
the mask from a screenshot taken just before and apply it to every frame.

`login` signs the installed app in through Maestro (maestro/ios-login.yaml), with the server and
credentials read from the demo seeder's env file (DEMO_SEED_SERVER, DEMO_SEED_EMAIL,
DEMO_SEED_PASSWORD). The password never reaches the output. The simulator reaches the host's
server at localhost, so a server written for the Android emulator (10.0.2.2) is rewritten. The
flow starts from the server screen: run it on a fresh install or after signing out.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import frame  # noqa: E402


def simctl(*args: str, capture: bool = False) -> str:
    out = subprocess.run(["xcrun", "simctl", *args], check=True,
                         capture_output=capture, text=True)
    return out.stdout if capture else ""


def booted(name: str) -> tuple[str, str]:
    """(udid, device type name) of the booted simulator called `name`."""
    devices = json.loads(simctl("list", "devices", "booted", "-j", capture=True))["devices"]
    for runtime in devices.values():
        for device in runtime:
            if device["name"] == name and device["state"] == "Booted":
                type_id = device["deviceTypeIdentifier"]
                types = json.loads(simctl("list", "devicetypes", "-j", capture=True))
                for t in types["devicetypes"]:
                    if t["identifier"] == type_id:
                        return device["udid"], t["name"]
    raise SystemExit(f"no booted simulator named '{name}' (xcrun simctl boot …)")


def statusbar(udid: str, on: bool):
    if not on:
        simctl("status_bar", udid, "clear")
        return
    simctl("status_bar", udid, "override", "--time", "9:41", "--dataNetwork", "wifi",
           "--wifiMode", "active", "--wifiBars", "3", "--cellularMode", "active",
           "--cellularBars", "4", "--operatorName", "", "--batteryState", "charged",
           "--batteryLevel", "100")


def screenshot(udid: str) -> Image.Image:
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "raw.png"
        subprocess.run(["xcrun", "simctl", "io", udid, "screenshot", "--mask=alpha", str(path)],
                       check=True, capture_output=True)
        im = Image.open(path).convert("RGBA")
        im.load()
        return im


def shot(udid: str, device_type: str, out: Path, raw_out: Path | None, scale: float):
    raw = screenshot(udid)
    if raw_out:
        raw.save(raw_out)
    framed = frame.frame_ios(raw, device_type)
    if scale != 1:
        framed = framed.resize((round(framed.width * scale), round(framed.height * scale)),
                               Image.LANCZOS)
    framed.save(out)
    print(f"{out}: {framed.width}x{framed.height}")


def record(udid: str, device_type: str, out: Path, seconds: int, width: int, fps: int):
    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg is needed for recordings")
    # The video goes over the frame, then a cover goes over the video: the frame again, opaque
    # wherever the screenshot's alpha says the screen isn't (rounded corners, camera cutout).
    raw = screenshot(udid)
    device, (sx, sy) = frame.frame_ios_at(Image.new("RGBA", raw.size, (0, 0, 0, 0)), device_type)
    cover = device.copy()
    hole = Image.eval(raw.getchannel("A"), lambda a: 255 - a)
    region = cover.crop((sx, sy, sx + raw.width, sy + raw.height))
    region.putalpha(hole)
    cover.paste(region, (sx, sy))
    height = round(device.height * width / device.width)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        device.save(tmp / "device.png")
        cover.save(tmp / "cover.png")
        video = tmp / "capture.mov"
        print(f"recording for {seconds}s …", flush=True)
        proc = subprocess.Popen(["xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264",
                                 "--force", str(video)], stderr=subprocess.PIPE)
        try:
            proc.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            proc.send_signal(2)  # SIGINT finishes the file
            proc.wait()
        graph = (
            f"[1:v]fps={fps},scale={raw.width}:{raw.height}[screen];"
            f"[0:v][screen]overlay={sx}:{sy}:shortest=1[framed];"
            f"[framed][2:v]overlay=0:0:shortest=1,"
            f"scale={width}:{height}:flags=lanczos,split[a][b];"
            "[a]palettegen=reserve_transparent=1:stats_mode=diff[p];"
            "[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle:alpha_threshold=128"
        )
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error",
                        "-loop", "1", "-framerate", str(fps), "-i", str(tmp / "device.png"),
                        "-i", str(video),
                        "-loop", "1", "-framerate", str(fps), "-i", str(tmp / "cover.png"),
                        "-filter_complex", graph, "-loop", "0", str(out)], check=True)
    print(f"{out}: {width}x{height}, {out.stat().st_size / 1e6:.1f} MB")


def login(udid: str, env_file: Path):
    maestro = shutil.which("maestro") or str(Path.home() / ".maestro/bin/maestro")
    if not Path(maestro).exists():
        raise SystemExit("Maestro is needed to sign in (https://maestro.mobile.dev)")
    env = {}
    for line in env_file.read_text().splitlines():
        line = line.strip().removeprefix("export ").strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip("'\"")
    missing = [k for k in ("DEMO_SEED_SERVER", "DEMO_SEED_EMAIL", "DEMO_SEED_PASSWORD") if k not in env]
    if missing:
        raise SystemExit(f"{env_file} has no {', '.join(missing)}")
    server = env["DEMO_SEED_SERVER"].replace("10.0.2.2", "localhost")
    password = env["DEMO_SEED_PASSWORD"]
    flow = Path(__file__).resolve().parent / "maestro/ios-login.yaml"
    run = subprocess.run([maestro, "--device", udid, "test", "-e", f"SERVER={server}",
                          "-e", f"EMAIL={env['DEMO_SEED_EMAIL']}", "-e", f"PASSWORD={password}",
                          str(flow)], capture_output=True, text=True)
    print((run.stdout + run.stderr).replace(password, "<password>")[-3000:])
    if run.returncode:
        raise SystemExit("sign-in failed; the output above says at which step")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("device", help="name of a booted simulator, e.g. 'iPhone 17 Pro'")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("statusbar").add_argument("state", choices=["on", "off"])
    s = sub.add_parser("shot")
    s.add_argument("out", type=Path)
    s.add_argument("--raw", type=Path)
    s.add_argument("--scale", type=float, default=0.5)
    r = sub.add_parser("record")
    r.add_argument("out", type=Path)
    r.add_argument("--seconds", type=int, default=8)
    r.add_argument("--width", type=int, default=560)
    r.add_argument("--fps", type=int, default=15)
    sub.add_parser("login").add_argument("--env-file", type=Path, required=True)
    args = p.parse_args()

    udid, device_type = booted(args.device)
    if args.cmd == "statusbar":
        statusbar(udid, args.state == "on")
    elif args.cmd == "shot":
        shot(udid, device_type, args.out, args.raw, args.scale)
    elif args.cmd == "record":
        record(udid, device_type, args.out, args.seconds, args.width, args.fps)
    elif args.cmd == "login":
        login(udid, args.env_file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
