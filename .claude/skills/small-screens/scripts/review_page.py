#!/usr/bin/env python3
"""Build the before/after review page for a run: the page the user reviews before any PR.

Usage: review_page.py <run_dir> <site_dir> [--title "..."]

Reads, per batch B<n> under <run_dir>/fixes/:
  before/*.png, after/*.png   the paired renders (same file names)
  meta.json                   from render.py: size, scale, locale, style and note per render
  result.json                 the batch's final workflow result, saved by the lead: branch,
                              changes (user-facing bullets), findings [{id, status, note}]
and <run_dir>/audit.md (optional) for each finding's severity, title and trigger.

Writes <site_dir>/index.html, data/index.json and data/B<n>.json. Renders are packed as WebP data
URIs (quality 82, at most 760px wide); pixel-identical pairs are flagged. Publish <site_dir> with
the Artifact tool: index.html as the page, data/*.json through `files`.
"""
import argparse
import base64
import glob
import io
import json
import os
import re
import shutil

from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
MAXW = 760

ap = argparse.ArgumentParser()
ap.add_argument("run_dir")
ap.add_argument("site_dir")
ap.add_argument("--title", default="Small-screen fixes, before and after")
a = ap.parse_args()

fixes = os.path.join(a.run_dir, "fixes")
os.makedirs(os.path.join(a.site_dir, "data"), exist_ok=True)
shutil.copyfile(os.path.join(HERE, "..", "review", "index.html"), os.path.join(a.site_dir, "index.html"))

audit = {}
doc_path = os.path.join(a.run_dir, "audit.md")
if os.path.exists(doc_path):
    doc = open(doc_path, encoding="utf-8").read()
    if "## 4. Findings table" in doc:
        table = doc.split("## 4. Findings table", 1)[1].split("## 5. Refuted", 1)[0]
        for line in table.splitlines():
            c = [x.strip() for x in line.strip().strip("|").split("|")]
            if len(c) >= 11 and c[0] not in ("id", "") and not set(c[0]) <= set("-:"):
                audit.setdefault(c[8], {})[c[0]] = {"sev": c[1], "file": c[4], "title": c[5], "trigger": c[6], "hint": c[10]}


def batch_title(b):
    p = os.path.join(a.run_dir, "batches", f"{b}.md")
    if os.path.exists(p):
        m = re.match(rf"# Fix batch {b}\. ([^\n]+)", open(p, encoding="utf-8").read())
        if m:
            return m.group(1).strip()
    return b


def enc(path):
    im = Image.open(path).convert("RGB")
    if im.width > MAXW:
        im = im.resize((MAXW, round(im.height * MAXW / im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "WEBP", quality=82, method=6)
    return "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode(), im.size


def same(p, q):
    ia, ib = Image.open(p).convert("RGB"), Image.open(q).convert("RGB")
    return ia.size == ib.size and ImageChops.difference(ia, ib).getbbox() is None


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


index = []
batches = sorted((os.path.basename(d) for d in glob.glob(os.path.join(fixes, "B*")) if os.path.isdir(d)),
                 key=lambda s: int(re.sub(r"\D", "", s) or 0))
for b in batches:
    bd, ad = os.path.join(fixes, b, "before"), os.path.join(fixes, b, "after")
    meta = {}
    mp = os.path.join(fixes, b, "meta.json")
    if os.path.exists(mp):
        meta = {m["name"]: m for m in json.load(open(mp))}
    result = {}
    rp = os.path.join(fixes, b, "result.json")
    if os.path.exists(rp):
        result = json.load(open(rp))
    status = {f["id"]: f for f in result.get("findings", [])}
    ids = list(audit.get(b, {})) + [i for i in status if i not in audit.get(b, {})]
    names = sorted({f[:-4] for d in (bd, ad) if os.path.isdir(d) for f in os.listdir(d) if f.endswith(".png")})
    renders = []
    for n in names:
        bp, apath = os.path.join(bd, n + ".png"), os.path.join(ad, n + ".png")
        m = meta.get(n.removesuffix("-scrolled"), {})
        ns = norm(n)
        hits = [i for i in ids if len(norm(i)) >= 3 and norm(i) in ns]
        r = {
            "name": n,
            "desc": m.get("note", ""),
            "cfg": "",
            "style": m.get("style") or "",
            "locale": m.get("locale") or "en",
            "width": f"{m['width']}dp" if m.get("width") else "",
            "scale": f"{m['scale']}x" if m.get("scale") else "",
            "control": n.endswith("__control"),
            "ids": hits,
        }
        if os.path.exists(bp):
            r["before"], r["bsize"] = enc(bp)
        if os.path.exists(apath):
            r["after"], r["asize"] = enc(apath)
        r["same"] = os.path.exists(bp) and os.path.exists(apath) and same(bp, apath)
        renders.append(r)
    findings = {i: {**audit.get(b, {}).get(i, {}), **{k: v for k, v in status.get(i, {}).items() if k != "id"}} for i in ids}
    with open(os.path.join(a.site_dir, "data", f"{b}.json"), "w") as fh:
        json.dump({"renders": renders, "findings": findings}, fh)
    index.append({"id": b, "title": batch_title(b), "branch": result.get("branch", ""), "changes": result.get("changes", [])})
    size = os.path.getsize(os.path.join(a.site_dir, "data", f"{b}.json"))
    print(b, len(renders), "renders,", sum(r["same"] for r in renders), "identical,",
          sum(not r["ids"] for r in renders), "unmatched,", f"{size / 1e6:.1f}MB")
with open(os.path.join(a.site_dir, "data", "index.json"), "w") as fh:
    json.dump({"title": a.title, "batches": index}, fh)
total = sum(os.path.getsize(p) for p in glob.glob(os.path.join(a.site_dir, "data", "*.json")))
print(f"{len(index)} batches, {total / 1e6:.1f}MB of data (each file must stay under 16MB)")
