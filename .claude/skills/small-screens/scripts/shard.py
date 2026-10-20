#!/usr/bin/env python3
"""Split the ledger into reader shards and attach the scan leads.

Usage: shard.py <ledger.json> <leads.txt> <run_dir> [--limit 2800]

A shard holds about --limit lines of source, stays inside one module, and keeps files in path
order, so a component and its nearby callers land in one reader's context. Writes:
  <run_dir>/shards.json        the workflow's input (ids, modules, files)
  <run_dir>/shards/<id>.md     the file list a reader works through, with its leads
"""
import argparse
import collections
import json
import os

ap = argparse.ArgumentParser()
ap.add_argument("ledger")
ap.add_argument("leads")
ap.add_argument("run_dir")
ap.add_argument("--limit", type=int, default=2800)
a = ap.parse_args()

files = json.load(open(a.ledger))
leads = collections.defaultdict(list)
for line in open(a.leads):
    if " " not in line:
        continue
    tag, rest = line.split(" ", 1)
    path, _, ln = rest.split(": ", 1)[0].rpartition(":")
    leads[path].append(f"{tag} L{ln}")

shards, cur, cur_lines, cur_mod = [], [], 0, None
for f in files:
    if cur and (f["module"] != cur_mod or cur_lines + f["lines"] > a.limit):
        shards.append(cur)
        cur, cur_lines = [], 0
    cur_mod = f["module"]
    cur.append({"path": f["path"], "lines": f["lines"], "prior": f["prior"], "leads": leads.get(f["path"], [])})
    cur_lines += f["lines"]
if cur:
    shards.append(cur)

os.makedirs(os.path.join(a.run_dir, "shards"), exist_ok=True)
res = []
for i, s in enumerate(shards):
    sid = f"S{i + 1:02d}"
    mod = s[0]["path"].split("/src/")[0]
    res.append({"id": sid, "module": mod, "files": [{"path": f["path"], "prior": f["prior"]} for f in s]})
    with open(os.path.join(a.run_dir, "shards", f"{sid}.md"), "w") as fh:
        fh.write(f"# Shard {sid} ({mod}), {sum(f['lines'] for f in s)} lines\n\n")
        for f in s:
            tags = (" — leads: " + ", ".join(f["leads"])) if f["leads"] else ""
            fh.write(f"- {f['path']} ({f['lines']} lines{', named in prior audit' if f['prior'] else ''}){tags}\n")
    print(sid, mod, len(s), sum(f["lines"] for f in s))
with open(os.path.join(a.run_dir, "shards.json"), "w") as fh:
    json.dump(res, fh)
print(len(res), "shards")
