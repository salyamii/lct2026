# App navigation

MainActivity hosts `app/LctApp`, which applies the theme and composes `LctNavHost`.
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

Live budget redirects keep `NavDisplay` and its entry decorators mounted. The
outgoing screen is temporarily noninteractive until the persisted planning route
replaces it; only initial gate loading uses the full loading screen. Successful
Day actions that require this redirect freeze their outgoing presentation too.
Short Day writes block duplicate input immediately, but show their saving
indicator only after 300 ms, avoiding a spinner flash for fast local commits.

| Menu action | Destination |
| --- | --- |
| Gear | `Gear` |
| Tasks | `Tasks` |
| Goal card / Goal shortcut | `Goal` |
| Coins | `Economy` (`coins`) |
| Savings balance / savings menu item | `Savings` (`savings`) |
| Village | `GameMap` (`village`) |
| Continue day | `Day` |
| Settings gear | `Settings` (`settings`) |
| Settings gear, long press | Internal `ParentsActivity` → PIN → local parent report |

The registered features render their working screens. Gear displays the inventory
with two sections, owned items only, and an entry-scoped Hilt ViewModel. Its saved
route ID remains `gear`; Back returns to the menu without changing game state.
Tasks displays current offered deeds and a Skill Training entry. The app host
maps its training callback to `SkillTraining`.
Goal owns read-only nested views inside its existing entry. Both toolbar and
system Back return from a preview to the catalogue, then to the current goal,
then to the menu. After the campaign, a catalogue with no current goal exits to
the menu. The catalogue's saveable list position remains mounted while details
are shown and survives Activity recreation. These view changes never select a
gameplay goal or purchase an item; pending writes block Back.
`ChapterPractice` (`chapter_practice`) is the separate finite catch-up route used
by blocked chapter continuations from MainMenu, Goal and Day (LEARNING-D-005).
It uses its own entry-scoped TrainingViewModel and the current saved milestones;
the route stores no question or world snapshot. Its Continue story callback opens
the existing Day entry when present. Voluntary Tasks training remains continuous.
Accepted gameplay work uses
`DeedGame` with the event occurrence ID. By ADVENTURE-D-020, Tasks has no demo
mini-game entry points. `StarPlates`, `PriceCheck` and `Telescope` retain the stable
IDs `tasks_star_plates`, `tasks_price_check` and `tasks_telescope` and their entries
only to restore older saved back stacks. Each legacy entry obtains its Hilt
ViewModel and collects state with lifecycle awareness. Back returns to Tasks;
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


## Game actions on existing routes - 2026-09-19

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

## Illustrated screen transitions - 2026-09-19

The host uses opaque horizontal slides (160 ms) for forward, Back and predictive
Back transitions. This replaces Navigation 3's default 700 ms crossfade, which
blended the Tasks and menu artwork and text during a return. The host fills the
window with the shared night background. Route order and entry-scoped state
retention are unchanged; predictive Back still follows the gesture.

## Offered deed mini-games - 2026-09-19

The stable `deed_game` route carries the occurrence ID and, since 2026-09-26,
an optional `choiceId` for a practical STORY/RANDOM branch. Its default is null,
so old routes still open the offered deed. Both values identify the entry and
its board state; no game snapshot or financial context is stored in the key.
Its serializer is registered alongside all existing keys. Starting from a Day proposal replaces
that entry; starting from Tasks pushes the game. Completion returns directly to
the menu. Both system and UI Back first save PauseEvent, then return to the menu.
A failed save leaves the game visible with retry; it does not silently navigate.
Back during loading waits for the saved occurrence before requesting its pause.
Practical event branches use the same route after a successful admission check;
their existing costs and story effects apply only on completion of the board.
See [the event-work contract](design/story-work-minigames.md).

DeedGameViewModel observes the aggregate; the existing board ViewModels belong
to this game's entry and retain their small SavedStateHandle state on recreation.
Explicit exit removes the entry, so the next start creates a new board. Restoring
a route whose occurrence was already completed or paused only exits; it grants
no rewards and does not execute a new event. Outgoing Day cards remain frozen
until replacement to avoid showing the intermediate generic day screen.

## Transient completion confirmation - 2026-09-19

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

## Waking and feeding - 2026-09-19

