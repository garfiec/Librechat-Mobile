---
name: audit-parity
description: >
  Audit Switchboard against the pinned LibreChat server and a reference client (the upstream web
  client by default, or another client with --ref). By default sweeps the WHOLE codebase area by
  area in multi-agent (ultracode) workflows; --feature or --areas narrows it. Finds API contract
  bugs (status codes and error codes we mishandle, request/response fields, pagination, repeats),
  behaviour gaps against the reference (gates, honoured settings, parsing, recovery, Android vs
  iOS) and dead wiring (unreachable screens, unread prefs, stubs, failures shown as empty data).
  Finders loop until two rounds add nothing, every finding is verified adversarially, then the
  lead walks the user through filing issues one at a time. Use when asked "what's broken?",
  "where do we diverge from the web?", after a run of field reports, before a release, or for one
  feature that feels thin or flaky. Not for absorbing new upstream releases: that is
  /sync-upstream.
allowed-tools: Bash, Read, Glob, Grep, Write, Workflow, Artifact, Skill, AskUserQuestion
argument-hint: "[--feature <name>] [--areas a,b,...] [--ref web|<path to another client>] [--rounds N] [--plan]"
---

# Parity audit

**How this differs from `/sync-upstream`.** Sync reads the *change* between two upstream
versions and ports it. This skill reads what we already ship, at the current pin, whatever
version we're on, and finds what is wrong with it. Defects older than the last sync never reach
sync's diff; this is where they surface.

**`reference.md` is the method**: the three lenses, scope, known issues, evidence rules, what is
not a finding, severity and the issue format. The workflow hands it to every agent. Read it
before Phase 0.

## Modes

| Invocation | Scope | Runs |
|---|---|---|
| `/audit-parity` | every area in the table below | 3 workflow batches, several hundred agents each |
| `/audit-parity --areas share,search` | those areas | one workflow, about 20–60 agents per area |
| `/audit-parity --feature <name>` | one feature, scoped by you | one workflow, about 20–60 agents |

The full sweep is the default and runs without a cost gate; invoking the skill is the opt-in.
Tell the user the batches and the estimate in one line, then launch. `--plan` stops after
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

`RUN=/Users/garfie/Documents/code/librechat/.audit-parity/<yyyy-mm-dd>-<scope>`, where `<scope>`
is `all`, `areas-<a>-<b>` or the feature slug. Outside the repo, so worktree cleanup and a session
ending don't lose it. If the directory exists, add `-2`, `-3`… unless the user is resuming that
run's filing loop. Always pass it expanded.

```
areas.json  known.md  issues.json
areas/<id>/scope.md  areas/<id>/findings.json      written by the workflow's agents
result-1.json ...                                  each batch's workflow return, saved by you
report.md                                          written by the report agent (or the fallback)
findings.json                                      assembled by scripts/assemble.py
drafts/F1.md ...  filed.json                       the filing loop
```

## Areas

