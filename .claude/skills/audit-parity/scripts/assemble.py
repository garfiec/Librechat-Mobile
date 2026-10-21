#!/usr/bin/env python3
"""Assemble $RUN/findings.json from the per-area files and the workflow results.

Usage: assemble.py <run dir>

Reads every $RUN/result-*.json (each batch's workflow return, saved by the lead), the
per-area $RUN/areas/<id>/findings.json files written by triage, and the unmerged findings of
any area whose triage failed. Applies the last batch's cross-area merge groups and rank order,
numbers the findings F1, F2, ... and writes $RUN/findings.json. If report.md is missing (the
report agent failed), writes a plain fallback report. Prints the post-merge totals.
"""
import glob, json, os, sys

SEV = {"high": 0, "medium": 1, "low": 2}


def main(run):
    results = [json.load(open(p)) for p in sorted(glob.glob(os.path.join(run, "result-*.json")))]
    if not results:
        sys.exit(f"no result-*.json in {run}")
    ranking = next((r["ranking"] for r in reversed(results) if r.get("ranking")), None)

    by_id = {}
    for p in glob.glob(os.path.join(run, "areas", "*", "findings.json")):
        for f in json.load(open(p)):
            by_id[f["id"]] = f
    for r in results:
        for f in r.get("unmerged", []):
            by_id.setdefault(f["id"], f)
    missing = [i["id"] for r in results for i in r.get("index", []) if i["id"] not in by_id]
    if missing:
        print(f"warning: {len(missing)} indexed findings have no full record: {', '.join(missing)}")

    merged_into = {}
    if ranking:
        for g in ranking.get("groups", []):
            for m in g["merged"]:
                if m != g["primary"] and m in by_id:
                    merged_into[m] = g["primary"]
    out = {}
    for fid, f in by_id.items():
        if fid in merged_into:
            continue
        out[fid] = dict(f, also=[])
    for m, p in merged_into.items():
        if p in out:
            src = by_id[m]
            out[p]["also"].append({"id": m, "area": src.get("area"), "title": src.get("title"), "ours": src.get("ours")})

    order = [i for i in (ranking or {}).get("order", []) if i in out]
    rest = sorted((i for i in out if i not in order),
                  key=lambda i: (bool(out[i].get("duplicate_of")), SEV.get(out[i].get("severity"), 3), i))
    final = []
    for n, fid in enumerate(order + rest, 1):
        final.append(dict(out[fid], fid=f"F{n}", source_id=fid))
    json.dump(final, open(os.path.join(run, "findings.json"), "w"), indent=1)

    totals = {
        "findings": len(final),
        "duplicates": sum(1 for f in final if f.get("duplicate_of")),
        **{s: sum(1 for f in final if f.get("severity") == s) for s in SEV},
    }
    print("post-merge totals:", json.dumps(totals))
    if ranking and ranking.get("totals") != totals:
        print("MISMATCH with the report agent's totals:", json.dumps(ranking.get("totals")))

    report = os.path.join(run, "report.md")
    if not os.path.exists(report):
        rows = "\n".join(
            f"| {f['fid']} | {f.get('severity')} | {f.get('kind')} | {f.get('area')} | {f.get('title')} | {f.get('duplicate_of', '')} |"
            for f in final)
        open(report, "w").write(
            "# Parity audit (fallback report)\n\nreport.md was missing, so this table was built from the per-area files. Shared roots "
            "and coverage notes are not included.\n\n## Totals\n\n" + json.dumps(totals) +
            "\n\n## Findings\n\n| id | sev | kind | area | title | duplicate_of |\n|---|---|---|---|---|---|\n" + rows + "\n")
        print("wrote fallback", report)


if __name__ == "__main__":
    main(sys.argv[1])
