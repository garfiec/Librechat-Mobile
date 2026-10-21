---
name: audit-parity
description: >
  Audit Switchboard against the pinned LibreChat server and a reference client (the upstream web
  client by default, or another client with --ref). By default sweeps the WHOLE codebase area by
  area in a multi-agent (ultracode) workflow; --feature narrows it to one feature. Finds API
  contract bugs (status codes and error codes we mishandle, request/response fields, pagination,
  repeats), behaviour gaps against the reference (gates, honoured settings, parsing, recovery,
  Android vs iOS) and dead wiring (unreachable screens, unread prefs, stubs, failures shown as
  empty data). Finders loop until two rounds add nothing, every finding is verified
  adversarially, then the lead walks the user through filing issues one at a time. Use when asked
  "what's broken?", "where do we diverge from the web?", after a run of field reports, before a
  release, or for one feature that feels thin or flaky. Not for absorbing new upstream releases:
  that is /sync-upstream.
allowed-tools: Bash, Read, Glob, Grep, Write, Workflow, Artifact, AskUserQuestion
argument-hint: "[--feature <name>] [--areas a,b,...] [--ref web|<path to another client>] [--rounds N] [--plan]"
---

# Parity audit

**How this differs from `/sync-upstream`.** Sync reads the *change* between two upstream
versions and ports it. This skill reads what we already ship, at the current pin, whatever
version we're on, and finds what is wrong with it. Defects older than the last sync never reach
sync's diff; this is where they surface.

**`reference.md` is the method**: the three lenses, evidence rules, what is not a finding,
severity and the issue format. The workflow hands it to every agent. Read it before Phase 0.

## Modes

| Invocation | Scope | Workflow scale |
|---|---|---|
| `/audit-parity` | every area in the table below | ultracode: hundreds of agents |
| `/audit-parity --areas share,search` | those areas | about 15–40 agents per area |
| `/audit-parity --feature <name>` | one feature, scoped by you | about 15–40 agents |

The full sweep is the default and runs as a dynamic workflow without a cost gate; invoking the
skill is the opt-in. Tell the user the area count and estimate, then launch. `--plan` stops after
Phase 0 so the user can review the areas first.

## Rules

- **Read-only on code.** The skill ends at filed issues. No fixes, branches or PRs.
- **Upstream at the pin only**: `git -C upstream show "<pin>:<path>"`. The submodule checkout is
  often not the pin.
- **One issue at a time.** Draft, show, wait for the user, file, next. Never file in bulk.
- **Third-party `--ref` stays out of issues.** If the reference is another client, issues
  describe the bug from our code and the server alone. The upstream web client may be cited.
- No device testing unless the user asks. Issues say "found by reading the code" where it matters.

## Run directory

`RUN=~/.claude/audit-parity/<yyyy-mm-dd>-<all|feature-slug>/`. Outside the repo and the
scratchpad, so it survives the session. Pass it to the workflow **expanded** (`/Users/…`, never
`~`).

```
areas.json  known.md  issues.json  areas/<id>/scope.md ...  findings.json  report.md  drafts/F1.md ...  filed.json
```

## Areas

The default sweep. Seeds are starting points for the scoper; check they still exist (`ls`) and
add an area for any new `feature/*` module or `*Api.kt` the table doesn't cover.

| id | area | seeds |
|---|---|---|
| auth | login, register, OAuth/SSO, forgot password, email verification, terms | `AuthApi`, `feature/auth` |
| account | profile, avatar, 2FA, backup codes, delete account | `UserApi`, account settings |
| session | token refresh, logout, multi-account, session expiry | `core/network/client`, `core/data` auth storage |
| convos | conversation list, rename, delete, archive, tags, import/export, offline cache | `ConversationsApi`, `TagsApi`, `feature/conversations` |
| messages | message tree, branches, edit, regenerate, feedback, fork | `MessagesApi`, chat viewmodel |
| send | sending, streaming, resume, stop, queued turns, steering | `ChatApi`, `core/network/sse`, `QueuedTurnsApi` |
| render | markdown, code, math, citations, artifacts, tool-call and reasoning display | `feature/chat` components |
| models | endpoints, models, model specs, presets, favourites, parameters | `ConfigApi`, `PresetsApi`, `FavoritesApi` |
| agents | agent marketplace, agent editor, tools, actions, subagents | `AgentsApi`, `AgentToolsApi`, `SubagentsApi`, `feature/agents` |
| files | upload, attachments, file manager, images, code interpreter files | `FilesApi`, `FilesExtApi`, `feature/files` |
| share | shared links: create, view, manage | `ShareApi` |
| search | conversation and message search | `SearchApi` |
| prompts | prompt library, prompt groups, slash commands | `PromptsApi`, `feature/chat/prompts` |
| memories | memories list, edit, opt-out | `MemoriesApi` |
| projects | projects, project chats | `ProjectsApi` |
| mcp | MCP servers, OAuth, tools | `McpApi` |
| skills | skills | `SkillsApi`, `feature/skills` |
| schedules | scheduled prompts | `SchedulesApi`, `feature/schedules` |
| keys | user-provided keys, API keys, endpoint tokens | `KeysApi`, `ApiKeysApi`, `EndpointTokenApi` |
| speech | text to speech, speech to text, read aloud | `SpeechApi`, `feature/chat/audio` |
| config | startup config, interface flags, balance, banner, roles and permissions | `ConfigApi`, `BalanceApi`, `BannerApi`, `RolesApi`, `PermissionsApi` |
| traces | traces | `TracesApi` |
| settings | local settings and preferences: does every row do something? | `feature/settings`, `AppPreferences` |

