# Project architecture

Current implementation: 2026-09-29. The approved cleanup scope is
[ARCH-D-001–007](design/decisions.md). This guide describes the current code;
historical decisions and migrations live in the design documents.

## Modules and ownership

| Module / package | Responsibility |
| --- | --- |
| `:core:game` | Pure Kotlin domain: game aggregate, commands, rules, content, history, financial projections, snapshot/backend contracts |
| `:app` / `app` | Android composition, Hilt graph, startup and cross-feature navigation |
| `:app` / `feature/<name>` | Feature UI, immutable screen state, ViewModels, actions and navigation entries |
| `:app` / `core/ui/components` | Domain-independent reusable Compose components and interaction states |
| `:app` / `core/ui/game` | Shared read-only domain-to-presentation adapters and game-action presentation helpers |
| `:app` / `core/ui/media` | Local asset playback, shared audio focus and lifecycle-aware media effects |
| `:app` / `core/ui/theme` | Colors, typography and visual tokens |
| `:app` / `data` | Room, content installation, backend transport and device identity |
| `:feature:onboarding` | Onboarding UI; app supplies artwork and persistence callbacks |
| `:feature:debug` | Debug-only controls; release composition has no dependency on them |

Most features remain packages inside `:app`; they are not separate Gradle modules.
Features never import another feature, app wiring or data implementations.
Screens receive state and callbacks, not repositories or navigation objects.
`core/ui/game` may import domain; `core/ui/components` may not. Domain does not
import Android, Compose, Hilt, Room or feature code. ArchitectureTest checks
explicit imports; Gradle enforces the pure Kotlin module boundary.

## State and commands

Room is the source of the current game. `GameSession.observe()` exposes the
committed aggregate; ViewModels project it into immutable StateFlow values.
Navigation entries collect with lifecycle awareness and own navigation effects.
UI actions invoke domain commands through `GameSession`; business rules do not
come from a composable, ID substring or resource name.

`GameEngine` checks the latest revision and gameplay guards inside the aggregate
write. Pet, money, story, ownership, journal, audit and delivery receipts commit
atomically. A preview or opening a confirmation does not dispatch its purchase.
See [state access](design/game-state-access.md), [state machine](design/app-state-machine.md)
and [navigation](navigation.md).

An uncertain write retains the same `EngineRequest`, including its ID, expected
revision and displayed evidence. A retry does not silently upgrade the revision
or create a new intent. Day and mini-game flows share `GameActionAttempt`; Goal,
Economy and Training preserve their pending request through their confirmation
states. A definite rejection releases the pending operation. A committed command
followed by a presentation-read failure is a refresh failure, not another purchase.
After a definite stale-revision rejection, the mini-game's explicit Retry starts
a new validated request; an automatic completion callback cannot do so.

An idempotent retry can return a newer current world. Completion feedback uses
the original command receipt when necessary, not unrelated later balance changes.
Navigation after a recovered start also targets its original occurrence and checks
that it is still active; the latest unrelated event is never used as its substitute.
Route keys never contain a game snapshot, pending payment, QR payload or mutable
progress. Screen restoration and persistent world restoration are separate.

## Content and media

An authored event groups its definition, choices and their effects, effort,
mini-game assignments, facts and outcomes in `EventSpec`. Compilation produces
the existing immutable `StoryContent`, policies and card copy. Current authoring
should not add more passes that patch unrelated maps by string ID.

`EventCardCopy.presentation` describes current presentation: card layout, display
copy, location and semantic media keys. Purchase composition does not depend on
whether an image exists. Screens, history and reflection use the same presentation
lookup. Android resolves semantic artwork keys to bundled resources; the domain
never receives resource integers.

Editorial conditional lore copy belongs to `EventPresentation.bodyVariants`, which
the day screen checks before legacy `EventCardCopy.variants`. Presentation is excluded
from the replay fingerprint; legacy variants and immutable definitions retain their
original values so a wording correction cannot invalidate saved history.

Media metadata supplies chapter music, appearance, ambience, narration and action
sound/voice cues. `BundledMediaCatalog` resolves these semantic keys to the
bundled MP3 assets; metadata itself never performs I/O or advances the game.
`StoryChapterPresentation` assigns one music key to the authored lore group. The app
reads the current story act's presentation to select its global background track:
the same chapter continues across screens, a new chapter changes the track, and
music is quieter under voice. Narration and short appearance/action cues belong to
the active scene or confirmed action. Playback is lifecycle-owned, pauses when the
app leaves the foreground, shares the global sound preference, and never replays a
purchase or reward. See [media provenance](design/assets/media-catalog.md).

