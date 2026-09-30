# Build Logic

Convention plugins that apply consistent Gradle configuration across all modules.

## Convention Plugins (13 total)

### Android
| Plugin ID | Class | What it does |
|-----------|-------|-------------|
| `librechat.mobile.application` | `AndroidApplicationConventionPlugin` | AGP application plugin, compileSdk 37, minSdk 26, JDK 17 |
| `librechat.mobile.library` | `AndroidLibraryConventionPlugin` | AGP library plugin, same SDK/JDK config |
| `librechat.mobile.compose` | `AndroidComposeConventionPlugin` | Compose compiler, Compose BOM, Material 3 |
| `librechat.mobile.koin` | `AndroidKoinConventionPlugin` | Koin core + Android dependencies |
| `librechat.mobile.feature` | `AndroidFeatureConventionPlugin` | Auto-applies: library + compose + koin + serialization + Nav 3 |
| `librechat.mobile.room` | `AndroidRoomConventionPlugin` | Room + KSP, schema export config |
| `librechat.detekt` | `DetektConventionPlugin` | Detekt static analysis (cross-platform, applied to all modules) |

### KMP
| Plugin ID | Class | What it does |
|-----------|-------|-------------|
| `librechat.kmp.library` | `KmpLibraryConventionPlugin` | KMP library setup (Android + iOS targets) |
| `librechat.kmp.compose` | `KmpComposeConventionPlugin` | Compose Multiplatform + Material 3 |
| `librechat.kmp.koin` | `KmpKoinConventionPlugin` | Koin multiplatform dependencies |
| `librechat.kmp.feature` | `KmpFeatureConventionPlugin` | Auto-applies: kmp library + compose + koin + serialization + Nav 3 |
| `librechat.kmp.room` | `KmpRoomConventionPlugin` | Room multiplatform + KSP |

### Shared
| Plugin ID | Class | What it does |
|-----------|-------|-------------|
| `librechat.kotlin.serialization` | `KotlinSerializationConventionPlugin` | Kotlinx Serialization plugin + JSON dependency |

## Critical: apply() Signature

Convention plugin `apply()` MUST use:
```kotlin
override fun apply(target: Project) {
    with(target) { ... }
}
```
Do NOT use `override fun apply(target: Project) = with(target) { ... }` -- that returns
the `with` result instead of `Unit`, which causes a Gradle error.

## Version Catalog

All dependency versions live in `gradle/libs.versions.toml`. Never hardcode versions in
build.gradle.kts files. Use `libs.` accessors (e.g., `libs.koin.android`, `libs.ktor.client.core`).

## Feature Module Convention

Feature modules use `id("librechat.kmp.feature")` (KMP) or `id("librechat.mobile.feature")` (Android-only) which auto-applies library, compose,
koin, serialization, and Nav 3. They only need to add feature-specific dependencies.
Feature modules depend on `:core:*` only, never on other feature modules.
