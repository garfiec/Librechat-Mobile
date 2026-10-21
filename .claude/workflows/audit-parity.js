export const meta = {
  name: 'audit-parity',
  description: 'Audit Switchboard area by area against the pinned server and a reference client: scope each area, three-lens finders looped until dry, two-lens adversarial verify, per-area critic and triage, then a cross-area report',
  whenToUse: 'Launched by the audit-parity skill after the lead has written areas and known.md into the run directory. Read-only on the repo; writes only into the run directory.',
  phases: [
    { title: 'Scope', detail: 'one agent per area maps our files, the endpoints, the server handlers at the pin and the reference client' },
    { title: 'Find', detail: 'contract, behaviour-parity and dead-wiring finders per area; repeats until two rounds add nothing' },
    { title: 'Verify', detail: 'two refuters per new finding; a split goes to a judge' },
    { title: 'Critic', detail: 'per area: what did the rounds not check? its findings are verified the same way' },
    { title: 'Triage', detail: 'per area: merge, mark known issues, rank' },
    { title: 'Report', detail: 'cross-area merge and the final report' },
  ],
}

// args can arrive as a JSON string; accept both.
const A = typeof args === 'string' ? JSON.parse(args) : args
const REPO = A.repo
const RUN = A.run
const PIN = A.pin
const REF = A.ref || 'web'
const AREAS = typeof A.areas === 'string' ? JSON.parse(A.areas) : A.areas
const MAX_ROUNDS = A.maxRounds || 4
const METHOD = `${REPO}/.claude/skills/audit-parity/reference.md`

function context(area) {
  return `
You are part of a READ-ONLY audit of Switchboard (Compose Multiplatform LibreChat client, Android + iOS). Do NOT edit, create or delete any file in the repo. Repo: ${REPO}.

Area under audit: ${area.name} (${area.id}). Seeds: ${area.seeds}
Reference client: ${REF === 'web' ? 'the upstream LibreChat web client (client/ and packages/data-provider/ in the upstream submodule)' : REF}.
Pinned upstream commit: ${PIN}. Read server and web files ONLY at the pin: git -C ${REPO}/upstream show "${PIN}:<path>". The submodule working tree is not the pin.

First read ${METHOD} completely: lenses, evidence rules, what is not a finding, severity. Then read ${RUN}/areas/${area.id}/scope.md, the file map for this area. Scope is a starting point, not a fence: follow calls wherever they lead, but report defects that belong to another area only if they are central to this one.
${RUN}/known.md lists issues already filed or recorded and deliberate divergences. Do not report those again.
`
}

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

const TRIAGED = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          ...FINDING.properties,
          verdict: { type: 'string', enum: ['CONFIRMED', 'PLAUSIBLE'] },
          duplicate_of: { type: 'string', description: '#issue or known.md entry, or empty' },
          merged_from: { type: 'array', items: { type: 'string' } },
        },
        required: [...FINDING.required, 'verdict', 'duplicate_of', 'merged_from'],
      },
    },
    coverage: { type: 'string', description: 'what this area traced, condensed to a few lines' },
  },
  required: ['findings', 'coverage'],
}

