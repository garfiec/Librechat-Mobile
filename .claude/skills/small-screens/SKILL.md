---
name: small-screens
description: >
  Find and fix UI that breaks on small screens, at large font sizes and in long translations:
  clipped labels, text squeezed to a letter per line, button rows that run off the edge, sheets
  and dialogs that can't scroll, content under the keyboard or system bars. Three modes. `check`
  reviews the UI a branch changed, before its PR. `audit` sweeps every UI file with a multi-agent
  workflow and groups the findings into fix batches. `fix` implements batches with before/after
  @Preview renders for the user to review. Use when a field report shows clipped or squeezed UI,
  when a feature adds screens, dialogs or long strings, before a release, or when asked whether
  the app works on a 320dp phone, an iPhone SE, at 2x font or in German.
allowed-tools: Bash, Read, Glob, Grep, Write, Edit, Workflow, Artifact, AskUserQuestion
argument-hint: "check [<paths>|--base <ref>] | audit [--module <path>]... | fix <B1,B2,...> [--run <dir>]"
---

# Small screens

"Small" means small **effective width in dp**. A narrow phone, a large font scale and a
translation longer than English each shrink it, and they stack. Many breaks happen in German at
1.0x on an ordinary phone.

**`reference.md` is the method.** It holds:
- the test matrix and the trigger/control convention;
- how to judge a finding and the severity rubric;
- the defect classes C1–C11 with the fix each one gets;
- the approaches the user rejected;
- the rules every fix follows;
- what renders can't show.

Read it before any mode. The workflows hand it to every agent, so changing a rule there changes it everywhere.

## Rules that hold in every mode

- **Renders, not simulators or devices.** Layout is checked with headless renders of committed
  `@PreviewNarrowScreens` previews (`harness/` + `scripts/render.py`). Run a device or simulator
  pass only when the user asks for one. Name what renders can't show (WebViews, popups, `iosMain`,
  the keyboard) so the user can choose to check it.
- **The user sees before/after renders of every fix before any PR exists.** Batches stay on
  local branches until the user has reviewed the review page and made the design calls.
- **Plan before code.** Present design choices as options with a recommendation, including
  "leave it". Never shrink fonts to make things fit.
- **The harness is never committed.**
  - `render.py apply` edits a module's build file and adds a generated test.
  - `render.py revert` restores the build file byte for byte and deletes the generated test.
  - It doesn't undo the temporary `internal` on preview hosts and fixtures; revert those by hand.
  - `render.py status <repo>` must print nothing before any commit.
- Full `:<module>:detekt`, never `detektMetadataCommonMain` alone.
- One plain git command per Bash call; the session hook refuses compound ones.
- Commits and PRs carry no co-author, session or generated-by lines.

## Run directory

`audit` and `fix` keep their state in a run directory, `~/.claude/small-screens/<yyyy-mm-dd>/`. It is outside the repo and outside the session scratchpad, so it survives worktree cleanup and a session ending mid-review.

**Always pass it to workflows and scripts as an absolute, expanded path** (`/Users/…`, never `~`). Agents in other worktrees receive it as a string, and Python's `open()` doesn't expand `~`.

Its layout:

```
ledger.json  leads.txt  worst-strings.tsv  shards.json  shards/S01.md ...
audit.md  critic.md  critic-notes.json  batches/B1.md ...
fixes/B1/{cases-*.json, before/, after/, meta.json, sheet-1.png, branch.patch, result.json}
site/   (the review page)
```

---

## `check`: review the UI a change touched

Use it on a feature branch, before `/create-pr`. Run it yourself, without the workflow. It costs a
few files' reading, not a fleet.

1. **Scope.**
   - Changed UI files: `git diff --name-only <base>...HEAD`, with base `origin/develop` by default, filtered to `.kt` files that contain `@Composable` or UIKit views. Or use the paths the user gave.
   - Run `scripts/worst_strings.py <repo> <tmp>/worst-strings.tsv` so translations are quoted, not guessed.
2. **Read each file completely through both lenses**, using the reference's classes and method:
   - horizontal fit: C1, C2, C3, C5, C8, C10;
   - vertical fit and containers: C4, C6, C7, C9.

   For a changed component, grep its callers. Write down the arithmetic.
3. **Try to refute each finding** as the reference says, and drop what doesn't survive.
4. **Report** a table in the audit's format: id `K<n>`, sev, cls, file:line, title, min trigger, fix hint. Say what you couldn't judge.
5. If the user wants them fixed, do it the `fix` way, one batch, yourself:
   1. previews commit;
   2. before renders;
   3. the fix;
   4. after renders;
   5. sheet;
   6. gates.

   Show the sheet. A single sheet can go in the reply instead of a page.

## `audit`: sweep the whole app

**Scale first.** The full sweep of about 440 files took 112 agents and about 13M tokens. Before launching, tell the user the file count and a cost estimate in that proportion. Offer `--module` to narrow it. Launch only on a yes. The Workflow tool needs that opt-in anyway.

**Phase 0, done yourself with direct Bash.** `RUN=~/.claude/small-screens/<date>`.
1. `scripts/ledger.py <repo> $RUN/ledger.json [--prior <previous audit.md>] [--module …]`. A previous run's `audit.md` is the prior: readers tag repeats of known rows instead of re-reporting them as new.
2. `scripts/worst_strings.py <repo> $RUN/worst-strings.tsv`
3. `scripts/scan.py <repo> $RUN/leads.txt`
4. `scripts/shard.py $RUN/ledger.json $RUN/leads.txt $RUN` writes `shards.json` and `shards/`.

