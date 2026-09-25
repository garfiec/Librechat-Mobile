# Switchboard - Discovery & Context Document

## Project Goal
Build a native mobile app (Switchboard) with full feature parity to the LibreChat web application. The app must connect to an existing LibreChat backend server (no backend changes). Users specify the server URL during onboarding.

## Tech Stack (Mandated)
- **UI**: Jetpack Compose + Compose Navigation
- **DI**: Koin
- **Network**: Ktor Client
- **Serialization**: Kotlinx Serialization
- **Min SDK**: TBD (recommend 26+)

---

## LibreChat Web Application Overview

### Architecture
- Monorepo: `/api` (Express/Node backend), `/client` (React/Vite frontend), `/packages` (shared libs)
- MongoDB database, optional Redis, optional Meilisearch
- JWT-based auth with refresh tokens

### Core Features (Full Parity Required)

#### 1. Authentication & Onboarding
- **Server URL entry** (Android-only: user specifies their LibreChat server)
- Local login (email/password)
- OAuth2 social logins (Google, GitHub, Discord, Facebook, Apple, OpenID, SAML)
- Registration with email verification
- Password reset flow
- Two-factor authentication (TOTP + backup codes)
- Terms of service acceptance

#### 2. Chat (Primary Experience)
- Conversation list in sidebar (cursor-paginated, sorted by updatedAt)
- Create new conversations
- Send messages, receive streaming AI responses via SSE
- Message tree with parent/child relationships and branching
- Sibling message navigation (e.g., "1/3" switcher for regenerated responses)
- Edit user messages (creates new branch)
- Regenerate AI responses
- Continue incomplete responses
- Stop generation mid-stream
- Fork conversations from any message
- Duplicate conversations
- Markdown rendering with syntax highlighting
- LaTeX/math rendering
- Code blocks with copy button and language badge
- Image display (inline + expandable)
- File attachments in messages
- Tool call display (expandable cards showing input/output)
- Message feedback (thumbs up/down with optional comment)
- Typing/streaming indicator
- Time-based greeting on landing ("Good morning", etc.)
- Custom welcome messages from server config

#### 3. Model/Endpoint Selection
- Multiple AI providers: OpenAI, Anthropic, Azure, Google, Groq, Mistral, Ollama, custom
- Model selector dropdown with search
- Endpoint icons (branded)
- Model parameters (temperature, top_p, frequency_penalty, etc.)
- Model specs from server config

#### 4. Agents & Assistants
- Agent marketplace (grid of cards with avatar, name, description, category)
- Agent chat with tool calling
- OpenAI Assistants integration
- Agent/assistant selection UI

#### 5. File Management
- Upload files (images, PDFs, documents, code)
- Drag-and-drop (not applicable on Android, use file picker)
- File preview in messages
- File download
- Image generation display
- Audio recording for voice input (STT)
- Text-to-speech playback (TTS)

#### 6. Conversation Management
- Rename conversations (inline)
- Archive/unarchive
- Delete (single + bulk)
- Share (generate public link)
- Export (JSON, markdown)
- Import conversations
- Tags for organization
- Bookmarks/favorites
- Search conversations (full-text via Meilisearch)

#### 7. Presets
- Save chat configurations as presets
- Load presets
- Delete presets

#### 8. Prompts Library
- Create/edit/delete prompts
- Share prompts
- Prompt versioning
- @mentions for prompts in chat input
- Prompt variables

#### 9. User Settings
- Theme (dark/light)
- Language selection (46+ languages)
- Font size
- Speech settings
- Data export
- Account management (profile, delete account)
- Balance/credits display

#### 10. Artifacts
- Split-pane code preview/editor
- Tabs: Preview, Code, Info
- Live editing

---

## API Endpoints (Key Routes)

### Authentication
```
POST /api/auth/login        → { token, user } or { twoFAPending, tempToken }
POST /api/auth/register     → { status, message }
POST /api/auth/logout       → { status }
POST /api/auth/refresh      → { token, user } (refresh token in cookies)
POST /api/auth/requestPasswordReset
POST /api/auth/resetPassword
GET  /api/auth/2fa/enable   → { secret, qrCode }
POST /api/auth/2fa/verify
POST /api/auth/2fa/confirm  → { confirmed, backupCodes }
POST /api/auth/2fa/disable
POST /api/auth/2fa/verify-temp → { token, user }
```

### Configuration
```
GET /api/config             → startup config (models, features, auth methods, interface config)
GET /api/endpoints          → available AI providers and models  (JWT required as of v0.8.5)
POST /api/user/settings/favorites → update user favorites (agent/model pins, v0.8.5+)
GET  /api/user/settings/favorites → list user favorites (v0.8.5+)
POST /api/prompts/groups/:id/use  → record prompt-group usage for analytics (v0.8.5+)
```

**v0.8.5 notes**
- `GET /api/config` response payload is now split into a pre-auth and post-auth variant.
  Pre-auth fields (what `validateServerUrl` / `fetchStartupConfig` rely on) are unchanged
  from v0.8.4; post-auth adds fields driven by the logged-in user (not consumed by mobile).
- `GET /api/config` removed `instanceProjectId`. Mobile previously used it as an OR fallback
  in `ConfigRepositoryImpl.isValidLibreChatConfig`; cleanup landed in v0.8.5 sync.
- `GET /api/config` added `allowAccountDeletion: Boolean`. Mobile honors this and hides
  the Delete Account button when `false`. Defaults to `true` for older servers that omit the field.
- `GET /api/endpoints` now requires JWT (was public in v0.8.4). Mobile already called it
  post-auth, so this is non-breaking.
- Favorites schema: each entry has exactly one of `{ agentId }`, `{ model, endpoint }`,
  or `{ spec }` — three mutually exclusive variants enforced by the server
  (`FavoritesController.js`: "Each favorite must have either agentId, model+endpoint, or spec";
  `model` and `endpoint` must be supplied together; combining `spec` with any of
  `agentId`/`model`/`endpoint` is rejected). Server also enforces 50 entries max /
  256-character max per string; mobile short-circuits oversize writes. The `spec`
  variant is round-tripped unchanged (mobile does not yet render a spec picker).
  `POST` replaces the entire list (upsert-by-overwrite), and the response echoes the stored list.

### Out of scope (admin panel)
```
/api/admin/auth/**   → admin-only SAML + Social OAuth callbacks (v0.8.5, web-only)
/api/admin/config/** → admin YAML config endpoints
/api/admin/grants/**, /api/admin/groups/**, /api/admin/roles/**, /api/admin/users/**
```
Admin panel is a web-only surface in upstream; mobile intentionally does not implement it.

### Conversations
```
GET    /api/convos                          → { conversations, nextCursor }
GET    /api/convos/:conversationId          → single conversation
POST   /api/convos/update                   → update title
POST   /api/convos/archive                  → archive/unarchive
DELETE /api/convos                           → delete conversation(s)
POST   /api/convos/fork                     → fork from message
POST   /api/convos/duplicate                → duplicate conversation
POST   /api/convos/import                   → import (multipart)
GET    /api/convos/gen_title/:conversationId → generated title
```

### Messages
```
GET    /api/messages/:conversationId           → messages for conversation
GET    /api/messages/:conversationId/:messageId
POST   /api/messages/:conversationId           → save message
PUT    /api/messages/:conversationId/:messageId → update message
DELETE /api/messages/:conversationId/:messageId
PUT    /api/messages/:conversationId/:messageId/feedback
```

### Chat (Streaming)
```
POST /api/agents/chat         → { streamId } (start generation)
GET  /api/agents/chat/stream/:streamId?resume=true → SSE stream
POST /api/agents/chat/abort   → abort generation
GET  /api/agents/chat/active  → { activeJobIds }
GET  /api/agents/chat/status/:conversationId → job status
```

### Files
```
GET    /api/files              → user's files
POST   /api/files              → upload (multipart)
GET    /api/files/download/:userId/:file_id
DELETE /api/files              → delete file(s)
POST   /api/files/speech/stt   → speech-to-text
POST   /api/files/speech/tts   → text-to-speech
```

### v0.8.6 endpoint surface (confirmed during sync; mobile status noted)
```
# Skills family (DEFERRED — no mobile counterpart; skill tool *invocations* already
# render via the generic tool-call path, so chatting with a skill-enabled agent works today)
GET    /api/skills                       → list
POST   /api/skills                       → create (perm: skills.create)
GET    /api/skills/:id                   → detail
PATCH  /api/skills/:id                   → update
DELETE /api/skills/:id                   → delete
GET    /api/skills/:id/files             → skill file tree
POST   /api/skills/:id/files             → add file
GET    /api/skills/:id/files/:relPath    → read file
DELETE /api/skills/:id/files/:relPath    → delete file
POST   /api/skills/import                → import skill
GET    /api/user/settings/skills/active  → per-user active-skill states (DEFERRED)
POST   /api/user/settings/skills/active  → set active-skill states (DEFERRED)
PUT    /api/roles/:roleName/skills       → admin skill-permission grant (DEFERRED; admin)

# Deferred-preview poll for inline office-doc rendering (DEFERRED — pairs with TFile.status/text)
GET    /api/files/:file_id/preview       → { file_id, status: pending|ready|failed,
                                            text?, textFormat?: html|text, previewError? }
                                            (status defaults to 'ready' for legacy/non-office files)

# CloudFront signed-URL download (DEFERRED — only when server is CloudFront-configured;
# the existing /api/files/download/:userId/:file_id bytes path still works on v0.8.6)
GET    /api/files/download-url/:userId/:file_id → { url, filename, type, metadata }
```

### /api/config v0.8.6 behavior (verified non-breaking)
- Pre-auth (no `req.user`) response is now sanitized to a minimal pre-login payload; `?context=share`
  merges a public-share payload. Mobile onboarding (`ConfigRepositoryImpl.validateServerUrl`) only
  checks `serverDomain.isNotBlank()` (not stripped) and re-fetches the full config post-auth, so this
  is safe. New top-level keys: `rum`, `cloudFront`, `buildInfo`; new `interface` keys: `skills`,
  `buildInfo`, `autoSubmitFromUrl`, `retentionMode`. Mobile now PARSES these (C1) but gates no UI yet.

### v0.8.7 endpoint surface (confirmed during sync; mobile status noted)
```
# Pinned conversations (BUILT — drawer pin/unpin + pinned section, gated >= 0.8.7)
POST   /api/convos/pin                   → { arg: { conversationId, pinned } } → updated Conversation

# Chat Projects / folders (BUILT — full: data layer + move-to-project picker + drawer folder
# section + Projects index (ConversationsRoute.Projects) + ProjectChats detail (Nav3) + project CRUD)
GET    /api/projects                     → { projects: [TChatProject], nextCursor }
                                            ?cursor&limit&sortBy&sortDirection&search
POST   /api/projects                     → { name, description? } → ChatProject (201)   (NOT arg-wrapped)
GET    /api/projects/:projectId          → ChatProject
PATCH  /api/projects/:projectId          → { name?, description? } → ChatProject          (NOT arg-wrapped)
DELETE /api/projects/:projectId          → { deletedCount, modifiedCount }
PUT    /api/projects/conversations/:conversationId → { projectId|null } → { conversation, previousProjectId, projectId }
GET    /api/convos?projectId=<id|unassigned>       → conversations filtered by project (added projectId query param)

# Context-usage gauge (BUILT — gauge + SSE; context-projection seeds the gauge on chat
# open / model switch; gated on interface.contextUsage && >= 0.8.7)
GET    /api/endpoints/token-config       → { [endpoint]: { [model]: { context, prompt?, completion?, cacheWrite?, cacheRead? } } }
POST   /api/endpoints/context-projection → { conversationId, messageId, endpoint, model?, agentId?, spec?,
                                            maxContextTokens?, calibrationRatio? } → ContextUsage | null

# Shared links: getSharedLinks dropped the isPublic query param (visibility now ACL-governed).
GET    /api/share?cursor&pageSize&sortBy&sortDirection&search   (NO isPublic — removed in v0.8.7)
```

