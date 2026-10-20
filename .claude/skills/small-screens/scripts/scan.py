#!/usr/bin/env python3
"""Regex leads for readers. Hints only: readers still read every file in full.

Usage: scan.py <repo> <out.txt>

One line per lead: "<TAG> <path>:<line>: <source>". Tags:
  NOELL  maxLines = 1 with no overflow within six lines
  WIDTH  a width of 200dp or more
  FRAC   fillMaxWidth/Height(0.x) or heightIn(max
  HGT    .height(N.dp) (mostly spacers and icons; check the ones that hold text)
  IOS    UIKit text settings
  THR    a dp threshold or window-size check
"""
import os
import re
import sys

repo, out = sys.argv[1], sys.argv[2]
RULES = [
    ("WIDTH", re.compile(r"\.(?:width|widthIn|requiredWidth|sizeIn)\(([^)]*)\)")),
    ("FRAC", re.compile(r"fillMaxWidth\(0?\.\d+f?\)|fillMaxHeight\(0?\.\d+f?\)|heightIn\(max")),
    ("HGT", re.compile(r"\.height\(\d+\.dp\)")),
    ("IOS", re.compile(r"numberOfLines|lineBreakMode|UILabel|UIButton")),
    ("THR", re.compile(r"(?:<|>|<=|>=)\s*\d{3}\.dp|WindowWidthSizeClass|isCompact|widthDp")),
]
leads = []
for top in ("core", "feature", "app", "shared"):
    for d, dn, fs in os.walk(os.path.join(repo, top)):
        dn[:] = [x for x in dn if x not in ("build", ".gradle")]
        if re.search(r"/src/[^/]*[Tt]est", d):
            continue
        for f in fs:
            if not f.endswith(".kt"):
                continue
            p = os.path.join(d, f)
            rel = os.path.relpath(p, repo)
            lines = open(p, encoding="utf-8").read().split("\n")
            for i, line in enumerate(lines):
                if re.search(r"maxLines\s*=\s*1\b", line) and "singleLine" not in line:
                    if "overflow" not in "\n".join(lines[max(0, i - 6):i + 6]):
                        leads.append(f"NOELL {rel}:{i + 1}: {line.strip()}")
                for tag, rx in RULES:
                    m = rx.search(line)
                    if not m:
                        continue
                    if tag == "WIDTH" and not any(int(n) >= 200 for n in re.findall(r"(\d+)\.dp", m.group(1))):
                        continue
                    leads.append(f"{tag} {rel}:{i + 1}: {line.strip()}")
with open(out, "w") as fh:
    fh.write("\n".join(leads) + "\n")
counts = {}
for lead in leads:
    counts[lead.split(" ", 1)[0]] = counts.get(lead.split(" ", 1)[0], 0) + 1
print(len(leads), "leads", counts)
