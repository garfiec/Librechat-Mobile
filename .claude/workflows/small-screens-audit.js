export const meta = {
  name: 'small-screens-audit',
  description: 'Per-file small-screen / large-font / long-translation layout audit: two lenses per shard, adversarial verify, ranked synthesis with fix batches, completeness critic',
  whenToUse: 'Launched by the small-screens skill (audit mode) after the lead has built the ledger, worst strings, leads and shards. Read-only on the repo.',
  phases: [
    { title: 'Read', detail: 'two independent lenses per shard (horizontal fit, vertical/containers); every file gets a verdict' },
    { title: 'Verify', detail: 'one skeptic per shard merges duplicates and tries to refute each finding' },
    { title: 'Synthesize', detail: 'rank, group by shared root, propose fix batches, write audit.md' },
    { title: 'Critic', detail: 'completeness check of the finished audit' },
  ],
}

// args: { repo, run, shards, prior? }. args can arrive as a JSON string; accept both.
const A = typeof args === 'string' ? JSON.parse(args) : args
const REPO = A.repo
const RUN = A.run
const SHARDS = typeof A.shards === 'string' ? JSON.parse(A.shards) : A.shards
const PRIOR = A.prior || ''
const REF = `${REPO}/.claude/skills/small-screens/reference.md`

const CONTEXT = `
You are part of a READ-ONLY layout audit of Switchboard (Compose Multiplatform, Android + iOS). Do NOT edit, create or delete any file in the repo. Repo: ${REPO} (paths below are relative to it).

First read ${REF} completely. It defines the bug class, the test matrix, the defect classes C1-C11, how to judge a finding (caller-dependent fit, arithmetic from the screen edge inward, minimum trigger, what is NOT a defect) and the severity rubric. Apply it exactly.
The longest translation of every string key is in ${RUN}/worst-strings.tsv (columns: module, key, en_w, worst_locale, worst_w, en, worst). Grep it for the keys you judge and QUOTE the worst string and its length. Never guess a translation.
${PRIOR ? `A previous audit lists known defects: ${PRIOR}. Skim its row ids and files. If your finding is the same defect at the same site, set known_prior_row to that id (still report it, briefly). A new site of a known pattern is a NEW finding.` : 'There is no previous audit to compare against; leave known_prior_row empty.'}
Scan leads attached to each file (HGT L120 = fixed height at line 120, NOELL = maxLines=1 without overflow nearby, WIDTH, FRAC, THR, IOS) are hints only; most HGT leads are spacers or icons. Your coverage is reading every file, not the leads.
`

const FINDING = {
  type: 'object',
  properties: {
    id: { type: 'string', description: 'shard id + lens + number, e.g. S07-H3' },
    file: { type: 'string' },
    line: { type: 'integer' },
    cls: { type: 'string', enum: ['C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'C9', 'C10', 'C11'] },
    title: { type: 'string', description: 'one line: what breaks' },
    min_trigger: { type: 'string', description: 'mildest breaking condition, e.g. "en, 1.0x, 320dp, MATERIAL"' },
    evidence: { type: 'string', description: 'code facts, quoted worst strings with lengths, dp arithmetic, callers checked' },
    user_impact: { type: 'string', description: 'what the user sees: clipped label, unreachable button, etc.' },
    known_prior_row: { type: 'string', description: 'prior audit row id if same defect at same site, else empty' },
    fix_hint: { type: 'string', description: 'idiomatic Compose fix in one line, from the reference fix table where one applies' },
    shared_component: { type: 'boolean', description: 'true if the defect is in a reusable component with multiple callers' },
  },
  required: ['id', 'file', 'line', 'cls', 'title', 'min_trigger', 'evidence', 'user_impact', 'known_prior_row', 'fix_hint', 'shared_component'],
}

const READER_SCHEMA = {
  type: 'object',
  properties: {
    files: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          path: { type: 'string' },
          verdict: { type: 'string', enum: ['clean', 'issues', 'not-ui'] },
          note: { type: 'string', description: 'one short line: why clean / what was checked' },
        },
        required: ['path', 'verdict', 'note'],
      },
    },
    findings: { type: 'array', items: FINDING },
  },
  required: ['files', 'findings'],
}

