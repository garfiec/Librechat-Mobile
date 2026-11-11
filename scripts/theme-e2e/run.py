#!/usr/bin/env python3
"""Check on a device that a server's theme reaches the app's pixels.

    scripts/theme-e2e/run.py --serial emulator-5554 --server http://localhost:3090 \\
        --config ~/rigs/theme/librechat.yaml --restart "docker compose -p lctheme restart api" \\
        --conversation 0b6f…-uuid --user-message "Show me a table"

For each fixture in fixtures/ (default: clickhouse, acme, none), the run writes the fixture's
`interface.theme` into the server's librechat.yaml, restarts the server and waits until
/api/config serves it. Then, in light and in dark, it cold-starts the app on a conversation and
samples four places: the chat canvas, the user's message, the composer and a code block's body.
Each must be within ΔE 3 (CIE76) of the fixture's `expect` value, or more than ΔE 3 from its
`differ` value. It retries for --settle seconds, since the app paints its cached theme first and
the served one once its config fetch lands.

With --api-stop and --api-start it also cold-starts the app with the server down after the first
fixture that has expectations: the theme cached from the last fetch must paint the first frame.

The server's librechat.yaml needs two marker lines under `interface:`, where the run writes the
theme (anything between them is replaced, and the whole file is restored afterwards):

    interface:
      # theme-e2e:begin
      # theme-e2e:end

Setup is in README.md: the app signed in to that server on Material style with the server's
colours chosen, and a conversation whose user message and a code block fit on one screen.

Screenshots, results.json and an index.html contact sheet go to --out
(default .screenshots/theme-e2e/<time>/). Python 3.11+, standard library only.
"""

from __future__ import annotations

import argparse
import html
import json
import math
import re
import shutil
import subprocess
import sys
import time
import tomllib
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURES = Path(__file__).resolve().parent / "fixtures"
DEFAULT_FIXTURES = ["clickhouse", "acme", "none"]
PACKAGE = "com.garfiec.librechat.debug"
ACTIVITY = "com.garfiec.librechat.MainActivity"
BEGIN, END = "# theme-e2e:begin", "# theme-e2e:end"
MAX_DELTA_E = 3.0
TARGETS = ("canvas", "user_message", "composer", "code_body")



def hex_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def lab(rgb: tuple[int, int, int]) -> tuple[float, float, float]:
    def linear(c: int) -> float:
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = map(linear, rgb)
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    def f(t: float) -> float:
        return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116

    return 116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))


def delta_e(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return math.dist(lab(a), lab(b))



@dataclass
class Device:
    serial: str
    display: str | None

    def adb(self, *args: str, binary: bool = False, check: bool = True):
        out = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, check=check)
        return out.stdout if binary else out.stdout.decode(errors="replace")

    def shell(self, command: str) -> str:
        return self.adb("shell", command)

    def density(self) -> float:
        out = self.shell("wm density")
        found = re.search(r"Override density: (\d+)", out) or re.search(r"Physical density: (\d+)", out)
        return int(found.group(1)) / 160

    def night(self) -> str:
        return self.shell("cmd uimode night").strip().rsplit(" ", 1)[-1]

    def set_night(self, mode: str) -> None:
        self.shell(f"cmd uimode night {mode}")

    def open_conversation(self, conversation: str) -> None:
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(
            f"am start -a android.intent.action.VIEW -d librechat://conversation/{conversation} -n {PACKAGE}/{ACTIVITY}",
        )

    def screen(self) -> "Screen":
        args = ["exec-out", "screencap"] + (["-d", self.display] if self.display else [])
        data = self.adb(*args, binary=True)
        width, height = int.from_bytes(data[0:4], "little"), int.from_bytes(data[4:8], "little")
        return Screen(width, height, data, len(data) - width * height * 4)

    def save_png(self, path: Path) -> None:
        args = ["exec-out", "screencap", "-p"] + (["-d", self.display] if self.display else [])
        path.write_bytes(self.adb(*args, binary=True))

    def nodes(self) -> list[dict[str, str]]:
        self.adb("shell", "uiautomator dump /sdcard/theme-e2e.xml", check=False)
        xml = self.adb("exec-out", "cat /sdcard/theme-e2e.xml", check=False)
        try:
            return [node.attrib for node in ET.fromstring(xml).iter("node")]
        except ET.ParseError:
            return []


@dataclass
class Screen:
    width: int
    height: int
    data: bytes
    header: int

    def pixel(self, x: int, y: int) -> tuple[int, int, int]:
        at = self.header + (y * self.width + x) * 4
        return self.data[at], self.data[at + 1], self.data[at + 2]


def bounds(node: dict[str, str]) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node["bounds"]))
    return x1, y1, x2, y2


