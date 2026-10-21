---
name: audit-feature
description: >
  Deep, surgical audit of ONE Switchboard feature against the pinned LibreChat server and a
  reference client (the upstream web client by default, or another client with --ref). Finds API
  contract bugs (status codes and error codes we mishandle, request/response fields, pagination,
  repeats), behaviour gaps against the reference (gates, honoured settings, parsing, recovery,
  Android vs iOS), and dead wiring (unreachable screens, unread prefs, stubs, failures shown as
  empty data). A multi-agent workflow loops finders until two rounds add nothing, verifies every
  finding adversarially, then the lead walks the user through filing issues one at a time. Use
  when a feature feels thin or flaky, after a field report in one area, or when asked "what's
  broken in <feature>?". Not for absorbing new upstream releases: that is /sync-upstream.
allowed-tools: Bash, Read, Glob, Grep, Write, Workflow, AskUserQuestion
argument-hint: "<feature> [--ref web|<path to another client>] [--rounds N]"
---

# Audit a feature

**How this differs from `/sync-upstream`.** Sync reads the *change* between two upstream
versions and ports it. This skill reads *one feature in full* at the current pin, whatever version
we're on, and finds what is wrong with what we already ship. Bugs that predate the last sync are
invisible to sync's diff; this is where they surface.

**`reference.md` is the method**: the three lenses, evidence rules, what is not a finding,
severity and the issue format. The workflow hands it to every agent. Read it before Phase 0.

## Rules

- **Read-only on code.** The skill ends at filed issues. No fixes, branches or PRs.
- **Upstream at the pin only**: `git -C upstream show "<pin>:<path>"`. The submodule checkout is
  often not the pin.
- **One issue at a time.** Draft, show, wait for the user, file, next. Never file in bulk.
- **Third-party `--ref` stays out of issues.** If the reference is another client, issues
  describe the bug from our code and the server alone. The upstream web client may be cited.
- No device testing unless the user asks. Issues say "found by reading the code" where it matters.

## Run directory

`RUN=~/.claude/audit-feature/<yyyy-mm-dd>-<feature-slug>/`. Outside the repo and the scratchpad,
so it survives the session. Pass it to the workflow **expanded** (`/Users/…`, never `~`).

```
scope.md  known.md  issues.json  findings.json  report.md  drafts/F1.md ...  filed.json
```

## Phase 0: scope and known issues (you, with Bash)

1. `PIN=$(grep '^commit=' UPSTREAM_VERSION | cut -d= -f2)`.
2. **Write `$RUN/scope.md`**, the file map. Spend a few minutes; it steers every agent.
   - **Ours:** the feature's screens, ViewModels, delegates, repository, `core/network/.../api/*Api.kt`
     and DTOs, Room entities if cached, settings rows and preference keys, `androidMain`/`iosMain`
     actuals and any `iosApp/` Swift. Grep the feature's nav keys, string resources and API paths.
   - **Endpoints:** every route those Api files call, as `METHOD /api/...`.
   - **Server at the pin:** for each route, the router file (`api/server/routes/`), controller,
     service and model files (`packages/api`, `packages/data-schemas`), and middleware or
     validators on the path. Note error codes it throws.
   - **Reference:** for the web, the data-service functions and endpoints
     (`packages/data-provider/src/data-service.ts`, `api-endpoints.ts`), the react-query hooks
     (`client/src/data-provider/`), and the components (`client/src/components/`). Also list web
     calls in this area that we make no equivalent of: these seed `missing-feature` and
     behaviour-gap leads. For `--ref <path>`, the same map in that client.
3. **Write `$RUN/known.md`**:
   - `gh issue list --state all --limit 1000 --json number,title,state > $RUN/issues.json`, then
     list the issues whose titles touch this feature, with numbers.
   - Unfiled bugs for this area from the memory directory (grep for "unfiled" and the feature).
   - Entries in `.claude/skills/sync-upstream/reference/upstream-backend-gaps.md` for this area.
   - Deliberate divergences for this area from the `CLAUDE.md` files and memory (for example
     "branch parity: no auto-jump").
4. Tell the user the scope in a few lines (files on each side, endpoints) and the cost: about 3
   finders per round, 2–4 rounds, 2–3 agents per new finding, one critic and one triage agent,
   so typically 20–40 agents. Launch on a yes.

## Phase 1: the workflow

```
Workflow({ name: "audit-feature", args: { repo: "<abs repo>", run: "<abs RUN>", feature: "<feature>", pin: "<PIN>", ref: "web" | "<abs path>", maxRounds: <--rounds N, default 4> } })
```

It runs, all read-only:
- **Find**: three finders per round (contract, behaviour parity, dead wiring). Each round is told
  what earlier rounds found and looks for what they missed. A dedupe step keeps only new defects.
- **Verify**: every new finding gets two refuters, one on our code and one on the server or
  reference. Both must fail to refute it. A split goes to a judge.
- It stops after **two rounds in a row add no surviving finding**, or at `maxRounds` (logged).
- **Critic**: one agent reviews what was traced and checks what wasn't. Its findings are verified
  the same way.
- **Triage**: merges same-root findings, marks duplicates against `known.md`, ranks, writes
  `findings.json` and `report.md`.

A usage-limit cut: relaunch with `resumeFromRunId` and the same args.

## Phase 2: check and report (you)

1. Totals in `report.md` must match the workflow's return. If it hit the round cap, say the tail
   may not be exhausted.
2. Spot-check the top findings yourself: open the cited lines on both sides. Drop or correct
   anything wrong, and say so.
3. Show the user the ranked table: id, severity, kind, title, duplicate. Then the missing
   features in one line each, and the refuted count.
4. Ask which to file. Default: every non-duplicate bug, highest severity first. Missing features
   are offered last as `[Feature]:` issues, or left for `/sync-upstream audit --full` or
   `/ux-overhaul` if the user prefers.

## Phase 3: the filing loop (you)

For each finding the user wants filed, in order:
1. Re-read the cited code once more and write `$RUN/drafts/<id>.md` in the format in
   `reference.md`. Title, labels and body.
2. Show the title, labels and full body. **Stop and wait.**
3. On approval ("file it", "lgtm"): `gh issue create --title "<title>" --label bug --label "priority: <p>" [--label well-scoped] --body-file $RUN/drafts/<id>.md`.
   Append `{id, number, url}` to `$RUN/filed.json` and print the URL.
4. On edits, revise and show again. On "skip", record it as skipped. On "merge with F3", fold it
   into that draft.
5. Go to the next one.

When the loop ends, list what was filed and skipped. If the run found bugs the user chose not to
file, offer to record them in memory as unfiled so a later audit's `known.md` picks them up.
