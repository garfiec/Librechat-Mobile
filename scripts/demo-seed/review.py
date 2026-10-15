#!/usr/bin/env python3
"""Print what the server saved for each seeded chat, so replies can be reviewed before capture.

    scripts/demo-seed/review.py --env-file scripts/demo-seed/.state/local.env
    scripts/demo-seed/review.py --env-file … --only car-wash --width 0     # full text

For each chat: its title, tags and pin, then every message with its content parts (thinking,
text, tool calls) truncated to --width characters. Check that artifact directives are
well-formed (`:::artifact{…}`), React artifacts import only react, and no reply apologises for a
broken tool.
"""

import argparse
import json
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import seed  # noqa: E402


def describe(message: dict, width: int) -> str:
    text = message.get("text") or ""
    if not text:
        parts = []
        for part in message.get("content") or []:
            if isinstance(part, dict):
                kind = part.get("type", "?")
                value = part.get(kind, "")
                parts.append(f"{kind}:{value if isinstance(value, str) else json.dumps(value)}")
        text = " | ".join(parts)
    return text if width <= 0 else text[:width]


def main() -> int:
    p = argparse.ArgumentParser(description="Show what the server saved for seeded chats.")
    p.add_argument("--env-file", required=True)
    p.add_argument("--only", nargs="+", metavar="KEY")
    p.add_argument("--width", type=int, default=300, help="characters per message; 0 for all")
    args = p.parse_args()

    for line in Path(args.env_file).read_text().splitlines():
        line = line.strip().removeprefix("export ").strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip().strip("'\""))
    server, email = os.environ["DEMO_SEED_SERVER"], os.environ["DEMO_SEED_EMAIL"]
    client = seed.Client(server, email, os.environ["DEMO_SEED_PASSWORD"], "")
    client.login()
    state = seed.State(seed.state_path(server, email)).data

    for key, record in state["chats"].items():
        if args.only and key not in args.only:
            continue
        cid = record.get("conversationId")
        if not cid:
            print(f"=== {key}: never started")
            continue
        convo = client.json("GET", f"/api/convos/{cid}")
        messages = client.json("GET", f"/api/messages/{cid}")
        print(f"=== {key}: {convo.get('title')!r} tags={convo.get('tags') or []} "
              f"pinned={bool(convo.get('pinned'))} messages={len(messages)}"
              + ("" if record.get("complete", True) else "  (partial)"))
        for m in messages:
            who = "USER" if m.get("isCreatedByUser") else (m.get("model") or m.get("sender"))
            print(f"  [{who}] {describe(m, args.width)!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
