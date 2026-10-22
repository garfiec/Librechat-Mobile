#!/usr/bin/env python3
"""Verify that dictation and read-aloud work with a speech provider the user installed.

    scripts/verify-speech-services.py              # boot the AOSP emulator, run everything, shut it down
    scripts/verify-speech-services.py --keep       # leave the emulator running afterwards
    scripts/verify-speech-services.py --api 33     # another AOSP image (default: 37.0, Android 17)
    scripts/verify-speech-services.py --manifest-only   # CI: merged-manifest check, no device

On API 30+ another app's services are hidden from us unless the manifest declares a matching
`<queries>` intent. Google's recognizer and TTS engine are force-queryable system packages, so on a
stock phone nothing breaks without one; on a ROM without Google (GrapheneOS with Whisper+, #601) the
mic said "not available" with a working recognizer installed. A Pixel can't show this, so the run
uses a no-GMS AOSP emulator image and a stand-in provider, `:testing:speech-fixture`, installed as
its own APK and made the default recognizer and TTS engine.

The device run:
  1. boots AVD Switchboard_AOSP_<api> (created on first use from system-images;android-<api>;default)
     headless on emulator-5580; every adb and Gradle call is pinned to it;
  2. installs the fixture and both app flavors, and makes the fixture the default provider;
  3. checks `dumpsys package queries`: the fixture is not force-queryable (so a pass can't come from
     a system exemption) and both installed apps can see it;
  4. runs :feature:chat's speech instrumented tests and requires every one to pass, none skipped.

Needs the Android SDK (ANDROID_HOME, or ~/Library/Android/sdk) with that system image installed.
"""

from __future__ import annotations

import argparse
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SDK = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Library/Android/sdk")

DEFAULT_API = "37.0"
PORT = 5580
SERIAL = f"emulator-{PORT}"

FIXTURE = "com.garfiec.librechat.testfixture.speech"
FIXTURE_RECOGNIZER = f"{FIXTURE}/.FixtureRecognitionService"
FIXTURE_APK = ROOT / "testing/speech-fixture/build/outputs/apk/debug/speech-fixture-debug.apk"
APPS = {
    "com.garfiec.librechat.debug": ROOT / "app/build/outputs/apk/foss/debug/app-foss-debug.apk",
    "com.garfiec.librechat.play.debug": ROOT / "app/build/outputs/apk/play/debug/app-play-debug.apk",
}

TEST_PACKAGE = "com.garfiec.librechat.feature.chat.audio"
TEST_RESULTS = ROOT / "feature/chat/build/outputs/androidTest-results/connected"
# SpeechServiceVisibilityInstrumentedTest (3) + SpeechFixtureEndToEndInstrumentedTest (3).
EXPECTED_TESTS = 6

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
REQUIRED_QUERIES = {
    "intent": {
        "android.speech.RecognitionService",
        "android.intent.action.TTS_SERVICE",
        "android.speech.action.RECOGNIZE_SPEECH",
    },
}


class Failure(Exception):
    pass


def run(*cmd: str, env: dict | None = None, capture: bool = False) -> str:
    out = subprocess.run(cmd, check=False, cwd=ROOT, env=env, capture_output=capture, text=True)
    if out.returncode != 0:
        detail = f"\n{out.stdout}{out.stderr}" if capture else ""
        raise Failure(f"`{' '.join(cmd)}` exited {out.returncode}{detail}")
    return out.stdout if capture else ""


def adb(*args: str) -> str:
    return run(str(SDK / "platform-tools/adb"), "-s", SERIAL, *args, capture=True)


def gradle(*tasks: str) -> None:
    run("./gradlew", "--quiet", *tasks, env={**os.environ, "ANDROID_SERIAL": SERIAL})


# ── Merged manifest ────────────────────────────────────────────────────

def check_manifest() -> None:
    """Every speech intent we look up is declared in :app's merged manifest, in both flavors."""
    for flavor in ("foss", "play"):
        merged = sorted((ROOT / f"app/build/intermediates/merged_manifests/{flavor}Debug").rglob("AndroidManifest.xml"))
        if not merged:
            raise Failure(f"no merged {flavor}Debug manifest; build :app:process{flavor.capitalize()}DebugManifest first")
        declared = {
            action.get(f"{ANDROID_NS}name")
            for queries in ET.parse(merged[0]).getroot().iter("queries")
            for intent in queries.iter("intent")
            for action in intent.iter("action")
        }
        missing = REQUIRED_QUERIES["intent"] - declared
        if missing:
            raise Failure(f"{flavor}Debug manifest is missing <queries> intents: {sorted(missing)}")
        print(f"ok  {flavor}Debug manifest declares the speech <queries>")