The summary's start-day action commits BeginDay with openFirst=false and exits
to the menu. The next Continue action opens the first pending/carried event.
Loading the morning or returning from the summary does not show an event or
create an offered deed. The outgoing summary remains frozen until navigation
removes it, just like other successful exits. Starting a day sends no event-
completion snackbar. Failed writes keep the summary open for retry.

Optional feeding opens a shared meal selector on the menu, event cards and Tasks.
By MEAL-D-001, paid meals cost 5/7/10; the latter two restore current effort by
1/2 up to the normal maximum and show HAPPY. The ordinary meal is unchanged.
If the ordinary meal is unaffordable, the selector also offers the free meal
with its current-day and next-morning consequences. Opening or cancelling the
selector does not execute a command; confirmation retains the source screen.
Event cards have no secondary feeding link; hunger replaces the blocked action
with feeding. Feeding never automatically executes the original action.

## Goal screen - 2026-09-19

The `goal` key is unchanged. Its entry obtains GoalViewModel and collects state
with lifecycle awareness. Selecting a goal and buying parts stay on that screen;
Back returns to the existing menu without executing a new event. The next explicit
Continue dispatches the next allowed event through GameSession. Purchase feedback
is transient local presentation state, never a saved route or domain event.

Under ADVENTURE-D-021, «Продолжить историю» explicitly continues from Goal.
Menu and Goal share `GameSession.continueDayPlan`: unallocated money opens Economy;
an active card resumes in Day; a finished day opens its summary; otherwise the
guarded command advances the day. The Goal entry replaces itself with Day only
after a successful command or a read-only resume. Missing financial practice
opens SkillTraining, from both Menu and Goal. Stale state stays on Goal with a
message; an uncertain write retries the same request. Buying itself never advances.

The lower Goal shortcut dispatches MainMenuAction.Goal; the header only informs
under ADVENTURE-D-008. Regression sources cover continuation, guards and retries.
They are compiled only under the user's verification preference; this does not
claim an on-device result.

## Pet name and floating village action - 2026-09-19

### Финальный Хроноскоп и архив прохождений - 2026-09-28

По CAMPAIGN-D-003 финальная карточка показывает «Воспользоваться силой».
Day сохраняет финальное событие, подготавливает архив и отправляет отдельное
событие app-owned startup gate. App очищает старый игровой стек до MainMenu и
выходит из него в CharacterSelection (`AppStartupState.Choose`). Промежуточного
перехода в меню или архив нет. У уже завершённого сохранения действие меню
по-прежнему открывает `CampaignArchive` (`campaign_archive`), где та же кнопка
ведёт в выбор персонажа. При неопределённой записи повторяется исходный запрос;
успешное финальное событие не выполняется повторно ради ошибки архива.
Budget gate сохраняет последнюю проекцию при временном отсутствии активного мира,
чтобы не уничтожить исходный экран до доставки перехода. После завершения новой
настройки создаётся новый мир и открывается обычное меню с начальным бюджетом.
AppStartupViewModel читает run ID из лёгкого snapshot head перед Ready. Этот ID
задаёт Compose-ключ NavHost: новое прохождение не восстанавливает старый игровой
стек даже при закрытии процесса между записью архива и навигационным callback.
Повторное открытие того же run сохраняет обычное восстановление маршрутов.
В истории текущего приключения есть вход «Прошлые приключения» в тот же экран.
Выбор архива читает его полную историю для просмотра, без restore и игровых команд.
Back из архивной детализации возвращает к списку. Ни snapshot, ни mutable state
не записываются в route key; идентичность устройства остаётся прежней.

Under D-093/D-094 the name badge is read-only, below the goal and to the right
of the coins. It has no click action, age label or menu editor. The saved name
still comes from the observed aggregate; the future name-entry flow is separate.

Under D-095/D-096 the village action uses the supplied menu_map illustration at
the physical right edge, directly above the status/actions block. Its vertical
position follows that block's measured top edge with an 8 dp gap. Part of the full canvas
extends outside the viewport and is clipped by the screen. Under D-099 it has
no separate panel, border or added shadow. There is no text label; accessibility
still names the action Village.
The landscape side panel reserves space beside it. It emits the same Village
action and opens the existing route, outside the side panel's scroll area.
Name and age are never navigation keys; moving these controls adds no game
transitions or travel costs.


## Goal chooser - 2026-09-20

