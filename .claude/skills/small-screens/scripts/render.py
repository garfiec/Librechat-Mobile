#!/usr/bin/env python3
"""Apply or revert the temporary Robolectric render harness for one module. Nothing it writes is
ever committed.

  render.py apply  <repo> <module> <cases.json> <out_dir>
  render.py revert <repo> <module>
  render.py status <repo>

apply:
- backs up <module>/build.gradle.kts;
- appends the test options and test dependencies the harness needs (every line marked TEMP-RENDER);
- generates TempSmallScreenRender.kt in the module's unit-test source set (androidUnitTest for a
  KMP module, src/test for :app), from the cases file;
- writes <out_dir>/meta.json describing each render;
- prints the Gradle command that renders into <out_dir>.

Render twice with the same cases file: once into before/ and once into after/, so the files pair
by name.

revert restores the build file byte for byte from its backup and deletes the generated test.
status lists every module that still has the harness applied; it must print nothing before a
commit.

cases.json:
{
  "package": "com.garfiec.librechat.feature.chat.components",  // where the preview hosts live
  "imports": ["com.garfiec.librechat.core.model.ui.UiStyle"],
  "cases": [
    {"name": "S04-V4-otp", "call": "OtpHost(UiStyle.LIQUID_GLASS)", "width": 375, "height": 388,
     "scale": 1.8, "locale": null, "dialog": false, "scrolled": true, "style": "glass",
     "note": "OTP body, SE with the keyboard up"}
  ]
}
Each case also gets a control render, <name>__control, at 360x640dp, 1.0x, English, same call,
unless it sets "control": false. "call" is a Kotlin expression that composes the preview host.
The hosts and fixtures it names must be internal for the run; revert that by hand afterwards.
"""
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TEMPLATE = os.path.join(HERE, "..", "harness", "Render.kt.tmpl")
BACKUP = ".small-screens-render.bak"
MARK = "// TEMP-RENDER"
GRADLE_BLOCK = f"""
android {{ testOptions.unitTests.isIncludeAndroidResources = true }} {MARK}
kotlin {{ {MARK}
    sourceSets {{ {MARK}
        named("androidUnitTest").dependencies {{ {MARK}
            implementation(libs.robolectric) {MARK}
            implementation(libs.compose.ui.test) {MARK}
            implementation(libs.compose.ui.test.manifest) {MARK}
            implementation(libs.espresso.core) {MARK}
        }} {MARK}
    }} {MARK}
}} {MARK}
"""
# :app is a plain Android module: its unit tests are src/test with testImplementation.
APP_GRADLE_BLOCK = f"""
android {{ testOptions.unitTests.isIncludeAndroidResources = true }} {MARK}
dependencies {{ {MARK}
    testImplementation(libs.robolectric) {MARK}
    testImplementation(libs.compose.ui.test) {MARK}
    testImplementation(libs.compose.ui.test.manifest) {MARK}
    testImplementation(libs.espresso.core) {MARK}
}} {MARK}
"""
CONTROL = {"width": 360, "height": 640, "scale": 1.0, "locale": None}


def is_kmp(repo, module):
    return "librechat.kmp." in open(os.path.join(repo, module, "build.gradle.kts")).read()


def test_root(repo, module):
    return os.path.join(repo, module, "src", "androidUnitTest" if is_kmp(repo, module) else "test", "kotlin")


def test_path(repo, module, pkg):
    return os.path.join(test_root(repo, module), *pkg.split("."), "TempSmallScreenRender.kt")


def marker(repo, module):
    return os.path.join(repo, module, BACKUP)


def kotlin_case(c):
    loc = f'"{c["locale"]}"' if c.get("locale") else "null"
    return (f'        shot("{c["name"]}", {c["width"]}, {c["height"]}, {float(c["scale"])}f, {loc}, '
            f'{str(bool(c.get("dialog"))).lower()}, {str(bool(c.get("scrolled"))).lower()}) {{ {c["call"]} }}')


