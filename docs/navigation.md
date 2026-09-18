# App navigation

The activity hosts `app/LctApp`, which applies the theme and composes `LctNavHost`.
The host owns a Navigation 3 `rememberNavBackStack` configured with
`AppNavigationSavedStateConfiguration` and starting at `MainMenu`. The menu is a
launch screen, so actions push onto one stack and Back returns to the menu. It
is not a persistent tab bar requiring independent stacks.

## Responsibilities

- `app/navigation/LctNavHost.kt` composes feature entry builders and maps menu intents
  to destinations. The menu UI receives callbacks and knows nothing about the stack.
- `app/navigation/AppNavigator.kt` applies single-top navigation and protects the root
  from being popped. Navigation 3 handles system and predictive Back; at the root
  Android handles leaving the activity.
- `app/navigation/AppNavigationSavedState.kt` explicitly registers every route's
  serializer. New saves use stable `@SerialName` IDs. The configuration also maps
  the seven original `ui` class names to the current feature serializers when
  decoding old payloads.
- Entry callbacks supply their source key. The navigator checks that it is still
  the stack's current entry before pushing or popping. This rejects different
  rapid menu actions and stale Back callbacks even before lifecycle changes
  reach an outgoing entry. System Back targets the current stack directly.
- Each feature owns its serializable `NavKey` and `EntryProviderScope<NavKey>`
  extension together in `feature/<feature>/navigation/*Navigation.kt`, including
  the menu feature. These are the actual key declarations, with no aliases or
  legacy route packages. This leaves one app navigation package and seven
  feature navigation packages.
- Entry decorators retain saveable UI state and scope ViewModels to each entry,
  clearing them when that entry is removed. `rememberNavBackStack` saves the route
  stack for recreation and process restoration. Store persistent game data in the
  data layer, not in navigation keys.

| Menu action | Destination |
| --- | --- |
| Gear | `Gear` |
| Tasks | `Tasks` |
| Goal card / Goal shortcut | `Goal` |
| Coins | `Coins` |
| Village | `Village` |
| Continue day | `Day` |

The feature entries currently display a shared placeholder with Back. Replace
that content as each feature is implemented; navigation already works. The menu
artwork and UI state remain owned by the menu. The entry obtains its ViewModel
with `hiltViewModel()`; Hilt injects the domain `GameRepository` and new-save fixture.
The ViewModel initializes
only a missing save and observes persisted state, with explicit loading and error
states and retry. The entry collects `uiState` with lifecycle awareness. The menu
maps saved balance and pet appearance; the adventure counter remains a display
fixture. Navigation keys contain no game snapshot. See
[project architecture](architecture.md) for ownership.

## Adding a screen

1. Define an `@Serializable` key implementing `NavKey` in the owning feature.
   Give it a stable `@SerialName`. Detail routes should contain stable IDs rather
   than domain objects; equal keys share entry state. Preserve serialized IDs and
   compatible fields, or provide an explicit saved-payload migration.
2. Register it with `entry<YourKey>` inside the feature's entry builder. Create
   destination ViewModels inside the entry and pass callbacks to screen UI.
3. Register its serializer under `NavKey` in
   `app/navigation/AppNavigationSavedState.kt`. This explicit configuration makes
   `@SerialName` the saved type ID, independent of the key's package name. Add
   legacy deserializer mappings only for IDs that a previous version saved.
4. Include the source key in entry callbacks and wire them to
   `AppNavigator.navigate(source, destination)` or `goBack(source)` in the host. Add a
   builder call there only when adding a new feature. Guard navigation callbacks
   against events from entries that are no longer resumed.
5. Cover the user journey and saved-state restoration. Add stack policy tests when
   behavior changes. Reserve the no-argument `goBack()` for `NavDisplay` system Back.

This structure can move into feature `api` (keys) and `impl` (entries/screens)
Gradle modules later. Hilt provides the menu ViewModel within its entry scope.
Multiple stacks, custom scenes and deep links should be added when needed.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Instrumentation covers all seven menu entry points, UI/system Back, activity
recreation and saved-state restoration of all six feature routes. Existing menu
tests cover accessible callbacks and compact/landscape layouts at large text.
Serialization tests check stable IDs and legacy route/back-stack payloads.
Payload compatibility and restoration within the current build do not prove
full saved UI restoration across an APK upgrade that changes the Compose tree
or saved-state registry identity.

## References

- [Navigation 3 setup](https://developer.android.com/guide/navigation/navigation-3/get-started)
- [Save and manage navigation state](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Modularize navigation code](https://developer.android.com/guide/navigation/navigation-3/modularize)