# ── Emulator ───────────────────────────────────────────────────────────

def avd_name(api: str) -> str:
    return f"Switchboard_AOSP_{api.replace('.', '_')}"


def host_abi() -> tuple[str, str]:
    """The (system-image ABI, hw.cpu.arch) the emulator can run on this host without translation."""
    return ("arm64-v8a", "arm64") if platform.machine().lower() in ("arm64", "aarch64") else ("x86_64", "x86_64")


def ensure_avd(api: str) -> None:
    abi, cpu_arch = host_abi()
    system_image = f"system-images/android-{api}/default/{abi}/"
    if not (SDK / system_image).is_dir():
        raise Failure(f"missing system image; install it with: sdkmanager \"{system_image.strip('/').replace('/', ';')}\"")
    avd = avd_name(api)
    avd_root = Path(os.environ.get("ANDROID_AVD_HOME") or Path.home() / ".android/avd")
    avd_dir = avd_root / f"{avd}.avd"
    if avd_dir.is_dir():
        return
    # No avdmanager needed: an AVD is two ini files, and the emulator builds userdata on first boot.
    avd_dir.mkdir(parents=True)
    (avd_root / f"{avd}.ini").write_text(
        f"avd.ini.encoding=UTF-8\npath={avd_dir}\npath.rel=avd/{avd}.avd\ntarget=android-{api}\n"
    )
    (avd_dir / "config.ini").write_text(
        "\n".join([
            "avd.ini.encoding=UTF-8",
            "PlayStore.enabled=false",
            f"abi.type={abi}",
            f"hw.cpu.arch={cpu_arch}",
            f"image.sysdir.1={system_image}",
            "tag.id=default",
            "tag.display=Default Android System Image",
            "hw.lcd.width=1080",
            "hw.lcd.height=2340",
            "hw.lcd.density=420",
            "hw.ramSize=2048",
            "disk.dataPartition.size=4G",
            "hw.keyboard=yes",
            "",
        ])
    )
    print(f"ok  created AVD {avd}")


