# Project architecture

LCTApp uses one Android Gradle module (`:app`) with explicit package boundaries.
The current implementation is a menu and six navigable placeholders. It has no
persisted game model yet. The [state-machine specification](design/app-state-machine.md)
defines product behavior for future features and records decisions still open.

## Ownership

All paths below are relative to `app/src/main/java/ru/nksk/lctapp/`.

| Package / file | Responsibility | App dependencies allowed |
| --- | --- | --- |
| `MainActivity.kt` | Android activity and system bars | `app` |
| `app/LctApp.kt` | Theme and application composition | Features, shared UI |
| `app/navigation` | Back stack, navigation policy, cross-feature wiring | Feature navigation contracts and UI actions |
| `feature/<name>/navigation` | Stable route keys, entry registration, lifecycle-aware callbacks | Own feature, shared UI |
| `feature/<name>/ui` | Screen state, actions, rendering, feature-specific components | Own UI, shared UI; future domain contracts |
| `core/ui/components` | Presentation shared by several features | Shared UI |
| `core/ui/theme` | App typography, colors, and theme | Shared UI |
| Seven existing `ui/**` route key files | Serialized route compatibility only | Navigation 3 `NavKey`, serialization annotations |

Generated Android resources remain in the shared `R` namespace while there is
one module. Kotlin `internal` limits exposure outside the module, not between
packages. The JVM `ArchitectureTest` checks package placement and explicit
imports so common boundary violations fail `:app:testDebugUnitTest`. It is not
a full compiler dependency analysis: fully qualified references and reflection
still need code review. A future module split can enforce these boundaries in
the compiler when the project needs stronger isolation.

The existing `MainMenu`, `Gear`, `Tasks`, `Goal`, `Coins`, `Village`, and `Day`
key declarations retain their original class/package identities. Navigation 3
1.1.7's Android serializer persists the JVM class name and restores it using
reflection; `@SerialName` alone does not protect a package move. Feature
navigation exposes typealiases to these keys. This is a narrow compatibility
exception, not a location for new screen code. New features define keys in their
own navigation package, and later key moves need an explicit migration.

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

Add layers as a working feature requires them; do not create empty repositories,
use cases, ViewModels, or dependency injection containers for placeholders.

- `domain/<area>` will own pure Kotlin models, repository interfaces, and
  business operations. No Android, Compose, navigation, database, or
  serialization annotations belong here. Coroutine/Flow APIs may be used.
- `data/<area>` will implement those interfaces, coordinate data sources, and
  map storage/network representations into domain models. It must not depend
  on features, shared UI, or app wiring.
- A feature's ViewModel will consume domain contracts and map results into UI
  state. Collect observable state with lifecycle awareness at its entry/route;
  continue passing values and callbacks to stateless screens.
- Wire concrete dependencies in `app`. Constructor injection is enough until
  an actual dependency graph calls for a framework.

Persistent game state belongs outside navigation keys. Keep selected appearance
and visual state in PetState, financial state in EconomyState, and story
progression in StoryState. Resolve the product specification's open decisions
before implementing affected behavior. This cleanup makes no such decisions.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The first command checks navigation policy and package boundaries, builds the
APK, and runs Android lint. Device coverage checks screen inputs, accessible
actions, compact/landscape layouts, Back, recreation, and saved-state restoration.
Legacy route fixtures also check decoding the original class names and retaining
them on encoding. These checks do not establish full UI-state compatibility
across APK upgrades that change the Compose tree.

## References

- [Cleanup design and scope](design/architecture-cleanup.md)
- [Android modularization patterns](https://developer.android.com/topic/modularization/patterns)
- [Android domain-layer guidance](https://developer.android.com/topic/architecture/domain-layer)

This project follows the clean-architecture skill's dependency inversion:
repository interfaces belong to `domain` and implementations to `data`. Google's
domain-layer guide uses a different convention; its advice to add use cases
when they address real complexity is applicable without changing our ownership.
