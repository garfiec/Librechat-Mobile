#!/usr/bin/env python3
"""Run the AI RCA for one issue against a rig that rig/up.sh started.

  run.py ISSUE OUT_DIR [--as USER --repo DIR]

Env: RIG_URL, RIG_EMAIL, RIG_PASSWORD, RIG_VERSION (from rig/up.sh), GH_TOKEN, and either
ANTHROPIC_API_KEY or ANTHROPIC_BASE_URL (proxy.py). GH_TOKEN is used here only. With --as, the agent
runs as USER in the copy at DIR with an allow-listed environment: no GitHub token and no API key.
Writes OUT_DIR/report.md and OUT_DIR/meta.json; meta.status is ready or unavailable. Prints only
the status, never the transcript, report or anything that tells a held report from a failed run:
workflow logs on this repo are public.
"""
import json, os, subprocess, sys, time
from pathlib import Path

MODEL = "claude-opus-5-5"
BUDGET_USD = "10"
TIMEOUT_S = 50 * 60


def main(issue, out, user=None, agent_repo=None):
    here = Path(__file__).resolve().parent
    repo = Path(subprocess.check_output(["git", "-C", str(here), "rev-parse", "--show-toplevel"], text=True).strip())
    agent_repo = Path(agent_repo) if agent_repo else repo
    out.mkdir(parents=True, exist_ok=True)
    data = json.loads(subprocess.check_output(
        ["gh", "issue", "view", str(issue), "--repo", os.environ["GITHUB_REPOSITORY"], "--json", "title,body"],
        text=True))
    related = []
    for kind in ("issue", "pr"):
        rows = json.loads(subprocess.check_output(
            ["gh", kind, "list", "--repo", os.environ["GITHUB_REPOSITORY"], "--state", "all", "--limit", "150",
             "--json", "number,title,state"], text=True))
        related += [f"- {kind} #{r['number']} ({r['state'].lower()}): {r['title']}" for r in rows]
    base = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
    upstream = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD:upstream"], text=True).strip()

    prompt = (here / "brief.md").read_text()
    for key, value in {"issue_number": issue, "title": data["title"], "base_sha": base,
                       "upstream_sha": upstream, "backend_version": os.environ["RIG_VERSION"]}.items():
        prompt = prompt.replace("{" + key + "}", str(value))
    prompt = prompt.replace("{related}", "\n".join(related)).replace("{body}", data["body"] or "(empty)")

    keep = ("PATH", "LANG", "RIG_URL", "RIG_EMAIL", "RIG_PASSWORD", "ANTHROPIC_BASE_URL", "ANTHROPIC_API_KEY")
    env = {k: os.environ[k] for k in keep if k in os.environ}
    env.update(RCA_RUN_DIR=str(out), DISABLE_AUTOUPDATER="1", CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC="1")
    env["PATH"] = f"{agent_repo / '.github/ai-rca'}:{env.get('PATH', '/usr/local/bin:/usr/bin:/bin')}"
    claude = ["claude", "-p", "--model", MODEL, "--output-format", "json", "--settings",
              str(agent_repo / ".github/ai-rca/settings.json"), "--setting-sources", "project",
              "--permission-mode", "dontAsk",
              "--max-budget-usd", BUDGET_USD, "--no-session-persistence"]
    if user:
        env["HOME"] = env["RCA_RUN_DIR"] = f"/home/{user}"
        env["ANTHROPIC_API_KEY"] = "via-proxy"
        cmd = ["sudo", "-u", user, "env", "-i", *(f"{k}={v}" for k, v in env.items()), *claude]
    else:
        env["HOME"] = os.environ["HOME"]
        cmd = claude
    started = time.time()
    try:
        r = subprocess.run(cmd, cwd=agent_repo, env=None if user else env, input=prompt, text=True,
                           capture_output=True, timeout=TIMEOUT_S)
    except subprocess.TimeoutExpired:
        r = subprocess.CompletedProcess(cmd, -1, "", "timeout")
    finally:
        if user:
            subprocess.run(["sudo", "pkill", "-KILL", "-u", user])
    result = json.loads(r.stdout) if r.stdout.strip().startswith("{") else {}
    report = (result.get("result") or "").strip()

    # A held report is reported exactly like a failed run: the status reaches public logs.
    status = "ready" if "## TL;DR" in report and "SECURITY HOLD" not in report else "unavailable"
    meta = {"status": status, "issue": issue, "base": base, "upstream": upstream,
            "backend": os.environ["RIG_VERSION"], "minutes": round((time.time() - started) / 60, 1),
            "cost_usd": round(result.get("total_cost_usd") or 0, 2), "turns": result.get("num_turns"),
            "exit": r.returncode}
    (out / "report.md").write_text(report if status == "ready" else "")
    (out / "meta.json").write_text(json.dumps(meta))
    print(json.dumps({"status": status}))


args = sys.argv[1:]
opts = dict(zip(args[2::2], args[3::2]))
main(int(args[0]), Path(args[1]), opts.get("--as"), opts.get("--repo"))