const VERIFY_SCHEMA = {
  type: 'object',
  properties: {
    verdicts: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string', description: 'surviving finding id (for merged duplicates, the kept id)' },
          merged_ids: { type: 'array', items: { type: 'string' }, description: 'other ids folded into this one' },
          verdict: { type: 'string', enum: ['CONFIRMED', 'PLAUSIBLE', 'REFUTED'] },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          file: { type: 'string' },
          line: { type: 'integer' },
          cls: { type: 'string' },
          title: { type: 'string' },
          min_trigger: { type: 'string', description: 'corrected mildest trigger' },
          evidence: { type: 'string', description: 'your own re-derivation: code facts, quoted strings, arithmetic' },
          user_impact: { type: 'string' },
          known_prior_row: { type: 'string' },
          fix_hint: { type: 'string' },
          shared_component: { type: 'boolean' },
        },
        required: ['id', 'merged_ids', 'verdict', 'severity', 'file', 'line', 'cls', 'title', 'min_trigger', 'evidence', 'user_impact', 'known_prior_row', 'fix_hint', 'shared_component'],
      },
    },
    uncovered_files_reviewed: {
      type: 'array',
      items: { type: 'object', properties: { path: { type: 'string' }, verdict: { type: 'string' }, note: { type: 'string' } }, required: ['path', 'verdict', 'note'] },
      description: 'files neither reader gave a verdict for, which you read yourself',
    },
  },
  required: ['verdicts', 'uncovered_files_reviewed'],
}

const LENSES = [
  { key: 'H', name: 'horizontal fit', focus: 'Primary focus: classes C1, C2, C3, C5, C8, C10 (rows, labels beside controls, button rows, segmented/tab/pill cells, maxLines without overflow, fractional widths, hardcoded English). Still report any other class you notice.' },
  { key: 'V', name: 'vertical and containers', focus: 'Primary focus: classes C4, C6, C7, C9, C11 (fixed heights on text holders, non-scrolling sheets/dialogs/screens including keyboard-up on a 667pt SE, fixed pane widths and breakpoints, fractional heights, iOS native views). Still report any other class you notice.' },
]

function readerPrompt(shard, lens) {
  return `${CONTEXT}
Your lens: ${lens.name}. ${lens.focus}

Your shard: ${shard.id} (${shard.module}). Its file list with leads is ${RUN}/shards/${shard.id}.md. Read EVERY file in it completely (all lines, not excerpts). Every file must appear in your "files" output with a verdict: clean, issues, or not-ui (no rendered UI, e.g. pure state or helpers). Use ids ${shard.id}-${lens.key}1, ${shard.id}-${lens.key}2, ...
Be thorough but precise: a finding needs a concrete breaking condition with arithmetic, not "might be tight".`
}

function verifyPrompt(shard, readers) {
  const covered = new Set(readers.flatMap(r => (r ? r.files : []).map(f => f.path)))
  const uncovered = shard.files.map(f => f.path).filter(p => !covered.has(p))
  const findings = readers.flatMap(r => (r ? r.findings : []))
  return `${CONTEXT}
You are the adversarial VERIFIER for shard ${shard.id} (${shard.module}). Two independent readers (horizontal-fit and vertical/containers lenses) produced the findings below. Your job:
1. Merge duplicates (same defect, same site): keep one id, list the others in merged_ids.
2. For EACH finding, re-read the cited code and its callers yourself and try to REFUTE it, as the reference's "Verifying a finding" says. Look up the worst strings yourself. Default to REFUTED when the breaking condition cannot be shown concretely; PLAUSIBLE when it depends on runtime data or an uncertain measurement.
3. Correct the line number, class and minimum trigger where the reader got them wrong.
4. Assign severity by the reference's rubric.
5. Include REFUTED findings in verdicts too, with your reason in evidence, so the record is complete.
${uncovered.length ? `6. These files got no verdict from either reader; read them fully yourself, report them in uncovered_files_reviewed, and add any defects to verdicts with ids ${shard.id}-X1, ...: ${uncovered.join(', ')}` : '6. Both readers covered every file; uncovered_files_reviewed may be empty.'}

Findings (JSON):
${JSON.stringify(findings, null, 1)}`
}

phase('Read')
const perShard = await pipeline(
  SHARDS,
  shard => parallel(LENSES.map(lens => () =>
    agent(readerPrompt(shard, lens), { label: `read:${shard.id}:${lens.key}`, phase: 'Read', schema: READER_SCHEMA }))),
  (readers, shard) => {
    const ok = readers.filter(Boolean)
    const findings = ok.flatMap(r => r.findings)
    const covered = new Set(ok.flatMap(r => r.files.map(f => f.path)))
    const missing = shard.files.map(f => f.path).filter(p => !covered.has(p))
    if (!findings.length && !missing.length) {
      return { shard: shard.id, module: shard.module, readers, verify: { verdicts: [], uncovered_files_reviewed: [] } }
    }
    return agent(verifyPrompt(shard, readers), { label: `verify:${shard.id}`, phase: 'Verify', schema: VERIFY_SCHEMA, effort: 'high' })
      .then(verify => ({ shard: shard.id, module: shard.module, readers, verify }))
  },
)

const done = perShard.filter(Boolean)
const failedShards = SHARDS.map(s => s.id).filter(id => !done.some(d => d.shard === id && d.verify))
if (failedShards.length) log(`Shards with no verify result: ${failedShards.join(', ')}`)

