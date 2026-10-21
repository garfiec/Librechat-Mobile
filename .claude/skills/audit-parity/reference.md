# audit-parity: the method

Every agent in the `audit-parity` workflow reads this file first. Change a rule here and it
changes everywhere.

## What we are looking for

Each area of the app (or one feature, when the user names one) is audited against two sources of truth:

1. **The server at the pinned commit.** Its routes, controllers, services, validators and models
   define what each call accepts, what it returns and every way it can fail.
2. **The reference client** (the upstream web client by default). It shows how the server is meant
   to be used: which calls come before which, which gates and settings it honours, and how it
   recovers.

The findings are **bugs and gaps in what we already ship**. A feature we don't have at all is a
`missing-feature` finding: give the title and the reference's `path:line` in `theirs`, keep
`trigger` and `evidence` to a line, and don't investigate further. Missing features are listed,
not verified.

**Scope is a starting point, not a fence.** Follow calls wherever they lead. Report a defect that
belongs to another area only when it is central to yours; that area's own audit will find the
rest.

**Known issues.** `known.md` lists filed issues, recorded bugs, backend-less web features and
deliberate divergences.
- A defect that is already **filed**: still report it, with `duplicate_of: "#N"`. The audit
  needs the count, and the issue may be incomplete.
- A **closed** issue whose defect still reproduces in the code: report it with
  `duplicate_of: "#N (closed, regression)"`.
- **Deliberate divergences** and **backend-less features**: don't report them (see below).

There is **no cap** on findings. Report everything that survives your own check. A finder who
stops at five has hidden the sixth.

## Reading the pinned upstream

The submodule checkout is often NOT the pin. Always read server and web files at the pinned
commit:

```
git -C <repo>/upstream show <pin>:api/server/routes/share.js
git -C <repo>/upstream grep -n "SHARE_EXISTS" <pin> -- packages api
```

Cite upstream evidence as `path:line` at the pin (`routes/share.js:75`). Use `git show` with
`"<pin>:<path>"`; never read `upstream/` from the working tree.

## The three lenses

Each finder gets one lens. A finding outside your lens is still welcome; just put it under the
right `kind`.

### Contract (kind `contract-bug`)

Trace every call the area makes into the server handler, then check:
- **Every status code** the handler and its middleware can send, and what our code does with
  each one. Example: the app waited for a 403 before asking for the 2FA code; the server sends
  400 (#577).
- **Error codes in the body** (`SHARE_EXISTS` → 409, #578) and whether we map them to anything
  better than the raw message.
- **Request shape**: field names, `{ arg: … }` wrapping, optional vs required fields, which fields
  the validator accepts (a backup code where we only send a TOTP code, #582).
- **Response shape**: fields we ignore that change meaning (a cursor, `hasNextPage`, a
  `termsAccepted` flag).
- **Pagination**: do we only ever load the first page? (#584)
- **Idempotence and repeats**: what happens on the second call (re-share, re-create)?
- **Wrong verb or route**: a GET that regenerates (`View backup codes` called the regenerate
  route, #582), a route the server never mounted.
- **Gating**: role permissions, `GET /api/config` flags, `BackendVersion` gates.

### Behaviour parity (kind `behaviour-gap`)

Read the reference client's flow for the same feature or screen, from the component down through the hook
to the data-service call, and compare:
- **Ordering**: what does it look up before it writes? (the web looks up an existing share
  before creating one.)
- **Gates and flows**: terms acceptance, email verification, re-auth, confirmations (#581).
- **Settings it honours**: a setting we show that has no effect is a bug (#586).
- **Parsing**: inline markers, special tokens, formats the server emits for this area
  (citation markers `turn0search0`, #583).
- **Recovery**: what it does on failure, retry, refetch, optimistic rollback.
- **Platform parity inside our app**: Android does it, iOS doesn't, or the reverse (#580, #587).

A difference in **layout or styling** is never a finding. A difference in **what the user can do
or what happens to their data** is.

### Dead wiring and failure states (kind `dead-wiring` or `failure-state`)

Things that exist in our code and don't work:
- **Screens and routes nothing navigates to** (`VerifyEmail` and `Terms` existed, #581). Grep
  every caller of the composable and of its nav key.
- **Preferences nothing reads**, and settings rows that write a key no one consumes (#586).
- **Stubs**: "not available yet" dialogs, `TODO()`, empty `actual` implementations, iOS
  `expect/actual` that do nothing (#587).
- **Failures shown as empty data**: a load that fails and renders the empty state, "Not set" or
  an empty list (#585).
- **Fire-and-forget writes**: a sheet or editor that closes before the server answers and ignores
  the result, so a rejected save loses input (#579).
- **Cancellation**: a Stop or Cancel that doesn't cancel the work still in flight (#580).

## Evidence rules

- Every finding cites **our side** (`path:line`) and, where the server or the reference is the
  reason, **their side** (`path:line` at the pin).
- A claim that something is **absent** ("never called", "not handled", "no iOS implementation")
  needs several searches: the symbol, its string resource, the route path, callers of the
  function or composable, and the `commonMain`, `androidMain`, `iosMain` source sets plus
  `iosApp/` Swift. Absence claims are the ones most often wrong. Say which searches you ran.
- Name the **trigger**: the concrete steps or server response that produce the bug.
- If it depends on server configuration (a flag, a role, a provider), say which.
- Nothing is run on a device. Say what a device check would add, if anything.

## Not findings

- Layout, spacing or visual-style differences from the web.
- Admin-only routes (`admin/*`) unless our app calls them.
- Features listed in `.claude/skills/sync-upstream/reference/upstream-backend-gaps.md` (no working
  backend).
- Deliberate divergences recorded in the repo's `CLAUDE.md` files or in `known.md`. If you suspect
  a difference is deliberate but can't find it recorded, report it with `maybe_intentional: true`.
- Hypotheticals with no trigger ("could race if…" with no path to the race).

## Severity

- **high**: a user can't do the thing at all, loses data or input, or is locked out (account
  deletion impossible with 2FA).
- **medium**: the feature fails in a common case, shows wrong data, or a visible control does
  nothing.
- **low**: a rare path, a cosmetic misreport, a setting with little effect.

## Verifying a finding

You are trying to **break** it. Default to `REFUTED` when the evidence is not solid and
`PLAUSIBLE` when it hinges on runtime data you can't see.
- Open every cited line yourself. Does it say what the finding says?
- Trace the path end to end, including callers and the other platform's source set.
- For server claims, read the handler and middleware at the pin yourself. A third-party reference
  client (`--ref <path>`) is read from its own working tree.
- Correct line numbers, severity, trigger and kind where they're wrong.

## Issue format

Filed issues use the repo's style. Titles start `[Bug]:` for bugs and `[Feature]:` for gaps that
are really new capability. Labels: `bug` or `enhancement`, plus `priority: high|medium|low`, plus
`well-scoped` when the fix is contained.

```
## Summary
What the user does, what goes wrong, which platforms.

## Root cause
Our code path with path:line, then the server or reference behaviour with path:line at the pin.

## Expected
What should happen. Cite how the web does it when that helps.

## Fix
Bullets: the change, edge cases, and the test that pins it.

## Notes
Related things out of scope. How it was found only if it matters ("found by reading the code,
not reproduced on a device").
```

When `--ref` is a third-party client, never mention it in an issue. Describe the bug from our
code and the server alone. The upstream web client may be cited.
