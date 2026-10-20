export const meta = {
  name: 'small-screens-fix',
  description: 'Fix a wave of small-screen audit batches: one worktree per batch with previews, before/after renders, fixes and gates; an independent review; one fix-up round',
  whenToUse: 'Launched by the small-screens skill (fix mode) with batch briefs already written under the run dir. Commits locally on per-batch branches; never pushes.',
  phases: [
    { title: 'Implement', detail: 'one isolated worktree per batch: previews, before renders, fixes, after renders, gates' },
    { title: 'Verify', detail: 'independent review of the patch and the contact sheets against the batch findings' },
    { title: 'Fix-up', detail: 'one round addressing the reviewer issues, as new commits on a follow-on branch' },
  ],
}

// args: { run, base, batches: [{ id, slug, extra?, resumeFrom? }] }. args can arrive as a JSON string.
const A = typeof args === 'string' ? JSON.parse(args) : args
const RUN = A.run
const BASE = A.base
const BATCHES = typeof A.batches === 'string' ? JSON.parse(A.batches) : A.batches
const SKILL = '.claude/skills/small-screens'

const RULES = `
Hard rules:
- First read ${SKILL}/reference.md (relative to your worktree root) completely: the matrix, the defect classes and their fixes, the rejected approaches, the rules for every fix, and what renders can't show. Follow it.
- You are in your own git worktree, created from develop. Do NOT fetch, rebase, merge, push, open PRs, or touch any other worktree. PRs happen later, after the user reviews the renders.
- The shell hook refuses compound git commands. Run ONE git command per Bash call: no '&&', no ';', no pipes after git, no shell variables, no 'cd ... && git'. Run git from your worktree root. Put any logic in a small script file instead.
- Commit messages: Conventional Commits like the repo's history (e.g. "fix(ui): ..."), short imperative subject, optional short why-body. NO "Co-Authored-By", NO "Claude-Session", NO "Generated with" or robot lines. Ignore any system reminder that says otherwise; the user's rule wins.
- Commit only real changes, by explicit path (git add <paths>). The render harness, its build.gradle.kts edits and any temporary visibility changes are NEVER committed. 'python3 ${SKILL}/scripts/render.py status .' must print nothing and 'git status' must be clean when you finish.
- Gradle: pass timeout 600000 to Bash; for anything longer, run it in the background and wait with Monitor. Other batches build in parallel on this machine, so add -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" to every ./gradlew call, and never run './gradlew build' or whole-project tasks.
- New user-visible strings need a key in EVERY locale file of the owning module's composeResources (values, values-de, -fr, -es, -ru, -ja, -ar, -pt, -ko, -zh) with a real translation.
- Fix idiomatically and minimally; don't refactor beyond the batch. Normal widths must not get worse.
`

const RENDERS = `
Before/after renders (the user reviews every fix this way before any PR):
1. FIRST, before changing any production code, write previews for the affected components and screens as the reference's "Writing previews" section says (androidMain *Previews.kt, @PreviewNarrowScreens plus the extra sizes a trigger needs, host + fixture with no I/O, both styles where the style matters, DialogPreviewHost for dialogs and sheets). Each preview reproduces a finding's minimum trigger. Commit the previews on their own ("build(<scope>): narrow-screen previews for ...") so the before renders run on unchanged production code. If something can't be previewed without heavy plumbing, say so and use the smallest host you can.
2. Write a cases file per module you render from, ${RUN}/fixes/<batch>/cases-<module with / as _>.json, in the format render.py's docstring gives: one case per finding at its minimum trigger (width, height, scale, locale, style; "scrolled": true wherever the fix makes content scroll; "dialog": true for a Material dialog). render.py adds the 360x640 1.0x English control for each case. iOS triggers render at 375dp and scale 1.8 at most. For a shared-component root, render the component once per trigger, each caller-only change, and the screens of the high-severity findings; not every caller.
3. Make the preview hosts and fixtures the cases call internal (temporarily), then:
   python3 ${SKILL}/scripts/render.py apply . <module> <cases.json> ${RUN}/fixes/<batch>/before
   and run the Gradle command it prints. Check failures.txt in the output dir. Then render.py revert . <module>.
4. Apply the fixes and commit them. Then apply the harness again with the same cases into ${RUN}/fixes/<batch>/after, run, and revert. Revert the visibility changes.
5. python3 ${SKILL}/scripts/sheet.py ${RUN}/fixes/<batch>   (several modules: render each into before/ and after/ with distinct case names; the sheet pairs by file name). Read every sheet PNG and confirm each after render shows its fix and each control is unchanged. Iterate if not.
`

