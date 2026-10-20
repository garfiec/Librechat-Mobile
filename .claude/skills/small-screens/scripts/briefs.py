#!/usr/bin/env python3
"""Write one brief per fix batch from the audit doc.

Usage: briefs.py <run_dir> [--notes <batch-notes.json>]

Reads <run_dir>/audit.md, which the synthesis agent writes with fixed headings ("## 3. Fix
batches" with one "### B<n>. <title>" section each, "## 4. Findings table", "## 5. Refuted").
Writes <run_dir>/batches/B<n>.md: the batch section plus every table row whose batch column is
that batch. --notes is a JSON object {"B4": "text"} for critic gaps the lead assigned to a batch.
"""
import argparse
import json
import os
import re

ap = argparse.ArgumentParser()
ap.add_argument("run_dir")
ap.add_argument("--notes")
a = ap.parse_args()

doc = open(os.path.join(a.run_dir, "audit.md"), encoding="utf-8").read()
notes = json.load(open(a.notes)) if a.notes else {}
head, table = doc.split("## 4. Findings table", 1)
table = table.split("## 5. Refuted", 1)[0]
header = [line for line in table.splitlines() if line.startswith("| id")][:1]
secs = re.split(r"\n### (B\d+)\. ", head.split("## 3. Fix batches", 1)[1])

out = os.path.join(a.run_dir, "batches")
os.makedirs(out, exist_ok=True)
for i in range(1, len(secs), 2):
    b, body = secs[i], secs[i + 1]
    rows = [line for line in table.splitlines() if line.startswith("|") and re.search(rf"\|\s*{b}\s*\|", line)]
    with open(os.path.join(out, f"{b}.md"), "w") as f:
        f.write(f"# Fix batch {b}. {body.strip()}\n\n## Findings in this batch ({len(rows)} rows)\n\n")
        f.write("\n".join(header + ["|" + "---|" * 11] + rows) + "\n")
        if b in notes:
            f.write(f"\n## Also in this batch (completeness critic)\n\n{notes[b]}\n")
    print(b, len(rows), "rows")
