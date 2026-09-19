# Project architecture

> Implementation update, 2026-09-19: the game domain now lives in the JVM
> module `:core:game`; Room is at v6 (including compatibility with both v2 branches, mini-game costs and inventory metadata). The original description below is retained.
> See the dated addition at the end and the [engine implementation](design/game-engine.md)
> for the current paths and scope.

LCTApp uses one Android Gradle module (`:app`) with explicit package boundaries.
The current UI is a menu, the Tasks hub with three demo mini-games, inventory, and four
navigable placeholders. Pure Kotlin game-state
models feed the menu through a repository-backed ViewModel. Room persists the
game and reference catalog locally; Hilt owns their application-scoped instances. The [state-machine specification](design/app-state-machine.md)
defines approved state behavior and records future gameplay decisions still open.

## Ownership

All paths below are relative to `app/src/main/java/ru/nksk/lctapp/`.

| Package / file | Responsibility | App dependencies allowed |
| --- | --- | --- |
| `MainActivity.kt` | Android activity, system bars, Hilt entry point | `app` |
| `app/LctApp.kt` | Theme and application composition | Features, shared UI, domain contracts |
| `app/di`, `app/LctApplication.kt` | Hilt application graph and singleton database/repositories | Data and domain |
| `domain` | Pure Kotlin game snapshots, reference content, repository contracts | Kotlin, coroutines/Flow |
| `data/game` | Room repositories, mapping, atomic writes, immutable content installation | Data and domain |
| `app/navigation` | Back stack, navigation policy, serializer registration, cross-feature wiring | Feature navigation contracts and UI actions |
| `feature/<name>/navigation` | Stable route keys, entry registration, lifecycle-aware callbacks | Own feature, shared UI |
| `feature/<name>/ui` | ViewModels, screen state, actions, rendering, feature-specific components | Own UI, shared UI, domain contracts |
| `core/ui/components` | Presentation shared by several features | Shared UI |
| `core/ui/theme` | App typography, colors, and theme | Shared UI |

Generated Android resources remain in the shared `R` namespace while there is
one module. Kotlin `internal` limits exposure outside the module, not between
packages. The JVM `ArchitectureTest` checks package placement and explicit
imports so common boundary violations fail `:app:testDebugUnitTest`. It is not
a full compiler dependency analysis: fully qualified references and reflection
still need code review. A future module split can enforce these boundaries in
the compiler when the project needs stronger isolation.

There is one app navigation package and seven feature navigation packages.
Each feature's `*Navigation.kt` declares its actual route key and entry builder;
there is no parallel `ui` route tree or typealias layer.
`app/navigation/AppNavigationSavedState.kt` registers the route serializers in
an explicit `SavedStateConfiguration`. New saved routes use stable `@SerialName`
IDs instead of JVM class names. Its deserializer also accepts the seven original
`ui` class names so existing encoded route payloads remain readable. Keep these
IDs stable and add an explicit migration when changing a saved route's format.

Features must not import another feature, concrete data implementations, or
app wiring. Route-to-route connections belong to the app host. A screen receives
immutable UI state and action callbacks; it must not import navigation types or
mutate a back stack. See [navigation](navigation.md) for registration and restoration.

## Current menu data

`MainActivity` is a Hilt entry point. The menu entry obtains its entry-scoped
`MainMenuViewModel` through `hiltViewModel()`. Hilt supplies the domain
`GameRepository` and the new-save fixture from `InitialGameStateModule` through
constructor injection. The ViewModel initializes a save only if absent, then
observes Room through the repository. The entry collects its immutable
`StateFlow<MainMenuLoadState>` with lifecycle awareness.

Loading, ready and error are explicit presentation states. A failed read or write
shows retry; it never substitutes the initial fixture for saved data. The current
fixture is 100 coins, NORMAL/BACKPACK, zero satiety/fatigue and plan allocations,
no story references, decisions or owned items. These values are technical starting
data, not approved parameter ranges, weekly income or authored story content.
The catalog starts empty until actual definitions are installed.

`MainMenuUiStateMapper` projects saved hunger, fatigue and pet appearance.
Under D-074 the menu shows temporary labels for savings, current goal, event
and map. Savings display a dash; the shared balance is not presented as savings.
The former adventure title and zero-of-four fixture counter have been removed. `MainMenuPreviewState` is only
for previews and UI tests. The loading/error wrapper does not alter menu artwork.
Normal backpack uses `menu_ryzhik`; other verified mappings use bundled teen art.
WORRIED and NEEDS_HELP still show their labels without substitute artwork.

Navigation changes do not mutate game data. Route restoration is separate from
Room restoration. Screen UI receives immutable values and callbacks; no Room,
repository writes or navigation objects belong in screen rendering.