`MediaPlaybackViewModel` observes the current world only to select a chapter theme;
it does not initialize a game or read/replay its journal. It retains the playback
controller across activity recreation. The Compose host releases native players
outside the foreground; their positions and occurrence deduplication stay in the
controller. Leaving a scene stops its narration. The intro owns a separate video
player with its position retained in the startup ViewModel.

Music, narration and intro pause directly in `ON_PAUSE`/`ON_STOP` lifecycle
callbacks through `PlaybackLifecycleEffect`. This must not wait for a Compose
SideEffect: a background window can stop scheduling frames before recomposition.
Foreground return resumes the retained playback position and keeps sound preferences.

Illustrated adventure screens and the menu use `GameArtworkScene` to reveal their
initial image group together after asynchronous decode. Errors release the barrier;
later pose or clothing updates do not hide a scene already on screen. Scene identity
is an event/location key, never the changing world revision or animation phase.

Before a world exists, the same music projection selects the first authored act's
theme for onboarding. App composition suppresses the music cue only while the
intro video is shown, independently of the shared sound preference. The video's
own audio keeps that preference; leaving the intro restores the chapter theme.
This selection does not initialize or mutate the game.

Music loops on its own lane; short cues and voice use a sequential lane which
ducks the music. Short location clips play twice (MEDIA-D-005); narration and
action effects play once. Scene deduplication records each completed clip/pass,
not an attempted async preparation. A cancelled or failed clip can play again
on reopening/unmuting; ordinary recomposition does not restart it, and decoder
failure cannot create an automatic retry loop. Both lanes share one audio-focus lease. Permanent focus loss
pauses playback until a new interaction or a foreground restart; transient loss
waits for focus gain. Muting releases audio playback, while the intro continues
silently. The preference lives in device DataStore, outside the world snapshot.

Payment audio listens only to fresh, successfully committed commands, with request
ID deduplication. Rejected commands, previews, history, restores and idempotent
retries produce no payment sound. An uncertain write whose commit reply failed
stays silent, even if a later retry discovers the commit; feedback must never cause
another game command. Background/muted action cues are dropped rather than replayed
on return. Screens contain no MediaPlayer or filesystem calls.

Native player construction, asset access, preparation, playback controls and release
run on one media worker, outside the Compose/main thread. Main-thread scene state and
audio focus communicate with it asynchronously; player callbacks return to main and
discard stale completions. Reading video position uses a cached value instead of a
synchronous native call. The active, unmuted session prepares only the five short
location/action clips in advance; idle prepared clips are released on mute/background.
A newly opened event cancels any leftover action queue from the previous event.
The navigation host identifies the current entry explicitly, so its event audio
can start while the incoming card is already visible in STARTED, without waiting
for the slide to finish. Outgoing entries and predictive Back previews cannot
replace the current scene. Playback, focus and release work take priority over
speculative preparation; a cancelled focus request clears its pending state.
Music and long narration are not loaded into that short-clip cache.
Actual music decoder errors release the failed player. A new event/action, unmute
or foreground return can retry the same chapter; ordinary recomposition cannot
start a retry loop, and stale callbacks cannot close a replacement track.
Chapter selection first deduplicates story decisions and runs its projection on a
computation dispatcher; unrelated wallet, clothing and UI updates do not recompute
the chapter on main. Repository observation keeps its existing error/retry contract.

Historical content IDs and installed definitions remain readable. Compatibility
aliases/replacements belong to explicit compatibility data, not the rendering
layer. Presentation-only metadata is excluded from the gameplay fingerprint;
existing legacy copy and gameplay fields retain their previous fingerprint rules.
Changing music/artwork does not invalidate historical gameplay replay.

See [event authoring](design/event-authoring.md) and the
[artwork guide](design/assets/README.md).

## Shared presentation

`GameActionButton` owns primary, secondary and quiet interaction states, visible
disabled treatment, loading footprint, multiline text and minimum touch height.
Feature wrappers may select approved typography/shape; they do not reimplement
click handling or disabled logic. Loading and unavailable are distinct states;
short writes do not recolor the entire inventory.