The default sweep. Seeds are starting points for the scoper; check they still exist (`ls`) and
add an area for any new `feature/*` module or `*Api.kt` the table doesn't cover.
`GitHubReleasesApi.kt` is deliberately out: it talks to GitHub for the in-app update check, not
to LibreChat.

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
   - Unfiled bugs from memory:
     `grep -il unfiled /Users/garfie/.claude/projects/-Users-garfie-Documents-code-librechat-LibreChat-Android/memory/*.md`,
     then copy each listed bug in one line with its memory file.
   - Entries in `.claude/skills/sync-upstream/reference/upstream-backend-gaps.md`.
   - Deliberate divergences from the `CLAUDE.md` files and memory (for example "branch parity:
     no auto-jump", "app-bar model selector removed").
4. **Batches.** The workflow runtime stops a run at 1000 agents, and the script splits its own cap
   (`agentCap`, default 950) evenly across the areas in that run. Eight areas per run gives each
   about 118 agents, enough for 4 rounds and 30+ verified findings. So:
   - full sweep: split the areas into batches of at most 8 (23 areas → 8, 8, 7);
   - `--areas` or `--feature`: one batch.
5. **Estimate and launch.** Per area: one scoper, 3 finders and a dedupe per round for 2–4
   rounds, 2–3 agents per new finding, one critic, one triage. That is about 20–60 agents per
   area; a full sweep is roughly 500–1400 agents and a very large token bill. Tell the user the
   batches and estimate in one line, then launch. With `--plan`, show the areas and batches and
   stop.

## Phase 1: the workflow

Run the batches **one after another**. For each batch `n` of `N`:

```
Workflow({ name: "audit-parity", args: {
  repo: "<abs repo>", run: "<abs RUN>", pin: "<PIN>", ref: "web" | "<abs path>",
  areas: <this batch's entries, parsed>, maxRounds: <--rounds, default 4>,
  report: <n == N>,
  // last batch of a multi-batch sweep only, from result-1..N-1.json:
  priorIndex: [...all earlier index], priorRuns: [...all earlier runs],
  priorLeads: [...all earlier leads], priorRefuted: [...all earlier refuted]
} })
```

Pass arrays as parsed values, not strings. Save each return to `$RUN/result-<n>.json` before
anything else.

Per area, as a pipeline (areas don't wait for each other):
- **Scope**: an agent writes `areas/<id>/scope.md`, mapping our files, the endpoints, the server
  handlers and middleware at the pin, the reference client's calls, hooks and components, and
  reference calls we make no equivalent of.
- **Find**: three finders per round (contract, behaviour parity, dead wiring), with no cap. Each
  round is told everything found so far, refuted findings included, so a refuted finding is not
  re-examined. A dedupe step keeps only new defects. Missing features go to a leads list and are
  not verified.
- **Verify**: two refuters per new finding, one on our code and one on the server or reference;
  findings with no upstream citation get two checks on our own code. A split goes to a judge.
- The area stops after **two complete rounds in a row** (all three finders ran) add no surviving
  finding, at `maxRounds`, or when its agent share can't fund another round.
- **Critic**: checks what the rounds didn't trace. Its findings are verified the same way.
- **Triage**: merges same-root findings, marks `duplicate_of` against `known.md` (open issue, or
  closed issue that regressed), and writes `areas/<id>/findings.json`.

On the last batch a **Report** agent decides cross-area merges, shared roots and the rank order,
and writes `report.md`.

### What the workflow returns, and how it fails

The return has `agentsUsed`, `sharePerArea`, `failedAreas`, `preMerge` totals, `postMerge`
totals (from the report agent, or null), `ranking` (`order`, `groups`, `shared_roots`), `index`
(one short entry per finding), `runs` (per area: rounds, agents, `exhausted`, `budgetStop`,
verdict counts, `triaged`), `leads`, `refuted` and `unmerged`.

Every failure degrades rather than throws, and is logged:
- an agent that crashes or would exceed the budget returns nothing;
- a failed **scoper**: the area's finders map it from the seeds;
- a failed **dedupe**: every candidate goes through, so expect some duplicates;
- **verifiers** that didn't run: the finding is kept as `UNVERIFIED`, never counted as refuted;
- a failed **triage**: the area's findings come back unmerged in `unmerged` and no
  `areas/<id>/findings.json` is written;
- a failed **report**: no `report.md` and `ranking` is null.

A usage-limit cut: relaunch that batch with `resumeFromRunId` and the same args; finished agents
replay from cache.

## Phase 2: assemble, check and report (you)

1. `python3 .claude/skills/audit-parity/scripts/assemble.py $RUN`. It merges the per-area files
   and any `unmerged` findings, applies the report's merge groups and order, numbers findings
   F1, F2 …, writes `findings.json`, and writes a fallback `report.md` if the report agent
   failed. It prints the post-merge totals and flags a mismatch with the report's totals.
2. Name every gap: failed areas, areas not triaged, areas that hit the round cap or stopped on
   budget, and the `UNVERIFIED` count. Don't report full coverage when any exist.
3. Spot-check the top findings yourself: open the cited lines on both sides. Drop or correct
   anything wrong in `findings.json`, and say so.
4. Report: totals by kind, severity and area; the shared roots; the ranked non-duplicate
   findings (id, severity, kind, area, title); missing features in one line each; the refuted
   count. For a full sweep, also publish `report.md` as an artifact (load `artifact-design`
   first) so the list is easy to scan.
5. Ask which to file. Default: high and medium non-duplicate bugs, highest first, a shared root
   as one issue. Offer the lows afterwards as one decision. Missing features are offered last as
   `[Feature]:` issues, or left for `/sync-upstream audit --full` or `/ux-overhaul`.

## Phase 3: the filing loop (you)

For each finding the user wants filed, in order:
1. Re-read the cited code once more and write `$RUN/drafts/<fid>.md` in the format in
   `reference.md`. Title, labels and body.
2. Show the title, labels and full body. **Stop and wait.**
3. On approval ("file it", "lgtm"):
   `gh issue create --title "<title>" --label <bug|enhancement> --label "priority: <p>" [--label well-scoped] --body-file $RUN/drafts/<fid>.md`.
   Append `{fid, number, url}` to `$RUN/filed.json` and print the URL.
4. On edits, revise and show again. On "skip", record it as skipped. On "merge with F3", fold it
   into that draft. On "stop", end the loop; `filed.json` lets a later session resume it.
5. Go to the next one.

When the loop ends, list what was filed and skipped. If the run found bugs the user chose not to
file, offer to record them in memory as unfiled so a later audit's `known.md` picks them up.