def boot(api: str) -> subprocess.Popen | None:
    devices = run(str(SDK / "platform-tools/adb"), "devices", capture=True)
    if re.search(rf"^{SERIAL}\s+device$", devices, re.M):
        running = adb("shell", "getprop", "ro.build.version.release").strip()
        print(f"ok  {SERIAL} already running (Android {running}); not checking it is the --api image")
        return None
    ensure_avd(api)
    proc = subprocess.Popen(
        [str(SDK / "emulator/emulator"), "-avd", avd_name(api), "-port", str(PORT),
         "-no-window", "-no-audio", "-no-snapshot", "-no-boot-anim"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    # Any way out of here but a booted device (timeout, Ctrl-C) must not orphan a headless emulator.
    try:
        deadline = time.time() + 300
        while time.time() < deadline:
            if proc.poll() is not None:
                raise Failure(f"emulator exited with {proc.returncode} during boot")
            try:
                if adb("shell", "getprop", "sys.boot_completed").strip() == "1":
                    print(f"ok  {SERIAL} booted")
                    return proc
            except Failure:
                pass
            time.sleep(3)
        raise Failure("emulator did not boot within 5 minutes")
    except BaseException:
        proc.kill()
        raise


def shutdown(proc: subprocess.Popen | None) -> None:
    if proc is None:
        return
    try:
        adb("emu", "kill")
        proc.wait(timeout=60)
    except (Failure, subprocess.TimeoutExpired):
        proc.kill()


# ── Device checks ──────────────────────────────────────────────────────

def install_and_configure() -> None:
    adb("install", "-r", "-t", str(FIXTURE_APK))
    for apk in APPS.values():
        adb("install", "-r", "-t", str(apk))
    adb("shell", "pm", "grant", FIXTURE, "android.permission.RECORD_AUDIO")
    adb("shell", "settings", "put", "secure", "voice_recognition_service", FIXTURE_RECOGNIZER)
    adb("shell", "settings", "put", "secure", "tts_default_synth", FIXTURE)
    print("ok  fixture installed and set as default recognizer and TTS engine")


def dumpsys_sections() -> dict[str, list[str]]:
    """`dumpsys package queries`, split into its two-space-indented sections."""
    sections: dict[str, list[str]] = {}
    current = None
    for line in adb("shell", "dumpsys", "package", "queries").splitlines():
        if re.match(r"^  \S.*:$", line):
            current = line.strip()
            sections[current] = []
        elif current is not None:
            sections[current].append(line)
    return sections


def visible_to(section: list[str], package: str) -> list[str]:
    """The packages listed under `package` (a four-space `pkg:` line) in a dumpsys section."""
    seen, inside = [], False
    for line in section:
        if re.match(r"^    \S", line):
            inside = line.strip() == f"{package}:"
        elif inside:
            seen.append(line.strip())
    return seen


def check_visibility() -> None:
    sections = dumpsys_sections()
    force = sections.get("forceQueryable:")
    if force is None:
        raise Failure("dumpsys package queries has no forceQueryable section; output format changed?")
    if any(FIXTURE in line for line in force):
        raise Failure("the fixture is force-queryable, so the visibility checks would prove nothing")
    print("ok  negative control: the fixture is not force-queryable")

    # The visibility cache is rebuilt asynchronously after an install, so give it a moment.
    waiting = set(APPS)
    deadline = time.time() + 30
    while True:
        by_component = sections.get("queries via component:", [])
        for package in sorted(waiting):
            if any(FIXTURE in entry for entry in visible_to(by_component, package)):
                waiting.discard(package)
                print(f"ok  {package} sees the fixture")
        if not waiting:
            return
        if time.time() > deadline:
            raise Failure(f"{', '.join(sorted(waiting))} cannot see the fixture (dumpsys package queries, via component)")
        time.sleep(2)
        sections = dumpsys_sections()


def run_tests() -> None:
    shutil.rmtree(TEST_RESULTS, ignore_errors=True)
    try:
        gradle(":feature:chat:connectedDebugAndroidTest",
               f"-Pandroid.testInstrumentationRunnerArguments.package={TEST_PACKAGE}")
    except Failure:
        pass  # the report below names what failed
    reports = sorted(TEST_RESULTS.rglob("TEST-*.xml"))
    if not reports:
        raise Failure(f"no instrumented test results under {TEST_RESULTS.relative_to(ROOT)}")
    total = failed = skipped = 0
    problems = []
    for report in reports:
        for case in ET.parse(report).getroot().iter("testcase"):
            total += 1
            name = f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}"
            if case.find("failure") is not None or case.find("error") is not None:
                failed += 1
                node = case.find("failure") if case.find("failure") is not None else case.find("error")
                detail = (node.get("message") or node.text or "").strip().splitlines()
                problems.append(f"FAIL {name}: {detail[0] if detail else '(no message)'}")
            elif case.find("skipped") is not None:
                skipped += 1
                problems.append(f"SKIP {name}")
    for problem in problems:
        print(f"    {problem}")
    if failed or skipped or total != EXPECTED_TESTS:
        raise Failure(f"speech tests: {total} run (expected {EXPECTED_TESTS}), {failed} failed, {skipped} skipped")
    print(f"ok  {total} speech instrumented tests passed")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--manifest-only", action="store_true", help="check :app's merged manifests; no device")
    parser.add_argument("--keep", action="store_true", help="leave the emulator running afterwards")
    parser.add_argument("--api", default=DEFAULT_API, help=f"AOSP system image API level (default {DEFAULT_API})")
    args = parser.parse_args()

    if args.manifest_only:
        return report([check_manifest])
    try:
        gradle(":testing:speech-fixture:assembleDebug", ":app:assembleFossDebug", ":app:assemblePlayDebug")
        proc = boot(args.api)
    except Failure as failure:
        print(f"FAIL {failure}", file=sys.stderr)
        return 1
    try:
        try:
            install_and_configure()
        except Failure as failure:
            print(f"FAIL {failure}", file=sys.stderr)
            return 1
        # The checks are independent: run them all so one run shows everything that is wrong.
        return report([check_manifest, check_visibility, run_tests])
    finally:
        if not args.keep:
            shutdown(proc)


def report(checks) -> int:
    failures = 0
    for check in checks:
        try:
            check()
        except Failure as failure:
            failures += 1
            print(f"FAIL {failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
