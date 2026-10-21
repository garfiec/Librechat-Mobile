export const meta = {
  name: 'audit-parity',
  description: 'Audit Switchboard area by area against the pinned server and a reference client: scope each area, three-lens finders looped until dry, two-lens adversarial verify, per-area critic and triage, then a cross-area ranking and report',
  whenToUse: 'Launched by the audit-parity skill after the lead has written areas.json and known.md into the run directory. Read-only on the repo; agents write only into the run directory.',
  phases: [
    { title: 'Scope', detail: 'one agent per area maps our files, the endpoints, the server handlers at the pin and the reference client' },
    { title: 'Find', detail: 'contract, behaviour-parity and dead-wiring finders per area; repeats until two complete rounds add nothing' },
    { title: 'Verify', detail: 'two refuters per new finding; a split goes to a judge' },
    { title: 'Critic', detail: 'per area: what did the rounds not check? its findings are verified the same way' },
    { title: 'Triage', detail: 'per area: merge, mark known issues, rank, write areas/<id>/findings.json' },
    { title: 'Report', detail: 'cross-area merge groups, shared roots and report.md (last batch only)' },
  ],
}

// args can arrive as a JSON string; accept both.
const A = typeof args === 'string' ? JSON.parse(args) : args
const parse = v => (typeof v === 'string' ? JSON.parse(v) : v)
const REPO = A.repo
const RUN = A.run
const PIN = A.pin
const REF = A.ref || 'web'
const AREAS = parse(A.areas) || []
const MAX_ROUNDS = A.maxRounds || 4
const CAP = A.agentCap || 950
const REPORT = A.report !== false
const PRIOR_INDEX = parse(A.priorIndex) || []
const PRIOR_RUNS = parse(A.priorRuns) || []
const METHOD = `${REPO}/.claude/skills/audit-parity/reference.md`
const WEB = REF === 'web'

if (!REPO || !RUN || !PIN) throw new Error('audit-parity: args.repo, args.run and args.pin are required')
if (!AREAS.length && !(REPORT && PRIOR_INDEX.length)) throw new Error('audit-parity: args.areas is empty and there is no priorIndex to report on')
for (const a of AREAS) if (!a.id || !a.name) throw new Error(`audit-parity: every area needs id and name, got ${JSON.stringify(a)}`)

// Agent budget. The runtime refuses agents past its lifetime cap, so the script keeps its own
// count: each area gets an equal share and the cross-area report keeps a reserve.
const REPORT_RESERVE = REPORT ? 2 : 0
const SHARE = AREAS.length ? Math.floor((CAP - REPORT_RESERVE) / AREAS.length) : 0
const TAIL = 9 // critic, its dedupe, triage, and verification of up to three critic findings
const SKIP = { skipped: true }
const spent = {}
let used = 0

async function call(areaId, prompt, opts) {
  const limit = areaId ? SHARE : CAP
  if ((areaId && (spent[areaId] || 0) >= limit) || used >= CAP) {
    log(`${opts.label}: skipped, agent budget spent`)
    return SKIP
  }
  used++
  if (areaId) spent[areaId] = (spent[areaId] || 0) + 1
  try {
    return await agent(prompt, opts)
  } catch (e) {
    log(`${opts.label} threw: ${e && e.message ? e.message : e}`)
    return null
  }
}
const left = id => SHARE - (spent[id] || 0)
const got = r => r && r !== SKIP

function context(area, opts = {}) {
  return `
You are part of a READ-ONLY audit of Switchboard (Compose Multiplatform LibreChat client, Android + iOS). Do NOT edit, create or delete any file in the repo. Repo: ${REPO}.

Area under audit: ${area.name} (${area.id}). Seeds: ${area.seeds || '(none given)'}
Reference client: ${WEB ? 'the upstream LibreChat web client (client/ and packages/data-provider/ in the upstream submodule)' : `${REF} (a third-party client; read it from its working tree)`}.
Pinned upstream commit: ${PIN}. Read server${WEB ? ' and web' : ''} files ONLY at the pin: git -C ${REPO}/upstream show "${PIN}:<path>". The submodule working tree is not the pin.

First read ${METHOD} completely: lenses, scope, evidence rules, known issues, what is not a finding, severity.
${opts.scoper ? '' : opts.noScope
    ? `The scoper for this area failed, so there is no scope.md. Build your own map from the seeds before you start.\n`
    : `Then read ${RUN}/areas/${area.id}/scope.md, the file map for this area.\n`}${RUN}/known.md lists filed issues, recorded bugs, backend-less web features and deliberate divergences.
`
}