## Phase 0: areas and known issues (you, with Bash)

1. `PIN=$(grep '^commit=' UPSTREAM_VERSION | cut -d= -f2)`.
2. **Areas.** Write `$RUN/areas.json` as `[{id, name, seeds}]`.
   - Full sweep: the table above, adjusted after the `ls` check.
   - `--areas`: those rows.
   - `--feature <name>`: one entry you write yourself. Grep the feature's nav keys, string
     resources and API paths so `seeds` names its screens, ViewModels and Api files.
3. **Write `$RUN/known.md`**:
   - `gh issue list --state all --limit 2000 --json number,title,state > $RUN/issues.json`, then
     list every issue as `#N [state] title`.
   - Unfiled bugs from the memory directory (grep for "unfiled").
   - Entries in `.claude/skills/sync-upstream/reference/upstream-backend-gaps.md`.
   - Deliberate divergences from the `CLAUDE.md` files and memory (for example "branch parity:
     no auto-jump", "app-bar model selector removed").
4. **Estimate and launch.** Per area: one scoper, 3 finders and a dedupe per round for 2–4
   rounds, 2–3 agents per new finding, one critic, one triage. That is about 15–40 agents per
   area. For the full sweep, pass `maxRounds: 3` unless the user gave `--rounds`: the workflow
   caps a run at 1000 agents. Tell the user the area count and the estimate in one line, then
   launch. With `--plan`, show the areas and stop.

## Phase 1: the workflow

```
Workflow({ name: "audit-parity", args: { repo: "<abs repo>", run: "<abs RUN>", pin: "<PIN>", ref: "web" | "<abs path>", areas: <parsed areas.json>, maxRounds: <N> } })
```

Pass `areas` as the parsed array, not a string. Per area, as a pipeline (areas don't wait for
each other):
- **Scope**: an agent writes `areas/<id>/scope.md`, mapping our files, the endpoints, the server
  handlers and middleware at the pin, the reference client's calls, hooks and components, and
  reference calls we make no equivalent of.
- **Find**: three finders per round (contract, behaviour parity, dead wiring), with no cap. Each
  round is told what earlier rounds found. A dedupe step keeps only new defects. The area stops
  after **two rounds in a row add no surviving finding**, or at `maxRounds` (logged).
- **Verify**: two refuters per new finding, one on our code and one on the server or reference;
  findings with no upstream citation get two checks on our own code. A split goes to a judge.
- **Critic**: checks what the rounds didn't trace. Its findings are verified the same way.
- **Triage**: merges same-root findings and marks duplicates against `known.md`.

Then a **Report** agent merges across areas, ranks, names shared roots and writes
`findings.json` and `report.md`.

A usage-limit cut: relaunch with `resumeFromRunId` and the same args; finished agents replay
from cache.

## Phase 2: check and report (you)

1. Totals in `report.md` must match the workflow's return. Name any failed areas and any that hit
   the round cap; don't report full coverage when they exist.
2. Spot-check the top findings yourself: open the cited lines on both sides. Drop or correct
   anything wrong, and say so.
3. Report: totals by kind, severity and area; the shared roots; the ranked non-duplicate
   findings (id, severity, kind, area, title); missing features in one line each; the refuted
   count. For a full sweep, also publish `report.md` as an artifact so the list is easy to scan.
4. Ask which to file. Default: every non-duplicate bug, highest severity first, shared roots as
   one issue each. Missing features are offered last as `[Feature]:` issues, or left for
   `/sync-upstream audit --full` or `/ux-overhaul`.

## Phase 3: the filing loop (you)

For each finding the user wants filed, in order:
1. Re-read the cited code once more and write `$RUN/drafts/<id>.md` in the format in
   `reference.md`. Title, labels and body.
2. Show the title, labels and full body. **Stop and wait.**
3. On approval ("file it", "lgtm"): `gh issue create --title "<title>" --label bug --label "priority: <p>" [--label well-scoped] --body-file $RUN/drafts/<id>.md`.
   Append `{id, number, url}` to `$RUN/filed.json` and print the URL.
4. On edits, revise and show again. On "skip", record it as skipped. On "merge with F3", fold it
   into that draft. On "stop", end the loop; `filed.json` lets a later session resume it.
5. Go to the next one.

When the loop ends, list what was filed and skipped. If the run found bugs the user chose not to
file, offer to record them in memory as unfiled so a later audit's `known.md` picks them up.