`AdaptiveActionPanel` measures bottom actions at their natural height before
allocating a scrolling body. When both cannot fit, the panel scrolls as one column.
Onboarding uses the same principle: a scrollable card must not receive only the
leftover height via weight. Safe system/keyboard insets apply to controls; compact
windows and large text retain accessible actions through scrolling (ARCH-D-008).

Use `interactionBlocked` for a short write that must retain the button's label,
color and size without a spinner. It disables pointer, keyboard and accessibility
activation while keeping ordinary availability (`enabled`) separate. The menu's
Continue day and Feed buttons use this state, so one action does not flash both.

Shared components already include `GameArtwork`, `MovingPetArtwork`, NPC movement,
quiz options and adventure surfaces. Payment-source wording belongs to a shared
formatter; its input is an actual domain quote. Formatting never allocates money
or decides whether a purchase is affordable.

`MealPolicy` is the common food use case for engine transitions and UI offers:
ordinary meal, free alternative, availability, food requirement and consequences.
The ordinary price is the cheapest positive authored meal price, not the first
list entry. The current bundled paid/free rules are unchanged. UI only formats
these offers and renders the engine's block reasons.

## Learning scenarios

- `LearningHistoryViewModel` owns financial reports and adventure history.
- `TrainingViewModel` owns the question/answer flow and its pending request.
- `ReflectionViewModel` owns educational replay, comparison and explanation.

Each has its own state/actions and navigation entry. Stable route IDs remain
`financial_learning`, `skill_training` and `other_paths`. Shared screens/components
and pure projectors remain reusable; no mode flag makes one ViewModel build all
three scenarios on every answer.

Ordinary training does not read the complete audit or calculate history reports.
For an old plan-review question, `TrainingQuestionPresentation` caches only its
immutable presentation keyed independently from answer/attempt/hint progress.
Stale answers and uncertain writes retain their guards. Explanation exposure and
automatic advancement respect the route's active lifecycle.

History and reflection observation runs only while their route is resumed. Hiding
the route cancels the observer and report work, not a pending write or telemetry
job. Re-entry reads the latest committed state.

## Persistence and compatibility

Room is version 22; snapshot is format 5 and history format 1. Schemas and earlier
migrations remain in `app/schemas`. A full snapshot includes the aggregate and
complete validated history, plus the flat archive of completed runs. Restore does not mean merging multiple active devices.
There is no destructive fallback or silent reset to the initial fixture.

Regular writes cannot change financial-period origins or confirmed plans. Legacy
campaign adoption has a separate `reconcileCampaign` operation. Its typed patch
may update the pet's story age, selected goal/target and rebind only the current
open period. Room validates that all other period fields are unchanged and writes
the compatibility audit in the same transaction. Closed periods and past audit
entries remain immutable. This fixes old-save adoption without weakening normal
financial writes or requiring a Room schema change.

Production repository tests cover this path in addition to pure engine fixtures.
A memory fake must explicitly model command receipts when testing retries;
`update(transform)` alone cannot establish idempotency or Room compatibility.

See [data model](design/game-data-schema.md), [normalized schema](design/schema-normalization.md),
[persistence contract](design/room-persistence.md) and [snapshot contract](backend/world-snapshot.md).

## Rendering and work placement

Runtime raster artwork is loaded through the existing Coil-backed shared artwork
component at explicit bounds. Preserve full transparent character canvases and
catalogued alignment. Do not decode a large bitmap on every composition or create
new raster copies for ordinary UI variants.

Frame-by-frame animation values are read in the drawing/graphics layer, rather
than recomposing a screen. Pet/NPC breathing avoids allocating a pose object on
every frame. Derived state exposes meaningful changes such as reaching a zoom
limit. Keep effects scoped to their artwork/lifecycle; do not mutate GameState to
animate it. Temporary pet reactions are root-scoped presentation and return to
the latest equipped appearance after four seconds with a 600 ms crossfade.

Pure report/replay work belongs off the main thread and only to the scenario
that needs it. Before larger caching or incremental-storage changes, measure a
long save on a permitted device; source inspection does not establish actual FPS.