const FINDING_PROPS = {
  title: { type: 'string', description: 'one line: what goes wrong for the user' },
  kind: { type: 'string', enum: ['contract-bug', 'behaviour-gap', 'dead-wiring', 'failure-state', 'missing-feature'] },
  severity: { type: 'string', enum: ['high', 'medium', 'low'] },
  platforms: { type: 'string', description: 'android, ios or both' },
  ours: { type: 'string', description: 'our code path, path:line list' },
  theirs: { type: 'string', description: 'server or reference behaviour, path:line; empty for pure dead wiring' },
  trigger: { type: 'string', description: 'concrete steps or server response that produce it; may be empty for missing-feature' },
  evidence: { type: 'string', description: 'what the code does, and for absence claims which searches you ran; may be one line for missing-feature' },
  fix_hint: { type: 'string' },
  maybe_intentional: { type: 'boolean' },
  duplicate_of: { type: 'string', description: '"#N" when known.md has it filed; "#N (closed, regression)" when a closed issue still reproduces in code; else empty' },
}
const FINDING_REQ = ['title', 'kind', 'severity', 'platforms', 'ours', 'theirs', 'trigger', 'evidence', 'fix_hint', 'maybe_intentional', 'duplicate_of']
const FINDING = { type: 'object', properties: FINDING_PROPS, required: FINDING_REQ }

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

const INDEX_ITEM = {
  type: 'object',
  properties: {
    id: { type: 'string', description: '<area>-<n>, as written in the file' },
    title: { type: 'string' },
    kind: FINDING_PROPS.kind,
    severity: FINDING_PROPS.severity,
    verdict: { type: 'string', enum: ['CONFIRMED', 'PLAUSIBLE', 'UNVERIFIED'] },
    ours: { type: 'string', description: 'the first one or two path:line entries only' },
    duplicate_of: { type: 'string' },
    merged: { type: 'integer', description: 'how many input findings this entry absorbed, counting itself' },
  },
  required: ['id', 'title', 'kind', 'severity', 'verdict', 'ours', 'duplicate_of', 'merged'],
}

const TRIAGE_SCHEMA = {
  type: 'object',
  properties: {
    index: { type: 'array', items: INDEX_ITEM },
    coverage: { type: 'string', description: 'what this area traced, condensed to a few lines' },
  },
  required: ['index', 'coverage'],
}

const REPORT_SCHEMA = {
  type: 'object',
  properties: {
    order: { type: 'array', items: { type: 'string' }, description: 'every PRIMARY finding id in rank order: non-duplicates first, then by severity, then reach' },
    groups: {
      type: 'array',
      items: { type: 'object', properties: { primary: { type: 'string' }, merged: { type: 'array', items: { type: 'string' } } }, required: ['primary', 'merged'] },
      description: 'cross-area merges only: ids folded into a primary because they are the same defect or one fix closes both',
    },
    shared_roots: {
      type: 'array',
      items: { type: 'object', properties: { name: { type: 'string' }, cause: { type: 'string' }, ids: { type: 'array', items: { type: 'string' } } }, required: ['name', 'cause', 'ids'] },
    },
    totals: {
      type: 'object',
      properties: { findings: { type: 'integer' }, duplicates: { type: 'integer' }, high: { type: 'integer' }, medium: { type: 'integer' }, low: { type: 'integer' } },
      required: ['findings', 'duplicates', 'high', 'medium', 'low'],
      description: 'counts AFTER the cross-area merge, exactly as written in report.md',
    },
  },
  required: ['order', 'groups', 'shared_roots', 'totals'],
}

const LENSES = [
  { key: 'contract', focus: 'the Contract lens: trace every call this area makes into the server handler and its middleware at the pin; check every status code, error code, request and response field, pagination, repeats, verbs and gating.' },
  { key: 'parity', focus: 'the Behaviour parity lens: walk the reference client\'s flow for this area from component to hook to data-service call, and compare ordering, gates, honoured settings, parsing, recovery, and Android vs iOS inside our app.' },
  { key: 'wiring', focus: 'the Dead wiring and failure states lens: unreachable screens and nav keys, preferences nothing reads, stubs and empty iOS actuals, failures rendered as empty data, writes that close before the server answers, cancels that do not cancel.' },
]

