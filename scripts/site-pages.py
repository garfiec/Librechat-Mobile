#!/usr/bin/env python3
"""Fill the website's generated pages in a built site: What's new, Privacy and Compatibility.

    scripts/site-pages.py <built site dir> [--strict]

`:web:demo:assembleSite` runs this on its output. Each page in site/ carries a fallback between
`<!-- generated:NAME -->` and `<!-- /generated -->` (a link to GitHub), so the unbuilt pages still
read correctly; this replaces it:

- changelog: every published release's "Highlights" section, from the GitHub releases API.
- privacy: docs/PRIVACY.md, converted to HTML.
- compatibility: one row per release tag, from that tag's version.properties (best-tested backend)
  and README badge (tested range).
- range: the newest release's tested range, inline (the home page's "Works with" line and FAQ).

A source that can't be read leaves that page's fallback, with a warning; --strict makes it fail
instead (for the deploy). Standard library only.
"""
from __future__ import annotations

import html
import json
import os
import re
import subprocess
import sys
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = "garfiec/Librechat-Mobile"
MARKER = re.compile(r"(<!-- generated:(\w+) -->)(.*?)(<!-- /generated -->)", re.S)


# ----------------------------------------------------------------------------- Markdown

def inline(text: str) -> str:
    """Escape, then the inline syntax the release notes and privacy policy use."""
    out = []
    # Code spans first, so nothing inside them is formatted.
    for i, part in enumerate(re.split(r"(`[^`]+`)", text)):
        if i % 2:
            out.append(f"<code>{html.escape(part[1:-1])}</code>")
            continue
        s = html.escape(part, quote=False)
        s = re.sub(r"\[([^\]]+)\]\((https?://[^)\s]+|[\w./#-]+)\)",
                   lambda m: f'<a href="{html.escape(m[2])}">{m[1]}</a>', s)
        s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
        s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
        s = re.sub(r"(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])", r"<em>\1</em>", s)
        out.append(s)
    return "".join(out)


LIST_ITEM = re.compile(r"^(\s*)([-*]|\d+\.)\s+(.*)$")


def markdown(text: str, heading_offset: int = 0) -> str:
    """Headings, paragraphs, nested lists, pipe tables and inline formatting: enough for
    docs/PRIVACY.md and release notes, which use nothing else."""
    lines = text.splitlines()
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if m := re.match(r"^(#{1,6})\s+(.*)$", line):
            level = min(6, len(m[1]) + heading_offset)
            out.append(f"<h{level}>{inline(m[2].strip())}</h{level}>")
            i += 1
        elif line.lstrip().startswith("|"):
            rows = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            head, body = rows[0], [r for r in rows[1:] if not all(re.fullmatch(r":?-+:?", c) for c in r)]
            out.append('<div class="table-scroll"><table><thead><tr>'
                       + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr></thead><tbody>"
                       + "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in body)
                       + "</tbody></table></div>")
        elif LIST_ITEM.match(line):
            block = []
            while i < len(lines) and lines[i].strip() and (LIST_ITEM.match(lines[i]) or lines[i].startswith(" ")):
                block.append(lines[i])
                i += 1
            out.append(list_html(block))
        else:
            para = []
            while i < len(lines) and lines[i].strip() and not re.match(r"^(#{1,6}\s|\s*\||\s*([-*]|\d+\.)\s)", lines[i]):
                para.append(lines[i].strip())
                i += 1
            # A run of lines that each start bold ("**Effective date:** …") is a list of fields.
            joiner = "<br>" if len(para) > 1 and all(p.startswith("**") for p in para) else " "
            out.append(f"<p>{joiner.join(inline(p) for p in para)}</p>")
    return "\n".join(out)


def list_html(block: list[str]) -> str:
    """Nested lists by indentation; a line indented under an item continues it."""
    items: list[tuple[int, bool, str]] = []  # (indent, ordered, text)
    for raw in block:
        if m := LIST_ITEM.match(raw):
            items.append((len(m[1]), m[2][0].isdigit(), m[3]))
        elif items:
            indent, ordered, text = items[-1]
            items[-1] = (indent, ordered, text + " " + raw.strip())

    def render(start: int, indent: int) -> tuple[str, int]:
        ordered = items[start][1]
        parts, i = [], start
        while i < len(items) and items[i][0] >= indent:
            if items[i][0] > indent:  # a deeper list belongs to the item before it
                child, i = render(i, items[i][0])
                parts[-1] = parts[-1][:-5] + child + "</li>"
                continue
            parts.append(f"<li>{inline(items[i][2])}</li>")
            i += 1
        tag = "ol" if ordered else "ul"
        return f"<{tag}>{''.join(parts)}</{tag}>", i

    return render(0, items[0][0])[0] if items else ""


# ----------------------------------------------------------------------------- pages