The same Goal route now hosts the catalog list and a project's detail view.
GoalViewModel's SavedStateHandle keeps viewed_goal/show_list as presentation
state only. Back from details returns to the list; the explicit main-screen
button leaves the feature. System Back follows the same detail/list behavior.
Select/Buy carry catalog IDs into GameSession commands; no game snapshot or
progress flag is added to route keys. [Campaign rules](design/campaign-choice.md).

## First-launch gate - 2026-09-19

LctApp checks AppStartupViewModel before composing LctNavHost. The onboarding
entry belongs to `:feature:onboarding` and receives an app-owned start callback.
It is outside the game back stack: completion exposes LctNavHost with MainMenu
as root, so Back cannot return to character selection. Existing saves skip
onboarding and restore the normal Navigation 3 stack. No existing route IDs
change. The feature uses SavedStateHandle for the local fox selection only;
persistent completion is the existence of the aggregate save. Read errors do
not substitute a new game. See [onboarding](design/onboarding.md).

CAMPAIGN-D-003 adds durable CHARACTER to the existing onboarding step column.
An archived completed run can leave no active aggregate while a new profile is
being selected. Startup resumes Choose for CHARACTER and the saved editor step
for subsequent drafts. A missing active world never erases the archive. The
ordinary final onboarding write commits the chosen new profile and clears the
draft atomically; late background preparation cannot create a default profile.

### Customization step - 2026-09-20

The startup gate has Choose, IntroVideo, Customize, Accessories, GoalBriefing,
GoalSelection and Introduction steps. Start from the character screen persists a
Room profile draft, then presents the intro video before CustomizationScreen.
AppStartupViewModel owns the step and persisted draft; UI receives values
and callbacks. Back from Customize clears the draft before returning to Choose.
Successful introduction confirmation commits the new aggregate and only then exposes the
normal Navigation 3 host. Existing saves bypass all onboarding steps; no route key contains
pet data. Failed writes keep the editor visible. Drafts resume after cold starts.

CUST-D-016: Customize confirms the profile into the accessory step. Accessories
uses the same feature module and state/callback boundary. Back from Accessories
returns to Customize without resetting the profile. The draft persists both step
and selected accessory; only Start on Introduction commits a game and opens LctNavHost.

Under CUST-D-020 accessory confirmation opens GoalBriefing; “Дальше” opens
GoalSelection. Explicit goal confirmation opens Introduction (CUST-D-019).
Back returns to GoalSelection, then GoalBriefing and Accessories.
GoalBriefing persists as GOAL_BRIEFING in the existing draft step column;
existing GOAL_SELECTION/INTRODUCTION drafts keep their steps. The schema is unchanged.
Room v15 adds draft goal_id and migrates the old INTRODUCTION to GOAL_SELECTION.
Under CUST-D-021, Introduction explains the budget once during onboarding.
“Дальше” commits the pet and goal together, clears the draft and opens MainMenu.
Existing saves skip this explanation, including after a weekly income.
INITIAL/RECEIPT does not automatically redirect MainMenu to Economy; ContinueDay
and Coins open it explicitly. Allocation, weekly receipts and restored gameplay
routes retain the mandatory budget gate.
Existing saves skip onboarding. No Navigation 3 key or day/event transition is added.

### Intro video after character selection - 2026-09-27

Only a successful `startAdventure` draft save enters transient `IntroVideo`.
Completion, Skip and system Back all continue to `Customize` with that same
draft; they do not create a game, accept an empty name or skip later onboarding
steps. Playback position belongs to AppStartupViewModel and survives activity
configuration changes. A cold process start restores the persisted Profile
draft directly into Customize, so the interrupted video does not replay.
Existing complete games bypass the video with all other onboarding screens.

`IntroVideoScreen` in `:feature:onboarding` receives player, sound-toggle and
action slots from app composition. `IntroVideoEntry` connects the shared media
runtime and sound preference; it owns Back and the return to profile editing.
The video has no Navigation 3 key, Room step or world flag. Its mute button uses
the same persistent sound setting as Settings; a media/preference error leaves
an explicit way to continue. [Intro contract](design/intro-video.md).

## Карта - 2026-09-20

