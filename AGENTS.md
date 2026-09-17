# Agent instructions

## Project

LCTApp is a native Android app written in Kotlin with Jetpack Compose. Application
code lives in `app/src/main/java/ru/nksk/lctapp/`.

Read [the architecture guide](docs/architecture.md) before adding or moving app
code. Keep app composition in `app`, each feature in `feature/<name>`, and shared
presentation in `core/ui`. Features do not import each other or app wiring;
screen UI receives state and callbacks instead of navigation objects.

## Tech stack

- Keep Kotlin, Gradle Kotlin DSL, Jetpack Compose, Material 3, and Navigation 3.
  Manage dependency versions in [the version catalog](gradle/libs.versions.toml).
- Use reinforced MVVM: unidirectional data flow, immutable `UiState` exposed as
  `StateFlow`, explicit UI actions, and lifecycle-aware collection at screen entries.
  Screens render state and emit callbacks; ViewModels coordinate domain operations;
  domain code owns business rules, and repositories own data access. Use coroutines
  and Flow for asynchronous work.
- Use Room for persistent app data. Design schemas in Boyce–Codd normal form
  (BCNF / НФБК, stronger than 3NF); document reasons for any denormalization.
  Use Preferences DataStore for small device preferences.
- Use constructor injection with Hilt for the real dependency graph, and
  Retrofit + OkHttp + Kotlin serialization for backend HTTP/JSON calls.
- Keep the app offline-first: UI observes local persisted data through repositories.
  Future backend integration primarily provides cloud backup and restore;
  use WorkManager for durable background backups and retries. Multi-device merging
  is outside the current scope; define backup/restore policy before implementing it.
- Keep JUnit 4, Compose UI tests, and AndroidX Test/Espresso. Add coroutine,
  database migration, and backup/restore tests as those features are introduced.

These are approved defaults, not a claim that every library is installed. Add
Room, DataStore, Hilt, networking, and WorkManager when a working feature needs
them; do not scaffold unused layers or dependencies for placeholders.

## App state machine and behavior

Before implementing, changing, reviewing, or testing any app state machine,
gameplay state, event, transition, guard, progression, reward, or navigation that
changes domain state, read and follow [the app state-machine specification](docs/design/app-state-machine.md).
Use its documented rules and distinguish explicit requirements from examples,
interpretations, and unresolved questions. Do not invent missing rules.

For navigation architecture and route registration, also read
[the navigation guide](docs/navigation.md). Keep persistent game state outside
navigation keys and keep UI callbacks separate from navigation ownership.

## Working conventions

- Preserve existing uncommitted work and limit changes to the requested scope.
- Keep the local state-machine specification consistent with approved behavior
  changes. Record unresolved design decisions rather than silently guessing.
- Validate behavior changes with relevant tests. Common checks are
  `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`;
  use `./gradlew :app:connectedDebugAndroidTest` when device coverage is needed.
- Documentation-only changes require checking local links and consistency;
  they do not require an Android build.

## Assets

Before adding or using artwork, read the [Android artwork guide](docs/design/assets/README.md)
and search the [asset catalog](docs/design/assets/catalog.md) and
[manifest](docs/design/assets/manifest.json). Reuse bundled resources first.
The catalog distinguishes imported artwork from exports still unavailable.
Resource paths below are under `app/src/main/` (for example,
`app/src/main/res/drawable-nodpi/`).

- Keep raster art as lossless exact WebP in `res/drawable-nodpi`, simple vectors
  as XML in `res/drawable`, fonts in `res/font`, and launcher icons in `res/mipmap-*`.
  Retain SVG originals and provenance in `docs/design`, and font licenses in
  `app/src/main/assets/licenses`.
- Preserve full canvas dimensions, transparent margins and shared coordinates
  when importing characters or equipment. Set image bounds explicitly in
  Compose. Preserve existing `menu_*` resource names and use drawable aliases
  for verified exact duplicates.
- Use the guide's semantic prefixes for new resources and update the manifest
  and catalog with source node links, dimensions, hashes and conversion details.
  Do not infer gameplay rules from asset names or silently substitute missing art.

Development prompt: "Read the artwork guide and search the catalog before
implementing this UI. Reuse existing Android resources; add missing artwork in
the documented locations and update its provenance. Preserve full canvas
alignment, report unavailable exports, and follow the state-machine
specification for all behavior."