const VERIFIERS = [
  { key: 'ours', focus: 'Check OUR side: open every cited Switchboard line, trace callers and both platforms (commonMain, androidMain, iosMain, iosApp Swift). Does our code really behave as claimed? Absence claims: search several ways yourself.' },
  { key: 'theirs', focus: `Check THEIR side: read the server handler, middleware and models at the pin yourself${WEB ? ', and the web client at the pin if cited' : `, and the reference client in ${REF} if cited`}. Does the server or reference really behave as claimed, so that our behaviour is actually wrong for a user?` },
]

// Dead wiring and failure states usually cite nothing upstream, so a server-side refuter would
// have nothing to check. Those get two angles on our own code instead.
const OURS_ONLY = [
  { key: 'callers', focus: 'Check OUR side by REACHABILITY: open every cited Switchboard line, then grep every caller, nav key, preference key and string resource involved. Is the thing really unreachable, unread, stubbed or mishandled on the path a user takes?' },
  { key: 'platform', focus: 'Check OUR side by PLATFORM: look for the behaviour in every source set (commonMain, androidMain, iosMain) and in iosApp Swift, and in shared components that might handle it generically. Is it really missing or broken on the platforms the finding names?' },
]

const brief = f => `${f.title} [${f.kind}] ours: ${f.ours}`
const short = (s, n) => (s && s.length > n ? `${s.slice(0, n)}…` : s || '')

async function verify(area, ctx, f, label) {
  const angles = f.theirs && f.theirs.trim() ? VERIFIERS : OURS_ONLY
  const votes = await parallel(angles.map(v => () =>
    call(area.id, `${ctx}
You are an adversarial VERIFIER. ${v.focus}
Try to REFUTE this finding. Default to REFUTED when the evidence is not solid; PLAUSIBLE when it hinges on runtime data you cannot see.

Finding (JSON):
${JSON.stringify(f, null, 1)}`, { label: `verify:${label}:${v.key}`, phase: 'Verify', schema: CHECK_SCHEMA })))
  const ok = votes.filter(got)
  const reasons = ok.map(v => v.reason).join(' | ')
  if (!ok.length) return { ...f, verdict: 'UNVERIFIED', reason: votes.some(v => v === SKIP) ? 'agent budget spent' : 'no verifier returned' }
  const refuted = ok.filter(v => v.result === 'REFUTED').length
  if (ok.length === 2 && refuted === 1) {
    const judge = await call(area.id, `${ctx}
Two verifiers disagree about a finding. Read the code yourself and decide. Default to REFUTED if neither side is solid.

Finding (JSON):
${JSON.stringify(f, null, 1)}

Verifier (${angles[0].key}): ${JSON.stringify(votes[0])}
Verifier (${angles[1].key}): ${JSON.stringify(votes[1])}`, { label: `judge:${label}`, phase: 'Verify', schema: CHECK_SCHEMA, effort: 'high' })
    if (!got(judge)) return { ...f, verdict: 'PLAUSIBLE', reason: `verifiers split and the judge ${judge === SKIP ? 'had no budget' : 'failed'}: ${reasons}` }
    return { ...judge.corrected, verdict: judge.result, reason: judge.reason }
  }
  if (refuted >= 1) return { ...f, verdict: 'REFUTED', reason: reasons }
  const keep = ok.find(v => v.result === 'CONFIRMED') || ok[0]
  const verdict = ok.length === 2 && ok.every(v => v.result === 'CONFIRMED') ? 'CONFIRMED' : 'PLAUSIBLE'
  return { ...keep.corrected, verdict, reason: ok.length === 1 ? `one verifier only: ${reasons}` : reasons }
}