`feature/map/navigation/GameMap` заменяет заглушку Village. Стабильный serial ID
`village` и чтение старого имени `ru.nksk.lctapp.ui.village.navigation.Village`
сохранены. Ключ не содержит игровое состояние. Кнопка карты меню открывает
mapEntry; после успешного сохранения AppNavigator возвращает к корню меню.
MapViewModel получает GameLocationController через Hilt; entry собирает состояние
с lifecycle и обрабатывает завершение только в RESUMED. UI получает callbacks.

## Экономика - 2026-09-21

feature/economy заменяет coins; Economy сохраняет serial ID `coins` и чтение
старого имени `ru.nksk.lctapp.ui.coins.navigation.Coins`. Ключ не содержит данные.
EconomyGateViewModel в app наблюдает сохранённую сессию. Новое поступление и
восстановленные игровые маршруты при незавершённом бюджете ведут к Economy.
На RECEIPT Back разрешает меню. На ALLOCATION Back блокируется, пока бюджет
не готов; готовый бюджет подтверждается перед выходом (D-143).
Текущая сессия не открывается циклически сама собой.
Continue вновь открывает её. Этап RECEIPT/ALLOCATION читается из Room.

По LEARNING-D-007 (2026-09-29) бюджет, открытый из `ChapterPractice`, после
подтверждения или обычного выхода возвращает к сохранённому разбору главы.
Принятые ответы остаются в том же entry. После завершения разбора кнопка
«Продолжить историю» сначала вычисляет `GameSession.continueDayPlan` по свежему
агрегату и сохраняет его команду, затем открывает `Day` без промежуточного меню.
Существующий `Day` переиспользуется, иначе он заменяет завершённый разбор.
Неопределённый повтор сохраняет исходный ID и revision команды.
Если бюджет уже находится ниже разбора в стеке, его единственный ключ переносится
поверх разбора без удаления промежуточного пути и без дублирования `Economy`.

Успешное подтверждение бюджета передаёт ID завершённой сессии в app host.
Пока наблюдатель догоняет запись, только эта сессия не вызывает повторный
редирект; другой ID и холодное восстановление по-прежнему проходят gate.
После правильного итогового ответа новая редакция бюджета не требуется.
Реальная уже открытая незавершённая сессия и питание остаются доменными
ограничениями. Продолжение не кормит и не подтверждает бюджет само.

После знакомства с выбранным приключением host открывает INITIAL/RECEIPT.
Вне сессии тот же маршрут открывает редактор; первое изменение создаёт MANUAL/ALLOCATION без начисления. Подтверждение
и Back возвращают к меню. Gameplay-защита независимо находится в домене;
навигационный переход сам по себе не начисляет деньги.

По D-142 «Монетки» всегда открывает редактор текущих статей. MANUAL/ALLOCATION создаётся при первом изменении на их основе и сохраняет черновик; требует распределить все доступные деньги. По FINANCE-UI-D-015 минимум «Нужно» для INITIAL/WEEKLY - min(35, база), для MANUAL/MIGRATION - min(оставшаяся потребность в еде, база). Простое открытие ничего не начисляет и не блокирует игру; без редактирования выход не требует заново пополнять потраченное на еду. Автоматического распределения нет.

## Выбор подцели - CAMPAIGN-D-001, 2026-09-24

Маршрут онбординга сохранён: GoalBriefing → GoalSelection → Introduction.
Выбирается savingItemId из четырёх предметов первой главы. GameSession создаёт
игру с её комплектом и выбранным предметом; до финального подтверждения это
только черновик. Существующая игра не подменяется черновиком. Goal показывает
последовательные главы и выбор/прогресс накопления внутри текущей. Просмотр
состава остаётся локальным состоянием навигации; выбранный предмет хранится
в агрегате через команду движка.

По реализации FINANCE-UI-D-009 экран Goal сразу показывает превью текущей
главы, даже если подцель ещё не выбрана. Просмотр не выполняет SelectGoal.
«Другие цели» открывает список превью; View запоминает источник входа в
SavedStateHandle. Back из открытой таким способом главы возвращает список,
а из первоначально открытой текущей главы - предыдущий маршрут. Суммы и
предметы остаются в наблюдаемом игровом состоянии, не в ключе маршрута.

## Копилка и Хроноскоп - FINANCE-UI-D-007/017, актуализация 2026-09-26

