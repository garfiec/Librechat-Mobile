#!/usr/bin/env python3
"""Coverage ledger: every non-test .kt file under core/ feature/ app/ shared/ that declares UI.

Usage: ledger.py <repo> <out.json> [--prior <previous-audit.md>] [--module <prefix>]...

A file is UI when it mentions @Composable or a UIKit view type; *Previews.kt files and preview/
packages are skipped. --prior marks the files a previous audit's findings table names, so readers
can tag a repeat of a known row. --module limits the ledger to paths that
start with the given prefix (e.g. --module feature/chat); repeat it for several.
"""
import argparse
import json
import os
import re

UI = re.compile(r"@Composable|UIView|UIViewController|UIButton|UILabel")
TOPS = ("core", "feature", "app", "shared")

ap = argparse.ArgumentParser()
ap.add_argument("repo")
ap.add_argument("out")
ap.add_argument("--prior")
ap.add_argument("--module", action="append", default=[])
a = ap.parse_args()

prior_text = open(a.prior, encoding="utf-8").read() if a.prior else ""
# An audit doc's coverage appendix names every file; only its findings table says which had defects.
if "## 4. Findings table" in prior_text:
    prior_text = prior_text.split("## 4. Findings table", 1)[1].split("## 5. Refuted", 1)[0]
files = []
for top in TOPS:
    for dp, dn, fn in os.walk(os.path.join(a.repo, top)):
        dn[:] = [d for d in dn if d not in ("build", ".gradle")]
        if re.search(r"/src/[^/]*[Tt]est", dp):
            continue
        for f in fn:
            # Previews are fixtures for the renders, not product UI.
            if not f.endswith(".kt") or f.endswith("Previews.kt") or "/preview" in dp:
                continue
            p = os.path.join(dp, f)
            src = open(p, encoding="utf-8").read()
            if not UI.search(src):
                continue
            rel = os.path.relpath(p, a.repo)
            if a.module and not any(rel.startswith(m.rstrip("/") + "/") for m in a.module):
                continue
            parts = rel.split("/")
            files.append({
                "path": rel,
                "module": "/".join(parts[:2]) if parts[0] in ("core", "feature") else parts[0],
                "lines": src.count("\n") + 1,
                "prior": bool(prior_text) and f in prior_text,
            })
files.sort(key=lambda x: x["path"])
with open(a.out, "w") as fh:
    json.dump(files, fh, indent=1)

by = {}
for x in files:
    m = by.setdefault(x["module"], [0, 0, 0])
    m[0] += 1
    m[1] += x["lines"]
    m[2] += x["prior"]
print(len(files), "files,", sum(x["prior"] for x in files), "named in the prior audit")
for k, (n, lines, p) in sorted(by.items()):
    print(f"{k:28} files={n:4} lines={lines:6} prior={p}")
