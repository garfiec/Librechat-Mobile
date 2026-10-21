export const meta = {
  name: 'audit-feature',
  description: 'Audit one Switchboard feature against the pinned server and a reference client: three-lens finders looped until dry, two-lens adversarial verify, completeness critic, triage against known issues',
  whenToUse: 'Launched by the audit-feature skill after the lead has written scope.md and known.md into the run directory. Read-only on the repo.',
  phases: [
    { title: 'Find', detail: 'contract, behaviour-parity and dead-wiring finders; repeats each round until two rounds add nothing' },
    { title: 'Verify', detail: 'two refuters per new finding (our code, server/reference); a split goes to a judge' },
    { title: 'Critic', detail: 'what did the rounds not check? its findings are verified the same way' },
    { title: 'Triage', detail: 'merge, drop known issues, rank, write findings.json and report.md' },
  ],
}

// args can arrive as a JSON string; accept both.
const A = typeof args === 'string' ? JSON.parse(args) : args
const REPO = A.repo
const RUN = A.run
const FEATURE = A.feature
const PIN = A.pin
const REF = A.ref || 'web'
const MAX_ROUNDS = A.maxRounds || 4
const METHOD = `${REPO}/.claude/skills/audit-feature/reference.md`

const CONTEXT = `
You are part of a READ-ONLY audit of one feature of Switchboard (Compose Multiplatform LibreChat client, Android + iOS). Do NOT edit, create or delete any file in the repo. Repo: ${REPO}.

Feature under audit: ${FEATURE}.
Reference client: ${REF === 'web' ? 'the upstream LibreChat web client (client/ and packages/data-provider/ in the upstream submodule)' : REF}.
Pinned upstream commit: ${PIN}. Read server and web files ONLY at the pin: git -C ${REPO}/upstream show "${PIN}:<path>". The submodule working tree is not the pin.

First read ${METHOD} completely: lenses, evidence rules, what is not a finding, severity. Then read ${RUN}/scope.md, the file map for this feature (our files, server files, reference files). Scope is a starting point, not a fence: follow calls wherever they lead.
${RUN}/known.md lists issues already filed or recorded and deliberate divergences. Do not report those again.
`

const FINDING = {
  type: 'object',
  properties: {
    title: { type: 'string', description: 'one line: what goes wrong for the user' },
    kind: { type: 'string', enum: ['contract-bug', 'behaviour-gap', 'dead-wiring', 'failure-state', 'missing-feature'] },
    severity: { type: 'string', enum: ['high', 'medium', 'low'] },
    platforms: { type: 'string', description: 'android, ios or both' },
    ours: { type: 'string', description: 'our code path, path:line list' },
    theirs: { type: 'string', description: 'server or reference behaviour, path:line at the pin; empty for pure dead wiring' },
    trigger: { type: 'string', description: 'concrete steps or server response that produce it' },
    evidence: { type: 'string', description: 'what the code does, and for absence claims which searches you ran' },
    fix_hint: { type: 'string' },
    maybe_intentional: { type: 'boolean' },
  },
  required: ['title', 'kind', 'severity', 'platforms', 'ours', 'theirs', 'trigger', 'evidence', 'fix_hint', 'maybe_intentional'],
}

const FIND_SCHEMA = {
  type: 'object',
  properties: {
    checked: { type: 'array', items: { type: 'string' }, description: 'calls, flows and states you traced, one line each, including ones that were fine' },
    findings: { type: 'array', items: FINDING },
  },
  required: ['checked', 'findings'],
}

const NEW_SCHEMA = {
  type: 'object',
  properties: {
    fresh: { type: 'array', items: { type: 'integer' }, description: 'indexes into the candidate list that are NOT the same defect as anything in the seen list or an earlier candidate' },
  },
  required: ['fresh'],
}

const CHECK_SCHEMA = {
  type: 'object',
  properties: {
    result: { type: 'string', enum: ['CONFIRMED', 'PLAUSIBLE', 'REFUTED'] },
    reason: { type: 'string', description: 'what you found, with path:line' },
    corrected: { ...FINDING, description: 'the finding with line numbers, severity, trigger and kind fixed; copy unchanged fields' },
  },
  required: ['result', 'reason', 'corrected'],
}

const LENSES = [
  { key: 'contract', focus: 'the Contract lens: trace every call this feature makes into the server handler and its middleware at the pin; check every status code, error code, request and response field, pagination, repeats, verbs and gating.' },
  { key: 'parity', focus: 'the Behaviour parity lens: walk the reference client\'s flow for this feature from component to hook to data-service call, and compare ordering, gates, honoured settings, parsing, recovery, and Android vs iOS inside our app.' },
  { key: 'wiring', focus: 'the Dead wiring and failure states lens: unreachable screens and nav keys, preferences nothing reads, stubs and empty iOS actuals, failures rendered as empty data, writes that close before the server answers, cancels that do not cancel.' },
]