`Savings` (`savings`) зарегистрирован в сериализации и принадлежит economy.
Экран открывается из меню, HUD бюджета и задания. По реализации
FINANCE-UI-D-017 это одна страница: постоянные переключатели «Пополнить» /
«Взять», ввод суммы, последствия и результат записи. Промежуточного обзора
и отдельного экрана RESULT больше нет. Подтверждение снятия и предупреждение
о нехватке на еду появляются внутри этой же страницы. Режим, сумма,
подтверждение и результат принадлежат `EconomyViewModel`; маршрут не содержит
денег или снимка и не создаёт дополнительные ключи для этих состояний.

Back при открытом подтверждении или предупреждении отменяет его без перевода;
в остальных случаях возвращает к предыдущему разделу. Во время записи
повторное действие и выход заблокированы. Отмена и повторное открытие никогда
не отправляют перевод автоматически. До денежного решения сохраняются
фактические доступные монеты, копилка, известная потребность в еде и последствия
для выбранной цели; сокращение переходов не снимает доменные проверки.

Перекрёстные переходы Goal ↔ Savings и Budget ↔ Learning используют
`navigateToExisting`: если раздел уже находится ниже в стеке, удаляются только
экраны над ним. Это сохраняет его ViewModel и исключает повтор одинакового ключа.
Каждый callback проверяет текущий source. «На главный экран» отдельно связан
с `returnToRoot`, обычный Back сохраняет порядок предыдущих экранов.

По LEARNING-D-001/002 история, тренировка из «Дел» и пересмотр из итогов дня
имеют отдельные entry-scoped ViewModel и UiState. Разделение реализации
2026-09-27 сохраняет существующие ключи и их сериализацию:

| Ключ / serialName | ViewModel | Экран |
| --- | --- | --- |
| `Learning` / `financial_learning` | `LearningHistoryViewModel` | `HistoryScreen` |
| `SkillTraining` / `skill_training` | `TrainingViewModel` | `TrainingScreen` |
| `OtherPaths(day)` / `other_paths` | `ReflectionViewModel` | `ReflectionScreen`, шаги в `ChronoscopeScreen` |

День в `OtherPaths` - фильтр просмотра, не копия игры. Entry получает ViewModel
через Hilt, собирает UiState с lifecycle и передаёт UI только состояние и callbacks.
History и Reflection через `repeatOnLifecycle(RESUMED)` включают наблюдение
в `setActive(true)` и при уходе отменяют только observer job. Это прекращает
чтение журнала и построение отчётов скрытого экрана; при возврате подписка
получает актуальный агрегат. Незавершённые команды и записи аналитики остаются
в `viewModelScope` и не отменяются при переходе в STARTED/STOPPED.

Шаги пересмотра принадлежат `ReflectionViewModel`: воспоминания → выбор →
сравнение → вопрос → объяснение. Back возвращает по шагам; выход - к итогам.
В тренировке Back закрывает вопрос с сохранением серии либо возвращает из
списка тем. Автоматический переход после верного ответа выполняется только
в RESUMED и несёт ID вопроса; закрытый или уже сменившийся вопрос не продвигается.
Просмотр истории не меняет состояние игрового дня. Бюджетный gate и доменные
ограничения планирования продолжают действовать.
[Текущие сценарии и разделение состояния](design/skills-and-reflection.md).


## Кнопка панели в главном меню - 2026-09-26

По ADVENTURE-D-004 существующая debug-панель открывается шестерёнкой в верхней
строке главного меню. `LctApp` передаёт необязательный composable-слот через
`LctNavHost` и `mainMenuEntry`; feature/menu не зависит от feature/debug.
Состояние панели остаётся локальным у владельца debug-панели, новый маршрут
и поля игрового сохранения не добавлены. Release передаёт пустой слот и
по-прежнему не включает отладочный модуль.


## Распределение сразу после onboarding - 2026-09-27

CUST-D-022: Introduction → INITIAL/ALLOCATION → MainMenu. Кнопка
«Распределить монеты» завершает onboarding с уже выбранными целью и подцелью;
сохранённая незавершённая сессия направляет в Economy. Пока gate переводит
маршрут, меню не показывается промежуточным кадром. Перезапуск возвращает
к тому же распределению без повторного дохода. Legacy INITIAL/RECEIPT остаётся
читаемым и возобновляемым по прежним правилам; новый маршрут не содержит данных
игрового сохранения.

По ADVENTURE-D-008 верхняя плашка цели только информирует; кнопка настроек
стоит отдельно справа. Переход к цели остаётся у нижней кнопки меню.