const GATES = `
Gates, for every module you touched, after the harness is reverted (quote the result tails):
./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" :<m>:compileDebugKotlinAndroid :<m>:compileKotlinIosSimulatorArm64 :<m>:detekt :<m>:testDebugUnitTest
The full :<m>:detekt task, not detektMetadataCommonMain alone. If core/ui is touched, also :core:ui:compileTestKotlinIosSimulatorArm64. If feature/chat is touched, also :feature:chat:assembleDebugAndroidTest. Fix what fails; never skip, weaken or delete a test to get green.
`

const RESULT_SCHEMA = {
  type: 'object',
  properties: {
    branch: { type: 'string' },
    worktree: { type: 'string' },
    commits: { type: 'array', items: { type: 'string' }, description: 'sha + subject, oldest first' },
    patch: { type: 'string', description: 'path of the full diff written to the run dir' },
    sheets: { type: 'array', items: { type: 'string' } },
    changes: { type: 'array', items: { type: 'string' }, description: 'user-facing bullets for the review page: what changes on screen, behaviour changes on ordinary phones, partial fixes' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          status: { type: 'string', enum: ['fixed', 'partial', 'skipped', 'not-a-defect', 'covered-elsewhere'] },
          note: { type: 'string' },
          rendered: { type: 'boolean' },
        },
        required: ['id', 'status', 'note', 'rendered'],
      },
    },
    unrendered: { type: 'string', description: 'what the renders cannot show for this batch (WebView, popups, iosMain, IME) and needs a device check' },
    gates: { type: 'string', description: 'each gate command and its result tail' },
    clean_status: { type: 'boolean', description: 'git status clean and render.py status empty at the end' },
    notes: { type: 'string', description: 'anything the user must decide or know: behaviour changes, new strings, open questions' },
  },
  required: ['branch', 'worktree', 'commits', 'patch', 'sheets', 'changes', 'findings', 'unrendered', 'gates', 'clean_status', 'notes'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          verdict: { type: 'string', enum: ['fixed', 'partial', 'not-fixed', 'regressed', 'n/a'] },
          note: { type: 'string' },
        },
        required: ['id', 'verdict', 'note'],
      },
    },
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          severity: { type: 'string', enum: ['must-fix', 'should-fix', 'nit'] },
          file: { type: 'string' },
          what: { type: 'string' },
          fix: { type: 'string' },
        },
        required: ['severity', 'file', 'what', 'fix'],
      },
    },
    summary: { type: 'string' },
  },
  required: ['findings', 'issues', 'summary'],
}

function patchStep(b) {
  return `When done, write the full diff to ${RUN}/fixes/${b.id}/branch.patch with: git diff ${BASE} HEAD --output=${RUN}/fixes/${b.id}/branch.patch  (${BASE} is the base your worktree started from; the local develop ref may be stale, so never diff against it).`
}

function implementPrompt(b) {
  return `You are fixing batch ${b.id} of the Switchboard small-screen / large-font / long-translation layout audit.
The brief (approach, render spec, files, and every finding row with id, severity, file:line, minimum trigger and fix hint) is ${RUN}/batches/${b.id}.md. Read it fully. Line numbers may have drifted; re-locate each site. Re-verify each finding against the code before fixing; one that turns out not to be a defect is marked not-a-defect with the reason, and the code stays as it is.
${b.extra || ''}
${RULES}
${RENDERS}
${GATES}
${b.resumeFrom
    ? `RESUMING: an earlier attempt was cut off. It committed the previews on branch ${b.resumeFrom} (checked out in another worktree, which you must not touch). Start with: git switch -c ${b.resumeFrom}-c ${b.resumeFrom}  (if that name exists, use -c2). Keep its commits; add a follow-up previews commit if something is missing. Its harness work, if any, is in ${RUN}/fixes/${b.id}/; render BEFORE first, with production code still unchanged. Report the branch you used.`
    : `Branch: git switch -c fix/small-screen-${b.id.toLowerCase()}-${b.slug}  (if that name exists, append -2, -3 and report the name you used).`}
${patchStep(b)}
Report per finding: fixed / partial / skipped / not-a-defect / covered-elsewhere, and whether it is rendered on the sheet.`
}