const VERIFIERS = [
  { key: 'ours', focus: 'Check OUR side: open every cited Switchboard line, trace callers and both platforms (commonMain, androidMain, iosMain, iosApp Swift). Does our code really behave as claimed? Absence claims: search several ways yourself.' },
  { key: 'theirs', focus: 'Check THEIR side: read the server handler, middleware and models (and the reference client if cited) at the pin yourself. Does the server or reference really behave as claimed, so that our behaviour is actually wrong for a user?' },
]

// Dead wiring and failure states usually cite nothing upstream, so a server-side refuter would
// have nothing to check. Those get two angles on our own code instead.
const OURS_ONLY = [
  { key: 'callers', focus: 'Check OUR side by REACHABILITY: open every cited Switchboard line, then grep every caller, nav key, preference key and string resource involved. Is the thing really unreachable, unread, stubbed or mishandled on the path a user takes?' },
  { key: 'platform', focus: 'Check OUR side by PLATFORM: look for the behaviour in every source set (commonMain, androidMain, iosMain) and in iosApp Swift, and in shared components that might handle it generically. Is it really missing or broken on the platforms the finding names?' },
]

const brief = f => `${f.title} [${f.kind}] ours: ${f.ours}`

function findPrompt(lens, round, seen) {
  return `${CONTEXT}
Your lens is ${lens.focus}
This is round ${round}. ${seen.length ? `These defects are already found; do NOT report them again, look for what they missed:\n${seen.map((f, i) => `${i + 1}. ${brief(f)}`).join('\n')}` : 'Nothing has been found yet.'}
Report every defect that survives your own check against the evidence rules. There is no cap. In "checked", list what you traced, including things that were fine, so later rounds know what was covered.`
}

async function verify(f, label) {
  const angles = f.theirs && f.theirs.trim() ? VERIFIERS : OURS_ONLY
  const votes = await parallel(angles.map(v => () =>
    agent(`${CONTEXT}
You are an adversarial VERIFIER. ${v.focus}
Try to REFUTE this finding. Default to REFUTED when the evidence is not solid; PLAUSIBLE when it hinges on runtime data you cannot see.

Finding (JSON):
${JSON.stringify(f, null, 1)}`, { label: `verify:${label}:${v.key}`, phase: 'Verify', schema: CHECK_SCHEMA })))
  const ok = votes.filter(Boolean)
  const refuted = ok.filter(v => v.result === 'REFUTED').length
  if (ok.length === 2 && refuted === 1) {
    const judge = await agent(`${CONTEXT}
Two verifiers disagree about a finding. Read the code yourself and decide. Default to REFUTED if neither side is solid.

Finding (JSON):
${JSON.stringify(f, null, 1)}

Verifier (${angles[0].key}): ${JSON.stringify(votes[0])}
Verifier (${angles[1].key}): ${JSON.stringify(votes[1])}`, { label: `judge:${label}`, phase: 'Verify', schema: CHECK_SCHEMA, effort: 'high' })
    if (!judge) return { ...f, verdict: 'PLAUSIBLE', reason: `verifiers split and the judge failed: ${ok.map(v => v.reason).join(' | ')}`, votes }
    return { ...judge.corrected, verdict: judge.result, reason: judge.reason, votes }
  }
  if (!ok.length || refuted >= 1) return { ...f, verdict: 'REFUTED', reason: ok.map(v => v.reason).join(' | '), votes }
  const keep = ok.find(v => v.result === 'CONFIRMED') || ok[0]
  const verdict = ok.every(v => v.result === 'CONFIRMED') ? 'CONFIRMED' : 'PLAUSIBLE'
  return { ...keep.corrected, verdict, reason: ok.map(v => v.reason).join(' | '), votes }
}

async function fresh(candidates, seen, round) {
  if (!candidates.length) return []
  const r = await agent(`You are deduplicating audit findings. Two findings are the same if they describe the same defect at the same code site, even in different words. A new site of a known pattern is NOT the same.

Seen:
${seen.map((f, i) => `S${i + 1}. ${brief(f)}`).join('\n') || '(none)'}

Candidates:
${candidates.map((f, i) => `${i}. ${brief(f)}`).join('\n')}

Return the indexes of candidates that are new (not in Seen, and not a repeat of an earlier candidate).`, { label: `dedupe:r${round}`, phase: 'Find', schema: NEW_SCHEMA, effort: 'low' })
  return r ? r.fresh.filter(i => i >= 0 && i < candidates.length).map(i => candidates[i]) : candidates
}