const LENSES = [
  { key: 'contract', focus: 'the Contract lens: trace every call this area makes into the server handler and its middleware at the pin; check every status code, error code, request and response field, pagination, repeats, verbs and gating.' },
  { key: 'parity', focus: 'the Behaviour parity lens: walk the reference client\'s flow for this area from component to hook to data-service call, and compare ordering, gates, honoured settings, parsing, recovery, and Android vs iOS inside our app.' },
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

async function verify(area, f, label) {
  const ctx = context(area)
  const angles = f.theirs && f.theirs.trim() ? VERIFIERS : OURS_ONLY
  const votes = await parallel(angles.map(v => () =>
    agent(`${ctx}
You are an adversarial VERIFIER. ${v.focus}
Try to REFUTE this finding. Default to REFUTED when the evidence is not solid; PLAUSIBLE when it hinges on runtime data you cannot see.

Finding (JSON):
${JSON.stringify(f, null, 1)}`, { label: `verify:${label}:${v.key}`, phase: 'Verify', schema: CHECK_SCHEMA })))
  const ok = votes.filter(Boolean)
  const refuted = ok.filter(v => v.result === 'REFUTED').length
  if (ok.length === 2 && refuted === 1) {
    const judge = await agent(`${ctx}
Two verifiers disagree about a finding. Read the code yourself and decide. Default to REFUTED if neither side is solid.

Finding (JSON):
${JSON.stringify(f, null, 1)}

Verifier (${angles[0].key}): ${JSON.stringify(votes[0])}
Verifier (${angles[1].key}): ${JSON.stringify(votes[1])}`, { label: `judge:${label}`, phase: 'Verify', schema: CHECK_SCHEMA, effort: 'high' })
    if (!judge) return { ...f, verdict: 'PLAUSIBLE', reason: `verifiers split and the judge failed: ${ok.map(v => v.reason).join(' | ')}` }
    return { ...judge.corrected, verdict: judge.result, reason: judge.reason }
  }
  if (!ok.length || refuted >= 1) return { ...f, verdict: 'REFUTED', reason: ok.map(v => v.reason).join(' | ') || 'no verifier returned' }
  const keep = ok.find(v => v.result === 'CONFIRMED') || ok[0]
  const verdict = ok.length === 2 && ok.every(v => v.result === 'CONFIRMED') ? 'CONFIRMED' : 'PLAUSIBLE'
  return { ...keep.corrected, verdict, reason: ok.map(v => v.reason).join(' | ') }
}

async function fresh(candidates, seen, label) {
  if (!candidates.length) return []
  const r = await agent(`You are deduplicating audit findings. Two findings are the same if they describe the same defect at the same code site, even in different words. A new site of a known pattern is NOT the same.

Seen:
${seen.map((f, i) => `S${i + 1}. ${brief(f)}`).join('\n') || '(none)'}

Candidates:
${candidates.map((f, i) => `${i}. ${brief(f)}`).join('\n')}

Return the indexes of candidates that are new (not in Seen, and not a repeat of an earlier candidate).`, { label: `dedupe:${label}`, phase: 'Find', schema: NEW_SCHEMA, effort: 'low' })
  if (!r) log(`dedupe:${label} failed; passing all ${candidates.length} candidates through`)
  return r ? r.fresh.filter(i => i >= 0 && i < candidates.length).map(i => candidates[i]) : candidates
}

async function scope(area) {
  await agent(`${context(area).replace(/Then read [^\n]*scope\.md[^\n]*\n/, '')}
You are the SCOPER for this area. Write ${RUN}/areas/${area.id}/scope.md (the ONLY file you may write) as a file map that every finder will read:
- Ours: screens, ViewModels, delegates, repositories, core/network api/*Api.kt and DTOs, Room entities if cached, settings rows and preference keys, androidMain/iosMain actuals and iosApp Swift. Grep nav keys, string resources and API paths.
- Endpoints: every route those Api files call, as METHOD /api/....
- Server at the pin: for each route, the router file (api/server/routes/), controller, service and model files (packages/api, packages/data-schemas), and middleware or validators on the path. Note the error codes and status codes it can send.
- Reference: ${REF === 'web' ? 'the data-service functions and endpoints (packages/data-provider/src/data-service.ts, api-endpoints.ts), react-query hooks (client/src/data-provider/) and components (client/src/components/)' : `the same map in ${REF}`}.
- Reference calls in this area we make no equivalent of: one line each. These seed missing-feature and behaviour-gap leads.
Use path:line where useful. Be complete rather than brief. Return one line: how many files on each side and how many endpoints.`, { label: `scope:${area.id}`, phase: 'Scope' })
}

async function auditArea(area) {
  const ctx = context(area)
  const seen = []
  const results = []
  const coverage = []
  let dry = 0
  let round = 0
  while (dry < 2 && round < MAX_ROUNDS) {
    round++
    const raw = await parallel(LENSES.map(l => () =>
      agent(`${ctx}
Your lens is ${l.focus}
This is round ${round}. ${seen.length ? `These defects are already found; do NOT report them again, look for what they missed:\n${seen.map((f, i) => `${i + 1}. ${brief(f)}`).join('\n')}` : 'Nothing has been found yet.'}
Report every defect that survives your own check against the evidence rules. There is no cap. In "checked", list what you traced, including things that were fine, so later rounds know what was covered.`,
      { label: `find:${area.id}:r${round}:${l.key}`, phase: 'Find', schema: FIND_SCHEMA })))
    raw.forEach((o, i) => { if (o) coverage.push({ round, lens: LENSES[i].key, checked: o.checked }) })
    const failed = LENSES.filter((l, i) => !raw[i]).map(l => l.key)
    if (failed.length) log(`${area.id} round ${round}: finder(s) failed: ${failed.join(', ')}`)
    const news = await fresh(raw.filter(Boolean).flatMap(o => o.findings), seen, `${area.id}:r${round}`)
    seen.push(...news)
    const judged = (await parallel(news.map((f, i) => () => verify(area, f, `${area.id}:r${round}-${i + 1}`)))).filter(Boolean)
    results.push(...judged)
    const survived = judged.filter(j => j.verdict !== 'REFUTED').length
    log(`${area.id} round ${round}: ${news.length} new, ${survived} survived`)
    dry = survived ? 0 : dry + 1
  }
  const exhausted = dry >= 2
  if (!exhausted) log(`${area.id}: stopped at the ${MAX_ROUNDS}-round cap before two dry rounds`)

  const critic = await agent(`${ctx}
You are the completeness critic for this area. ${round} rounds of finders have run. What they traced:
${coverage.map(c => `[r${c.round} ${c.lens}] ${c.checked.join('; ')}`).join('\n')}

What they found:
${seen.map((f, i) => `${i + 1}. ${brief(f)}`).join('\n') || '(nothing)'}

Ask what is missing: calls in scope.md nobody traced, status codes or error codes nobody checked, reference flows nobody walked, states (loading, error, offline, session expired) nobody looked at, the iOS side of anything checked only on Android. Check those yourself and report every defect you confirm. Do not repeat the list above.`, { label: `critic:${area.id}`, phase: 'Critic', schema: FIND_SCHEMA, effort: 'high' })
  const criticNew = critic ? await fresh(critic.findings, seen, `${area.id}:critic`) : []
  seen.push(...criticNew)
  results.push(...(await parallel(criticNew.map((f, i) => () => verify(area, f, `${area.id}:c-${i + 1}`)))).filter(Boolean))

  const kept = results.filter(r => r.verdict !== 'REFUTED')
  const refuted = results.filter(r => r.verdict === 'REFUTED')
  const triage = kept.length ? await agent(`${ctx}
You are triaging this area's verified findings. Merge findings that one fix would close (same root cause), keeping each one's evidence and listing merged titles in merged_from. Check each against ${RUN}/known.md and set duplicate_of where it is already filed or recorded (keep duplicates). Rank by severity then reach. Do not add new findings.

Findings (JSON):
${JSON.stringify(kept)}

Coverage: ${coverage.map(c => c.checked.join('; ')).join(' | ')}`, { label: `triage:${area.id}`, phase: 'Triage', schema: TRIAGED, effort: 'high' }) : null
  const final = triage ? triage.findings : kept.map(k => ({ ...k, duplicate_of: '', merged_from: [] }))
  if (kept.length && !triage) log(`triage:${area.id} failed; keeping ${kept.length} unmerged findings`)
  return {
    area: area.id,
    name: area.name,
    rounds: round,
    exhausted,
    found: seen.length,
    findings: final.map(f => ({ ...f, area: area.id })),
    refuted: refuted.map(r => ({ area: area.id, title: r.title, ours: r.ours, reason: r.reason })),
    coverage: triage ? triage.coverage : coverage.map(c => c.checked.join('; ')).join(' | '),
  }
}

const perArea = (await pipeline(AREAS, area => scope(area), (_, area) => auditArea(area))).filter(Boolean)
const failedAreas = AREAS.map(a => a.id).filter(id => !perArea.some(p => p.area === id))
if (failedAreas.length) log(`areas with no result: ${failedAreas.join(', ')}`)
const all = perArea.flatMap(p => p.findings)
const refuted = perArea.flatMap(p => p.refuted)

phase('Report')
const report = await agent(`You are writing the final result of a parity audit of Switchboard against ${REF === 'web' ? 'the upstream LibreChat web client' : REF} and the pinned server. Read ${METHOD} for the vocabulary. Write exactly two files and nothing else:
1. ${RUN}/findings.json: an array of every finding below after a cross-area merge: when two areas reported the same defect, or one fix would close both, merge them (keep both areas' evidence, list merged titles in merged_from, set "area" to the primary area). Give each an "id" F1, F2, ... in rank order: non-duplicates first, by severity, then by reach (how many users or screens). Keep every field.
2. ${RUN}/report.md: plain, terse English for a solo dev who is a Kotlin/Android expert. Sections: Totals (by kind, severity, verdict, area; duplicates; refuted count; per-area rounds and whether each was exhausted); Shared roots (defects in several areas with one underlying cause, such as a repository pattern or a shared component); Findings (one table: id | sev | kind | area | title | ours | duplicate_of); Missing features (one line each, by area); Refuted (one line each with the reason); Coverage (per area, condensed); Gaps (areas that failed or hit the round cap).

Findings (JSON):
${JSON.stringify(all)}

Refuted (JSON):
${JSON.stringify(refuted)}

Per-area runs (JSON):
${JSON.stringify(perArea.map(p => ({ area: p.area, name: p.name, rounds: p.rounds, exhausted: p.exhausted, found: p.found, kept: p.findings.length, coverage: p.coverage })))}
Areas with no result: ${failedAreas.join(', ') || 'none'}

Return a plain-text summary under 300 words: totals, shared roots, and the top 15 non-duplicate findings.`, { label: 'report', phase: 'Report', effort: 'high' })

return {
  findings: `${RUN}/findings.json`,
  report: `${RUN}/report.md`,
  areas: perArea.map(p => ({ area: p.area, rounds: p.rounds, exhausted: p.exhausted, found: p.found, kept: p.findings.length })),
  failedAreas,
  totals: {
    kept: all.length,
    confirmed: all.filter(f => f.verdict === 'CONFIRMED').length,
    plausible: all.filter(f => f.verdict === 'PLAUSIBLE').length,
    refuted: refuted.length,
    duplicates: all.filter(f => f.duplicate_of).length,
  },
  summary: report,
}
