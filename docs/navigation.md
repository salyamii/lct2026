# App navigation

The activity hosts `app/LctApp`, which applies the theme and composes `LctNavHost`.
The host owns a Navigation 3 `rememberNavBackStack`
starting at `MainMenu`. The menu is a launch screen, so actions push onto one
stack and Back returns to the menu. It is not a persistent tab bar requiring
independent stacks.

## Responsibilities

- `app/navigation/LctNavHost.kt` composes feature entry builders and maps menu intents
  to destinations. The menu UI receives callbacks and knows nothing about the stack.
- `app/navigation/AppNavigator.kt` applies single-top navigation and protects the root
  from being popped. Navigation 3 handles system and predictive Back; at the root
  Android handles leaving the activity.
- Entry callbacks supply their source key. The navigator checks that it is still
  the stack's current entry before pushing or popping. This rejects different
  rapid menu actions and stale Back callbacks even before lifecycle changes
  reach an outgoing entry. System Back targets the current stack directly.
- Each feature owns its serializable `NavKey` and `EntryProviderScope<NavKey>`
  extension in `feature/<feature>/navigation`, including the menu feature. The
  seven preexisting key classes stay at their original `ui` package names and
  are exposed through typealiases, preserving their serialized JVM identities.
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
artwork and UI state remain owned by the menu. Its entry explicitly supplies
`MainMenuDemoState`; the coin/goal values are a design fixture, not persisted game
data. See [project architecture](architecture.md) for layer ownership.

## Adding a screen

1. Define an `@Serializable` key implementing `NavKey` in the owning feature.
   Give it a stable `@SerialName`. Detail routes should contain stable IDs rather
   than domain objects; equal keys share entry state. The Android serializer also
   saves the JVM class name, so do not relocate an existing key without migration.
2. Register it with `entry<YourKey>` inside the feature's entry builder. Create
   destination ViewModels inside the entry and pass callbacks to screen UI.
3. Include the source key in entry callbacks and wire them to
   `AppNavigator.navigate(source, destination)` or `goBack(source)` in the host. Add a
   builder call there only when adding a new feature. Guard navigation callbacks
   against events from entries that are no longer resumed.
4. Cover the user journey and saved-state restoration. Add stack policy tests when
   behavior changes. Reserve the no-argument `goBack()` for `NavDisplay` system Back.

This structure can move into feature `api` (keys) and `impl` (entries/screens)
Gradle modules later. Dependency injection, multiple stacks, custom scenes and
deep links should be added when a real feature needs them.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Instrumentation covers all seven menu entry points, UI/system Back, activity
recreation and saved-state restoration of all six feature routes. Existing menu
tests cover accessible callbacks and compact/landscape layouts at large text.

## References

- [Navigation 3 setup](https://developer.android.com/guide/navigation/navigation-3/get-started)
- [Save and manage navigation state](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Modularize navigation code](https://developer.android.com/guide/navigation/navigation-3/modularize)
