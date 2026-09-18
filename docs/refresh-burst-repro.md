# Reproducing the refresh burst (#376)

How to make the app fire a storm of rejected `POST /api/auth/refresh` at a real server, for anyone
re-verifying the fix or investigating a related report.

**Read this first: there is a deterministic in-process repro, and it is the one to use.**
`RefreshBurstThroughClientTest` (`:core:data`) drives the real `LibreChatHttpClient` + `SwitchGate` +
`CommonTokenDataStore` with a mock engine and counts POSTs at the refresh endpoint. It measured **10
POSTs** against the pre-fix source and **2** after. The live procedure below exists to confirm the
client behaves the same against a real LibreChat and a real proxy — not to discover the bug.

The live run has been done, against LibreChat v0.8.7 on an emulator, with both builds installed in
turn and the POSTs counted in an nginx access log in front of the server. Every row is one cold start
onto chat:

| Condition | Build | Refresh POSTs | What it establishes |
| --- | --- | --- | --- |
| dead refresh token | **pre-fix** | **20**, all 403, inside 5 s | the reported burst |
| dead refresh token | post-fix | **2**, both 403 | fixes 1 + 2 together |
| valid token | post-fix | **1**, 200 | the success path still coalesces |
| a bouncer 403ing all of `/api/` | post-fix | **1**, 403 | **session kept**, not dropped |
| that block lifted | post-fix | **1**, 200 | **recovers with no re-login** |
| a bouncer 403ing **only** `/api/auth/refresh` | post-fix | **1**, 403, with **17 × 401** behind it | the reactive path is damped too |
| that block lifted | post-fix | **1**, 200 | the stand-down does not strand the session |

20-in-5-seconds is the reporter's description ("15+ in a few seconds") reproduced on a single start,
from one screen, on a LAN with no latency to widen the window. Their remote server behind CrowdSec had
every reason to produce more.