async function fresh(area, candidates, seen, label, phase) {
  if (!candidates.length) return []
  const r = await call(area.id, `You are deduplicating audit findings. Two findings are the same if they describe the same defect at the same code site, even in different words. A new site of a known pattern is NOT the same.

Seen:
${seen.map((f, i) => `S${i + 1}. ${brief(f)}`).join('\n') || '(none)'}

Candidates:
${candidates.map((f, i) => `${i}. ${brief(f)}`).join('\n')}

Return the indexes of candidates that are new (not in Seen, and not a repeat of an earlier candidate).`, { label: `dedupe:${label}`, phase, schema: NEW_SCHEMA, effort: 'low' })
  if (!got(r)) {
    log(`dedupe:${label} ${r === SKIP ? 'skipped' : 'failed'}; passing all ${candidates.length} candidates through`)
    return candidates
  }
  return [...new Set(r.fresh)].filter(i => i >= 0 && i < candidates.length).map(i => candidates[i])
}

// Verify what the area's budget allows; the rest is kept as UNVERIFIED rather than dropped.
async function verifyAll(area, ctx, news, prefix, reserve) {
  const fits = Math.max(0, Math.floor((left(area.id) - reserve) / 2.5))
  const now = news.slice(0, fits)
  const later = news.slice(fits)
  if (later.length) log(`${area.id}: ${later.length} finding(s) left UNVERIFIED, agent budget`)
  const judged = (await parallel(now.map((f, i) => () => verify(area, ctx, f, `${prefix}-${i + 1}`)))).filter(Boolean)
  return [...judged, ...later.map(f => ({ ...f, verdict: 'UNVERIFIED', reason: 'agent budget spent' }))]
}

async function scope(area) {
  const r = await call(area.id, `${context(area, { scoper: true })}
You are the SCOPER for this area. Write ${RUN}/areas/${area.id}/scope.md (the ONLY file you may write) as a file map that every finder will read:
- Ours: screens, ViewModels, delegates, repositories, core/network api/*Api.kt and DTOs, Room entities if cached, settings rows and preference keys, androidMain/iosMain actuals and iosApp Swift. Grep nav keys, string resources and API paths.
- Endpoints: every route those Api files call, as METHOD /api/....
- Server at the pin: for each route, the router file (api/server/routes/), controller, service and model files (packages/api, packages/data-schemas), and middleware or validators on the path. Note the error codes and status codes it can send.
- Reference: ${WEB ? 'the data-service functions and endpoints (packages/data-provider/src/data-service.ts, api-endpoints.ts), react-query hooks (client/src/data-provider/) and components (client/src/components/)' : `the same map in ${REF}`}.
- Reference calls in this area we make no equivalent of: one line each. These seed missing-feature and behaviour-gap leads.
Use path:line where useful. Be complete rather than brief. Return one line: how many files on each side and how many endpoints.`, { label: `scope:${area.id}`, phase: 'Scope' })
  if (!got(r)) log(`scope:${area.id} ${r === SKIP ? 'skipped' : 'failed'}; finders will map the area from its seeds`)
  return { ok: got(r) }
}