const seen = []
const results = []
const coverage = []
let dry = 0
let round = 0
while (dry < 2 && round < MAX_ROUNDS) {
  round++
  phase('Find')
  const raw = await parallel(LENSES.map(l => () =>
    agent(findPrompt(l, round, seen), { label: `find:r${round}:${l.key}`, phase: 'Find', schema: FIND_SCHEMA })))
  raw.forEach((o, i) => { if (o) coverage.push({ round, lens: LENSES[i].key, checked: o.checked }) })
  const failed = LENSES.filter((l, i) => !raw[i]).map(l => l.key)
  if (failed.length) log(`round ${round}: finder(s) failed: ${failed.join(', ')}`)
  const outs = raw.filter(Boolean)
  const news = await fresh(outs.flatMap(o => o.findings), seen, round)
  seen.push(...news)
  const judged = (await parallel(news.map((f, i) => () => verify(f, `r${round}-${i + 1}`)))).filter(Boolean)
  results.push(...judged)
  const survived = judged.filter(j => j.verdict !== 'REFUTED').length
  log(`round ${round}: ${news.length} new, ${survived} survived verification (${results.filter(j => j.verdict !== 'REFUTED').length} total)`)
  dry = survived ? 0 : dry + 1
}
if (dry < 2) log(`stopped at the ${MAX_ROUNDS}-round cap before two dry rounds; the tail may not be exhausted`)

phase('Critic')
const critic = await agent(`${CONTEXT}
You are the completeness critic. ${round} rounds of finders have run. What they traced:
${coverage.map(c => `[r${c.round} ${c.lens}] ${c.checked.join('; ')}`).join('\n')}

What they found:
${seen.map((f, i) => `${i + 1}. ${brief(f)}`).join('\n') || '(nothing)'}

Ask what is missing: calls in scope.md nobody traced, server status codes or error codes nobody checked, reference-client flows nobody walked, states (loading, error, offline, session expired) nobody looked at, the iOS side of anything checked only on Android. Check those yourself and report every defect you confirm. Do not repeat the list above.`, { label: 'critic', phase: 'Critic', schema: FIND_SCHEMA, effort: 'high' })
const criticNew = critic ? await fresh(critic.findings, seen, 'critic') : []
seen.push(...criticNew)
const criticJudged = (await parallel(criticNew.map((f, i) => () => verify(f, `c-${i + 1}`)))).filter(Boolean)
results.push(...criticJudged)
log(`critic: ${criticNew.length} new, ${criticJudged.filter(j => j.verdict !== 'REFUTED').length} survived`)

const kept = results.filter(r => r.verdict !== 'REFUTED')
const refuted = results.filter(r => r.verdict === 'REFUTED')

phase('Triage')
const triage = await agent(`${CONTEXT}
You are writing the audit result for the lead. Write exactly two files and nothing else:
1. ${RUN}/findings.json: an array, one object per surviving defect after merging, ranked by severity then reach. Each object: { "id": "F1", "title", "kind", "severity", "platforms", "verdict", "ours", "theirs", "trigger", "evidence", "fix_hint", "maybe_intentional", "duplicate_of": "<#issue or known.md entry, or empty>", "merged_from": [titles] }.
   - Merge findings that one fix would close (same root cause), keeping the evidence of each.
   - Re-check each against ${RUN}/known.md and set duplicate_of where it is already filed or recorded. Keep duplicates in the file, ranked last.
2. ${RUN}/report.md: plain, terse English for a solo dev who is a Kotlin/Android expert. Sections: Totals (by kind, severity, verdict, duplicates, refuted count, rounds run); Findings (a table: id | sev | kind | title | ours | duplicate_of); Missing features (one line each); Refuted (one line each with the reason); Coverage (what was traced, condensed).

Surviving findings (JSON):
${JSON.stringify(kept.map(({ votes, ...f }) => f))}

Refuted (JSON):
${JSON.stringify(refuted.map(r => ({ title: r.title, ours: r.ours, reason: r.reason })))}

Coverage (JSON):
${JSON.stringify(coverage)}

Return a plain-text summary under 250 words: totals and the ranked findings that are not duplicates.`, { label: 'triage', phase: 'Triage', effort: 'high' })

return {
  findings: `${RUN}/findings.json`,
  report: `${RUN}/report.md`,
  rounds: round,
  exhausted: dry >= 2,
  totals: {
    found: seen.length,
    confirmed: kept.filter(k => k.verdict === 'CONFIRMED').length,
    plausible: kept.filter(k => k.verdict === 'PLAUSIBLE').length,
    refuted: refuted.length,
  },
  summary: triage,
}