## Demo mini-games

`domain/minigame` owns the pure Kotlin memory, price-comparison and target-stop
rules. These models are independent of Compose, Android, Hilt and persisted game
state. `feature/tasks/ui` owns immutable screen state and three Hilt ViewModels;
entry-scoped ViewModels coordinate domain actions and cancellable feedback jobs.
Screens receive state and explicit actions. Only the telescope animation stays
in composition; it runs while the entry is RESUMED and uses the same coordinate
system as the domain hit check.

Each mini-game has a stable Navigation 3 key registered by the Tasks feature.
The app host wires the hub actions to those keys. System/UI Back both return to
the hub. Small bounded session fields are kept in `SavedStateHandle`: card order,
opened/matched indices and moves; quiz amounts, question index and answer result;
current telescope zone, round, hits and stopped position. Reconstruction resumes
pending feedback once. This supports configuration changes and Android saved-state
restoration, not durable game saves after dismissing the task or force-stopping.

The section retains the original PR's demo mechanics. Demo coins never update the shared balance. Under D-072, successful mini-games
atomically increase saved hunger/fatigue and record a durable attempt receipt;
pet appearance and story progress are unchanged. See
[mini-game scope and open rules](design/mini-games.md). Actual monetary earnings still need approved gameplay rules.

## Domain and persistence

| Package / model | Responsibility |
| --- | --- |
| `domain/game/GameState` | Pet, economy, story, satiety, fatigue and ordered owned-item occurrences |
| `domain/game/GameRepository` | Observe/read, initialize-if-absent, transactional aggregate update |
| `domain/pet` | One persistent visual state, independent selected look and appearance projection |
| `domain/economy` | Shared balance and four independent budget-plan values |
| `domain/story` | Current day, script position, active event and ordered decision occurrences |
| `domain/content` | Reference definitions and the content repository interface |
| `data/game/local` | 13 Room entities, DAO primitives, explicit codes and mapping |
| `data/game` | Aggregate transactions and immutable reference-content installation |

`PetState.transitionTo` retains the selected look and explicitly replaces the one
visual state. HAPPY/UPSET persist until another explicit change. There are no
hidden states, timers, automatic resets or automatic event effects.

The Room v1 schema follows the [normalized model](design/schema-normalization.md).
`GAME_STATE` combines scalar pet, economy and progression fields;
`OWNED_ITEM` and `PLAYER_DECISION` hold ordered occurrences. Chapter and goal are
resolved through reference relationships; event and impact come from the selected
choice. These values are not duplicated in the save. Repository contracts and
Kotlin domain models carry no Android or Room annotations.

`GameRepository.update(transform)` reads the latest complete state inside a
write transaction. The caller supplies a pure transformation of that argument;
all resulting pet, money, story, item and decision changes commit together.
Callers must not close over a stale UI snapshot or perform external effects in
the transform. Gameplay guards and occurrence/idempotency rules belong to future
domain operations, not this generic storage primitive. UI observation and
initialization never execute content effects or grant recurring income.

`StoryContentRepository.install` atomically adds definitions and accepts repeated
identical rows. It rejects replacing existing IDs or adding choices/effects to an
existing event, effects to a choice, requirements to a goal, or schedule entries
to an existing day. Install each complete definition with its children. Revised
content requires new IDs; old definitions remain readable for saved decisions.
The combined catalog is checked against the STORY/goal-item restriction D-038.

Hilt provides one `GameDatabase` using Room 3 and `BundledSQLiteDriver`, plus the
two repository implementations. Suspend DAO queries and transactional reads keep
SQLite work off the UI thread. Database invalidation reloads the entire saved
aggregate in a read transaction; independent table flows are not combined into
partially updated game snapshots. Storage errors propagate to callers.

The exported [version 1 schema](../app/schemas/ru.nksk.lctapp.data.game.local.GameDatabase/1.json)
is the migration baseline. There is no earlier on-disk Room schema to migrate.
Every future version requires explicit, data-preserving migrations and tests;
no destructive fallback is configured. Database files are excluded from Android
cloud backup and device transfer until their policy is designed. Local app
reopening restores the existing database without using real elapsed time.

See the [current model](design/game-data-schema.md),
[decision register](design/decisions.md) and
[persistence contract](design/room-persistence.md). Open questions such as event
stages, prices, time/effort units and completion guards remain open. No unused
networking, WorkManager or DataStore dependencies were added.

## Dependency injection

Use Hilt for the application's dependency graph (D-044). `app/LctApplication`
is registered in the manifest with `@HiltAndroidApp`; `MainActivity` is an
`@AndroidEntryPoint`. Keep bindings in `app/di`, prefer constructor injection,
and use `@Binds` for repository interfaces and `@Provides` for constructed objects.
Domain models and contracts remain free of DI annotations.

