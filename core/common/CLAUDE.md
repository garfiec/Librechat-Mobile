# core:common

Pure Kotlin utilities shared by all modules. This is the lowest layer -- no other `:core:*` module is a dependency.

## What This Module Provides

- **Result sealed class** (`result/Result.kt`): `Success<T>`, `Error(exception, message)`, `Loading`. Used by repositories and ViewModels to propagate outcomes.
- **Dispatcher & Scope DI** (`di/CommonModule.kt`): Named Koin qualifiers (`named("io")`, `named("default")`, `named("main")`) for dispatchers and `named("applicationScope")` for coroutine scope. Always inject dispatchers -- never hardcode `Dispatchers.IO`. The sole exception is `safeApiCall` / `apiCallCatching`, which read the platform `ioDispatcher` directly; see below for why.
- **Extensions** (`extensions/`): `StringExt`, `RelativeTimeReference`, `DayBoundary`.
- **Date/time formatting** (`datetime/`, #445): the pure half of every displayed date. Read it before
  adding a date anywhere.
  - `DateTimeFormatPrefs` (style / clock / date format) resolves once into `ResolvedDateTimeFormat`, which holds
    every LDML pattern. Resolving is the expensive part (a skeleton lookup); formatting against a
    resolved pattern is cached per (pattern, locale, zone) and cheap enough per row.
  - `messageLabel` / `listLabel` / `toDateGroup` return descriptors (`TimestampLabel`, `DateGroup`),
    not strings: the relative wording is plural resources in core/ui, resolved in composition.
  - The actuals format with **ICU on Android** (not java.time — ICU skeletons can emit `B`, which
    API 26 java.time rejects) and with a **constructed NSLocale on iOS**, never `currentLocale`,
    which ignores the in-app language until relaunch and lets the device 12/24h toggle override an
    explicit `HH`.
  - Under plain JVM unit tests the ICU stubs return null, so tests pass a fake
    `InstantPatternFormatter`. `PlatformDateFormatAndroidTest` (Robolectric) and
    `PlatformDateFormatIosTest` cover the real platforms.
- **ConnectivityObserver**: Wraps Android `ConnectivityManager.NetworkCallback` to detect network changes. Used by SSE reconnection logic.

## safeApiCall Pattern

All repository implementations use this to wrap network calls. It does two things: run the block on
the IO dispatcher, and turn a failure into a displayable `Result.Error` (`toSafeError` classifies it
into a `FailureKind` and screens server text before it can reach the UI).

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    apiCallCatching({ Result.Success(block()) }) { it.toSafeError() }
```

This lives here (not in `:core:network`) because repositories in `:core:data` call it.

**The dispatcher hop is deliberately here rather than at the call sites**, and it is the one
sanctioned exception to the "always inject dispatchers" rule stated above. Ktor's engine does its socket
I/O on engine threads, but the continuation resumes in the caller's context — so body
deserialization, the auth plugin's `401` branch, the token refresh it drives and that refresh's
keystore-backed reads all run wherever the call was launched from. Several cold-start paths launch
from `viewModelScope`, which put all of it on the UI thread (#326). One hop in the wrapper every API
call already funnels through covers all of them and cannot be forgotten by a repository added later.
A Ktor plugin was the other candidate. It would have to cover two pipelines rather than one — the
send, and the response pipeline that `body()` drives — and it still would not cover the Room and
DataStore work that sits in the same block as the call.

`apiCallCatching(block) { e -> ... }` is the same hop and catch with the mapping left to the caller,
for the handful of calls that classify their own failures (a bespoke validation message, or a
failure that is expected and must not be logged as an error). Use it rather than hand-rolling a
`catch (e: Exception)` around an API call. Prefer `safeApiCall` unless you are one of those.

**Never let a catch around suspending code swallow `CancellationException`.** A cancelled job that
catches it keeps running and writes a stale error to state after the user has left. Put
`catch (e: CancellationException) { throw e }` ahead of any `catch (e: Exception)` whose `try`
suspends, and use `suspendRunCatching { }` (`result/SuspendRunCatching.kt`) in place of
`runCatching { }` when the block suspends. Import `kotlinx.coroutines.CancellationException`, never
the `java.util.concurrent` one, in commonMain.

## Rules

- **Pure Kotlin preferred.** Minimal Android dependencies (only what ConnectivityObserver and Koin require).
- **No network or data dependencies.** This module must not depend on `:core:network`, `:core:data`, or `:core:model`.
- Dependencies: `coroutines-core`, `coroutines-android`, Koin.
- Convention plugin: `librechat.mobile.library` + `librechat.mobile.koin`.
