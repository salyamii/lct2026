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

Four feature entries display a shared placeholder with Back. Gear displays the inventory
with two sections, owned items only, and an entry-scoped Hilt ViewModel. Its saved
route ID remains `gear`; Back returns to the menu without changing game state. Tasks displays the
mini-game hub and registers `StarPlates`, `PriceCheck` and `Telescope` with stable
IDs `tasks_star_plates`, `tasks_price_check` and `tasks_telescope`. The app host
maps `DeedsAction` to those keys. Each game entry obtains its Hilt ViewModel and
collects its state with lifecycle awareness. Back pops the game entry to Tasks;
leaving that entry discards its demo session. Navigation keys contain no scores
or domain snapshots. The menu
artwork and UI state remain owned by the menu. The entry obtains its ViewModel
with `hiltViewModel()`; Hilt injects the domain `GameRepository` and new-save fixture.
The ViewModel initializes
only a missing save and observes persisted state, with explicit loading and error
states and retry. The entry collects `uiState` with lifecycle awareness. The menu
maps saved hunger, fatigue and pet appearance. Savings, current goal and event
use temporary labels under MAIN-D-074; the former adventure counter is removed.
The labels for Coins, Village and ContinueDay are now savings, map and event
name; route keys remain stable. Navigation keys contain no game snapshot. See
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
recreation and saved-state restoration of all six feature routes and the three
mini-game routes. Existing menu
tests cover accessible callbacks and compact/landscape layouts at large text.
Serialization tests check stable IDs and legacy route/back-stack payloads.
Payload compatibility and restoration within the current build do not prove
full saved UI restoration across an APK upgrade that changes the Compose tree
or saved-state registry identity.

## References

- [Navigation 3 setup](https://developer.android.com/guide/navigation/navigation-3/get-started)
- [Save and manage navigation state](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Modularize navigation code](https://developer.android.com/guide/navigation/navigation-3/modularize)


## Game actions on existing routes — 2026-09-19

MainMenu, Day and Tasks retain their serialized route IDs. A menu click dispatches
an explicit domain action before navigation; reopening an active event or saved
day summary performs no transition. Day loading and navigation restoration only
observe the save. Starting the next day remains an explicit button in the summary.
Tasks reports successful StartDeed through an entry callback; the app host opens
DeedGame. Acknowledging a result or choosing to return later reports an entry exit.
Navigation carries no game snapshot, reward, offer deadline or revision.

When a Day action saves a pause, dismisses a proposal or acknowledges a result
and then leaves the entry, its outgoing card stays visible with controls disabled
until navigation removes it. Repository observation still receives the saved
state, but does not replace that card with the generic day screen during exit.
Blocked or failed commands unlock the card and show the error without navigating.

Successful Day exits use `AppNavigator.returnToRoot(source)` to return directly
to the existing menu entry, including when the event was opened from Tasks.
Ordinary Back still pops one screen. Stale completion callbacks cannot clear
the stack of a newer destination, and the root is never removed.

## Illustrated screen transitions — 2026-09-19

The host uses opaque horizontal slides (160 ms) for forward, Back and predictive
Back transitions. This replaces Navigation 3's default 700 ms crossfade, which
blended the Tasks and menu artwork and text during a return. The host fills the
window with the shared night background. Route order and entry-scoped state
retention are unchanged; predictive Back still follows the gesture.

## Offered deed mini-games — 2026-09-19

The new stable `deed_game` route carries only the occurrence ID. Its serializer
is registered alongside all existing keys. Starting from a Day proposal replaces
that entry; starting from Tasks pushes the game. Completion returns directly to
the menu. Both system and UI Back first save PauseEvent, then return to the menu.
A failed save leaves the game visible with retry; it does not silently navigate.

DeedGameViewModel observes the aggregate; the existing board ViewModels belong
to this game's entry and retain their small SavedStateHandle state on recreation.
Explicit exit removes the entry, so the next start creates a new board. Restoring
a route whose occurrence was already completed or paused only exits; it grants
no rewards and does not execute a new event. Outgoing Day cards remain frozen
until replacement to avoid showing the intermediate generic day screen.

## Transient completion confirmation — 2026-09-19

Day and DeedGame exit callbacks optionally carry presentation text after a
successful completion command. The app host accepts it only from the current
destination, returns to the menu, then shows a short Snackbar after the slide.
The snackbar uses the light game-card palette and disappears automatically.
Leaving the menu cancels it. Each delivery has its own in-memory identity, so
two distinct completions with the same text remain distinct confirmations.

No snackbar state is serialized in route keys or Room. Pausing, deferring,
failed writes and restoring an already completed occurrence send no success
text. Deed payout text uses the committed balance delta of the revision-checked
command. Screens and the menu ViewModel do not recalculate or grant this reward.

## Waking and feeding — 2026-09-19

The summary's start-day action commits BeginDay with openFirst=false and exits
to the menu. The next Continue action opens the first pending/carried event.
Loading the morning or returning from the summary does not show an event or
create an offered deed. The outgoing summary remains frozen until navigation
removes it, just like other successful exits. Starting a day sends no event-
completion snackbar. Failed writes keep the summary open for retry.

Optional feeding is available on the menu. If the paid meal is unaffordable,
the menu offers the free meal in place, with its next-morning consequence.
Event cards have no secondary feeding link; hunger replaces the blocked action
with feeding. Feeding never automatically executes the original action.

## First-launch gate — 2026-09-19

LctApp checks AppStartupViewModel before composing LctNavHost. The onboarding
entry belongs to `:feature:onboarding` and receives an app-owned start callback.
It is outside the game back stack: completion exposes LctNavHost with MainMenu
as root, so Back cannot return to character selection. Existing saves skip
onboarding and restore the normal Navigation 3 stack. No existing route IDs
change. The feature uses SavedStateHandle for the local fox selection only;
persistent completion is the existence of the aggregate save. Read errors do
not substitute a new game. See [onboarding](design/onboarding.md).

### Customization step — 2026-09-20

The startup gate now has Choose, Customize, Accessories and Introduction steps (CUST-D-018). Start from the character
screen persists a Room draft and opens CustomizationScreen in the onboarding
module. AppStartupViewModel owns the step and persisted draft; UI receives values
and callbacks. Back from Customize clears the draft before returning to Choose.
Successful introduction confirmation commits the new aggregate and only then exposes the
normal Navigation 3 host. Existing saves bypass all onboarding steps; no route key contains
pet data. Failed writes keep the editor visible. Drafts resume after cold starts.

CUST-D-016: Customize confirms the profile into the accessory step. Accessories
uses the same feature module and state/callback boundary. Back from Accessories
returns to Customize without resetting the profile. The draft persists both step
and selected accessory; only Start on Introduction commits a game and opens LctNavHost.

Introduction follows accessory confirmation (CUST-D-018); Back returns to
Accessories. The persisted INTRODUCTION step uses the existing Room v7 text
column. No additional migration or Navigation 3 key is needed.