Two caveats on that 20, both of which cut toward it being a floor. The launch printed
`Warning: Activity not started, intent has been delivered to currently running top-most instance`, so
it may have been a warm start rather than a cold one — which narrows the fan-out, not widens it. And
while the wire count is corroborated twice (nginx's log, plus an independent per-pid tally of 21
`refresh_rejected` in the app's own diagnostics), the split *within* it — roughly 8 from the proactive
fan-out and the rest from 401'd requests each running their own reactive ladder — is inferred by
differencing cumulative diag totals, not measured per phase. Mark the log depth before each phase
(below) and that split becomes a measurement too.

The bouncer rows are the compounding half of the report, and they are the ones worth re-running on any
change to 403 handling: with a simulated bouncer returning a 403 HTML page (containing the word
"banned", which the pre-fix substring check matched on) to **every** `/api/` route, the app errored
but did **not** sign out — one POST, classified `refresh_forbidden_unattributed` — and when the block
was lifted the stored refresh token produced a 200 with **no login POST at all**. Pre-fix, that same
response dropped the tokens down two independent paths.

**Block the refresh route on its own as well as blocking everything — the two are different tests,
and only the narrow one exercises the reactive path.** A 403 on a data route never enters the
401→refresh ladder, so a bouncer covering all of `/api/` produces a low POST count for a reason that
has nothing to do with damping: no 401 ever happens. Scope the block to `= /api/auth/refresh` and data
routes still reach LibreChat and 401 on the expired bearer, which is what a proxy rate-limit rule on
the auth endpoint actually looks like. That configuration measured **1 POST with 17 × 401 behind it**
and 25 `refresh_suppressed`; in process, before the endpoint-wide stand-down existed, the same shape
measured **8** and repeated on every fan-out.

## Verifying against the in-process repro

```
./gradlew :core:data:testDebugUnitTest --tests '*RefreshBurstThroughClientTest*'
./gradlew :core:data:testDebugUnitTest --tests '*CommonTokenDataStoreRefreshBurstTest*'
./gradlew :core:network:testDebugUnitTest --tests '*BanClassificationTest*'
```

**Always run a negative control** — revert the source, keep the tests, re-run. Of the eight tests in
`CommonTokenDataStoreRefreshBurstTest` only two (the success-path fan-out and the 401 ladder) are
controls that pass either way, so confirm the other six actually fail against pre-fix code: a green
suite alone proves nothing about whether they detect the burst. Pre-fix numbers, for comparison:

| Check | Pre-fix | Post-fix |
| --- | --- | --- |
| End to end, 7-wide fan-out | 10 POSTs | 2 |
| Proactive fan-out alone (7 requests) | 7 | 1 |
| Two accounts, 7 each | 14 | 2 |
| LibreChat's own 403 (the ladder) | 3 | 1 |
| A proxy-shaped 403 | `HardExpired` (tokens dropped) | `Transient` (slot kept) |

## Live procedure

This has been run end to end against LibreChat **v0.8.7** on an emulator; the numbers each step is
expected to produce are quoted inline. What it confirmed, beyond the client's POST counts:

- **`Invalid refresh token` is the literal body.** `403`, `content-type: text/html`, 21 bytes, no
  JSON envelope. The `refresh-403-rejection-bodies` mirror matches real bytes, not just a code read.
- **The refresh route really does ignore the User-Agent.** `POST /api/auth/refresh` with
  `User-Agent: ktor-client` and a valid token answers **200**. The reporter's UA hypothesis is dead
  empirically, not only from reading `auth.js`.
- **A missing credential is a 200**, body `Refresh token not provided` — never a 403.
- **The server rotates the refresh token on every success**, and a replay of a consumed one answers
  **401**, which is the status the client deliberately ladders on. Nothing in the unit suite covers
  rotation (its mock engine returns a fixed body), so a rotation bug would surface here as a 401
  storm and nowhere else. Phase A below is that check.

### 1. Stand up LibreChat

Use the pinned upstream checkout's compose files as a starting point — `upstream/` is a read-only
submodule, so **copy** what you need out of it rather than editing in place. Note that a fresh
worktree does **not** have the submodule populated; `LibreChat-OfficialRepo/` or
raw.githubusercontent is the quicker read.

Pin the image to the version under test rather than building from source —
`ghcr.io/danny-avila/librechat:v0.8.7` and `:v0.8.8-rc1` are both published. `mongodb` + `api` are
the only services auth testing needs (`SEARCH=false` drops Meilisearch; RAG only warns).

Put **nginx in front** and give it a log format carrying status, `$http_user_agent` and
`$cookie_refreshToken`. The POST count is the entire measurement, and this is what makes it
first-hand. It also shows the refresh client's `ktor-client` UA next to the API client's browser one.

`.env` minimums for a container that boots: `JWT_SECRET`, `JWT_REFRESH_SECRET`, `CREDS_KEY` (32-byte
hex), `CREDS_IV` (16-byte hex), `ALLOW_REGISTRATION=true`, `SEARCH=false`.

### 2. Shorten the access-token lifetime

`SESSION_EXPIRY` in `.env`; 60 s (`SESSION_EXPIRY=60000`) works. Keep it comfortably above 30 s: the
app's proactive-renewal window is `PROACTIVE_RENEWAL_SKEW_MS` (20 s), and a deployment issuing tokens
already inside that window trips the runaway guard (`refresh_proactive_disabled`) and disables
proactive renewal for the slot, which suppresses the very behaviour you are trying to watch.

**Check the device's clock against the server's before trusting any run.** A Pixel 9 Pro emulator
measured **19 s behind** the host (`adb shell date +%s` vs `date -u +%s`), and the renewal decision is
made on the *device's* clock: a token the server sees 8 s from expiry still reads as 27 s away in the
app, outside the window, so nothing fires. That first run reported zero refresh POSTs and looked like
a flawless pass. A Play-Store system image cannot be rooted, so the clock cannot be corrected —
instead **force-stop the app and let the token age well past expiry** before cold-starting, which
puts it inside the window on either clock.

### 3. Induce the exact 403 — rotate `JWT_REFRESH_SECRET`

This is the whole trick, and it targets the precise branch the reporter hit:

1. Log into the app normally and confirm it works.
2. Change `JWT_REFRESH_SECRET` in the server's `.env` and **recreate** the api container (an
   `env_file` is read at container create, so a plain `restart` keeps the old secret).
3. The stored refresh token now fails `jwt.verify`, so `refreshController`'s `catch` arm answers
   **`403 'Invalid refresh token'`** — the dead-credential arm, deterministically, on every attempt.

**Order matters, and getting it backwards invents a server bug.** The token has to be minted *before*
the rotation. A token minted after it verifies fine, so a 403 in that case is the bench's own fault
and reads convincingly like a mismatched-secret server or an intercepting proxy.

To confirm the lever independently of the app, replay a token you minted before the rotation with
curl, **sending the app's exact request shape** — `CommonTokenDataStore` sends the refresh token both
as a hand-built `Cookie: refreshToken=` header *and* as a JSON body, and a probe carrying only one of
them can land on a different arm and answer 200.

Levers that do **not** work, and why:

- **Deleting the refresh cookie.** A missing cookie returns **`200`** with the body
  `Refresh token not provided`, never a 403 — measured, not inferred. There is also no cookie jar to
  empty, because the app builds that header by hand.
- **Sending a non-browser User-Agent.** `router.post('/refresh', refreshController)` carries no
  middleware at all — no `uaParser`, no `checkBan`, no rate limiter. Measured: `ktor-client` with a
  valid token answers **200**.
- **Replaying an already-used refresh token.** That is a **401**
  (`Refresh token expired or not found for this user`), not a 403, because each success rotates the
  token. Useful to know when reading a log: a 401 in a burst may be a replay rather than a dead
  session.

### 4. Widen the fan-out

The burst size is the number of requests that overlap the window before the first refresh resolves, so
anything that widens that window raises it:

- **Pick a wide screen.** A cold start or a chat screen fans out further than Settings, which measured
  7 concurrent renewals in the reporter's diagnostics. A cold start onto chat measured **21–22
  requests** reaching the server, of which **9 to 18** asked for a renewal — so the fan-out alone is
  enough and no amplifier was needed to reproduce the burst.
- **Add latency to `/api/auth/refresh` specifically.** A proxy `delay` on that one route holds the
  flight lock longer and lets more requests queue behind it. This is the most reliable amplifier,
  because it widens the window without slowing the requests that have to pile up inside it.
- **Throttle the connection.** Device-side throttling or a delaying proxy works but is blunter — it
  slows the fan-out as well as the refresh, so it raises N less than delaying the one route does.
  `adb reverse` can interpose a proxy on a physical device.

### 5. Observe

Any per-request access log in front of the server is enough — nginx or Caddy will do; CrowdSec is not
needed to see the pattern. Record the POST count, the spacing, and the User-Agent. Note that until the
browser-UA change lands on the refresh client (deferred), these POSTs are the app's only requests that
present `ktor-client`, which makes them easy to isolate in a log.

On a debuggable build the diagnostics can be read without the UI at all:

```
adb shell run-as com.garfiec.librechat.debug cat files/diag_logs/diag.log
```

The two runs' tallies, which is where the mechanism becomes visible rather than merely counted:

| App event | Valid secret | Rotated secret | Pre-fix, rotated | Bouncer 403 | Block lifted |
| --- | --- | --- | --- | --- | --- |
| `refresh_proactive` (asks) | 9 | 18 | 8 | 8 | 8 |
| `refresh_coalesced` | 8 | 8 | 0 | 0 | 7 |
| `refresh_suppressed` (the fix) | 0 | 8 | **not in the build** | 7 | 0 |
| `refresh_rejected` (LibreChat's own 403) | 0 | 2 | 20 | 0 | 0 |
| `refresh_forbidden_unattributed` | 0 | 0 | **not in the build** | 1 | 0 |
| `session_expired` / `_torn_down` | none | present | present | **none** | **none** |
| POSTs on the wire | 1 | **2** | **20** | **1** | **1** |

`diag.log` is cumulative across every run on the device, so mark its line count before a phase and
tally only the lines added after — a bare `uniq -c` over the whole file attributes earlier phases'
events to the current one.

That `refresh_forbidden_unattributed: 1` is also the guard against a **vacuous** pass on the bouncer
row. "Didn't sign out" is satisfied just as well by never attempting a refresh at all, and a 403 on a
data route does not enter the 401 ladder — so the thing to check is that a refresh was classified,
not merely that the app stayed logged in. The attempt came from the *proactive* path (expired access
token at cold start), which is why it fired even with every data route blocked.

In the post-fix rotated run the arithmetic closes exactly — `8 coalesced + 8 suppressed + 2 sent =
18 asks` — and the pre-fix column shows the burst arriving through *both* paths: 8 proactive asks
became 8 POSTs with nothing suppressing them, and the remaining 12 came from 401'd requests each
running their own reactive ladder.

Verify the control really is a control before trusting a delta like that: the pre-fix APK contains
**zero** occurrences of `refresh_suppressed` and the post-fix one contains it
(`unzip -p app-debug.apk 'classes*.dex' | strings | grep -c refresh_suppressed`). Two builds that
differ only in a marker you can grep for is cheaper than trusting that the right APK was installed.

Client-side, `Settings → export diagnostics` and look for `refresh_proactive`, `refresh_suppressed`,
`refresh_coalesced`, `refresh_rejected` and `refresh_forbidden_unattributed`. **Export before clearing
app data or reinstalling** — the original report's attached log was taken after a re-onboarding and so
contained no trace of the incident.