async function auditArea(area, scoped) {
  const ctx = context(area, { noScope: !scoped.ok })
  const seen = []
  const results = []
  const leads = []
  const coverage = []
  let dry = 0
  let round = 0
  let budgetStop = false
  while (dry < 2 && round < MAX_ROUNDS) {
    if (left(area.id) < LENSES.length + 1 + TAIL) { budgetStop = true; log(`${area.id}: no budget for round ${round + 1}`); break }
    round++
    const raw = await parallel(LENSES.map(l => () =>
      call(area.id, `${ctx}
Your lens is ${l.focus}
This is round ${round}. ${seen.length ? `These are already found (some were refuted); do NOT report them again, look for what they missed:\n${seen.map((f, i) => `${i + 1}. ${brief(f)}`).join('\n')}` : 'Nothing has been found yet.'}
Report every defect that survives your own check against the evidence rules. There is no cap. In "checked", list what you traced, including things that were fine, so later rounds know what was covered.`,
      { label: `find:${area.id}:r${round}:${l.key}`, phase: 'Find', schema: FIND_SCHEMA })))
    raw.forEach((o, i) => { if (got(o)) coverage.push({ round, lens: LENSES[i].key, checked: o.checked }) })
    const failed = LENSES.filter((l, i) => !got(raw[i])).map(l => l.key)
    if (failed.length) log(`${area.id} round ${round}: finder(s) did not run: ${failed.join(', ')}`)
    const news = await fresh(area, raw.filter(got).flatMap(o => o.findings), seen, `${area.id}:r${round}`, 'Find')
    seen.push(...news)
    leads.push(...news.filter(f => f.kind === 'missing-feature'))
    const judged = await verifyAll(area, ctx, news.filter(f => f.kind !== 'missing-feature'), `${area.id}:r${round}`, TAIL)
    results.push(...judged)
    const survived = judged.filter(j => j.verdict !== 'REFUTED').length
    log(`${area.id} round ${round}: ${news.length} new, ${survived} survived`)
    // Only a complete round that adds nothing counts as dry.
    if (survived) dry = 0
    else if (!failed.length) dry++
  }
  const exhausted = dry >= 2
  if (!exhausted && !budgetStop) log(`${area.id}: stopped at the ${MAX_ROUNDS}-round cap before two dry rounds`)

  const critic = await call(area.id, `${ctx}
You are the completeness critic for this area. ${round} rounds of finders have run. What they traced:
${coverage.map(c => `[r${c.round} ${c.lens}] ${c.checked.join('; ')}`).join('\n')}

What they found:
${seen.map((f, i) => `${i + 1}. ${brief(f)}`).join('\n') || '(nothing)'}

Ask what is missing: calls in the scope nobody traced, status codes or error codes nobody checked, reference flows nobody walked, states (loading, error, offline, session expired) nobody looked at, the iOS side of anything checked only on Android. Check those yourself and report every defect you confirm. Do not repeat the list above.`, { label: `critic:${area.id}`, phase: 'Critic', schema: FIND_SCHEMA, effort: 'high' })
  if (!got(critic)) log(`critic:${area.id} ${critic === SKIP ? 'skipped' : 'failed'}`)
  const criticNew = got(critic) ? await fresh(area, critic.findings, seen, `${area.id}:critic`, 'Critic') : []
  seen.push(...criticNew)
  leads.push(...criticNew.filter(f => f.kind === 'missing-feature'))
  results.push(...await verifyAll(area, ctx, criticNew.filter(f => f.kind !== 'missing-feature'), `${area.id}:c`, 1))

  const kept = results.filter(r => r.verdict !== 'REFUTED')
  const refuted = results.filter(r => r.verdict === 'REFUTED')
  const file = `${RUN}/areas/${area.id}/findings.json`
  let index = []
  let triageOk = false
  if (kept.length) {
    const triage = await call(area.id, `${ctx}
You are triaging this area's verified findings. Write ${file} (the ONLY file you may write): a JSON array, one object per finding after merging, each with every field of the input plus "id" ("${area.id}-1", "${area.id}-2", ... in rank order), "area": "${area.id}" and "merged_from" (titles folded into it).
- Merge findings that one fix would close (same root cause), keeping each one's evidence.
- Check each against ${RUN}/known.md and set duplicate_of: "#N" if filed and open, "#N (closed, regression)" if a closed issue still reproduces in the code.
- Rank by severity then reach. Do not add findings and do not drop any: every input must be in the file or in some merged_from.
Return the index of what you wrote and a condensed coverage note.

Findings (JSON):
${JSON.stringify(kept)}

Coverage: ${coverage.map(c => c.checked.join('; ')).join(' | ')}`, { label: `triage:${area.id}`, phase: 'Triage', schema: TRIAGE_SCHEMA, effort: 'high' })
    if (got(triage)) {
      triageOk = true
      index = triage.index.map(i => ({ ...i, area: area.id }))
      const accounted = index.reduce((n, i) => n + (i.merged || 1), 0)
      if (accounted < kept.length) log(`triage:${area.id}: ${kept.length} in, only ${accounted} accounted for`)
    } else {
      log(`triage:${area.id} ${triage === SKIP ? 'skipped' : 'failed'}; ${kept.length} findings returned unmerged and NOT written to ${file}`)
      index = kept.map((k, i) => ({ id: `${area.id}-${i + 1}`, title: k.title, kind: k.kind, severity: k.severity, verdict: k.verdict, ours: short(k.ours, 160), duplicate_of: k.duplicate_of || '', merged: 1, area: area.id }))
    }
  }
  return {
    area: area.id,
    name: area.name,
    rounds: round,
    exhausted,
    budgetStop,
    agents: spent[area.id] || 0,
    found: seen.length,
    verdicts: {
      confirmed: kept.filter(k => k.verdict === 'CONFIRMED').length,
      plausible: kept.filter(k => k.verdict === 'PLAUSIBLE').length,
      unverified: kept.filter(k => k.verdict === 'UNVERIFIED').length,
      refuted: refuted.length,
    },
    triaged: triageOk,
    index,
    unmerged: triageOk ? [] : kept.map((k, i) => ({ ...k, id: `${area.id}-${i + 1}`, area: area.id })),
    leads: leads.map(l => ({ area: area.id, title: l.title, theirs: short(l.theirs, 200) })),
    refuted: refuted.map(r => ({ area: area.id, title: r.title, reason: short(r.reason, 300) })),
    coverage: short(coverage.map(c => c.checked.join('; ')).join(' | '), 2000),
  }
}