**Phase 1: the workflow.**
- Run it with `Workflow({name: "small-screens-audit", args: {repo, run, shards, prior?}})`.
- Pass `shards` as the parsed array from `shards.json`, not as a string.
- It reads every shard through two lenses, verifies each shard adversarially, and writes `$RUN/audit.md` in fixed numbered sections, then `$RUN/critic.md`.
- **A usage-limit cut:** relaunch with `resumeFromRunId` and the same script and args. Finished agents replay from cache.

**Phase 2, done yourself:**
1. **Check the result against the doc.** Totals in `audit.md` must match the workflow's return. If any files are uncovered or any shards failed, list them; don't report full coverage.
2. **Assign the critic's gaps** to batches in `$RUN/critic-notes.json` (`{"B5": "…"}`).
3. Run `scripts/briefs.py $RUN --notes $RUN/critic-notes.json`.
4. **Report to the user:**
   - totals by severity;
   - the shared roots;
   - the batch list, ordered by reach × severity;
   - the top findings;
   - every open design decision, each with options.

   The audit ends there. Fixing is a separate yes.

## `fix`: implement batches

**Before launching:**
1. **Launch in waves of at most 3 batches.** Each one builds in its own worktree, and more than three exhausted memory.
2. **Fence overlapping batches.** Batches overlap on files, often. Give a batch an `extra` paragraph that names what a sibling owns ("Do NOT touch the message action row; B2 owns it"). Findings handled elsewhere end as `covered-elsewhere`.
3. **Pin `base`** to `git rev-parse origin/develop`. Worktree agents diff against it, because the local `develop` ref is often stale.
4. **Launch:** `Workflow({name: "small-screens-fix", args: {run, base, batches: [{id, slug, extra?, resumeFrom?}]}})`.

**What each batch does** (`.claude/workflows/small-screens-fix.js`):
- An implementer works in its own worktree on `fix/small-screen-b<n>-<slug>`:
  1. previews as their own commit;
  2. before renders;
  3. the fixes as Conventional Commits;
  4. after renders with the same names;
  5. contact sheets it reads itself;
  6. gates;
  7. a clean tree.
- An independent reviewer reads only the brief, the patch and the sheets.
- One fix-up round, as new commits on `<branch>-r2`, runs if the review raised anything above a nit.

**If a batch is cut off:**
- Relaunch with `resumeFromRunId`.
- If a batch committed its previews before the cut, pass `resumeFrom: <branch>`; it continues on `<branch>-c`.
- An empty branch with the batch's name blocks a fresh run; the agent then appends `-2`.

**After each wave, done yourself:**
1. **Save each batch's final result** (the fix-up's result if there was one, otherwise the implementer's) to `$RUN/fixes/<B>/result.json`.
2. **Check the claims yourself:**
   - Read the sheets.
   - `git log --format=%B <base>..<branch>` has no attribution lines.
   - `render.py status` in each worktree prints nothing.
   - No finding is marked fixed without a render, unless it is listed in `unrendered`.
3. **Build the review page:**
   1. `scripts/review_page.py $RUN $RUN/site`.
   2. Publish it with the Artifact tool:
      - `file_path` = `site/index.html`;
      - `files` maps each `data/*.json`;
      - the page stays private.
   3. Republish to the same URL after later waves.
4. **Report to the user:**
   - the link;
   - per batch: fixed / partial / skipped;
   - behaviour changes visible on an ordinary 360dp phone;
   - what the renders couldn't show;
   - the decisions waiting on them.

**After the user has reviewed every batch:**
- Ask whether they want one PR or one per batch. Last time it was one.
- **For one PR:**
  1. Branch `fix/small-screen-audit` from a fresh `origin/develop`.
  2. `git merge --no-ff` each final batch branch in batch order.
  3. Resolve conflicts:
     - string files: keep both sides, then check for duplicate keys;
     - code: merge by hand and say what you chose.
  4. Run the full gates: every touched module's detekt, unit tests and iOS compile, plus `:app:assembleFossDebug`.
  5. Install on a device only if the user asks.
  6. Open the PR with `/create-pr`, linking the review page.
- **If develop moved underneath:** it may rewrite code a fix touched. List every fix the rebase dropped or changed; don't let it vanish silently.

---

## Files

| Path | Role |
| --- | --- |
| `reference.md` | The method: matrix, judging, rubric, classes and fixes, rejected approaches, render limits |
| `scripts/ledger.py` | Every UI file, with module, size and whether a prior audit named it |
| `scripts/worst_strings.py` | Longest translation per key (CJK counts as 2) |
| `scripts/scan.py` | Regex leads for readers (hints only) |
| `scripts/shard.py` | ~2,800-line, module-contiguous shards, with leads |
| `scripts/briefs.py` | One brief per batch from `audit.md`, plus critic notes |
| `scripts/render.py` | `apply` / `revert` / `status` for the temporary Robolectric harness |
| `harness/Render.kt.tmpl` | The generated render test: real qualifiers, non-linear font scale, dialog capture, scrolled frame |
| `scripts/sheet.py` | BEFORE \| AFTER contact sheets, paged under 12,000px |
| `scripts/review_page.py`, `review/index.html` | The review page: batch tabs, findings, side-by-side and flip, identical-pair filter |
| `.claude/workflows/small-screens-audit.js` | Read → Verify → Synthesize → Critic |
| `.claude/workflows/small-screens-fix.js` | Implement → Verify → Fix-up, one worktree per batch |

The scripts need Python 3 with Pillow (`sheet.py`, `review_page.py`).