def changelog() -> str:
    request = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases?per_page=100",
                                     headers={"Accept": "application/vnd.github+json",
                                              "User-Agent": "switchboard-site-build"})
    if token := os.environ.get("GITHUB_TOKEN"):
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=30) as response:
        releases = json.load(response)
    entries = []
    for release in releases:
        if release.get("draft") or release.get("prerelease"):
            continue
        tag, url = release["tag_name"], release["html_url"]
        date = datetime.fromisoformat(release["published_at"].replace("Z", "+00:00"))
        body = highlights(release.get("body") or "")
        entries.append(
            f'<article>\n<h2 id="{html.escape(tag)}">{html.escape(tag.lstrip("v"))}</h2>\n'
            f'<p class="muted">{date:%B} {date.day}, {date.year} · <a href="{html.escape(url)}">Full release notes</a></p>\n'
            + body + "\n</article>")
    if not entries:
        raise RuntimeError("no published releases")
    return "\n".join(entries)


def highlights(body: str) -> str:
    """The release's "Highlights" section, without the empty "Misc …" category lines."""
    m = re.search(r"^#{1,3}\s+Highlights\s*$(.*?)(?=^#{1,3}\s|\Z)", body, re.M | re.S)
    if not m:
        return ""
    lines = m[1].strip().splitlines()
    kept = []
    for n, line in enumerate(lines):
        top = re.match(r"^[-*]\s+(.*)$", line)
        has_children = n + 1 < len(lines) and lines[n + 1].startswith((" ", "\t"))
        if top and not has_children and top[1].lower().startswith("misc"):
            continue
        kept.append(line)
    return markdown("\n".join(kept), heading_offset=2)


def privacy() -> str:
    text = (ROOT / "docs" / "PRIVACY.md").read_text()
    text = re.sub(r"^#\s.*\n", "", text, count=1)  # the page has its own title
    return markdown(text)


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True, text=True, check=True).stdout


def compatibility() -> str:
    rows = []
    tags = [t for t in git("tag", "--list", "v*", "--sort=-v:refname").split() if re.fullmatch(r"v\d+\.\d+\.\d+", t)]
    for tag in tags:
        try:
            props = git("show", f"{tag}:version.properties")
            readme = git("show", f"{tag}:README.md")
        except subprocess.CalledProcessError:
            continue
        best = re.search(r"^backendTargetVersion=(\S+)", props, re.M)
        tested = re.search(r"LibreChat-v([\d.]+(?:--?rc\d+)?)_[–-]+_v([\d.]+(?:--?rc\d+)?)", readme)
        if not best:
            continue
        span = f"v{tested[1].replace('--', '-')} – v{tested[2].replace('--', '-')}" if tested else "—"
        rows.append(f"<tr><td>{html.escape(tag.lstrip('v'))}</td><td>v{html.escape(best[1].split('+')[0])}</td>"
                    f"<td>{html.escape(span)}</td></tr>")
    if not rows:
        raise RuntimeError("no release tags with a version.properties")
    return ('<div class="table-scroll"><table><thead><tr><th>App version</th><th>Best tested with</th>'
            "<th>Tested range</th></tr></thead><tbody>\n" + "\n".join(rows) + "\n</tbody></table></div>")


def version_range() -> str:
    """The newest release's tested range, inline."""
    for tag in [t for t in git("tag", "--list", "v*", "--sort=-v:refname").split() if re.fullmatch(r"v\d+\.\d+\.\d+", t)]:
        if tested := re.search(r"LibreChat-v([\d.]+(?:--?rc\d+)?)_[–-]+_v([\d.]+(?:--?rc\d+)?)", git("show", f"{tag}:README.md")):
            return html.escape(f"v{tested[1].replace('--', '-')} – v{tested[2].replace('--', '-')}")
    raise RuntimeError("no release tag has a tested range")


GENERATORS = {"changelog": changelog, "privacy": privacy, "compatibility": compatibility, "range": version_range}
INLINE = {"range"}


def main() -> int:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    site, strict = Path(sys.argv[1]), "--strict" in sys.argv
    failed = False
    for page in sorted(site.glob("*.html")):
        text = page.read_text()

        def fill(m: re.Match) -> str:
            nonlocal failed
            try:
                sep = "" if m[2] in INLINE else "\n"
                return f"{m[1]}{sep}{GENERATORS[m[2]]()}{sep}{m[4]}"
            except Exception as e:  # noqa: BLE001 -- any failure keeps the fallback
                failed = True
                print(f"warning: {page.name}: kept the fallback for {m[2]}: {e}", file=sys.stderr)
                return m[0]

        new = MARKER.sub(fill, text)
        if new != text:
            page.write_text(new)
            print(f"{page.name}: filled")
    return 1 if failed and strict else 0


if __name__ == "__main__":
    sys.exit(main())