const runs = AREAS.length
  ? (await pipeline(AREAS, area => scope(area), (scoped, area) => auditArea(area, scoped))).filter(Boolean)
  : []
const failedAreas = AREAS.map(a => a.id).filter(id => !runs.some(r => r.area === id))
if (failedAreas.length) log(`areas with no result: ${failedAreas.join(', ')}`)

const batch = {
  index: runs.flatMap(r => r.index),
  runs: runs.map(({ index, unmerged, ...r }) => r),
}
const preMerge = {
  findings: batch.index.length,
  confirmed: batch.index.filter(i => i.verdict === 'CONFIRMED').length,
  plausible: batch.index.filter(i => i.verdict === 'PLAUSIBLE').length,
  unverified: batch.index.filter(i => i.verdict === 'UNVERIFIED').length,
  refuted: runs.reduce((n, r) => n + r.verdicts.refuted, 0),
  duplicates: batch.index.filter(i => i.duplicate_of).length,
}

let ranking = null
if (REPORT) {
  const index = [...PRIOR_INDEX, ...batch.index]
  const allRuns = [...PRIOR_RUNS, ...batch.runs]
  const r = await call(null, `You are writing the final report of a parity audit of Switchboard against ${WEB ? 'the upstream LibreChat web client' : REF} and the pinned server. Read ${METHOD} for the vocabulary. The full findings are in ${RUN}/areas/<area>/findings.json; read one only when an index entry is not enough to decide a merge.

Do two things:
1. Decide cross-area merges (two areas reported the same defect, or one fix closes both), shared roots (defects in several areas with one underlying cause, such as a repository pattern or a shared component) and a rank order (non-duplicates first, by severity, then by reach). Return them in the schema.
2. Write ${RUN}/report.md (the ONLY file you may write): plain, terse English for a solo dev who is a Kotlin/Android expert. Sections: Totals (AFTER your merges: by kind, severity, verdict, area; duplicates; refuted count; per-area rounds, agents, and whether each was exhausted or stopped on budget); Shared roots; Findings (one table in rank order: rank | id | sev | kind | area | title | ours | duplicate_of); Missing features (one line each, by area); Refuted (one line each with the reason); Coverage (per area, condensed); Gaps (areas that failed, were not triaged, hit the round cap or ran out of budget, and any UNVERIFIED findings). The totals in report.md must equal the totals you return.

Index of every finding (JSON):
${JSON.stringify(index)}

Missing-feature leads (JSON):
${JSON.stringify([...runs.flatMap(x => x.leads), ...parse(A.priorLeads || '[]')])}

Refuted (JSON):
${JSON.stringify([...runs.flatMap(x => x.refuted), ...parse(A.priorRefuted || '[]')])}

Per-area runs (JSON):
${JSON.stringify(allRuns)}
Areas with no result in this batch: ${failedAreas.join(', ') || 'none'}`, { label: 'report', phase: 'Report', schema: REPORT_SCHEMA, effort: 'high' })
  if (got(r)) ranking = r
  else log(`report ${r === SKIP ? 'skipped' : 'failed'}; report.md not written, rank with assemble.py's fallback`)
}

return {
  run: RUN,
  agentsUsed: used,
  sharePerArea: SHARE,
  failedAreas,
  preMerge,
  postMerge: ranking ? ranking.totals : null,
  ranking,
  index: batch.index,
  runs: batch.runs,
  leads: runs.flatMap(r => r.leads),
  refuted: runs.flatMap(r => r.refuted),
  unmerged: runs.flatMap(r => r.unmerged),
}
