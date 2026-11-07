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
  lead walks the user through filing them one at a time, as drafts on the Switchboard Roadmap
  board (a repo issue only when the user picks one). Use when asked "what's broken?",
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
not a finding, severity and the ticket format. The workflow hands it to every agent. Read it
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

- **Read-only on code.** The skill ends at filed tickets. No fixes, branches or PRs.
- **Upstream at the pin only**: `git -C upstream show "<pin>:<path>"`. The submodule checkout is
  often not the pin.
- **Findings go on the board, not the Issues page.** File each one as a draft item on the public
  [Switchboard Roadmap](https://github.com/users/garfiec/projects/2) (project #2). The Issues page
  is kept for outside reports and bugs users would hit and search for, so a repo issue is filed
  only when the user picks "issue" for that finding.
- **Security findings go to neither.** The board is public too. Flag them to the user and leave
  the channel to them (`SECURITY.md`).
- **One ticket at a time.** Draft, show, wait for the user, file, next. Never file in bulk.
- **Third-party `--ref` stays out of tickets.** If the reference is another client, tickets
  describe the bug from our code and the server alone. The upstream web client may be cited.
- No device testing unless the user asks. Tickets say "found by reading the code" where it matters.

## Run directory

`RUN=/Users/garfie/Documents/code/librechat/.audit-parity/<yyyy-mm-dd>-<scope>`, where `<scope>`
is `all`, `areas-<a>-<b>` or the feature slug. Outside the repo, so worktree cleanup and a session
ending don't lose it. If the directory exists, add `-2`, `-3`… unless the user is resuming that
run's filing loop. Always pass it expanded.

```
areas.json  known.md  issues.json  board.json
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

Each area's findings default to a board **Workstream**: auth, account and session →
`Accounts & sign-in`; config, keys, traces and settings → `Foundations`; everything else →
`Chat`. A finding about the OS rather than the server (share sheet, notifications, background
work, platform APIs) goes to `OS integration` whatever its area.

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
   - `gh issue list --repo garfiec/Librechat-Mobile --state all --limit 2000 --json number,title,state,stateReason > $RUN/issues.json`.
   - `gh project item-list 2 --owner garfiec --limit 2000 --format json > $RUN/board.json`.
   - List every board item as `[draft] title (Status)`, adding `— moved from #N` when its body
     starts `Migrated from #N`. Then list every issue as `#N [state] title`, writing
     `[moved to board]` as the state for any issue that a draft says it was moved from. Those
     issues were closed as not planned, but the work is still open: a finding that matches one
     is a duplicate of the draft, not a regression.
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

Run the batches **one after another**. For each batch `n` of `N`, first write the args to
`$RUN/args-<n>.json`, then launch with that file's contents pasted unchanged:

```
Workflow({ name: "audit-parity", args: {
  repo: "<abs repo>", run: "<abs RUN>", pin: "<PIN>", ref: "web" | "<abs path>",
  areas: <this batch's entries, parsed>, maxRounds: <--rounds, default 4>,
  report: <n == N>,
  // last batch of a multi-batch sweep only, from result-1..N-1.json:
  priorIndex: [...all earlier index], priorRuns: [...all earlier runs],
  priorLeads: [...all earlier leads], priorRefuted: [...all earlier refuted],
  // optional: stopAt: <agents>, pause at the next round/critic/triage/report boundary once this
  // many agents have run (cached replays count); resume with a higher value
} })
```

Pass arrays as parsed values, not strings. The workflow logs `args fingerprint <hash>` first;
write it, the run ID and the tool result's Script file path to `$RUN/RESUME.md`.
Save each return to `$RUN/result-<n>.json` before anything else. A return with `halted` set has
no findings: resume it (below) until `halted` is null, overwriting `result-<n>.json`, before the
next batch or Phase 2.

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
- **Triage**: merges same-root findings, marks `duplicate_of` against `known.md` (open issue, board
  draft, or closed issue that regressed), and writes `areas/<id>/findings.json`.

On the last batch a **Report** agent decides cross-area merges, shared roots and the rank order,
and writes `report.md`.

### What the workflow returns, and how it fails

The return has `agentsUsed`, `sharePerArea`, `failedAreas`, `preMerge` totals, `postMerge`
totals (from the report agent, or null), `ranking` (`order`, `groups`, `shared_roots`), `index`
(one short entry per finding), `runs` (per area: rounds, agents, `exhausted`, `budgetStop`,
verdict counts, `triaged`), `leads`, `refuted` and `unmerged`.

**An agent that fails halts the run** (almost always the usage limit, which fails everything
after it): no new agent starts, agents already running finish and are cached, and the workflow
returns `{halted: "<reason>", fingerprint, agentsUsed, areas}` with no findings. A `stopAt` pause
returns the same shape.

**Resuming.** In the same session:
`Workflow({ scriptPath: "<the run's Script file>", resumeFromRunId: "<run ID>", args: <args-<n>.json, plus expectFingerprint: "<hash>"> })`.
Every agent whose prompt is unchanged replays from cache; only what never finished runs. The
prompts embed the args, so a resume whose args differ re-runs every agent whose prompt they reach
(`repo`, `run`, `pin` or `ref`: all of them): `expectFingerprint` makes that throw before any agent
starts. Wait for the limit to
reset before resuming; a resume into a spent limit halts on its first agent. If a resume with limit
to spare halts on the same label again, that agent fails for another reason: resume once more with
`haltOnFailure: false` added (not part of the fingerprint) so it degrades instead. Finders and the
critic keep a work log in `areas/<id>/log/`, so one cut off mid-run resumes from its log instead
of re-reading everything (`workLog: false` turns this off, only for runs launched before it
existed). The cache is per session: a new session has to start the batch over, and first deletes
`areas/*/log/`, or each finder takes the old session's log for its own cut-off attempt.

**Cost.** Finders dominate (three per round, each about ten verifiers' worth). To spread a long
batch over several usage windows, launch with `stopAt` and resume with a higher one each window.

With `haltOnFailure: false`, or when the agent budget runs out, failures degrade instead, and
are logged:
- an agent that crashes or would exceed the budget returns nothing;
- a failed **scoper**: the area's finders map it from the seeds;
- a failed **dedupe**: every candidate goes through, so expect some duplicates;
- **verifiers** that didn't run: the finding is kept as `UNVERIFIED`, never counted as refuted;
- a failed **triage**: the area's findings come back unmerged in `unmerged` and no
  `areas/<id>/findings.json` is written;
- a failed **report**: no `report.md` and `ranking` is null.

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
   as one ticket. Offer the lows afterwards as one decision. Missing features are offered last as
   `Kind: Feature` drafts, or left for `/sync-upstream audit --full` or `/ux-overhaul`.
6. If several findings share one root or would be fixed together, propose an **Epic** for them:
   an existing option on the board, or a new one, which the user must approve. A new epic also
   gets a `📌 <Epic> — overview` draft (goal, order, "start here") filed first.

## Phase 3: the filing loop (you)

For each finding the user wants filed, in order:
1. Re-read the cited code once more and write `$RUN/drafts/<fid>.md` in the format in
   `reference.md`.
2. Show the title, the full body and the fields: Priority (from severity), Workstream (from the
   area, see Areas), Kind (`Bug`, or `Feature` for new capability), Platform (`Both`, `Android`
   or `iOS`), Epic if any, Status `Backlog`, Source `Audit`. Say **draft** as the destination,
   or recommend **issue** only for a bug users would hit and search for. **Stop and wait.**
3. On approval ("file it", "lgtm"), as a draft:
   `python3 .claude/skills/audit-parity/scripts/file_draft.py --title "<title>" --body-file $RUN/drafts/<fid>.md --priority <P> --workstream "<W>" --kind <K> --platform <Pl> [--epic "<E>"]`.
   A new Epic option has to exist first:
   `gh project field-list 2 --owner garfiec`, then add it to the Epic field in the board's
   settings (ask the user) or with the `updateProjectV2Field` GraphQL mutation, passing every
   existing option back with the new one. Passing only the new one deletes the others and clears
   them from every item.
   As an issue, only when the user picked it:
   `gh issue create --repo garfiec/Librechat-Mobile --title "<[Bug]: or [Feature]:> <title>" --label <bug|enhancement> --label "priority: <p>" [--label well-scoped] --body-file $RUN/drafts/<fid>.md`,
   then put it on the board with its fields:
   `file_draft.py --issue-url <issue url> --priority <P> --workstream "<W>" --kind <K> --platform <Pl> [--epic "<E>"]`.
   Append `{fid, dest: "draft"|"issue", id, url}` to `$RUN/filed.json` and print the URL.
4. On edits, revise and show again. On "skip", record it as skipped. On "merge with F3", fold it
   into that draft. On "stop", end the loop; `filed.json` lets a later session resume it.
5. Go to the next one.

When the loop ends, list what was filed and skipped. If the run found bugs the user chose not to
file, offer to record them in memory as unfiled so a later audit's `known.md` picks them up.