## Settings - 2026-09-27

The separate menu gear opens `feature/settings` in both debug and release builds.
The `settings` key carries no game state, profile identifier, linking token or QR
payload. Its entry owns the Hilt ViewModel, lifecycle-aware collection, copy action
and Back callback. Back only pops the route; opening settings does not advance the
day or change the budget. The pending-budget redirect allows this read-only route.

`AppDebugOverlay` stays at the app composition root. Its optional launcher is passed
to a developer-tools section inside Settings, so release builds retain real
settings without developer controls. Parent linking uses a repository outside the
game aggregate. An explicit button generates the QR offline from the saved
deviceId, without wrapping it in a URL or JSON. The entry then registers the profile
when a backend is configured; registration failure leaves the QR visible and offers
a separate retry. Without a configured server, the QR remains available with a
short connection note. The QR has no expiry and is never serialized into route keys.

The QR's «Поделиться QR-кодом» button opens the Android Sharesheet with a PNG
of the displayed matrix. The entry owns this external navigation, guards it by
RESUMED lifecycle and an in-flight flag, and shows a retry message if preparation
or opening fails. PNG creation runs off the main thread; the coroutine is scoped
to the entry. A private FileProvider exposes only `cache/parent_qr/` with temporary
read access. Sharing does not register a profile, regenerate its ID or upload the
world; it remains available offline. The user chooses the recipient application.

## Встроенный родительский режим - 2026-09-28

По PARENT-MODE-D-001/002 короткое нажатие шестерёнки сохраняет `Settings`,
долгое вызывает app-owned launcher внутренней `ParentsActivity`. Callback
проверяет RESUMED и текущий MainMenu; in-flight флаг исключает несколько
одновременных запусков. Родительский режим находится в том же Android task
и процессе, использует общий `LctApplication`, без собственного launcher.

Activity держит PIN-gate перед всем `ParentsNavHost`: чтение/создание/проверка
кода не являются восстанавливаемыми маршрутами с флагом доступа. Только
разблокированная in-memory ViewModel позволяет показать родительские ключи:
`parent_report`, `parent_topic(skillId)`, `parent_quests`. Ключи не содержат
PIN, deviceId или игрового снимка. Сериализаторы принадлежат отдельному
родительскому host; игровой список сериализаторов не меняется.

Выход из корня завершает Activity и возвращает игровое меню. После фона и
смерти процесса снова требуется PIN; смена конфигурации сохраняет ViewModel.
Ошибка чтения PIN не открывает настройку нового кода. Поздние результаты
проверки после ухода в фон игнорируются. Первоначальная установка четырёх
цифр с повтором сохраняет поведение исходника как выбор реализации;
сброс забытого PIN отдельно не определён. Отчёт читает текущее локальное
прохождение; сканер и ввод/выбор чужого ID не перенесены.
По PARENT-MODE-D-003 (2026-09-29) внутри разблокированного host действует
`ParentReportRefreshEffect`: при RESUMED он обновляет серверные оценки текущего
устройства, при уходе отменяет запрос. Обычная рекомпозиция и переход между
родительскими маршрутами не запускают его заново; новый вход или возврат в
RESUMED обновляет оценки. Ручное обновление есть в отчёте и карточке навыка.
Локальные данные и подходящий кеш остаются видны при сетевой ошибке.
Вопросы и квесты остаются заглушками. PIN/отчёт не выполняют игровые команды.

## Parent quest demo - 2026-09-29

By PARENT-MODE-D-006/008, the report's real-life quests block opens three
hardcoded demos: Shopping, Weekend and Second life. `parent_quest` carries only
a serializable ParentQuest identifier. The existing `parent_shopping_quest`
route still opens Shopping when restoring an older stack, and `parent_quests`
retains its placeholder for old back-stack restoration only. By PARENT-MODE-D-007,
the Create quest button and its callback are removed. Skill detail has no quest entry.
ParentQuestEntry obtains an entry-scoped Hilt ViewModel and collects state while
RESUMED. Checkbox progress and completion are separate for each quest entry and
live only in memory. Popping the entry discards them; no progress is serialized
in the route or saved to the game. All four checks enable local demo completion.
The accessory image is a placeholder and completion performs no backend request
or reward delivery. Back returns to the report.