### v0.8.7 SSE additions (BUILT)
```
{ event: 'title',            data: { conversationId, title } }        # immediate title (interface.titleTiming === 'immediate')
{ event: 'on_token_usage',   data: TTokenUsageEvent }                 # per-call provider usage
{ event: 'on_context_usage', data: TContextUsageEvent }               # context-window snapshot (breakdown + remaining)
```

### /api/config v0.8.7 behavior
- New `interface` keys (mobile PARSES; gates noted): `contextUsage` (default true), `contextCost` (default false),
  `titleTiming` ('immediate'|'final'), `defaultPinnedTools: string[]` (detection-only), `sharedLinks`
  (bool | { create, share, public, snapshotFiles }), `maxCatalogSkills` (detection-only). New startup-level key:
  `sharedLinksSnapshotFilesEnabled` (detection-only). New conversation/preset field: `promptCacheTtl` ('5m'|'1h').
  New conversation fields: `pinned`, `chatProjectId`.

### v0.8.7 chat-payload addition (BUILT)
- The chat request now sends `timezone` (IANA id, e.g. "America/New_York"; #13815) so the server
  resolves agent `{{current_date}}`/`{{current_datetime}}` to the user's wall clock. Always sent,
  ungated; set in `ChatPayloadBuilder` from `TimeZone.currentSystemDefault()`.

### v0.8.7 known-deferred parity gaps (NOT built; tracked in proposal-v0.8.7.md)
- `url_context` conversation toggle (Google URL Context) — no mobile param-sheet control yet.
- per-message `quotes[]` round-trip (selected-text quote-reply context) — since BUILT (v0.8.8-rc1 sync);
  Android-only capture, iOS deferred — see the Quotes block below.

### v0.8.8-line partial sync (untagged dev commit 6c97a7f4, 2026-07-23) — endpoint / shape changes
These landed upstream on the post-v0.8.7 `dev` branch (package.json still reports 0.8.7; the
target commit is untagged). Date-gated paths use `BackendVersion.supportsFeature`; paths where
support can be discovered by asking use `BackendVersion.featureSupport` and probe once instead
(see VERSION_GATES.md — a dev build reporting 0.8.7 is not evidence of an old server).
```
# Newly-discovered / revised request contracts
POST   <generation endpoint>             + top-level `clientRequestId` (uuid) idempotency key (#14344,
                                            landing commit for the target itself is #14411 stream-order).
                                            Server claims it before job creation so a replayed POST dedups
                                            to the original run instead of double-billing. Additive; mobile
                                            mints one per send in ChatPayloadBuilder. (BUILT)
POST   /api/auth/login                    now 403s when ALLOW_EMAIL_LOGIN=false (#14180). /api/config already
                                            exposes `emailLoginEnabled` (default true); mobile hides the
                                            email/password form off it and maps the 403 to a clear message.
                                            Config-driven, no version gate, fail-open. (BUILT)
DELETE /api/files                         reworked (#14149): agent-attached unlink 400s without a valid
                                            `tool_resource` ∈ {execute_code, file_search, image_edit, context,
                                            ocr}; the non-owner via-agent fallback was dropped. Mobile already
                                            complies (owner manager sends neither agent_id nor tool_resource;
                                            AgentFilesDelegate always routes a valid resource). (NO CHANGE — documented)
DELETE /api/files                         (v0.8.8-rc2) response is now
                                            `{ message, deletedFileIds[], failedFileIds[] }` — additive, so it
                                            decodes on any client, but the SEMANTICS changed: **a partial
                                            delete answers 200**, with the outcome in the body rather than the
                                            status. Upstream's rule for clients is to read `failedFileIds` and
                                            "treat everything else they asked for as gone", which also keeps an
                                            older server (whose body names no ids) behaving as before. Mobile
                                            evicts requested-minus-failed and reports the partial. (BUILT)
POST   /api/files/images/…/avatar         (v0.8.8-rc3) gained a content-filter preflight that can answer **400**
                                            `{ error: 'content_filter_block', message, source, field }` on a
                                            deployment with active `filters` policies. A `{message}` envelope,
                                            so `extractErrorMessage` already surfaces the server's own sentence
                                            through both avatar upload paths. (NO CHANGE — documented)
GET    /api/share/:shareId                (v0.8.8-rc3) gained `shareIpLimiter` + `shareUserLimiter`, so this
                                            route can now answer **429** `{ message: 'Too many shared link
                                            requests. Try again later' }`. The 429 body carries NO machine-
                                            readable code — the `share_limit` ViolationType goes to the
                                            server's violation log, not the response — so status is the only
                                            signal. **Mobile does not call this route**: it has no public-share
                                            viewer, and the limiters are on `/:shareId` alone, not on the
                                            owner-side list/create/update/delete or on `/:shareId/fork`. So
                                            there is no reachable 429 today. `StreamErrorType.SHARE_LIMIT`
                                            exists for the error-payload path, where upstream's registry keys
                                            ViolationTypes off the same `type` field as ErrorTypes.
                                            (NO CHANGE — documented)
GET    /api/memories                      now returns EVERY memory of the user, agent-partitioned ones included,
                                            each with `agentId` (null = shared personal pool) and `agentName`
                                            (resolved server-side, present only when the caller may VIEW that
                                            agent). `tokenLimit`/`totalTokens` still count the shared pool only.
PATCH  /api/memories/:key                 + optional `?agentId=` query param — SELECTS THE PARTITION. Keys are
DELETE /api/memories/:key                   unique only *within* a partition, and the server's filter is
                                            `{ agentId: agentId ?? null }`, so omitting the param targets the
                                            shared pool: mutating an agent-scoped entry without it edits/deletes
                                            a different same-named shared entry (or 404s). Mobile threads
                                            `Memory.agentId` from the list row through repository → API. (BUILT)
POST   /api/memories                      + optional `agentId` in the body, partitioning the new entry to an
                                            agent. Mobile always omits it (shared pool) — no agent picker.
GET    /api/memories                      (v0.8.8-rc3) rows can now carry `contentFilterBlocked: true`, meaning
                                            the deployment's `filters.memories.pii` policy matched the entry and
                                            the server **BLANKED** `key`/`value`/`summary` to empty strings —
                                            they are not omitted. So an empty `value` is "withheld", not "the
                                            user stored nothing", and an empty `key` stops being a usable
                                            address. Reachable on a route mobile already calls, with no version
                                            gate. Two consequences mobile had to handle: two redacted rows in
                                            one partition used to collapse to the same Compose list key and
                                            CRASH the screen, and editing one would PATCH the blank back over
                                            the real content. `_id` is on every row and always has been (the
                                            query is an unprojected `.lean()` find), so its presence is NOT a
                                            version signal. The same projection runs on the PATCH response, so
                                            an edit can come back redacted. (BUILT)

# Added
POST   /api/agents/chat  (+ `compact`)     (v0.8.8-rc3) `compact: true` on the send payload turns the turn into
                                            a summarize-only one: the server summarizes the branch up to
                                            `parentMessageId` and ends WITHOUT a reply, persisting the summary
                                            as the boundary every later turn starts from. **No user message is
                                            created**, so the turn is regenerate-shaped — upstream's `ask`
                                            derives `isRegenerate = isRegenerate || compact` and sends both.
                                            `messageId` and `parentMessageId` are BOTH the branch leaf (the
                                            summary's parent and the server-side anchor), `text` is empty, and
                                            `overrideParentMessageId` stays null so the summary parents onto the
                                            leaf rather than replacing a sibling. Announced by
                                            `/api/config.compactionEnabled`
                                            (`appConfig?.summarization?.enabled !== false`, so true unless an
                                            admin disabled summarization) — a presence gate, no version check.
                                            Not offered on the assistants endpoints (their thread lives on the
                                            provider, so a local summary compacts nothing) nor on a leaf that is
                                            already a finished compaction. The reply arrives as SUMMARY content
                                            parts, not text, so no message deltas stream. (BUILT)
GET    /api/mcp/tools                     (v0.8.8-rc2) each `servers[name]` entry gained
                                            `authorizationState?: 'reauth_required'` and
                                            `authorizationGeneration?`, and each tool gained
                                            `serverToolName?`. **These are on the TOOLS response, not on
                                            `/connection/status`** — `reauth_required` is NOT a member of the
                                            connection-status `authorizationState` union, which is unchanged.
                                            Passive discovery notices a lapsed OAuth authorization here before
                                            the status route does; upstream folds it into the status map as
                                            `needs_authorization` rather than teaching every surface a new
                                            state, skipping the verdict when the live status contradicts it
                                            (actively connecting/authorizing, or connected with a DIFFERENT
                                            `authorizationGeneration` — the generation is the staleness
                                            tiebreaker). Mobile mirrors that fold. (BUILT)
GET    /api/mcp/connection/status         (v0.8.8-rc2) `MCPServerStatus` gained `requestScoped?` (the server
                                            only connects inside a chat request, so being `disconnected`
                                            between requests is not a fault), `configurationState?:
                                            'configured'|'needs_configuration'`, and `authorizationGeneration?`.
                                            All decode surface; only the generation is read, by the fold above.
PATCH  /api/memories/id/:id               (v0.8.8-rc2) `{ value, key? }` + optional `?agentId=` →
DELETE /api/memories/id/:id                 `{updated, memory}` / `{deleted}`. Addresses a row by its stable
                                            `_id` instead of by key. **There is no GET by id** — only these
                                            two. They exist because the key-addressed routes cannot reach a
                                            row whose key the content filter blanked, and are ambiguous by
                                            construction (keys are unique only *within* a partition). PATCH
                                            answers 409 on a key collision inside the partition and 404 on an
                                            unresolved id; its returned memory is content-filter projected.
                                            Mobile prefers these and falls back to the key-addressed routes on
                                            404 — except for a redacted entry, where no fallback can address
                                            anything. (BUILT)
POST   /api/convos/archive/all            (v0.8.8-rc2) no body — unlike every other convo mutation it reads
                                            nothing off `arg` — → `{ archivedCount }`. Archives every
                                            conversation the caller can currently see. Mobile surfaces it in
                                            Settings → Data beside Clear All; the local cache is bulk-updated
                                            rather than refetched. 404-probeable and gated on
                                            `featureSupport(…, "0.8.8-rc2")` — see VERSION_GATES.md. (BUILT)
POST   /api/agents/chat/resume            { conversationId, actionId, + the paused turn's endpoint/model/agent
                                            config, plus `decisions[]` (tool approval) or `answer`
                                            (ask_user_question) }. Resumes a run paused for human-in-the-loop
                                            review. Shares the chat router's middleware, and the server replays
                                            the paused turn's graph config from the pending action, so a crafted
                                            resume cannot swap the agent or tool set — it recomputes the request
                                            fingerprint and 403s a mismatch, which is why the client pins the
                                            turn config at pause ARRIVAL, not at decision time. The continuation
                                            arrives on the SSE stream that is still open (a paused run never
                                            emits `final`). Pairs with the `on_pending_action` SSE and the
                                            `requires_action` job status. NOT version-gated: the client only
                                            calls it in response to a server-announced pause carrying an
                                            actionId, which is itself proof the route exists — a date gate would
                                            instead strand real pauses on any server built past the pinned
                                            commit (BackendCommitMap → null → gate false). See VERSION_GATES.md.
                                            (#13942 + #14139, landed 2026-06-29 / 2026-07-08) (BUILT)
POST   /api/agents/chat/steer             { conversationId, text, files? } → 202 { status: 'queued', steerId,
                                            position, conversationId }. Queues a message for injection into the
                                            run that is ALREADY generating, at its next tool boundary;
                                            `streamId === conversationId` as everywhere else. Carries the same
                                            PII-filter + moderation + rate-limit chain as a normal message, and
                                            re-checks the caller against the ORIGINATING run's agent ACL (read
                                            from job metadata, never the request body — a steer cannot swap the
                                            agent). Server caps: 16k characters (`STEER_MAX_LENGTH`), 10 queued
                                            per run, 10 attachments.
                                            Rejections are ROUTINE, not errors, and the `code` — not the status —
                                            decides the client's fallback: 404 NO_ACTIVE_RUN (send as a new turn),
                                            409 RUN_PAUSED / 429 STEER_QUEUE_FULL / 501 STEER_UNSUPPORTED (hold
                                            in the client queue). Mobile treats an unrecognized code and a
                                            bodyless 404 from a pre-0.8.8 server the same way, so a wrong gate
                                            answer degrades instead of losing the message. Mobile sends no
                                            `files` — steering here is text-only and a during-run send carrying
                                            attachments is routed to the follow-up queue instead.
                                            (#14220, landedDate 2026-07-14) (BUILT)
POST   /api/agents/chat/steer/cancel      { conversationId, steerId } → { removed }. Withdraws a still-queued
                                            steer before injection. No moderation pass (nothing model-bound
                                            yet). `removed: false` is a 200, not a failure: the cancel lost its
                                            race (already injected, or the run ended) and the client defers to
                                            the events it will receive. (#14220, landedDate 2026-07-14) (BUILT)
POST   /api/agents/chat/queued-turns      { conversationId, parentMessageId, clientRequestId, text, files?,
                                            quotes?, manualSkills?, priority?, expectedPredecessorCreatedAt? }
                                            → 202 { receipt, capability } (200 on a replay of a settled row).
                                            Hands the SERVER a follow-up it will admit and RUN itself when the
                                            current turn finishes — unlike steering, which injects into a run
                                            already generating. Shares the steer limiters and the PII filter.
                                            `clientRequestId` (≤128 chars) is the idempotency key: a UNIQUE
                                            index on (tenantId, user, conversationId, clientRequestId) resolves
                                            a re-POST to the existing row, so a retry after a lost response is
                                            free — **but only while the id is stable across retries**. Reusing
                                            one for different text is 409 QUEUED_TURN_IDEMPOTENCY_CONFLICT.
                                            Other codes: 501 QUEUED_TURNS_UNSUPPORTED /
                                            QUEUED_TURN_PRIORITY_UNSUPPORTED, 429 QUEUED_TURN_QUEUE_FULL,
                                            503 QUEUED_TURN_SCHEDULING_PENDING (transient), 400 EMPTY_TEXT /
                                            QUEUED_TURN_TOO_LONG / INVALID_QUEUED_TURN, 404
                                            CONVERSATION_NOT_FOUND.
                                            **There is no server guard against enqueueing a turn and then also
                                            sending it the ordinary way** — the admission check is gated on
                                            `req._isAgentTrigger === true`, which an ordinary send never sets.
                                            The client refusing to drain a server-owned row is the only thing
                                            preventing a double send, which is why an ambiguous failure must
                                            reconcile by id rather than fall back. (#14512, v0.8.8-rc2) (BUILT)
GET    /api/agents/chat/queued-turns      ?conversationId=&clientRequestIds=a&clientRequestIds=b →
                                            { queuedTurns, capability, revision }. Read-only; no admission
                                            budget, so it is the safe way to resolve an ambiguous enqueue.
                                            Repeat the parameter per id — the server accepts a bare string for
                                            one and an array for several. Max 100 ids, each ≤128 chars, or the
                                            whole call is 400 INVALID_CLIENT_REQUEST_IDS. Passing the ids is
                                            what makes the answer exact proof about them. `position` is 1-based
                                            and numbers only the rows still queued/claimed. (BUILT)
DELETE /api/agents/chat/queued-turns/:id  → { receipt }. Withdraws a queued turn; only wins while the row is
                                            still `queued` or `claimed`. (BUILT)
GET    /api/schedules                 → { schedules[], limits }. Scheduled chats (v0.8.8-rc2): a prompt the
                                            SERVER sends to an agent on a cadence, filing each run's
                                            conversation under a chat project. **This is the ONLY route that
                                            wraps** — every other one answers a bare schedule.
                                            `limits` is served here on purpose: `minIntervalMinutes` lets a
                                            form refuse a too-frequent cadence instead of surfacing a 400
                                            after submit, `maxPerUser` caps the list, `requireProject` forces
                                            a destination, and `projectId` PINS one (the client must not then
                                            offer a picker — the server ignores any choice sent).
                                            Each row carries `inFlight[]`, the runs generating right now with
                                            the conversation each is producing. Read from the run rows, not
                                            from `lastRun`, which is projected only once a run settles; a run
                                            parked on an approval is deliberately absent. (BUILT)
GET    /api/schedules/:id             → a bare schedule. (BUILT)
POST   /api/schedules                 → 201 bare schedule. `clientRequestId` is REQUIRED and is an
                                            idempotency key: creation commits the row and arms it in two
                                            writes, so a failure between them leaves the client unable to tell
                                            whether anything persisted — a blind retry makes a SECOND
                                            recurring schedule. Must be stable across retries of one creation.
                                            `cadence` is a discriminated union on `frequency`: the structured
                                            arms (`hourly`/`daily`/`weekdays`/`weekly`) carry `hour` + `minute`
                                            (+ `daysOfWeek`), and `cron` carries `expression` instead. Sending
                                            a structured cadence WITHOUT `frequency`, or a cron one carrying
                                            `hour`, is a 400 — see the mobile note below. (BUILT)
PATCH  /api/schedules/:id             → a bare schedule. Every field optional; **omitting one leaves it
                                            alone**, which is what makes editing a cron schedule safe from a
                                            client whose controls cannot represent one. `expectedConfigRevision`
                                            is the revision the edit was computed from — the server fences on
                                            it and answers 409 rather than letting a concurrent edit be
                                            overwritten, which a fresh-read fence cannot catch because
                                            `cadence` is sent whole. A no-op update is a 400.
                                            `chatProjectId: null` CLEARS the scope. (BUILT)
DELETE /api/schedules/:id             → { id }. 200 erased, **202 still draining a live run**. (BUILT)
POST   /api/schedules/:id/run         → { scheduleId, conversationId, status:'started' }. 409 when a run is
                                            already in progress, 429 when the caller's own message limiter
                                            refuses, 400/503 with a `code` when the unattended MCP preflight
                                            fails. (BUILT)

Scheduled chats: three things that are easy to get backwards.

- **`interface.schedules` is ABSENT-means-OFF**, the opposite of every other `interface.*` flag.
  The feature is experimental and an admin opts in explicitly. Truth table, from
  `useSideNavLinks.ts:148-159` and `getLimits`: absent/`null` → off, `false` → off, `true` → on,
  `{}` → **on**, `{use:false}` → off. Upstream's own comment: *"Any mismatch would show an entry
  whose create/run operations the backend rejects."* The boolean form is a RUNTIME FEATURE DISABLE,
  not a permission denial (`RUNTIME_CONFIG_INTERFACE_FIELDS = {'schedules'}`), so a disabled server
  must not be reported as "you lack permission". Mobile reads it through one dedicated resolver,
  `isSchedulesEnabled`; do not fold it into a generic interface-flag helper.
- **Mounted is not enabled.** `app.use('/api/schedules', …)` is unconditional, so a 404 proves only
  that the server predates the feature. **Never derive enablement from a probe.**
- **Two different 503s, discriminated by `code`.** `SCHEDULES_NOT_READY` carries `Retry-After` and
  is the genuinely transient window while the engine arms; `SCHEDULES_UNAVAILABLE` is terminal for
  the life of the server process — arming is attempted exactly once at boot, so a client obeying a
  backoff would poll a condition that cannot change without operator action. Reads and DELETE never
  touch the engine, so a server whose engine failed to arm still lists and deletes.

Not ported, and why:
- **Attachments on a schedule** (`file_ids`, max 10, with a renewable bounded upload hold). Safe to
  omit only because PATCH is `.partial()`: an update that never sends the field leaves existing
  attachments alone. **Never send `file_ids: []`** — that detaches them.
- **The MCP recovery flow.** Upstream's card links to the agent so the user can reconnect a server
  in an interactive chat. Mobile shows the reason and the failed server names; the one-tap recovery
  is not built. `mcp_reauth_required` / `mcp_configuration_missing` / `mcp_permission_denied` stop a
  schedule immediately, so the reason has to render — a schedule that says only "paused" leaves the
  user with nothing to act on.
- **`target`.** Only `'new'` exists, so there is nothing to choose.

# Removed
POST   /api/endpoints/context-projection  REMOVED (#13953, landing commit 376370d6, 2026-06-25). The gauge is
                                            now computed client-side / seeded from the on_context_usage SSE, so
                                            the POST 404s on the 0.8.8 line. Mobile version-gates the call OFF
                                            (supportsFeature minVersion 0.8.8-rc1 — deliberately two-state, since
                                            the ungated branch just issues a POST whose 404 is discarded; was
                                            landedDate 2026-06-26 — one day
                                            past the landing, because three commits merged earlier that same day
                                            and the date gate is day-granular; see VERSION_GATES.md);
                                            < 0.8.8 backends keep the POST path. Inverts the >= 0.8.7 enable gate. (BUILT)
```

Response envelopes on the memories routes (unchanged by this cycle, corrected here because the
partition work above sits on them): the list route answers `{ memories, totalTokens, tokenLimit,
charLimit, usagePercentage }`, POST answers `{ created, memory }`, PATCH `{ updated, memory }`,
PATCH `/preferences` `{ updated, preferences: { memories } }`, DELETE `{ deleted }` — none of them
return the bare entity. `/preferences` also READS `{ memories: boolean }`, not `{ enabled }`. Memory
rows carry `updated_at` (snake_case) and no creation timestamp at all.

**Out of the 0.8.8 sync's scope, and not version-gated.** These envelopes are identical in v0.8.4,
v0.8.5, v0.8.6 and on the 0.8.8 line, so decoding them as bare entities was a pre-existing client
breakage against *every* supported server, and correcting it changes the memories screen's runtime
behavior on all of them — the list can now produce rows, edit/delete now hit the row the user picked,
and the enable toggle now reaches the server. It rode this branch only because the agent-partition
work (F9) sits on top of it and was otherwise unreachable — see the mandatory device-test item below.

### Mandatory device-test item: memories screen on a pre-0.8.8 server

Tracked apart from the 0.8.8 feature test plan because it is the only change on this branch whose
blast radius is every supported server, and because the 0.8.8 dev server cannot verify it: the
envelopes above are identical from v0.8.4 through the 0.8.8 line, so a pass there says nothing about
the older servers this also changes. Walk it against a v0.8.6 or v0.8.7 server before merge:

- **List** — the screen populates instead of showing the empty/failed state it showed before.
- **Edit** — editing a row changes that row's value and the change survives a reload.
- **Delete** — deleting a row removes that row and no other.
- **Enable toggle** — flipping memory on/off round-trips and survives a reload (the request used to
  send the `enabled` key, which the server rejects).
- **Row rendering** — rows show a last-updated time (from `updated_at`); no creation time exists.
- **Agent partitions (F9, dev server only)** — an agent-scoped row edits/deletes inside its own
  partition and leaves a same-key shared-pool row untouched.

### v0.8.8-line endpoints (built)
```
GET    /api/agents/:id/versions           → Agent[] version history. Requires EDIT on the agent; loaded lazily
                                            because histories are large — /expanded now answers with a `version`
                                            count and no `versions[]`. Mobile fetches it when the history sheet
                                            opens, guarded on the list already being empty so pre-0.8.8 servers
                                            (which still inline the array) pay for no second request.
                                            (#13977, 12fea693b, landed 2026-06-26)
GET    /api/user/settings/favorites/tools → TToolFavorite[] ({ itemType, itemId })
PUT    /api/user/settings/favorites/tools/:itemType/:itemId  → the added { itemType, itemId }
DELETE /api/user/settings/favorites/tools/:itemType/:itemId  → { ok: true }
                                            itemType ∈ {builtin, tool, mcp, skill}; itemId capped at 256 chars
                                            and 100 favorites per user, 400 otherwise. This is the real backend
                                            that replaced the v0.8.6 "skill favorites" client stubs; mobile now
                                            builds against it, so that backend-gap ledger entry is CLOSED.
                                            Gate: featureSupport("0.8.8-rc1").isRuledOut — suppressed only for a
                                            server PLACED below rc1 (a tag). A server the commit map cannot place,
                                            and a dev build still reporting 0.8.7, are probed instead: one GET, no
                                            rate limiter, and a 404 that turns pinning off rather than reporting a
                                            failure — latched so the picker does not re-ask on every open, reset on
                                            account switch. (#13952, landed 2026-07-05; the landedDate fallback was
                                            dropped at the rc1 sync and the probe replaced it 2026-08-18)
POST   /api/share/:shareId/fork           { targetMessageIndex? } → 201 with the forked conversation. Continues
                                            a SHARED conversation as the caller's own copy — distinct from the
                                            existing POST /api/convos/fork mobile already calls. Wired through
                                            ShareRepository but with NO caller: mobile has no shared-link viewer
                                            to fork from. Ungated — a pre-0.8.8 server 404s, which is the error
                                            a future caller has to handle anyway. (#13714, landedDate 2026-06-24)
POST   /api/files/usage                   { file_ids } → { held } (was { marked } before #14470). A RENEWABLE
                                            BOUNDED HOLD, not a release, so uploads sitting in a client-side
                                            queue are not reaped before they drain:
                                            expiresAt = max(expiresAt, min(now + renewMs, createdAt +
                                            maxLifetimeMs)) where renewMs = 24 h + checkpointer.ttl
                                            (FILES_USAGE_BASE_HOLD_MS) and maxLifetimeMs = 24 h +
                                            checkpointer.ttl × 8 (FILES_USAGE_QUEUED_RUN_ALLOWANCE). Widen-only,
                                            and `expiresAt: { $exists: true }` means an already-released file
                                            never gets a TTL back. Replay converges on a per-file ceiling
                                            instead of pinning forever, so a client that stops touching lapses
                                            one renewMs after its last call. No longer $inc: usage — a queue
                                            touch is not a send, and queued-then-drained files were landing at
                                            usage: 2. Called when a message is enqueued as a follow-up; capped
                                            at 10 ids per call server-side (upstream QUEUE_USAGE_MAX_FILES), so
                                            the repository chunks rather than forfeiting a whole batch. Exempt
                                            from the upload rate limiter ONLY on the 0.8.8 line that added it —
                                            older servers limit every POST under /api/files except /speech, so
                                            the call is version-gated (featureSupport 0.8.8-rc1: suppressed only
                                            for a server PLACED below rc1; an unplaceable server or a dev build
                                            reporting 0.8.7 gets exactly ONE touch and a 404 latches it off, since
                                            withholding it lets the reaper take a queued attachment out from under
                                            the send); error code FILES_USAGE_FAILED. NOW METERED by its own
                                            per-user limiter — FILE_USAGE_USER_MAX (default 120) per
                                            FILE_USAGE_USER_WINDOW (default 15 min), 429 { message: "Too many
                                            file usage requests…" } — and a breach LOGS A FILE_UPLOAD_LIMIT
                                            VIOLATION scored by FILE_UPLOAD_VIOLATION_SCORE, so it is no longer
                                            a free call. Still off the upload quota; trailing slash normalized
                                            (/usage/ hits the same limiter). Web renews on a 30-min heartbeat
                                            while anything is queued (useQueueDrain); mobile matches it —
                                            MessageQueueDelegate.startHoldRenewal touches at enqueue, then
                                            renews the whole queue every 30 min on the ChatViewModel scope.
                                            Deliberately a no-op once that scope or the process is gone: the
                                            queue is never persisted, so there is nothing left to hold. Mobile
                                            stays on the multipart-JSON upload path — #14295 / 2026-07-21 is the
                                            separate upload-SSE heartbeat work under F8, which is NOT adopted.
```
UI shipped alongside them: the unified Tools Marketplace picker in the agent editor (one catalog over
built-in capabilities, plugin tools, MCP servers and skills, with per-item favorites), the MCP OAuth
consent dialog, and agent contact info on agent detail.

Sandbox `read_file` images (U9) needed no rendering change: the tool builds its artifact as an inline
`data:` URI, but the agent callback runs `saveBase64Image` over every `image_url` part before emitting
the attachment, so the client receives a stored `/images/…` path and renders it through the existing
tool-call attachment path. `ImageUrlResolver` gained a `data:` passthrough as defence in depth only —
it is unreachable against the pinned server and fixes nothing that was broken. If a sandbox image is
observed not rendering, the fault is in the tool-call attachment path, not here.

Deliberately NOT ported from the same upstream window, each because it needs a mobile surface that
does not exist or is pointer-specific web polish: upstream's **StatefulSessions** panel (sandbox
session reuse), the `on_sandbox_starting` cold-boot indicator, the MessageNav rework (a pinned
scroll-to-bottom rib and hover chevrons; mobile already has a scroll-to-bottom FAB), and the web
touch select/drag fixes. None affects wire compatibility.

**OrchestrationHub was on that list and is now split** — the read-only half is ported. The entry
above previously read "OrchestrationHub and StatefulSessions panels … not ported"; that was true
when written and is no longer, so it is corrected here rather than left to contradict the code.

PORTED (v0.8.8-rc2 read-only child-thread viewing):
- `GET /api/convos/:parentConversationId/subagents` — the parent's child index.
- `GET /api/convos/:parentConversationId/subagents/:threadId` — one child's view, with `?taskId=`
  for a single execution boundary and `?cursor=` for the next older page. **The two are mutually
  exclusive: sending both is a 404**, which is why the client exposes them as two methods.
- A sheet off the existing subagent trace card, opening on the child LIST and then one child.

NOT ported, and deliberately:
- **Every control.** `POST …/:threadId/control` and its `SubagentControlAction` — `steer`, `queue`,
  `interrupt`, `cancel`, `cancel_message`. The receipt types (`SubagentControlReceipt`,
  `SubagentControlRequest`) are not modelled either; `controlReceipts` decodes as raw JSON so the
  view still parses, because giving the receipt a type is the first step toward wiring the route
  that produces one.
- **The per-task activity SSE stream** (`GET …/:threadId/tasks/:taskId/activity`). Live progress for
  the run in front of the user already arrives on `on_subagent_update`; this is a second live
  channel for a child the parent is not running, and read-only viewing does not need it.
- **Fork-to-chat and saved teams.** `subagents.graphs` round-trips on the agent (see the
  additive-fields policy below) and is not surfaced.

Why the views are worth having when mobile already renders a subagent trace: `on_subagent_update`
and the persisted `AgentToolCall.subagentContent` both hang off the parent's `subagent` tool call,
so between them they cover exactly the children a TOOL spawned, for the run in front of you. The
views add the three things that cannot arrive that way — a child spawned by an event binding
(`origin: "event"`, whose `parentToolCallId` is an `event-binding:…` sentinel and which therefore
renders nowhere today), a child's history ACROSS turns (`turns[]`), and the child's own messages.
`threadId` likewise appears on neither the SSE envelope nor the persisted trace, so the index is
the only place one exists.

Two shapes worth knowing before touching this:
- `GET /api/convos/:conversationId` **404s when the conversation is itself a subagent thread**
  (`convo.subagentThread != null`). A child is unreachable through the ordinary conversation route
  by design; the thread view is how it is read.
- The index's own `404` covers three unrelated conditions behind one body — parent missing, not the
  caller's, or itself a child. **None of them says the route is absent**, so nothing latches off it;
  see VERSION_GATES.md.

### Conversation trace viewer (v0.8.8-rc3)

A provider-neutral read of what the deployment's tracing backend recorded for a conversation's
turns. The server maps whatever its backend stores into the wire shapes, so the client never sees a
backend's field names, credentials, URL structure or API version — nothing on this side should grow
a Langfuse-shaped field.

PORTED:
- `GET /api/traces/:conversationId/availability` — whether this conversation has a readable trace.
- `GET /api/traces/:conversationId/records?cursor=` — newest turns first; `nextCursor` pages older.
- `GET /api/traces/:conversationId/records/:recordId?message=&source=` — one record's detail.
- A sheet off the chat overflow menu, which is where upstream puts it on mobile as well.

Five shapes worth knowing before touching this:
- **There is no `GET /api/traces/:conversationId`.** Only the three sub-routes exist
  (`api/server/routes/traces.js`), so the section is reachable in no other way.
- **`/availability` never fails.** Disabled, not found, not owned and every other refusal answer
  `200 { available: false }`; only an unexpected throw is a 500. So the gate has no error arm, and
  there is no response shape meaning "this deployment does not have the routes" — nothing latches.
  It is also deliberately NOT rate-limited, while the two record routes are, which is why the
  client's re-read loop bounds itself rather than relying on the server to stop it.
- **`retryAfterMs` is a wait, not a poll.** It is set only while the backend cannot decide yet
  (a run still being ingested). Bounded at ten re-reads client-side, as upstream bounds it.
- **`messageId` is required on the detail read and `sourceId` is not optional in practice.** The
  message id is the turn the list attributed the record to, and its traces are what authorize the
  read; `sourceId` names the backend project that served the PAGE, and a multi-project deployment
  serves different sources across pages, so a detail read must carry the source of the page the
  record came from rather than a global one.
- **Cost is filtered server-side by `interface.contextCost`** (`applyCostPolicy` in
  `packages/api/src/traces/handlers.ts`), and `showInputOutput` is enforced in the reader
  (`packages/api/src/langfuse/reader.ts`, surfacing as `contentAvailable: false`). Neither is
  mirrored client-side: a second gate here would hide what the deployment chose to show.

`interface.traceViewer` is **object-only and default-OFF**, and that is NOT the same rule as
`interface.schedules` despite the resemblance. `traceViewerSchema` is `z.object({…}).optional()`
with no boolean arm, and `resolveTraceViewerConfig` reads `config?.enabled === true` — so `{}` is
**off** here, where `schedules: {}` is **on**. Both absent-cases are off, which is itself unlike the
rest of `interface.*`. The two resolvers are separate functions in `InterfaceConfig.kt` and a test
pins the divergence; reusing one for the other compiles.

NOT ported, and deliberately:
- **The zoomable timeline, the collapsible record tree and the text filter.** Each trades screen for
  navigation, which is the wrong trade on a phone for a surface opened to answer one question. The
  records still nest — depth is computed and indented — they simply cannot be folded.
- **The Langfuse session link** (`GET /api/admin/langfuse/session/:conversationId`, gated on
  `startupConfig.langfuseConnectionAccess`). An admin route that opens a web console this app has
  no session for; it is also outside the provider-neutral contract the rest of this is built on.
- **`interface.currency`.** Not modelled on `InterfaceConfig` at all — a pre-existing gap, not one
  this introduced. Cost renders as USD, which is what the wire contract states.

Two divergences from upstream's own rendering, both deliberate:
- **Turns are newest first**, where the desktop viewer is oldest first. The surface is opened to look
  at the turn that just settled, and oldest-first means scrolling past every earlier turn to reach
  it; it also matches the direction the server pages in, so "load older" appends at the bottom.
- **A record with an unreadable `startTime` is kept and sorted last**, where upstream drops it
  (`toNode` returns null on a non-finite parse). On a diagnostic surface the malformed row is the
  one most likely to be what the user came to look at.

Revised message / SSE shapes:
- Message content parts add a `steer` type (`type == "steer"`, #14220) — mid-run steering. `ContentType`
  gained `STEER` and `MessageContentPart` a nullable `steer: JsonElement?`, so a persisted message carrying
  it deserializes instead of throwing `SerializationException` on conversation load (`ignoreUnknownKeys` does
  NOT rescue an unknown enum value). Parsed, not yet rendered as its own bubble: the injected instruction is
  visible through the reply it steers. (BUILT)
- `on_steer_applied` (#14220) — a queued steer reached a tool boundary and went into the run. The injected
  text rides the nested `part` (the `steer` content part above), not the top level, and the event races its
  own HTTP 202: it regularly arrives naming a `steerId` the sending client has not learned yet, so a consumer
  must RECORD applied ids rather than only remove a chip that may not exist. (BUILT)
- `resumeState.pendingSteers` on the sync frame (#14220) — the steers still queued for injection, replayed on
  reconnect. A full authoritative snapshot, not a delta: an EMPTY list is meaningful (chips the client is
  still showing were drained), while an ABSENT key is not (a server with no steering says nothing). (BUILT)
- SSE resumable-stream ordering is now preserved across turns (#14411 — the pinned target commit). Server-side
  ordering fix on resume/reconnect; no wire-shape change, transparent to the client.

Additive response fields (parse-layer only unless a row says BUILT):
- **Agent** (v0.8.8-rc2) — `git_identity` (`{name, email}`, the sandbox's commit author),
  `code_workspace_id` (the persistent workspace its sandbox attaches to), `skills_scope`
  (catalog exposure while skills are enabled; kept a raw String because a missing value has
  legacy meaning server-side and an unrecognized one would throw at decode and fail the whole
  agent), and `subagents.graphs` + `subagents.shareFiles`. **Round-trip only, per the standing
  policy for new agent fields** — decoded, carried on create/update, never surfaced — so an
  edit made on mobile cannot silently drop what an admin configured on the web. `graphs` stays
  raw JSON: the shape is a whole agent team (members, edges, entry and result nodes) and
  modelling it would imply an editor that is not being built.
- `GET /api/agents/chat/status/:conversationId` — adds `status` (`running` | `requires_action` | terminal),
  `pendingAction` (client-safe projection of a run paused for tool approval / `ask_user_question`;
  `requestFingerprint` and `resumeContext` are stripped server-side, so it must never be echoed back), and
  `unrecoveredSteers[]`. `active: true` now also covers a paused run, so it is NOT "tokens are arriving".
  **`unrecoveredSteers` is claim-on-read**: the server clears them once returned, so a client that ignores
  the list drops the user's queued words permanently. Only populated when the run is not active. Mobile now
  claims them on every resume-status read and re-homes them as queued follow-ups. (BUILT)
- `POST /api/agents/chat/abort` — adds `pendingSteers[]` (steers queued mid-run that never reached an
  injection boundary, handed back exactly once). `aborted` (the stream id actually aborted) already existed
  at v0.8.7 and is only newly modeled on mobile. The `final` frame carries the same `pendingSteers` list for
  a run that ended normally, so between the two every ending has a report. Mobile consumes both and turns
  them into queued follow-ups; a stream that dies on an error carries no report at all, and there the
  locally-held chip text is converted instead. (BUILT)
- `GET /api/user/terms` — adds `termsAccepted` / `termsAcceptedAt`; `POST /api/user/terms/accept` now returns
  `{ message, termsAcceptedAt }` instead of an empty body. `GET /api/user` adds `termsAcceptedAt`.
- `POST /api/mcp/:serverName/reinitialize` — adds `connectionDeferred`: the reinitialize was accepted but the
  connection is being established in the background, so `success` does not mean the server is reachable.
- Conversation + preset — add `reasoning_mode` / `reasoning_context` (Responses-API siblings of
  `reasoning_effort`); agents add `stateful_code_sessions` (persistent code-interpreter sandbox across a run's
  tool calls) and `memory_scope` (`"agent"` isolates memories per user+agent, `"user"`/null = shared pool).
  Round-tripped so a mobile edit doesn't drop what was set on web; no mobile editor controls.
- Model spec — adds `showInMenu`. The server already drops `showInMenu: false` specs from `/api/config`, so
  mobile never receives one; a hidden spec stays resolvable by name on a conversation.
- `GET /api/config` — adds `fileUploadSseEnabled` (`FILE_UPLOAD_SSE_ENABLED`, off by default). Detection-only:
  mobile stays on the multipart/JSON upload path regardless.

File-picker accept types (upstream `client/src/hooks/Files/useUploadOptions.ts`): the picker is filtered to
the endpoint's `supportedMimeTypes` allowlist from `GET /api/files/config`, translated into concrete types via
the `fullMimeTypesList` mirror in `core/model/.../PickerMimeTypes.kt` (a regex can't be handed to a native
picker). Applied on the two surfaces whose accept set upstream derives from that allowlist — the chat composer
attach and the files manager. The agent editor's code / knowledge / context pickers stay unrestricted on
purpose: upstream sources their accept sets from the per-`tool_resource` lists
(`codeInterpreterMimeTypesList`, `retrievalMimeTypesList`), not from `supportedMimeTypes`, so filtering them
on this allowlist would be the wrong restriction.

### v0.8.8-line partial sync (untagged dev commit 91adcf3f, 2026-07-29) — SSE / shape changes
Continues the range above; `package.json` still reports 0.8.7 and the commit is still untagged.
- `on_activity_label` (#14391) — the activity-group header over a reasoning+tool block.
  `{ index, part: { type:'activity_label', activity_label, tool_call_ids?, counts?, status?,
  agentId?, pending? }, responseMessageId?, conversationId? }`, where `index` is the ABSOLUTE
  content index. **Two emissions per block**: an empty reservation at the tool-batch boundary
  (`activity_label: ""`, `pending: true`), then the resolved label once the fast label model
  answers. Mobile drops the live event through `SseEventMapper`'s forward-compat `else -> null`
  and renders labels from persisted content instead — the same posture already documented for
  `on_subagent_update`. The persisted part is what matters; see the `MessageContentPart` note.
- `usage_type` on `on_token_usage` (#14391) — `summarization` | `subagent` | `sequential` |
  `activity-label`. Present ONLY on non-primary buckets; absent on the turn's own model call.
  These are separate model calls and must be **EXCLUDED** from the live context gauge and the
  breakdown sheet — mobile's handler is last-write-wins, so a bucketed event that gets through
  replaces the turn's Input/Output figures with the bucket's. Activity labels make that acute:
  one usage event per tool batch, default up to 20 per run, from a cheap fast model. Modeled as
  a plain nullable String, never an enum, so a bucket upstream adds later stays inert (non-null
  ⇒ excluded) rather than failing the decode. (BUILT)
- `GET /api/agents/chat/stream/:streamId` no longer waits for a subscriber before generation
  starts (#14423), and resume subscriptions are two-phase server-side (`activate()` after the
  sync frame). Contract-identical for the client and requires no change — recorded because it
  is the kind of thing that would look like the cause of a future resume bug.

### v0.8.8-line partial sync (untagged dev commit db431210, 2026-08-12) — generation protocol, HITL, MCP
Continues the range above; `package.json` still reports 0.8.7 and the commit is still untagged.

**Generation routes (`/api/agents/chat/*`).**
- `POST /chat/abort` validates its targets BEFORE resolving one: any of `streamId` /
  `conversationId` / `abortKey` that is present but zero-length or over 512 chars is
  **400 `INVALID_ABORT_TARGET`**. The user-scoped fallback is now gated on the literal `"new"`
  appearing in `streamId` or `conversationId` — an `abortKey` of `"new"` does NOT reach it. So an
  unknown conversation id must be sent as `conversationId: "new"`, never as an empty `abortKey`.
  Correct against older servers too: they skipped `"new"` when choosing a job id and then took the
  same fallback unconditionally. New codes on the route: `RUN_STILL_ACTIVE` (409 + `Retry-After: 1`,
  retryable), `RUN_REPLACED`, `AMBIGUOUS_ACTIVE_RUN`, `ABORT_PERSISTENCE_FAILED`. (BUILT)
- `GET /chat/status/:conversationId` can answer **503 `SERVER_NOT_READY` + `Retry-After: 1`**,
  regardless of negotiated protocol, while the route re-reads the job (up to 3×) to verify the
  resume snapshot's generation epoch, or while `terminalPersistencePending` is true. A transient
  race, explicitly retryable — treating it as "the run is gone" abandons a live run. (BUILT)
- `POST /chat/{endpoint}` start envelope gained `generationCreatedAt` and
  `generationProtocolVersion`, plus statuses `resumed` / `replaced` / `settled` (**no `streamId`**)
  / `predecessor_mismatch`. New **409 with no `code`** when the request's `parentMessageId` is a
  still-unsaved preliminary id ("Cannot submit a follow-up while the selected parent response is
  still being saved") — reachable by an ordinary send right after an abort, and retryable. Every
  OTHER 409 on that route is coded (`RESOURCE_RECOVERY_REQUIRED`, `RUN_REPLACED`,
  `GENERATION_PREDECESSOR_MISMATCH`, `RECOVERY_PAYLOAD_MISMATCH`) and must NOT be retried; the
  absence of a `code` is the discriminator. (BUILT)
- Generation protocol negotiation (`x-librechat-generation-protocol`, body and query markers; the
  **lower** wins). This client advertises nothing and stays on v1 deliberately. Consequence worth
  knowing: a v1 client is rewritten a `{final:true, reconcile:true, …}` frame into an ordinary
  `event: error` carrying only the sentence *"Generation state changed; reconnect to load the saved
  response."* and `generationProtocolVersion`. `final: true` there is a `writeEvent` OPTION consumed
  by telemetry, never a body field — and the genuine-error frame on the same route also carries
  `generationProtocolVersion`, so **the literal message is the only signal** separating a benign
  reconciliation (the reply is durable; refetch it) from a real failure. Registered as a mirror.
  Related: `TERMINAL_PUBLICATION_RECONNECT_ERROR` `res.destroy()`s the socket with no error frame at
  all, relying on the client treating an abrupt close as reconnectable — `SseClient`'s existing
  retry ladder already does. (BUILT)

**HITL (`ask_user_question`).**
- The interrupt payload gained `questions[]` (1–4 items; ids `/^[A-Za-z][A-Za-z0-9_-]{0,63}$/`,
  optional 80-char `header`) and `tool_call_id`. **`questions` alone selects the resume channel:**
  where it is an array the route requires `answers` covering every id exactly (a missing id, an
  empty answer, or an unknown id is 400) and rejects a bare `answer`; where it is absent only
  `answer` is accepted. `question` stays populated with the first item as a display fallback even on
  a batch, so branching on it renders one question and submits a body the route rejects.
  `MAX_ASK_ANSWER_LENGTH` is 16000. `moderateText` and the PII filter now scan `answers`. (BUILT)

**Content parts.**
- `activity_label` gained `activity_label_type` (**absent = the per-batch label**, `"phase"` = a
  parent phase), `activity_start_index`, `activity_count`, `agent_ids`. A phase part is APPENDED AT
  THE END of the content array while `activity_start_index` names where the phase began, so its
  position carries no scope — a renderer that treats any filled label as a batch header lets it
  claim the reply's tail. TEXT parts and run-step `message_creation` gained
  `phase?: 'commentary' | 'final_answer'`. (BUILT — phase labels are skipped in grouping; nested
  phase groups deferred, upstream re-anchored the bounds twice in #14729/#14741.)

**Permissions and keys.**
- `PATCH /api/share/:shareId` now carries the SHARED_LINKS **CREATE** permission (updating
  re-publishes conversation content). `DELETE` stays ungated. The route also no longer mints a new
  `shareId` — the link is stable across a re-publish. (BUILT)
- MCP tool keys embed `normalizeServerName(server)` (non-`[a-zA-Z0-9_.-]` → `_`, ends trimmed,
  hashed to `server_<n>` if nothing survives), but `GET /api/mcp/servers` still reports the RAW
  configured name. Registered as a mirror. (BUILT)
- `PATCH /api/mcp/servers/:serverName` can answer **400 `MCP_OAUTH_SECRET_REENTRY_REQUIRED`**: the
  stored client secret is bound to the authorization/token endpoint it was issued for, so changing
  either invalidates it and every retry of the same body fails identically. Raised from
  `ServerConfigsDB.update` alone — the create route never runs the check, so an edit sent as a
  create cannot produce it. `handleMCPError` puts the code under **`error`**, not `code` as the
  generation routes do. (BUILT — edit-mode saves now PATCH; they used to POST a create.)

**Additive decode surface.** `isShared` on list-fetched conversations (derived per request, never
persisted, absent from single-conversation payloads — so null means *unknown*); `adminPanelURL`
(admin-gated, so its PRESENCE is the admin signal and it must never be cached across accounts) plus
`langfuseFanoutEnabled` / `langfuseConnectionAccess` on `/api/config`; `isEditable` on agent list
rows **only** — `getListAgents` stamps it and neither `GET /api/agents/:id` nor `/expanded` carries
it, so the list read records the verdict for the detail screen to narrow its own per-agent EDIT
probe with (upstream documents fail-OPEN, this client applies it fail-CLOSED);
`owner_contact` **no longer carries `email`** (security advisory);
`flowId` / `oauthTimeout` / `failureReason` / `missingUserVars` / `authorizationState` on the MCP
reinitialize response, plus `authorizationState` per server and `oauthTimeout` on the envelope of
`GET /api/mcp/connection/status`. (BUILT)

**Typed errors.** `ErrorTypes` gained `resource_recovery_required` (required CodeAPI files could not
be restored before the model ran — user must reattach; previously the run continued on stale image
URLs). Separately, a LangChain `MODEL_NOT_FOUND` documentation URL in provider prose is matched by
regex — `/langchain\.com\/.*\/MODEL_NOT_FOUND(?:\/|\b)/i` — and replaced with localized guidance;
it is NOT an `ErrorTypes` value. (BUILT)

**Verified non-changes, recorded so they are not re-discovered.** A Swagger 2.0 action spec is
rejected by `validateAndParseOpenAPISpec` (no `servers` array) *before* `validateActionDomain` runs,
so a synthesized `host`+`basePath` domain is never compared. `validateActionDomain`'s new port check
compares `getExplicitPort(clientDomain)` against `specUrl.port || protocol default`, so posting
`servers[0].url` verbatim can never mismatch — including an explicit default port, which WHATWG
strips from both sides. The actions route LOGS `Port mismatch:` / `Domain mismatch:` and returns a
fixed generic sentence, so that text never reaches a client.

**Mirrors (Phase 0).** The 12 mirrors registered before this sync were checked over
91adcf3f→db431210 and one reported DRIFT: `memory-storage-error-types`, a **file-mode** watch on
`packages/api/src/agents/memory.ts`. Examined and dismissed — the change is `registerMemoryTools`
gaining a `toolNames` field in its return type; the two literals the entry guards
(`errorType: 'already_exceeded'` and `'would_exceed'`) are byte-identical at both revisions, so
nothing was owed on the Kotlin side. A file-mode entry reports any churn in its file by design, so
expect this one to fire again on the next unrelated edit and re-verify the two literals rather than
the file. Further mirrors were registered as the sync went on — the schedules cadence pair with C14, four
trace-viewer entries with C13, the queued-turn classifiers, and the run-step close statuses. The
count is whatever `python3 scripts/check-mirrors.py --list` prints; this sentence has now carried a
wrong one three times, so it no longer carries one at all.

### v0.8.8-rc1 sync (tag v0.8.8-rc1, commit eaef87fa, 2026-08-14) — shape changes and divergences

Message / content shapes:
- `MessageContentPart` gains `activity_end_index` (#14768) — the EXCLUSIVE end of a parent activity
  phase's span. The marker itself trails its span (phases split past ~200 chars of label text, so
  several markers can land in one response), which means a phase's position carries no scope; the
  `[activity_start_index, activity_end_index)` pair is authoritative. Mobile consumes it to suppress
  late per-batch labels a finalized phase absorbed (mirroring web `findLateActivityLabelsConsumedByPhase`);
  the collapsed parent-group UI itself remains deliberately unported. (BUILT)
- A TEXT part's `text` can arrive as `{value, annotations}` (#14770 / d920328b): the PUT
  message-content edit spread-preserves the part, so an edited part PERSISTS its text as the
  annotated-object form and every later fetch returns it. Mobile decodes both shapes via a tolerant
  serializer (`FlexibleTextSerializer`), normalizing to the string; `annotations` are dropped
  (nothing renders them) and re-encoding writes the plain string. Correct on every server, no gate. (BUILT)
- Whole-message copy serializes ALL parts (web `serializeMessageForClipboard`, d920328b): tool calls,
  reasoning, media and steer parts as labeled blocks. Mobile mirrors it in
  `feature/chat/.../util/MessageClipboard.kt`; `getMessageText` (TTS / edit prefill) is unchanged. (BUILT)

HITL / generation protocol:
- `POST /api/agents/chat/resume` takes `generationCreatedAt` — the run's generation epoch, from the
  start POST's `generationCreatedAt` or `GET /chat/status`'s `createdAt` (newly modeled). The server
  fences a mismatched resume 409 RUN_REPLACED; omitting stays legal but unfenced. Mobile records the
  epoch and echoes it on every resume. (BUILT)
- `PendingAction.expiresAt` is now consumed: the card dismisses with expiry copy when it passes, and
  a resume 409 arriving past it maps to the same copy instead of a retryable failure. (BUILT)
- Pauses from agents SDK <= 3.3.8 omit `payload.tool_call_id`; the streaming-card suppression now
  scopes to ONE call (question-text match, else first unanswered) instead of hiding every unanswered
  ask — two parallel asks used to render as one. (BUILT)
- A multi-question ask batch is answerable from the composer, one send per question; the resume goes
  up only when every id has an answer (a partial `answers` map is 400). (BUILT)

Uploads / files:
- Upload rejections now answer 400/415 with a `{message}` body (5e464bc9: 415
  "Unsupported file type: <mime>", 400 "No file provided", 415 on import-JSON) instead of a bare
  500. Mobile's error pipeline already surfaced server-authored `{message}` bodies end-to-end;
  pinned by test, no production change. (VERIFIED)
- `mimeTypeAliases` gains `application/x-shellscript` and `text/x-shellscript` → `application/x-sh`
  (5e464bc9). Mirrored in `UploadRouting.kt` behind an `isCompatibleOrNewer(v, "0.8.8-rc1")` gate —
  a pre-rc1 server does not normalise them. Registered mirror `mime-type-aliases`. (BUILT)
- `fullMimeTypesList` gains `.potx` (`application/vnd.openxmlformats-officedocument.presentationml.template`,
  6c46fd12). Mirrored in `PickerMimeTypes.kt`; the picker offers it only at ≥ rc1. Extension→MIME
  resolution (CommonMimeTypes / IosFilePicker) is ungated. Registered mirror `full-mime-types-list`. (BUILT)

Quotes (v0.8.7 feature, capture newly built):
- `ChatRequest.quotes: string[]` (5eb1c2c1 #13868, first tag v0.8.7 — NOT in 0.8.7-rc1): the server
  merges the excerpts into the user message as Markdown blockquotes and persists/echoes
  `message.quotes`. Mobile now CAPTURES quotes too — Android selection-toolbar "Add to chat" →
  pending chips → drained onto the next fresh send (or composer-origin queue item).
  Regenerate/edit-assistant replay the parent user message's persisted quotes (web `overrideQuotes`
  parity); continue/edit-user send none; assistants endpoints are skipped. Gated
  `isCompatibleOrNewer(v, "0.8.7")`, fail-closed. (BUILT)

**CORRECTED at v0.8.8-rc2 — "server steers never carry quotes" is FALSE, in both directions.**
`POST /api/agents/chat/steer` takes `quotes` in its BODY (`SteerMessageParams.quotes`), and the
persisted `steer` content part carries them back, rendered by web as reference blocks with the same
component normal message quotes use — not folded into the text. A composer steer therefore TAKES
the staged excerpts now rather than leaving them for the next send.

The recovery contract is a **capability echo, never a version gate**. `SteerMessageResponse` echoes
`quotesAccepted` only when the durable item kept the quotes; a pre-quotes server 202s while silently
dropping them. Four rules, and the second is the one that looks wrong and is not:

1. **Send** the excerpts on the steer, taken from the composer onto the steer's fallback spec — so
   every route out (injection, a rejection that re-homes to the queue, a terminal leftover) carries
   or restores them.
2. **An ACK with `quotesAccepted` absent does NOT re-stage.** The steer is still queued and will
   still inject; re-staging here would deliver the excerpts twice, once inside the injected part and
   once on the next send. Upstream is explicit about this (`useSteering.ts:1649-1657`).
3. **`on_steer_applied` whose part carries NO quotes is where the loss is real** — the words went in
   bare and the local record holds the only copy, so the excerpts are re-staged there, before the
   record becomes a tombstone.
4. **A settled receipt replay** (`settled` without `leftover`) re-stages immediately: the steer has
   already left the queue, so no future event will name it. `leftover` is excluded because that
   branch re-homes the whole spec into the follow-up queue, whose normal send delivers quotes on any
   server.

**Both restore paths must stay idempotent or the chips duplicate.** Mobile has two independent
guards: `mergeRestagedQuotes` (dedupe-append, capped at 10, mirroring upstream) and stripping the
excerpts off the record's spec once the chips hold them.

The reports that hand a steer back (`resumeState.pendingSteers`, the final frame, the abort ack)
also carry its `quotes` now, still claim-on-read — so a steer with no local spec re-homes with the
reported excerpts attached to the follow-up built for it.

Three parts of the rc2 steer surface are deliberately NOT ported:
- **`generationCreatedAt` on the steer POST** — a server-side fence against a run that has since
  been replaced. Mobile already fences locally with `SteerRecord.turnEpoch`, which exists for
  exactly that failure (a slow 202 from a finished turn attaching to the current one). Sending the
  epoch would need it plumbed out of `PendingActionDelegate`, and mobile would gain nothing it does
  not already have. `clientSteerId` IS sent — it costs nothing (the placeholder already exists) and
  is what correlates an event that beats its own POST.
- **`on_steer_updated` / `TSteerUpdatedEvent`** — a preempt-label change on a still-queued steer.
  Mobile never asks to `preempt`, so every steer it sends is `preempt: false` and the event could
  only ever relabel a chip to what it already reads. Lands in `SseEventMapper`'s `else -> null`.
- **Rendering steer quotes as reference blocks.** The persisted `steer` part carries them and web
  draws them with the same component normal message quotes use. Mobile renders the part's text as a
  user turn and ignores the excerpts — a display gap on a persisted part, separable from the
  send/recovery contract above.
- iOS capture is DEFERRED (deliberate): the chips display/removal plumbing is commonMain and renders
  on iOS, but nothing stages a quote there — the capture affordance is the Android text-context-menu
  provider (`AddToChatSelectionMenu.kt`), and CMP's iOS text-context-menu API surface differs and
  needs its own investigation. Android-only until then; do not re-flag as a gap.

Server-side queued turns (v0.8.8-rc2) reverse a mobile invariant, deliberately:

**#167 stated that a queued item's lineage is recomputed LIVE at drain, so each send chains onto the
freshly finalized turn.** That is still true for every legacy row, and it has to be: the
second-and-later item in a queue has no parent at the moment it is composed — its parent is the
reply to the item ahead of it, which does not exist yet. A row the SERVER owns cannot work that way,
because the server does the admitting and must be told what to admit behind. So `parentMessageId`
and `expectedPredecessorCreatedAt` are captured at ENQUEUE for those rows only, and the
`QueuedMessage` KDoc now says so rather than asserting the old rule over code that no longer honours
it.

The two are reconciled by the SERVER, not by the client. `parentMessageId` is an **anchor**, not a
literal parent: `latestAssistantDescendant` (`packages/api/src/agents/queuedTurns.ts:357`) walks
forward from it to the newest assistant message that descends from it, and uses THAT as the new
turn's parent — so a queue of several chains correctly from one captured anchor. The anchor upstream
sends is the running turn's **user** message (`clientQueueParentMessageId`, set to `intermediateId`
in `useChatFunctions.ts:661`), because the reply's own id is a synthesized `"${userMessageId}_"`
placeholder the server never persists. Mobile sends the same thing: while a run streams,
`displayMessages` is truncated at that leaf, so its tail IS the anchor. The enqueue route does not
validate it — a stale anchor surfaces at admission as `PARENT_NOT_FOUND` and kills that turn only.

Two fields on the rc3 surface are decode-only and nothing may branch on them:
- **`capability.durability`** — `packages/api/src/agents/queuedTurnHttp.ts:31` hardcodes
  `{ supported: true, durability: 'durable' }` and all six response sites use that one constant, so
  `process_local` is unreachable. The `process_local` in `IJobStore.ts` is a different union for a
  different subsystem; do not treat the two as related.
- **`revision`** — an immutable queue sequence, not a version counter. It is what server-owned rows
  are ordered by; `position` renumbers as predecessors settle, so two rows read in different polls
  can claim the same one.

Not ported, and why:
- **The reveal placeholder.** Upstream shows an admitted turn as the next user message while the
  server's run starts (`useQueuedTurnReveal.ts`). Mobile discovers and ATTACHES to that run (the
  existing `/chat/status` + resume path), so the reply streams — but the user's own words are not on
  screen until the turn finalizes. **The blocker is that seeding them means writing
  `messages`/`displayMessages` while a run is live**, which is the streaming-anchor invariant's
  exact failure mode (the path is truncated at the anchor for the stream's duration, and a rebuild
  un-truncates it — the #169 class). A second problem compounds it: admission mints an ordinary
  message id (nothing in `triggers.js` derives one from `clientRequestId` or `queuedTurnId`), so a
  seed cannot reconcile away by id the way the handoff seed does — `finalizeChatDisplay` clears
  `pendingResumeUserMessage` at Final either way, but `mergeFinalMessagesInMemory` replaces BY ID,
  so the unmatched seed would linger in `messages` as a phantom sibling until the next load.
  Display-only: no invariant depends on it and it cannot cause a double send.
- **`priority` / interrupt-and-send.** The route answers 501 `QUEUED_TURN_PRIORITY_UNSUPPORTED`
  unconditionally in rc3, so there is nothing to send.
- **Upstream's queue ordering.** `compareQueuedMessages` sorts the whole list by `createdAt`; mobile
  has a manual reorder to preserve, so server-owned rows sort first by `revision` and the legacy
  rows keep the order the user put them in.

Agent editor:
- The unified tool picker now mirrors web `buildCatalog` gating (catalog.ts): generic plugins only
  under the `tools` capability, and `ask_user_question` as a builtin-style row only when its OWN
  capability is enabled AND `/api/agents/tools` lists the plugin (also excluded from the generic
  loop — no double-list). Fail-open on an empty capabilities list, per the existing convention. (BUILT)
- Agent update stale-200 (da390fa9, server bug fixed in rc1): audited clean on mobile — the update
  response is never cached (invalidate-and-refetch), so the "Save reverted" failure web had was
  never reachable here. (VERIFIED)

Deliberate divergences (a future sync must not "fix" these):
- **Streaming cursor stays; word fade-in is NOT adopted.** Upstream ae24461146f4/8f1f43f33e73
  replaced the streaming cursor with per-word fade-in (default ON) and deleted the cursor CSS.
  Mobile keeps its inline streaming cursor (device-approved, `StreamingCursor.kt`): the markdown
  renderer has no efficient per-word fade path — the parse/render pipeline would re-render the
  whole tail per word, the exact per-flush cost class the cursor work eliminated. Precedent:
  the app-bar model-selector removal. Future syncs must not re-add fade-in.
- The message-row layout reorg (7694428c / d920328b) is not ported wholesale; only its one behavioral
  nugget — in-flight steer chips right-aligned as user-side turns — was taken.

`@librechat/agents` ^3.4.5 → ^3.4.6 (298a3d9e; external repo danny-avila/agents) — client-visible
wire verified UNCHANGED. Evidence (github.com/danny-avila/agents/compare/v3.4.5...v3.4.6): the diff
adds an `ON_RUN_STEP_CLOSED` library event, RunStep terminal timestamps
(`created_at`/`completed_at`/`cancelled_at`/`failed_at`), `ToolCompleteEvent.completed_at`, a
Langfuse tracing refinement, and an Anthropic citation-accumulation fix. At **rc1** none of it
reached this client: the rc1 SERVER registered no handler for `ON_RUN_STEP_CLOSED` (grep of `api/` +
`packages/api/src` at eaef87fa found no reference), so the event was never relayed onto the SSE
stream, and the new step/tool fields are additive keys the mobile decode ignores
(`ignoreUnknownKeys`). The citation fix corrects content the server aggregates, not a shape.

**CORRECTED at v0.8.8-rc2 — `ON_RUN_STEP_CLOSED` went from inert to live.** The server now relays
it (`packages/api/src/stream/GenerationJobManager.ts`, `RedisJobStore.ts`) and persists its verdict
onto the tool-call part as `runStepStatus` / `runStepDurationMs`. Mobile maps it in
`SseEventMapper` (`on_run_step_closed` → `StreamEvent.ToolCallClosed`) and decodes the two
persisted fields on `AgentToolCall`.

Two things about the payload decided the mapping's shape. Its `id` is the **step** id, while every
tool-call event on this side is keyed by the tool_call id and the closure carries no tool_call of
its own — the announcing `on_run_step` is the only frame where both appear, so the mapper records
the pairing there. And it is **not** a replacement for `on_run_step_completed`: upstream keeps its
own progress heuristic for parts saved before the event existed and for endpoints that never emit
it. What it adds is the only signal separating a STOPPED step from one still in flight — steps
swept at end-of-run because the caller aborted close with `cancelled`, and without it an aborted
run leaves its tool cards spinning.

### v0.8.8-rc2+ — `/images/*` requires credentials BY DEFAULT (breaking, silent)

Upstream PR #15252 ("Require Credentials for Local Image Access by Default") landed in rc2. The
static mount serving generated images, tool-call image outputs and stored `/images/…` avatars is now
behind `validateImageRequest`, and the default flipped: `secureImageLinks` is
`z.boolean().optional()` with no schema default at both rc1 and rc3, but the app-config service went
from passing `undefined` through (rc1) to computing `config.secureImageLinks !== false` (rc3). **An
unset setting now means secured**, which is every deployment that never opted out.

- The middleware authenticates on `req.headers.cookie` alone — it extracts `refreshToken`, and there
  is **no `Authorization` branch, no query token and no signed URL**. A bearer-token client cannot
  satisfy it. Browsers are unaffected (`res.cookie('refreshToken', …)` sets no `path`, so it defaults
  to `path=/` and rides every same-origin `<img>` request), which is why upstream would not notice.
- rc3 rewrote it again: it no longer merely `jwt.verify`s but calls `findSession({userId,
  refreshToken})` against `session.refreshTokenHash`, which `generateRefreshToken` overwrites on
  every refresh. **Rotation hard-invalidates the previous refresh token server-side immediately, and
  a stale copy is a 403, not a 401.**
- Rejections are `res.status(401).send('Unauthorized')` — `.send`, not `.json`, so there is no
  `{message}` envelope and this does not decode into the server-authored-message error pipeline.
  403 `'Access Denied'` means a present-but-invalid cookie.
- **Not announced on `/api/config`**, so no version gate can detect it; a 401 on `/images/*` is the
  only signal.

Mobile consequence before the fix was worse than missing images: Coil resolves the app's
**authenticated** Ktor client, so an image 401 entered `AuthInterceptorPlugin`'s refresh-and-retry
leg, 401'd again, and emitted session-expired — **opening any conversation containing a generated
image signed the user out**. Fixed by `isSecuredImagePath` (keeps the mount out of that leg) plus
`ImageCookiePlugin` (attaches the account's refresh-token cookie, authority- and path-gated, stripped
across cross-authority redirects, with a one-shot 403 retry for the rotation race).

**Guardrail — do not undo this on iOS.** The cookie is safe to send because nothing persists it:
Ktor's Darwin engine calls `setHTTPCookieStorage(null)` unconditionally, so there is no jar to merge
from or write into and nothing reaches `Cookies.binarycookies`. That call runs **before** the user's
`config.sessionConfig(this)` block, so adding a `configureSession { }` or `usePreconfiguredSession`
to any iOS Ktor client would **silently restore the jar** and create a real leak. None exist in
`core/`, `shared/` or `app/` today. The `NWConnection` SSE transport is unaffected either way — it
hand-writes its headers over a raw socket and never constructs an `NSURLSession`.

**Historical note on re-checking this:** while the `upstream/` submodule was pinned at rc1 it did not
contain PR #15252, so reading `upstream/api/server/middleware/validateImageRequest.js` showed the old
default-OFF behaviour and led straight to "there is no problem here". The submodule now points at
v0.8.8-rc3, so the checkout is authoritative again — but the general rule stands for any sync in
progress: read `git show <target-tag>:<path>` inside the submodule rather than the working tree, which
is pinned to the PREVIOUS target until the bookkeeping commit lands.

### Other
```
GET/POST/DELETE /api/presets
GET/POST/PUT/DELETE /api/prompts
GET/POST/DELETE /api/tags
GET/POST/PATCH/DELETE /api/share
GET /api/balance
GET /api/search
GET /api/user
GET /api/banner
```

---

## Data Models

### User
- id, name, email, username, avatar, role, provider
- emailVerified, twoFactorEnabled
- favorites, termsAccepted

### Conversation
- conversationId (UUID), title, user, endpoint, model
- agent_id, assistant_id, tags, isArchived
- Model parameters (temperature, top_p, etc.)

### Message
- messageId (UUID), conversationId, parentMessageId
- user, sender, text, content (structured parts)
- isCreatedByUser, model, endpoint
- files, attachments, feedback
- error, unfinished, finish_reason, tokenCount

### MessageContentPart
Discriminated by `type`: `text`, `think`, `text_delta`, `tool_call`, `image_file`,
`image_url`, `video_url`, `input_audio`, `agent_update`, `summary`, `activity_label`,
`steer`, `error`.

`steer` (v0.8.8 line, #14220) and `activity_label` (v0.8.8 line, #14391) both PERSIST into
saved message content, so both must be declared client-side: `ContentType` has no property
default, so an undeclared value is not rescued by `ignoreUnknownKeys` and fails the whole
message decode — which takes conversation load with it. The `steer` omission above was a
documentation gap from the prior sync, not a new value. Both are now rendered as well as
declared (see `feature/chat/CLAUDE.md`), from persisted content only — mobile drops the live
`on_activity_label` event through the forward-compat `else -> null` branch.

`activity_label` carries its label as a top-level plain string (`{"type":"activity_label",
"activity_label":"Searched the codebase", "tool_call_ids":[…], "counts":{…}, "status":…,
"agentId":…, "pending":…}`); mobile models the label, `pending` and `status`, and
`tool_call_ids`/`agentId` already existed on the part. `counts` is still unmodelled. Empty
label + `pending: true` is the reservation form, which renders as nothing.

`steer` carries the user's text as a top-level plain string alongside `steerId`, `files` and a
`createdAt` that is **epoch millis, a number** — unlike every other part's ISO-string
`createdAt`. Mobile shares one `createdAt: String?` field across part types, so only
`librechatJson`'s `isLenient` keeps that from throwing; dropping that flag would fail the whole
`GET /messages` decode. `steerId` and `files` are unmodelled (steer attachments render as
text-only).

**SUMMARY part wire shape (v0.8.5+)** — context-compaction emits a content part with
fields at the top level (not nested under a `summary` key):
```
{
  "type": "summary",
  "content": [{"type":"text","text":"..."}],  // array OR string (two variants)
  "tokenCount": 42,
  "summarizing": false,
  "summaryVersion": 1,
  "model": "gpt-4o",
  "provider": "openai",
  "createdAt": "2026-04-22T...",
  "boundary": {"messageId": "...", "contentIndex": 0}
}
```
Variants for the body text (mirrors upstream `BaseClient.getSummaryText`, last-wins):
1. `content: Array<{type:"text", text}>` — new default since v0.8.5.
2. `content: string` — intermediate variant; rare but emitted by some code paths.
3. No `content`; `text: "..."` at the top level — legacy fallback from pre-v0.8.5
   summarization or test fixtures.

Mobile's `MessageContentPart.content` is typed `JsonElement?` to absorb variants 1/2,
and falls back to the existing `text: String?` field for variant 3.

### File
- file_id, filename, filepath, type, bytes, source
- user, conversationId, messageId

---

## SSE Streaming Protocol

### Flow
1. POST to `/api/agents/chat` with message payload → returns `{ streamId }`
2. Connect SSE to `GET /api/agents/chat/stream/:streamId`
3. Receive events: message, step, created, attachment, final, sync, error
4. On disconnect: reconnect with `?resume=true` for sync event

### Agent-library event names (v0.8.5)
`on_message_delta`, `on_reasoning_delta`, `on_run_step`, `on_run_step_delta`,
`on_run_step_completed`, `on_chat_model_end`, `on_agent_update`, `attachment`, and —
added in v0.8.5 — `on_summarize_start`, `on_summarize_delta`, `on_summarize_complete`.

`on_summarize_complete` payload nests the finished summary block under a `summary` key
(distinct from the message-persistence SUMMARY content part described in
`MessageContentPart`):
```
{"id":"...","agentId":"...","summary":{"type":"summary","content":[{"type":"text","text":"..."}],...}}
```
Mobile only renders the compacted summary once it is persisted to the final message as
a SUMMARY content part; the delta/lifecycle events are surfaced as status only.

v0.8.6 adds `on_subagent_update` (envelope `{ event, data:{ phase, parentToolCallId, data } }`)
for the web subagent live-trace dialog. Mobile drops it via `SseEventMapper`'s forward-compat
`else -> null` branch with no crash; the subagent's actual reasoning/tool-calls/text still
arrive folded into the parent run's normal `on_run_step`/`on_message_delta` events, so
subagent activity renders today as ordinary parent-agent tool calls. A dedicated trace UI is
deferred.

### SSE Event Format
```
event: message
data: {"type":"content","chunk":"...","status":"streaming"}

event: message
data: {"type":"tool_call","toolName":"...","input":{...}}

event: message
data: {"sync":true,"resumeState":{"runSteps":[...],"aggregatedContent":[...]}}
```

### Reconnection
- Exponential backoff: 1s, 2s, 4s, 8s... max 30s
- Max 5 retries
- Resume preserves state via sync event

---

## Authentication Details

### Token Management
- JWT access token in `Authorization: Bearer <token>` header
- Refresh token stored as HTTP-only cookie (for web; Android should store securely)
- Access token expiry: ~15 minutes
- Refresh token expiry: ~24 hours
- Auto-refresh on 401 response

### OAuth Flow (Android)
- Open browser/Custom Chrome Tab for OAuth provider
- Callback redirect to app via deep link
- Exchange code for token

### Browser-header invariant — one header MUST be present, two MUST be absent

These two rules are inverses of each other and are recorded together on purpose: satisfying one
by "completing" the browser impersonation breaks the other, and nothing else in the codebase
states both.

- **`User-Agent` must look like a browser.** The stock server's `ua-parser-js` middleware answers
  403 and soft-bans the client on the **first** non-browser UA to reach one of its routes. Applied
  in `applyBrowserDefaults` (`core/network/.../client/LibreChatHttpClient.kt`) for every Ktor
  client, and hand-written in `core/network/src/iosMain/.../sse/SseHttpTransport.ios.kt` for the
  iOS SSE socket.
- **`Origin` and `Sec-Fetch-Site` must NOT be sent.** From **v0.8.8-rc3**, `requireSameOrigin`
  (`api/server/middleware/requireSameOrigin.js` → `createSameOriginGuard`) guards
  `POST /api/auth/login`, `POST /api/auth/2fa/verify-temp` and admin local login. Its
  `isCrossSiteRequest` passes a request carrying *neither* header — upstream's own comment: *"A
  request carrying neither header did not come from a browser page and passes."* A real mobile
  `Origin` can never match `DOMAIN_CLIENT`, so adding either header fails login and 2FA with
  `403 { message: 'Cross-site request rejected', code: 'auth_cross_origin' }`. Note the value is
  under **`code`**, which is `ServerErrorCode`'s key — `StreamErrorType.AUTH_CROSS_ORIGIN` models
  the same string as an error-payload `type`, which is a different channel and not the one this
  route uses. What a user would see is the server's own `message`, via `extractErrorMessage`.

Both directions are locked by `core/network/src/androidUnitTest/.../client/UserAgentGuardTest.kt`,
which asserts through the real client factory. The iOS SSE transport's hand-written header block
cannot be reached from a JVM test and is the one uncovered path — check it by hand when touching
those headers. The failure only reproduces against rc3+ servers, so it reads as a server bug.

---

## UI/UX Reference (from Web App)

### Theme
- Light: White bg (#fff), text #212121, surface hover #e3e3e3
- Dark: #0d0d0d bg, text #ececf1, surface hover #424242
- Material 3 equivalents should be used on Android

### Key Screens
1. **Server URL Entry** (Android-only onboarding)
2. **Login/Register** with social login buttons
3. **Chat List** (sidebar on web → drawer or dedicated screen on Android)
4. **Chat View** (messages + input)
5. **Landing/New Chat** (greeting + model icon)
6. **Model Selector** (dropdown → bottom sheet on Android)
7. **Settings** (tabbed → Material 3 navigation)
8. **Agent Marketplace** (grid of cards)
9. **Search** (full-text search)
10. **File Viewer/Picker**

### Navigation Patterns (Android Adaptation)
- Web sidebar → Navigation drawer or bottom navigation
- Web modals → Bottom sheets or new screens
- Web dropdowns → Material 3 menus or bottom sheets
- Web hover actions → Long-press menus or always-visible icons

### Responsive Behavior
- Single-pane on phones (chat list or chat view, not both)
- Potential dual-pane on tablets
- Bottom navigation for primary actions