def expand(cases):
    out = []
    for c in cases:
        out.append(c)
        if c.get("control", True) and not c["name"].endswith("__control"):
            out.append({**c, **CONTROL, "name": c["name"] + "__control", "scrolled": False, "note": "control"})
    return out


def apply(repo, module, cases_path, out_dir):
    spec = json.load(open(cases_path))
    gradle = os.path.join(repo, module, "build.gradle.kts")
    if os.path.exists(marker(repo, module)):
        sys.exit(f"{module} already has the harness applied; run revert first")
    kmp = is_kmp(repo, module)
    shutil.copyfile(gradle, marker(repo, module))
    with open(gradle, "a") as fh:
        fh.write(GRADLE_BLOCK if kmp else APP_GRADLE_BLOCK)
    cases = expand(spec["cases"])
    src = open(TEMPLATE).read()
    src = src.replace("__PKG__", spec["package"])
    src = src.replace("__IMPORTS__", "\n".join(f"import {i}" for i in spec.get("imports", [])))
    src = src.replace("__OUT__", os.path.abspath(out_dir))
    src = src.replace("__CASES__", "\n".join(kotlin_case(c) for c in cases))
    tp = test_path(repo, module, spec["package"])
    os.makedirs(os.path.dirname(tp), exist_ok=True)
    with open(tp, "w") as fh:
        fh.write(src)
    with open(marker(repo, module), "a"):
        pass
    # One meta.json per batch, beside before/ and after/, merged by name across modules.
    meta_path = os.path.join(os.path.dirname(os.path.abspath(out_dir.rstrip("/"))), "meta.json")
    os.makedirs(os.path.dirname(meta_path), exist_ok=True)
    meta = {m["name"]: m for m in (json.load(open(meta_path)) if os.path.exists(meta_path) else [])}
    for c in cases:
        meta[c["name"]] = {k: c.get(k) for k in ("name", "width", "height", "scale", "locale", "style", "note", "scrolled")}
    with open(meta_path, "w") as fh:
        json.dump(sorted(meta.values(), key=lambda m: m["name"]), fh, indent=1)
    with open(os.path.join(repo, module, BACKUP + ".test"), "w") as fh:
        fh.write(tp)
    task = ":" + module.replace("/", ":") + (":testDebugUnitTest" if kmp else ":testFossDebugUnitTest")
    print(f"Applied to {module}: {len(cases)} renders ({len(spec['cases'])} cases plus controls)")
    print(f"./gradlew -Dorg.gradle.jvmargs=\"-Xmx4g -Dfile.encoding=UTF-8\" {task} --tests '*TempSmallScreenRender'")
    print(f"PNGs land in {os.path.abspath(out_dir)}; failures are listed in failures.txt there")


def revert(repo, module):
    bak = marker(repo, module)
    if not os.path.exists(bak):
        print(f"{module}: harness not applied")
        return
    rec = os.path.join(repo, module, BACKUP + ".test")
    if os.path.exists(rec):
        tp = open(rec).read().strip()
        if os.path.exists(tp):
            os.remove(tp)
        # apply may have created the package directories; remove the ones left empty.
        stop = os.path.abspath(os.path.join(repo, module, "src"))
        d = os.path.dirname(os.path.abspath(tp))
        while d.startswith(stop + os.sep) and os.path.isdir(d) and not os.listdir(d):
            os.rmdir(d)
            d = os.path.dirname(d)
        os.remove(rec)
    shutil.copyfile(bak, os.path.join(repo, module, "build.gradle.kts"))
    os.remove(bak)
    print(f"{module}: reverted")


def status(repo):
    for top in ("core", "feature", "app", "shared"):
        for d, dn, fs in os.walk(os.path.join(repo, top)):
            dn[:] = [x for x in dn if x not in ("build", ".gradle", "src")]
            if BACKUP in fs:
                print(os.path.relpath(d, repo))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "apply" and len(sys.argv) == 6:
        apply(*sys.argv[2:6])
    elif cmd == "revert" and len(sys.argv) == 4:
        revert(*sys.argv[2:4])
    elif cmd == "status" and len(sys.argv) == 3:
        status(sys.argv[2])
    else:
        sys.exit(__doc__)