The menu entry uses `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()`.
Navigation 3's ViewModel-store decorator scopes the ViewModel to its entry;
screens and previews receive state and callbacks without accessing Hilt.
`GameDatabaseModule` and `GameRepositoryModule` in `GameStorageModule.kt` provide
singleton database/repository instances.
`InitialGameStateModule` supplies an unscoped new-save fixture in
`ViewModelComponent`; initialization still reads the database transactionally
and never replaces an existing save. Hilt manages dependencies; Room persists data.

Versions live in the version catalog. KSP processes production and instrumentation
sources. JVM tests construct ViewModels directly. Device tests use `HiltTestRunner`
and place `HiltAndroidRule` before the activity rule; a debug-only Hilt activity
hosts test Compose content. Tests replace repository bindings with `@BindValue`
for isolated navigation, initial-state injection, and in-memory Room menu coverage.

References: [Google's Hilt guide](https://developer.android.com/training/dependency-injection/hilt-android),
[Hilt build setup](https://dagger.dev/hilt/gradle-setup.html),
[Hilt testing](https://developer.android.com/training/dependency-injection/hilt-testing).

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The first command checks pet-state appearance, explicit transitions, menu mapping,
repository-driven loading/retry, navigation policy and package boundaries; it
builds the APK and runs Android lint.
Device coverage checks Room round trips, transactions, FK rollback, concurrency,
immutable content, schema compatibility and failure preservation, live saved-game
menu updates, screen inputs, accessible
actions, compact/landscape layouts, Back, recreation, and saved-state restoration.
Legacy route and back-stack fixtures check decoding the original class names;
encoding checks enforce stable route IDs. These checks and restoration within
the current build do not establish full UI-state compatibility across APK
upgrades that change the Compose tree or saved-state registry identity.

## References

- [Cleanup design and scope](design/architecture-cleanup.md)
- [Android modularization patterns](https://developer.android.com/topic/modularization/patterns)
- [Android domain-layer guidance](https://developer.android.com/topic/architecture/domain-layer)

This project follows the clean-architecture skill's dependency inversion:
repository interfaces belong to `domain` and implementations to `data`. Google's
domain-layer guide uses a different convention; its advice to add use cases
when they address real complexity is applicable without changing our ownership.

## Game module addition — 2026-09-19

`:app` depends on `:core:game`. The latter is a Kotlin/JVM library with only
Kotlin/JDK and coroutines in production dependencies. All former `domain`
sources and their unit tests moved there with package names preserved:
`core/game/src/main/kotlin/ru/nksk/lctapp/domain/`. This includes the existing
mini-game models; their demo behavior has not been integrated into earnings.
This split is an implementation choice for the requested engine.

Three main components own gameplay: `EventFactory` validates content and
creates event occurrences, `GameEngine` evaluates commands and outcomes, and
`GameRepository` commits the aggregate. `GameEngineProvider` loads the catalog
before constructing the engine. Its Hilt binding stays in
`app/di/GameEngineModule.kt`; the core module has no DI annotations.

`GameState.engine` contains the optional saved runtime. Room v2 adds three
tables to the v1 baseline and migrates without changing the original rows.
`PetState.selectedLookId` is an open string identifier. Known bundled artwork
is resolved in menu presentation; an unknown look retains its ID and gets a
missing-artwork description. There is no closed cosmetic enum.

The menu still observes the existing save. Gameplay controls are not connected
to production content yet. Authoring the rules/content and connecting feature
actions are subsequent work. See [engine boundaries](design/game-engine.md).
`ArchitectureTest` now scans both source roots. Core tests live in `:core:game`.

The user's current verification preference (2026-09-19) is build checks only:
do not launch the app, emulator or instrumented tests; the user runs the app.


## Inventory — 2026-09-19

`feature/gear` replaces the Gear placeholder with the read-only inventory (D-075).
Its Hilt ViewModel observes `GameRepository` and reads immutable item definitions
through `StoryContentRepository` after each saved aggregate. Only `ownedItems`
produce cards; repeated occurrences retain their IDs and relative order.
`ItemDefinition.category` selects the display section and `priceCoins` supplies
the catalog price. These are persisted in Room v5 with an additive migration.
No content is installed and no game is initialized or mutated by this screen.
Loading, error/retry and empty sections are distinct. Preview examples stay in
Compose previews. See [inventory](design/inventory.md).

D-076: hunger is stored only as `GameState.satiety`; UI hunger values project
that field. Room v6 merges the v4/v5 hunger increments into satiety and removes
the duplicate column while retaining completion receipts.