const coverage = []
for (const s of SHARDS) {
  const d = done.find(x => x.shard === s.id)
  const verdictOf = {}
  if (d) {
    for (const r of d.readers.filter(Boolean)) for (const f of r.files) {
      if (!verdictOf[f.path] || f.verdict === 'issues') verdictOf[f.path] = f.verdict
    }
    for (const f of (d.verify ? d.verify.uncovered_files_reviewed : [])) verdictOf[f.path] = verdictOf[f.path] || f.verdict
  }
  for (const f of s.files) coverage.push({ path: f.path, shard: s.id, prior: f.prior, verdict: verdictOf[f.path] || 'UNCOVERED' })
}
const uncovered = coverage.filter(c => c.verdict === 'UNCOVERED')
if (uncovered.length) log(`${uncovered.length} files still have no verdict`)

const all = done.flatMap(d => (d.verify ? d.verify.verdicts : []).map(v => ({ ...v, shard: d.shard, module: d.module })))
const kept = all.filter(v => v.verdict !== 'REFUTED')
const refuted = all.filter(v => v.verdict === 'REFUTED')
log(`${all.length} verified findings: ${kept.filter(v => v.verdict === 'CONFIRMED').length} confirmed, ${kept.filter(v => v.verdict === 'PLAUSIBLE').length} plausible, ${refuted.length} refuted`)

phase('Synthesize')
const synthesis = await agent(`${CONTEXT}
You are writing the audit document from verified findings. Write it with the Write tool to ${RUN}/audit.md (the ONLY file you may write). Plain, terse English for a solo dev who is a Kotlin/Android expert.

Use EXACTLY these numbered headings; scripts parse them:
## 1. Totals
  Files audited (${coverage.length}), verdict counts, findings by verdict, severity and class, how many are new vs known prior rows.
## 2. Shared roots
  Group findings whose fix lives in one reusable component (core/ui Adaptive*, glass/*, shared chat components) and list every affected call site under it. Grep callers to check the grouping. Give each root a short tag (e.g. DLG-BTN, CHOICE, TOPBAR).
## 3. Fix batches
  6-12 batches, each a coherent PR (by shared root first, then by screen area), ordered by reach x severity. Each is a subsection headed exactly "### B<n>. <title>" with: the finding ids, files touched, the fix approach (from the reference fix table where it applies), and the render spec: which width, height, scale, locale and style the before/after renders must show. Every surviving finding lands in exactly one batch.
## 4. Findings table
  One markdown table, columns in this order: | id | sev | ver | cls | file:line | title | min trigger | prior | batch | root | fix hint |. ver is C or P; prior is the prior row id or N; batch is B<n>; root is the shared-root tag or empty. Sort by severity, then module.
## 5. Refuted
  One line each with the reason.
## 6. Coverage
  Per module: files clean / issues / not-ui / uncovered. The full per-file ledger goes in an appendix.

Surviving findings (JSON):
${JSON.stringify(kept)}

Refuted findings (JSON):
${JSON.stringify(refuted.map(r => ({ id: r.id, file: r.file, line: r.line, title: r.title, reason: r.evidence })))}

Per-file coverage ledger (JSON):
${JSON.stringify(coverage)}

Return a short plain-text summary (under 300 words): totals, the top 10 findings by reach x severity, and the batch list.`, { label: 'synthesize', phase: 'Synthesize', effort: 'high' })

phase('Critic')
const critic = await agent(`${CONTEXT}
You are the completeness critic for a finished audit. Read ${RUN}/audit.md and the shard lists in ${RUN}/shards/. Edit nothing in the repo; write your result to ${RUN}/critic.md (the ONLY file you may write). Ask: what is missing?
- Shared components whose callers were not all evaluated (grep their call sites).
- Screens and flows judged only in isolation: dialogs launched from a screen, bottom sheets, overlays, glass vs material variants.
- Defect classes that look under-reported relative to the code (e.g. very few C6 findings despite many sheets and dialogs): spot-check 5-10 files to test it.
- Findings in the table that contradict each other or the prior audit.
- Files marked clean that are not: spot-check at least 10 clean files with the most leads.
For each gap give file:line, what to check, and the batch it belongs in. Add any defect you confirm yourself, with full arithmetic. Return the same list as your final text.`, { label: 'critic', phase: 'Critic', effort: 'high' })

return {
  doc: `${RUN}/audit.md`,
  critic: `${RUN}/critic.md`,
  totals: {
    files: coverage.length,
    uncovered: uncovered.map(u => u.path),
    failedShards,
    confirmed: kept.filter(v => v.verdict === 'CONFIRMED').length,
    plausible: kept.filter(v => v.verdict === 'PLAUSIBLE').length,
    refuted: refuted.length,
    high: kept.filter(v => v.severity === 'high').length,
    medium: kept.filter(v => v.severity === 'medium').length,
    low: kept.filter(v => v.severity === 'low').length,
  },
  synthesis,
  criticSummary: critic,
}
