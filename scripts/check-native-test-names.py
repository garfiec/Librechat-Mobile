#!/usr/bin/env python3
"""Reject backtick-quoted names that Kotlin/Native cannot compile.

The JVM accepts some names Kotlin/Native does not, so a test named
`` `reported as TLS, not as the network` `` compiles and passes in the ubuntu `test` job and
in every local `./gradlew test`. Kotlin/Native rejects it with "Name contains illegal
characters", and only the macOS `ios` job compiles that source set for iOS.

This scans every source set that is compiled for iOS (common*, ios*, apple*, native*) for a
declaration whose backtick-quoted name contains a character Kotlin/Native reserves.

    scripts/check-native-test-names.py
"""

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"build", "upstream", ".git", ".gradle", ".claude", "node_modules"}

# Source sets that end up in a Kotlin/Native compilation.
NATIVE_SOURCE_SET = re.compile(r"/src/(common|ios|apple|native)[A-Za-z0-9]*(Main|Test)/")

# `fun`/`val`/`var`/`class`/... declarations, optionally with type parameters or a receiver.
DECLARATION = re.compile(
    r"\b(?:fun|val|var|class|object|interface)\s+(?:<[^>\n]*>\s*)?(?:[\w.<>?, ]+\.)?`([^`\n]+)`"
)

# The characters Kotlin/Native refuses in an identifier.
ILLEGAL = set(".,;:/\\<>[]")


def kotlin_files():
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name.endswith(".kt"):
                path = os.path.join(root, name)
                if NATIVE_SOURCE_SET.search(path.replace(os.sep, "/")):
                    yield path


def main():
    problems = []
    for path in kotlin_files():
        with open(path, encoding="utf-8") as f:
            for lineno, line in enumerate(f, 1):
                for match in DECLARATION.finditer(line):
                    bad = sorted({c for c in match.group(1) if c in ILLEGAL})
                    if bad:
                        rel = os.path.relpath(path, REPO)
                        chars = " ".join(f"'{c}'" for c in bad)
                        problems.append(f"{rel}:{lineno}: `{match.group(1)}` contains {chars}")

    if problems:
        print("Names Kotlin/Native cannot compile (illegal: . , ; : / \\ < > [ ]):", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
