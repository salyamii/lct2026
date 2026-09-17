# Project architecture

LCTApp uses one Android Gradle module (`:app`) with explicit package boundaries.
The current UI is a menu and six navigable placeholders. Pure Kotlin game-state
models are implemented; persistence and integration with screen state are not
implemented yet. The [state-machine specification](design/app-state-machine.md)
defines approved state behavior and records future gameplay decisions still open.

## Ownership

All paths below are relative to `app/src/main/java/ru/nksk/lctapp/`.

| Package / file | Responsibility | App dependencies allowed |
| --- | --- | --- |
| `MainActivity.kt` | Android activity and system bars | `app` |
| `app/LctApp.kt` | Theme and application composition | Features, shared UI |
| `app/navigation` | Back stack, navigation policy, serializer registration, cross-feature wiring | Feature navigation contracts and UI actions |
| `feature/<name>/navigation` | Stable route keys, entry registration, lifecycle-aware callbacks | Own feature, shared UI |
| `feature/<name>/ui` | Screen state, actions, rendering, feature-specific components | Own UI, shared UI; future domain contracts |
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

`MainMenuScreen` requires `MainMenuUiState` and `onAction`. The entry supplies
`MainMenuDemoState`, which explicitly preserves the existing design fixture:
100 coins, zero completed goals, and four total goals. These values are sample
presentation data, not initial economy balances or progression rules. They are
not saved or modified by opening a destination. Resource-backed titles and
artwork remain presentation concerns.

The menu's layout, HUD, action panel, scene/background, text, and artwork decoder
have separate files inside the feature. Their composables retain local visual
state only, such as scroll position and background alignment.

## Adding real game data

The state foundation is already available under `domain`:

| Package / model | Responsibility |
| --- | --- |
| `domain/game/GameState` | Compose the pet, economy, and story snapshots |
| `domain/pet/PetState` | One current visual state and the independently saved selected look |
| `domain/pet/PetVisualState` | All eight states with the approved priority metadata |
| `domain/pet/PetLook` | The five documented cosmetic looks |
| `domain/pet/PetAppearance` | Select the saved look in NORMAL or a complete special-state appearance |
| `domain/economy/EconomyState` | Balance, budget allocations, savings goal, reserve, and expenses |
| `domain/story/StoryState` | Current chapter/event references and recorded decisions |

`PetState.transitionTo` applies an explicit state outcome while retaining the
selected look. It does not validate which quest may request that outcome or
calculate rewards. All states, including HAPPY and UPSET, remain until an
explicit update. Priority does not reject an update to a lower-ranked state.
There are no hidden conditions, timers, reaction queues, or automatic resets.
`PetAppearance` contains domain values only; Android drawable selection belongs
to the consuming feature's presentation code.

The snapshot constructors require their data explicitly. They supply no starting
balance, initial quest, prices, or reward formulas. Economy amounts use `Long`
virtual currency units; this representation does not define budget arithmetic
or whether reserve/savings are included in total balance. Story IDs refer to
future content without registering placeholder quests. Collection properties
use Kotlin read-only collection types; producers must not mutate their backing
collections after publishing a snapshot.

These domain models are neither Room entities nor screen `UiState`. A future
working feature will add normalized persistence, repositories, and ViewModels
that map the saved domain state to its own UI state. The current menu continues
to receive its explicitly named demo fixture.

Add layers as a working feature requires them; do not create empty repositories,
use cases, ViewModels, or dependency injection containers for placeholders.

- `domain/<area>` owns pure Kotlin models and will own repository interfaces and
  business operations. No Android, Compose, navigation, database, or
  serialization annotations belong here. Coroutine/Flow APIs may be used.
- `data/<area>` will implement those interfaces, coordinate data sources, and
  map storage/network representations into domain models. It must not depend
  on features, shared UI, or app wiring.
- A feature's ViewModel will consume domain contracts and map results into UI
  state. Collect observable state with lifecycle awareness at its entry/route;
  continue passing values and callbacks to stateless screens.
- Wire concrete dependencies in `app`. Use constructor injection with Hilt when
  a working feature needs a real dependency graph; do not add it for placeholders.

Persistent game state belongs outside navigation keys. Keep selected appearance
and visual state in PetState, financial state in EconomyState, and story
progression in StoryState. Resolve the product specification's open decisions
when implementing the affected gameplay; they do not block the state foundation.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The first command checks pet-state appearance and explicit transitions, navigation
policy, and package boundaries, builds the APK, and runs Android lint. Device
coverage checks screen inputs, accessible
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
