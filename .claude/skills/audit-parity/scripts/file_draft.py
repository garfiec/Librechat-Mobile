#!/usr/bin/env python3
"""Put one finding on the Switchboard Roadmap board and set its fields.

    file_draft.py --title T --body-file F --priority Medium --workstream Chat \
                  --kind Bug --platform Both [--epic "Model specs"] [--status Backlog]
    file_draft.py --issue-url URL --priority ... (same fields)

The first form creates a draft; the second adds an existing repo issue instead.
Prints one JSON line: {"item": <node id>, "url": <link to the item>}.
Option names are matched exactly against the board; a wrong name fails before
anything is created and lists the valid ones.
"""
import argparse
import json
import re
import subprocess
import sys

OWNER, NUMBER, REPO = "garfiec", "2", "garfiec/Librechat-Mobile"


def gh(*args):
    r = subprocess.run(["gh", *args], capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"gh {' '.join(args[:3])} failed: {r.stderr.strip()}")
    return r.stdout


def linkify(text):
    return re.sub(r"(?<![\w/&#\[])#(\d{1,5})\b", rf"[#\1](https://github.com/{REPO}/issues/\1)", text)


def main():
    p = argparse.ArgumentParser()
    target = p.add_mutually_exclusive_group(required=True)
    target.add_argument("--title")
    target.add_argument("--issue-url")
    p.add_argument("--body-file")
    p.add_argument("--priority", required=True)
    p.add_argument("--workstream", required=True)
    p.add_argument("--kind", required=True)
    p.add_argument("--platform", required=True)
    p.add_argument("--epic")
    p.add_argument("--status", default="Backlog")
    p.add_argument("--source", default="Audit")
    a = p.parse_args()
    if a.title and not a.body_file:
        p.error("--title needs --body-file")

    project = json.loads(gh("project", "view", NUMBER, "--owner", OWNER, "--format", "json"))["id"]
    fields = {f["name"]: f for f in json.loads(
        gh("project", "field-list", NUMBER, "--owner", OWNER, "--format", "json"))["fields"]}

    wanted = {"Status": a.status, "Priority": a.priority, "Workstream": a.workstream,
              "Kind": a.kind, "Platform": a.platform, "Source": a.source}
    if a.epic:
        wanted["Epic"] = a.epic
    resolved = {}
    for name, value in wanted.items():
        options = {o["name"]: o["id"] for o in fields[name].get("options", [])}
        if value not in options:
            sys.exit(f"{name}: no option {value!r}. Valid: {', '.join(options)}")
        resolved[name] = (fields[name]["id"], options[value])

    if a.issue_url:
        item = json.loads(gh("project", "item-add", NUMBER, "--owner", OWNER,
                             "--url", a.issue_url, "--format", "json"))["id"]
    else:
        body = linkify(open(a.body_file).read())
        item = json.loads(gh("project", "item-create", NUMBER, "--owner", OWNER,
                             "--title", a.title, "--body", body, "--format", "json"))["id"]
    for field_id, option_id in resolved.values():
        gh("project", "item-edit", "--id", item, "--project-id", project,
           "--field-id", field_id, "--single-select-option-id", option_id)

    db = gh("api", "graphql", "-f", "query=query($i:ID!){node(id:$i){... on ProjectV2Item{fullDatabaseId}}}",
            "-f", f"i={item}", "-q", ".data.node.fullDatabaseId").strip()
    url = f"https://github.com/users/{OWNER}/projects/{NUMBER}?pane=issue&itemId={db}"
    print(json.dumps({"item": item, "url": url}))


if __name__ == "__main__":
    main()