def sample_points(nodes: list[dict[str, str]], user_message: str, dp: float) -> dict[str, tuple[int, int]]:
    """Where each target's fill shows, from the nodes on screen; a target not on screen is left out."""
    points: dict[str, tuple[int, int]] = {}
    code = next((n for n in nodes if n.get("content-desc", "").lower().endswith("code block")), None)
    if code:
        x1, y1, x2, y2 = bounds(code)
        points["code_body"] = (x1 + round(4 * dp), y2 - round(6 * dp))
        # The screen's edge beside the block: clear of any bubble, below the avatar.
        points["canvas"] = (round(4 * dp), (y1 + y2) // 2)
    message = next((n for n in nodes if user_message in n.get("text", "")), None)
    if message:
        x1, y1, x2, y2 = bounds(message)
        # The bubble pads its text by 12dp.
        points["user_message"] = (x1 - round(6 * dp), (y1 + y2) // 2)
    composer = next((n for n in nodes if n.get("resource-id") == "composer"), None)
    if composer:
        x1, y1, x2, y2 = bounds(composer)
        points["composer"] = (x1 + round(4 * dp), (y1 + y2) // 2)
    return points



def write_theme(config: Path, original: str, theme: str | None) -> None:
    lines = original.splitlines(keepends=True)
    begin = next(i for i, line in enumerate(lines) if line.strip() == BEGIN)
    end = next(i for i, line in enumerate(lines) if line.strip() == END)
    indent = lines[begin][: len(lines[begin]) - len(lines[begin].lstrip())]
    block: list[str] = []
    if theme is not None:
        if "\n" in theme.strip():
            block.append(f"{indent}theme:\n")
            block += [f"{indent}  {line}\n" if line else "\n" for line in theme.strip("\n").splitlines()]
        else:
            block.append(f"{indent}theme: {theme.strip()}\n")
    config.write_text("".join(lines[: begin + 1] + block + lines[end:]))


def served_theme(server: str) -> str | None:
    try:
        with urllib.request.urlopen(f"{server.rstrip('/')}/api/config", timeout=5) as response:
            theme = json.load(response).get("interface", {}).get("theme")
    except (OSError, ValueError):
        return None
    if isinstance(theme, dict):
        return str(theme.get("name", ""))
    return theme or ""


def wait_served(server: str, expected: str, timeout: float = 180) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if served_theme(server) == expected:
            return
        time.sleep(2)
    raise SystemExit(f"{server}/api/config never served theme {expected!r} (last: {served_theme(server)!r})")


def wait_down(server: str, timeout: float = 60) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline and served_theme(server) is not None:
        time.sleep(1)



@dataclass
class Check:
    name: str
    passed: bool = False
    shot: str = ""
    rows: list[dict] = field(default_factory=list)


def evaluate(fixture: dict, mode: str, screen: Screen, points: dict[str, tuple[int, int]]) -> tuple[bool, list[dict]]:
    rows, passed = [], True
    for kind in ("expect", "differ"):
        for target, value in fixture.get(kind, {}).get(mode, {}).items():
            want = hex_rgb(value)
            if target not in points:
                rows.append({"target": target, "kind": kind, "want": value, "got": None, "delta_e": None, "ok": False})
                passed = False
                continue
            got = screen.pixel(*points[target])
            de = delta_e(got, want)
            ok = de <= MAX_DELTA_E if kind == "expect" else de > MAX_DELTA_E
            passed &= ok
            rows.append({
                "target": target, "kind": kind, "want": value, "got": "#%02X%02X%02X" % got,
                "delta_e": round(de, 1), "ok": ok, "at": points[target],
            })
    return passed, rows


def run_check(device: Device, args, fixture: dict, mode: str, name: str, out: Path) -> Check:
    check = Check(name)
    device.open_conversation(args.conversation)
    dp = device.density()
    deadline = time.monotonic() + args.settle
    previous = None
    while True:
        time.sleep(1.5)
        nodes = device.nodes()
        points = sample_points(nodes, args.user_message, dp)
        passed, check.rows = evaluate(fixture, mode, device.screen(), points)
        # Two captures in a row must agree, so a frame drawn before the chat (or mid-swap) can't pass.
        sampled = [row["got"] for row in check.rows]
        check.passed = passed and sampled == previous
        previous = sampled
        if check.passed or time.monotonic() > deadline:
            break
    check.shot = f"{name}.png"
    device.save_png(out / check.shot)
    return check


def report(check: Check) -> None:
    print(f"{'PASS' if check.passed else 'FAIL'}  {check.name}")
    for row in check.rows:
        relation = "≈" if row["kind"] == "expect" else "≠"
        got = row["got"] or "not on screen"
        de = "" if row["delta_e"] is None else f"  ΔE {row['delta_e']}"
        print(f"   {'ok ' if row['ok'] else 'BAD'} {row['target']:13s} {got} {relation} {row['want']}{de}")


def contact_sheet(out: Path, checks: list[Check]) -> None:
    cards = []
    for check in checks:
        rows = "".join(
            f"<tr class={'ok' if r['ok'] else 'bad'}><td>{r['target']}</td><td>{r['got'] or '—'}</td>"
            f"<td>{'≈' if r['kind'] == 'expect' else '≠'} {r['want']}</td><td>{r['delta_e'] if r['delta_e'] is not None else ''}</td></tr>"
            for r in check.rows
        )
        cards.append(
            f"<figure><img src='{check.shot}' alt='{html.escape(check.name)}'><figcaption>"
            f"<b class={'ok' if check.passed else 'bad'}>{'PASS' if check.passed else 'FAIL'}</b> {html.escape(check.name)}"
            f"<table>{rows}</table></figcaption></figure>"
        )
    (out / "index.html").write_text(
        "<!doctype html><meta charset=utf-8><title>Theme E2E</title><style>"
        "body{font:14px system-ui,sans-serif;margin:16px;background:#fafafa}"
        ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px}"
        "figure{margin:0;background:#fff;border:1px solid #ddd;border-radius:8px;padding:8px}"
        "img{width:100%}table{width:100%;font-size:12px}.ok{color:#0a7d3c}.bad{color:#b3261e}"
        "</style><div class=grid>" + "".join(cards) + "</div>",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--serial", required=True, help="adb serial of the device to drive")
    parser.add_argument("--display", help="screencap display id, for a device with more than one (a Fold)")
    parser.add_argument("--server", required=True, help="base URL the host reaches the server on")
    parser.add_argument("--config", required=True, type=Path, help="the server's librechat.yaml, with the marker lines")
    parser.add_argument("--restart", required=True, help="shell command that restarts the server")
    parser.add_argument("--api-stop", help="shell command that stops the server (enables the cold-start check)")
    parser.add_argument("--api-start", help="shell command that starts it again")
    parser.add_argument("--conversation", required=True, help="id of a conversation with a code block")
    parser.add_argument("--user-message", required=True, help="text from that conversation's user message")
    parser.add_argument("--fixtures", nargs="+", default=DEFAULT_FIXTURES, help="fixture names, in run order")
    parser.add_argument("--settle", type=float, default=30, help="seconds to wait for the theme to paint")
    parser.add_argument("--out", type=Path, help="output directory")
    args = parser.parse_args()

    if not shutil.which("adb"):
        raise SystemExit("adb is not on PATH")
    config = args.config.expanduser()
    original = config.read_text()
    if BEGIN not in original or END not in original:
        raise SystemExit(f"{config} needs '{BEGIN}' and '{END}' lines under interface:")
    out = args.out or ROOT / ".screenshots/theme-e2e" / time.strftime("%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)
    device = Device(args.serial, args.display)
    night = device.night()
    checks: list[Check] = []
    cold_start_done = not (args.api_stop and args.api_start)
    try:
        for name in args.fixtures:
            fixture = tomllib.loads((FIXTURES / f"{name}.toml").read_text())
            print(f"\n== {name}: {fixture.get('description', '')}")
            write_theme(config, original, fixture.get("theme"))
            subprocess.run(args.restart, shell=True, check=True, capture_output=True)
            wait_served(args.server, fixture.get("served", ""))
            for mode, uimode in (("light", "no"), ("dark", "yes")):
                device.set_night(uimode)
                checks.append(run_check(device, args, fixture, mode, f"{name}-{mode}", out))
                report(checks[-1])
            if not cold_start_done and fixture.get("expect"):
                cold_start_done = True
                subprocess.run(args.api_stop, shell=True, check=True, capture_output=True)
                wait_down(args.server)
                try:
                    checks.append(run_check(device, args, fixture, "dark", f"{name}-dark-server-down", out))
                    report(checks[-1])
                finally:
                    subprocess.run(args.api_start, shell=True, check=True, capture_output=True)
                    wait_served(args.server, fixture.get("served", ""))
    finally:
        config.write_text(original)
        subprocess.run(args.restart, shell=True, check=False, capture_output=True)
        device.set_night(night)
        contact_sheet(out, checks)
        (out / "results.json").write_text(json.dumps([check.__dict__ for check in checks], indent=2))
    failed = [check.name for check in checks if not check.passed]
    print(f"\n{len(checks) - len(failed)}/{len(checks)} passed; contact sheet: {out / 'index.html'}")
    if failed:
        print("failed: " + ", ".join(failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
