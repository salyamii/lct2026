# Architecture cleanup

The approved scope is package boundaries inside the existing `:app` Gradle
module. Preserve the current menu, artwork, six placeholder destinations,
route serialization names, saved-state restoration, and documented game rules.

## Design

- `MainActivity` remains the Android entry point. `app/LctApp` composes the
  theme and navigation; `app/navigation` owns the stack and feature wiring.
- `feature/<name>/navigation` owns its route keys and entry registration.
  `feature/menu/ui` owns the menu state, actions, rendering, and artwork.
  Features communicate with the host through callbacks and do not import one
  another. Stateless UI does not depend on navigation APIs.
- Keep the seven existing serialized key declarations in their original `ui`
  packages, exposed through aliases in each feature's navigation package.
  Navigation 3's Android serializer uses JVM class names, so moving the actual
  key classes would make old encoded routes unreadable. These compatibility
  declarations are the only legacy package exceptions; entries and UI move.
- `core/ui` holds shared presentation components and theme. It cannot depend
  on features or app wiring. Feature-specific artwork stays in its feature.
- `MainMenuUiState` explicitly supplies the three currently displayed numeric
  values. Existing values are a named demo fixture supplied at the entry, not
  default game rules hidden inside the screen. No repository, ViewModel, domain
  state machine, or persistence is introduced for static demo data.
- Menu rendering is split into layout, HUD, actions, scene/background, and text
  components without changing modifiers or visual behavior.
- Navigation callbacks carry their source route. Stack mutations reject a
  source that is no longer current, including two different taps received
  before the next composition. System Back remains owned by Navigation 3.
- An import-boundary check runs with JVM tests. This provides feedback while
  packages share a module; Kotlin visibility alone does not isolate packages.

When real game behavior is implemented, pure Kotlin models, repository
interfaces, and use cases belong in `domain`; persistence and repository
implementations belong in `data`. Wire concrete dependencies in `app`. Preserve
PetState/EconomyState/StoryState ownership from the [state specification](app-state-machine.md).
Resolve its open questions before implementing behavior that depends on them.

## Alternatives considered

Retaining the current `ui` tree would require fewer moves, but would leave app
wiring and feature boundaries implicit. Splitting Gradle modules now would
provide compiler-enforced isolation but add build configuration before actual
domain/data implementations exist. Package boundaries are the selected step.

## Validation

Establish the existing JVM/build/lint baseline; reproduce stale navigation with
a failing test; run the same checks after refactoring. Device tests must cover
all menu actions, custom menu values, large text, landscape, Back, activity
recreation, and saved-state restoration. Decode fixtures containing the original
route class names and preserve those names when encoding. Review changed paths
and local documentation links. These checks do not prove full saved UI
restoration across an APK upgrade with a changed Compose tree.

## Execution plan

- [x] Move navigation and add source-aware callback regression coverage.
- [x] Move shared UI and menu packages; extract explicit menu state and components.
- [x] Add package dependency checks; document current and future ownership.
- [x] Run JVM tests, debug assembly, lint, and emulator instrumentation; review.

Work stays in the current checkout because the requested recent work is
uncommitted there. No commits or changes to the existing artwork conversion
are part of this cleanup.