History reads follow the required scope. The day summary renders the committed
world first, then checks `TimeMachine.hasReflection(day)` through `readDayHistory`.
Room selects that day's commands before decoding and verifies the latest checkpoint;
an answer arriving after the displayed save changes cannot update the new summary.
`readFacts(eventIds)` resolves requested analytics facts through the existing fact-ID
index and decodes each matching audit entry once. The “Что больше?” quiz uses this
lookup for its recorded answers instead of materializing every historical world.
These are read-path optimizations, not a schema/snapshot change or a confirmed
diagnosis of the reported device crash.

## Backend boundary

Settings generates the parent's QR offline from the persisted `deviceId`. Plain
Preferences DataStore under `noBackupFilesDir` owns this identifier and registration
metadata. New installations use Android ID; existing identifiers survive a one-time
legacy migration. Legacy decryption only reads the old record; new credentials are
not generated. By PARENT-LINK-D-005 requests identify the device in their JSON body,
without authorization headers or tokens. Operation IDs still deduplicate retries.
Retrofit/OkHttp transport is in `data/backend`; `gradle.properties` configures
`https://fin-api.mortypython.ru/`, supplied by the team on 2026-09-27.
`-PLCT_BACKEND_BASE_URL=` can explicitly disable requests. Live endpoint compatibility
is not yet verified. Registration runs before synchronization and from the parent-code action. QR display and sharing
do not upload the world. The private FileProvider shares a PNG with temporary read
permission, without changing identity or game state.

`RemoteCloudSyncRepository` serializes registration, full snapshot upload, financial
evidence upload, skill assessment retrieval and parent reward delivery. Room owns
transport checkpoints and frozen pending requests; retry uses the same key and body.
Within one synchronization pass, the already exported immutable snapshot is reused
while `latestHistoryId()` is unchanged. A new history head triggers a fresh export;
the final change check reads only that ID, not the full audit. Full backup and restore
still carry the complete world, journal and completed-run archives.
The aggregate repository applies supported rewards against the latest local world,
atomically with their audit receipt. Only committed receipts are acknowledged.
Known new accessories apply now; coin allocation and duplicate accessory handling
remain gated by the unresolved product policy in the reward contract.

App composition schedules connected WorkManager requests after history changes
(five-second debounce), on foreground/network return, every minute in foreground,
and periodically every fifteen minutes subject to Android scheduling. This observes
only the history sequence; serialization and network work stay off the main thread.
The local world remains available offline and cloud conflicts never overwrite it.
Settings offers manual sync and a downloaded preview followed by explicit restore
confirmation. Restore validates the archive and guards the local history; its
durable intent recovers transport bookkeeping after a crash without restoring twice.
Firebase messaging is excluded. See [backend handoff](backend/README.md) and
[request triggers](backend/client-sync.md).

## Local diagnostics

The application installs its crash handler after Hilt application initialization.
`AppDiagnostics` retains a small, precomputed context; the handler writes an independent
bounded text file and always delegates to Android's original handler. It never reads
Room, exports a snapshot, waits for coroutines or sends an HTTP request while crashing.
App-level hooks record only screen types, lifecycle and structural game context.

Settings depends on the pure `DiagnosticLogRepository` contract. Its Android adapter
writes the local crash reports and available Android process exit summaries to the
document explicitly chosen through `CreateDocument`. Export runs on `Dispatchers.IO`;
no storage permission or external logging SDK is needed. Diagnostic files are separate
from gameplay history and cloud backup. See [diagnostics](design/diagnostics.md).

## Dependency injection

Use Hilt constructor injection. `app/di` binds repositories, the singleton session,
Room and platform adapters. Domain models stay free of DI annotations. Feature
entries obtain scoped ViewModels through `hiltViewModel`; composable screens receive
state and callbacks and remain previewable. Shared components do not obtain ViewModels.

## Verification

The current user preference is build-only: do not launch the app, emulator or tests.
Compile meaningful unit/instrumentation test sources, assemble debug, compile release
and run lint. Test source compilation is not a passing test run. Preserve existing
uncommitted work. Documentation-only edits require link/consistency checks.

Behavioral tests should cover transaction rollback, migration/restore preservation,
request identity on retry, stale revision rejection, and presentation independent
from gameplay. UI coverage should include large fonts, disabled/loading, restoration,
and lifecycle. Device timing/FPS measurements require the user to change the current
verification preference.
