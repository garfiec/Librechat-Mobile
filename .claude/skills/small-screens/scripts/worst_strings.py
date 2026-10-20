#!/usr/bin/env python3
"""The longest translation of every string key, per module.

Usage: worst_strings.py <repo> <out.tsv>

Columns: module, key, en_w, worst_locale, worst_w, en, worst. Width counts East Asian wide
characters as 2. A plural is measured by its "other" form. Readers grep this file for a key and
quote the worst string, so a fit judgement never rests on a guessed translation.
"""
import glob
import os
import sys
import unicodedata
import xml.etree.ElementTree as ET

repo, out = sys.argv[1], sys.argv[2]


def width(s):
    return sum(2 if unicodedata.east_asian_width(c) in "WF" else 1 for c in s)


def load(p):
    d = {}
    try:
        root = ET.parse(p).getroot()
    except ET.ParseError as e:
        print("parse failure", p, e, file=sys.stderr)
        return d
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            d[name] = "".join(el.itertext()).strip()
        elif el.tag == "plurals":
            items = {i.get("quantity"): "".join(i.itertext()).strip() for i in el}
            d[name] = items.get("other") or next(iter(items.values()), "")
    return d


def clean(s):
    return s.replace("\t", " ").replace("\n", "\\n")


rows = []
bases = sorted(set(glob.glob(f"{repo}/*/*/src/commonMain/composeResources")
                   + glob.glob(f"{repo}/shared/src/commonMain/composeResources")))
for base in bases:
    mod = os.path.relpath(base, repo).split("/src/")[0]
    en = load(f"{base}/values/strings.xml")
    locs = {p.split("/values-")[1].split("/")[0]: load(p) for p in glob.glob(f"{base}/values-*/strings.xml")}
    for k, v in en.items():
        best = ("en", v, width(v))
        for loc, d in locs.items():
            t = d.get(k)
            if t and width(t) > best[2]:
                best = (loc, t, width(t))
        rows.append((mod, k, width(v), best[0], best[2], clean(v), clean(best[1])))

with open(out, "w") as f:
    f.write("module\tkey\ten_w\tworst_locale\tworst_w\ten\tworst\n")
    for r in rows:
        f.write("\t".join(map(str, r)) + "\n")
print(len(rows), "keys from", len(bases), "modules")