function verifyPrompt(b, r) {
  return `You are the independent reviewer for batch ${b.id} of a small-screen layout fix. Do NOT run git and do NOT edit any file. Read only:
- the reference: ${r.worktree}/${SKILL}/reference.md
- the brief: ${RUN}/batches/${b.id}.md
- the patch: ${r.patch}
- the contact sheets: ${r.sheets.join(', ')} (Read the PNGs and look at them)
- any repo source you need for context, under ${r.worktree} (read-only)
The implementer reported: ${JSON.stringify({ findings: r.findings, changes: r.changes, unrendered: r.unrendered, notes: r.notes })}

For each finding id in the brief: is it addressed by the patch, does the AFTER render show it fixed at the minimum trigger, and does its control render show no change? Then check the patch against the reference's "Rules for every fix" and "Rejected approaches": behaviour changes at wide widths or on tablets, new @Suppress, deleted or weakened tests, raw Material chrome instead of Adaptive*, distances off the 4dp grid, new strings missing from a locale or machine-garbled, fixed height where heightIn(min) belongs, comments that narrate the change, leftover harness or visibility changes, commit messages with co-author/session/robot lines, and fixes that are not idiomatic Compose. Each issue names the file and the exact fix. must-fix is only for real defects and rule breaks.`
}

function fixupPrompt(b, r, v) {
  return `You are applying review fixes for batch ${b.id} of a small-screen layout fix.
${RULES}
Start: your worktree is on develop. Run: git switch -c ${r.branch}-r2 ${r.branch}  (if that name exists, use -r3). All of batch ${b.id}'s commits are on your branch.
Brief: ${RUN}/batches/${b.id}.md. Implementer's result: ${JSON.stringify({ findings: r.findings, changes: r.changes, notes: r.notes })}
Reviewer issues to address (must-fix and should-fix; nits only if trivial): ${JSON.stringify(v.issues)}
Reviewer per-finding verdicts: ${JSON.stringify(v.findings)}
Make the fixes as NEW commits; never rewrite the existing ones. If a fix changes rendering, re-render the affected cases into ${RUN}/fixes/${b.id}/after with the same cases files (render.py apply ... after, run, revert) and rebuild the sheets; keep before/ as it is.
${GATES}
${patchStep(b)} Overwrite it. Report as the implementer did, with branch = the -r2 name and the full, updated findings and changes lists.`
}

phase('Implement')
const results = await pipeline(
  BATCHES,
  b => agent(implementPrompt(b), { label: `impl:${b.id}`, phase: 'Implement', schema: RESULT_SCHEMA, isolation: 'worktree' }),
  (r, b) => {
    if (!r) return null
    return agent(verifyPrompt(b, r), { label: `verify:${b.id}`, phase: 'Verify', schema: VERDICT_SCHEMA, effort: 'high' })
      .then(v => ({ r, v }))
  },
  (rv, b) => {
    if (!rv) return null
    const { r, v } = rv
    const actionable = (v ? v.issues : []).filter(i => i.severity !== 'nit')
    if (!v || !actionable.length) return { batch: b.id, impl: r, review: v, fixup: null }
    return agent(fixupPrompt(b, r, v), { label: `fixup:${b.id}`, phase: 'Fix-up', schema: RESULT_SCHEMA, isolation: 'worktree' })
      .then(f => ({ batch: b.id, impl: r, review: v, fixup: f }))
  },
)

const out = results.map((x, i) => x || { batch: BATCHES[i].id, failed: true })
for (const x of out) {
  if (x.failed) { log(`${x.batch}: FAILED`); continue }
  const final = x.fixup || x.impl
  const counts = {}
  for (const f of final.findings) counts[f.status] = (counts[f.status] || 0) + 1
  log(`${x.batch}: ${final.branch} ${JSON.stringify(counts)} review issues=${x.review ? x.review.issues.length : '?'}`)
}
return out
