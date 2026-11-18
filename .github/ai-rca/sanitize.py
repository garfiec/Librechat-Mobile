#!/usr/bin/env python3
"""Make an agent-written report safe to post as a comment: stdin → stdout.

Outside code spans and fences, a zero-width space stops @mentions, owner/repo#N references and links
to other repositories on GitHub from notifying anyone or creating backlinks; Markdown images become
links and raw HTML is escaped.
Code is left alone: GitHub doesn't link inside it, and Kotlin annotations must survive a copy-paste.
"""
import os, re, sys

ZW = "​"
OWN = os.environ.get("GITHUB_REPOSITORY", "").lower()


def clean(text):
    text = re.sub(r"@(?=[A-Za-z0-9])", "@" + ZW, text)
    text = re.sub(r"(?<=[A-Za-z0-9_.-]/[A-Za-z0-9_.-])([A-Za-z0-9_.-]*)#(?=\d)", r"\1#" + ZW, text)
    text = re.sub(r"(?i)github\.com/(?!" + re.escape(OWN) + r"(?:[/?#)\s]|$))", "github.com" + ZW + "/", text)
    return text.replace("![", "!" + ZW + "[").replace("<", "&lt;")


out, fenced = [], False
for line in sys.stdin.read()[:60000].splitlines(keepends=True):
    if line.lstrip().startswith("```"):
        fenced = not fenced
        out.append(line)
    elif fenced:
        out.append(line)
    else:
        parts = line.split("`")
        if len(parts) % 2 == 0:  # an unclosed backtick renders literally, so nothing on the line is code
            out.append(clean(line))
        else:
            out.append("`".join(p if i % 2 else clean(p) for i, p in enumerate(parts)))
sys.stdout.write("".join(out))
