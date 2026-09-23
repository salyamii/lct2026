# Ryzhik: app state-machine specification

> Текущая адаптация, 2026-09-20, D-101–D-106: доступны три стартовые цели;
> карта открывается после первого завершённого проекта, большая экспедиция —
> после остальных четырёх. Пять общих актов раскрываются последовательно,
> независимо от порядка личных проектов. Финал требует текущего комплекта и
> сюжетных открытий. [Точные переходы и ограничения](campaign-choice.md).
> Исходные материалы Figma сохранены; новые связки описаны отдельно.

> Дополнение D-086, 2026-09-19: к действующему циклу подключены независимые
> покупка и неожиданная трата. Их точный состав и границы описаны в последнем
> дополнении [пакета контента](content/README.md). Для выбора платной услуги
> либо самостоятельной работы расход сил определяется выбранным ответом.
> Открытие карточки не списывает деньги/силы; успешный ответ атомарно меняет
> баланс, вещи, силы и историю, занимает один шаг и возвращает в меню.
> Недоступный платный вариант не блокирует бесплатную работу при достаточных
> силах. Если на доступную работу не хватает сил, после паузы разрешён сон
> с переносом всего остатка. Покупку можно отклонить завершённым ответом
> «Пройти мимо»; пауза ремонта не равна выполнению. Сохранённые планы не
> пересобираются при загрузке каталога. Исходная спецификация ниже сохранена.

> Implementation addition, 2026-09-19: the original specification below is
> retained. The implemented day/event kernel is documented in
> [game-engine.md](game-engine.md), including its explicit configuration and
> unsupported/open operations. It lives in `:core:game` and persists via Room v2.
> `RUNNING → READY_TO_END → FINISHED` describes the day; event occurrences use
> `PENDING → ACTIVE → RESULT → COMPLETED` with `CARRIED` for postponed lore.
> Offering a deed goes directly to `RESULT` and consumes one step without
> spending effort or granting its reward; later execution is a separate step.
> These are implementation stages, not an internal multi-step story graph.
>
> Pet visual state remains an explicit single-state replacement in `PetState`.
> Hunger/effort guards are currently owned by `GameEngine`; there is no separate
> pet behavioral FSM. Food changes the fed status, not current energy; configured
> free food can limit next morning's energy. Wake-up clears TIRED explicitly;
> acknowledging an event does not reset moods. Cosmetic selection is now the
> open string `selectedLookId` (D-070), independently preserved through transitions.

## Показатели меню — 2026-09-19

По [MAIN-D-071 и MAIN-D-072](decisions.md) главное меню отображает «Голод» и «Усталость»
в стиле блока монет, изначально 0. Оба показателя имеют шкалу 0–100.
Успешные «Перепутанные находки» / «Сверка счетов» / «Настрой телескоп» добавляют
30 / 50 / 40 усталости соответственно и по 20 голода. Допуск проверяет оба
будущих значения (`<= 100`); затраты показаны на карточках. По MAIN-D-073 викторина и телескоп успешны после всех пяти раундов, даже с нулём
верных ответов/попаданий; пары — после сбора всех пар. Прерывание игры
не меняет показатели. Одна успешная попытка учитывается один раз, с проверкой
последнего сохранения внутри транзакции. Меню наблюдает сохранённые значения.
По MAIN-D-076 голод хранится только в `satiety`; лишний `hunger` удалён в v6. Автоматические изменения визуального
состояния, отдых, питание, шаги сюжета и реальные денежные награды не добавлены.

## Scope and authority

This is a self-contained transcription and structured interpretation of the
“Рыжик — визуальная машина состояний” product-logic board in “Питомец Дизайн”,
captured on 2026-09-17. Scope: the complete requested board (102:672), including
all 102 text nodes, eight state cards, priority rules, architecture, and three
end-to-end examples. Other boards and screens are outside this specification.

Sections 1–7 organize explicit board requirements and clearly labeled subsequent
product decisions approved by the user. English identifiers below are
documentation aliases, not prescribed Kotlin names or existing implementation.
Section 8 records unspecified decisions; section 9 derives verification cases
from the requirements. The appendix preserves every source text verbatim,
including labels, examples, and image placeholders, for offline agent use.
The board contains eight image placeholders, no prototype reactions, no
annotations, and no additional node descriptions. It does not supply state artwork.

When changing app state behavior, follow the requirements here. Do not promote
an example into an exhaustive rule or silently resolve an unspecified decision.
Explicit subsequent user decisions take precedence over conflicting board
requirements. The source appendix remains unchanged as historical provenance.

**Принято пользователем · D-008 · 2026-09-17:** Figma remains the base source,
with the user's explicit refinements retained. The [decision register](decisions.md)
records those approvals and distinguishes them from agent proposals. Apply the
same provenance labels to subsequent decisions.

**Принято пользователем · D-023 · 2026-09-18:** event eligibility must account
for satiety and fatigue parameters, with requirements described by the event.
The [current data-model draft](game-data-schema.md) proposes threshold fields.
Their ranges, comparisons and relationship to the single visual state remain
unresolved. Do not infer automatic visual transitions or unspecified thresholds.

## 1. Separate appearance from visual state

| Concept | Meaning | Values documented on the board |
| --- | --- | --- |
| `PetLook` / selected look | Saved cosmetic appearance; not a machine state | ОБЫЧНЫЙ (plain), БАНДАНА (bandana), РЮКЗАК (backpack), ОЧКИ (glasses), ШЛЯПА (hat) |
| `PetVisualState` | Current visual state; special states temporarily replace the entire selected appearance | Eight states in section 3 |

The selected look remains remembered while a special state is displayed. Ending
a special state restores that look when the resolved state is ОБЫЧНОЕ.
ОБЫЧНЫЙ is a cosmetic look; ОБЫЧНОЕ is a visual state. Do not conflate them.

Do not create combined states such as ГОЛОДЕН_С_БАНДАНОЙ,
ГОЛОДЕН_С_РЮКЗАКОМ, or РАДУЕТСЯ_В_ОЧКАХ.

**Принято пользователем · D-005 · 2026-09-17:** changing cosmetics updates the
selected look; items already owned remain in the player's gear. Changing the
selection does not remove previously owned items. The rendering contract below
still applies: during a special state, the selected look is saved, and NORMAL
displays the current selection.

**Принято пользователем · D-017 · 2026-09-18:** an event can cause the player
to acquire or lose an item. Reference story content must describe this consequence;
the resulting ownership belongs to the saved player data. D-005 still governs
cosmetic selection: changing the look alone does not remove an item. Explicit
event-driven loss is a separate action. The selected-look policy when an equipped
item is lost remains open; do not silently choose a fallback or infer automatic
equipping when an item is acquired.

**Принято пользователем · D-018 · 2026-09-18:** item consequences are optional.
An event can grant and remove no items; in that case ownership remains unchanged.
Other authored consequences, such as money or pet-state changes, are separate.

**Принято пользователем · D-026 · 2026-09-18:** collecting the required items
is sufficient for an item-collection goal; completing it does not consume or
remove those items. They remain owned; D-029 describes subsequent item loss.

**Принято пользователем · D-029 · 2026-09-19:** if a required item is later lost
and the required set is no longer owned, the goal becomes incomplete again.
Completion is reversible. Story rollback and an automatic visual change are
not specified by this rule; D-030 defines when the active goal changes.

**Принято пользователем · D-030 · 2026-09-19; уточнено D-102 · 2026-09-20:**
goal progression accompanies chapter progression, but the next project is chosen
under D-101/D-102 rather than fixed by the chapter number. Switching an unfinished
project is not specified.

**Принято пользователем · D-031 · 2026-09-19; уточнено D-102 · 2026-09-20:**
advancing to the next chapter requires the currently selected goal to be complete:
its required items must still be owned when advancing. Any available selected
project can satisfy this requirement; it is not tied to one predetermined goal.
This is a necessary condition, not an automatic transition upon collecting the
last item. Required story discoveries must also be completed.

**Принято пользователем · D-032 · 2026-09-19; уточнено D-102 · 2026-09-20:**
an authored story timeline contains a final lore event that advances to the next
chapter. Project choice follows D-101/D-102 rather than a fixed chapter-to-goal
mapping. The completed-goal requirement from D-031 applies.
The precise event stage and whether
an incomplete goal blocks event entry or only the transition remain unspecified.
This does not define how non-lore events are selected.

**Принято пользователем · D-033–D-036 · 2026-09-19:** events are described by
five content categories: state/need, random circumstance, wants, earning, and
story progression. A want is a legitimate pleasant purchase; its category does
not determine the fixed impact rating of a particular choice. Earning exchanges
effort and game time for money; units and amounts remain open. D-037 specifies
when earning is offered. Story
events must be passed in their prerequisite order, and need not involve an item.
Mentioning health does not define a numeric health meter or another visual state.
The [current schema discussion](game-data-schema.md#23-типы-появление-и-прохождение-событий)
separates these requirements from proposed event fields and activation rules.

**Принято пользователем · D-037 · 2026-09-19:** the game offers an earning
event when money is insufficient for a particular action. Actual money is shared
according to D-040. Automatic acceptance, repeat offers, and returning to the
original action remain unspecified.

**Принято пользователем · D-038 · 2026-09-19:** goal-required items are not
awarded by story-progression events; they can be purchased in the goal tab.
This refines D-036 and restricts the general event-item consequences from D-017
for this combination of event and item. Story progress and buying goal items
are separate actions; payment uses the common balance from D-040. Prices,
purchase availability, and obtaining goal items from other event categories
remain unspecified.

## 2. Rendering contract

```text
if visualState == ОБЫЧНОЕ:
    display the saved selectedLook
else:
    display the dedicated image for visualState
    fully replace the cosmetic appearance, including accessories
```

Examples in ОБЫЧНОЕ: БАНДАНА displays Ryzhik wearing a bandana; РЮКЗАК
displays Ryzhik wearing a backpack; ОБЫЧНЫЙ displays plain Ryzhik.
A hungry Ryzhik has no selected accessory visible, but the selected look is
retained and appears again on return to ОБЫЧНОЕ.

## 3. State catalog and lifecycle

**Принято пользователем · D-001 · 2026-09-17:** only one pet state is active at a
time. There are no hidden conditions, suppressed reactions, or pending reaction
queues. Explicitly clearing the current condition returns the pet to NORMAL.
A quest may explicitly transition between
its decision, need, and result states as in the source scenarios; each new state
replaces the previous one rather than hiding it. See section 5 for the distinction
between an explicit transition and the board's superseded fallback rule.

**Принято пользователем · D-002 · 2026-09-17:** the current pet state,
including HAPPY and UPSET, remains until an explicit gameplay event updates it.
There is no automatic reset after a duration, animation, or generic Continue
action. A later event can explicitly change UPSET to NORMAL, for example; the
pet need not become NORMAL before that event is processed. This supersedes the
board's description of HAPPY and UPSET as brief reactions. Event-specific
mechanics are outside the initial state-model foundation.

| Source state / English alias | Entry condition | Duration and exit |
| --- | --- | --- |
| ОБЫЧНОЕ / NORMAL | No active special state | Display the saved cosmetic look until a relevant event activates another state. |
| ЗАДУМАЛСЯ / THINKING | Before an important financial decision; for example, buy an item now or save money toward a goal | After the decision, an explicit outcome can replace it with another state or NORMAL. No hidden decision state remains. |
| ГОЛОДЕН / HUNGRY | A story event says food is needed, or a planned food need remains uncovered | Remains active until the need is met. The food scenario explicitly changes it to HAPPY; a later explicit update can return it to NORMAL. Controlled by game logic, never real elapsed time. |
| УСТАЛ / TIRED | A consequence of a story decision, such as additional work for virtual money | Rest or an explicitly defined fatigue-clearing event → NORMAL. Exact events are deferred; do not make every next event clear fatigue. No continuous energy meter or real-time energy consumption. |
| ОБЕСПОКОЕН / WORRIED | Before the child's choice, when an unexpected mandatory expense, insufficient funds for a need, or a financial story problem occurs | After the decision, follow its explicit result transition, such as a reaction followed by NORMAL or the source example leading to HUNGRY. WORRIED does not remain hidden afterward. |
| РАДУЕТСЯ / HAPPY | Positive outcome: goal reached, problem solved, desired item received, or adventure stage succeeded | Remains HAPPY until an explicit gameplay event changes the state. An explicit change to NORMAL restores the selected look. |
| РАССТРОЕН / UPSET | Outcome: goal postponed, less advantageous option chosen, or part of the adventure temporarily unavailable | Remains UPSET until an explicit gameplay event changes the state, for example to NORMAL. Not punishment or a moral judgment. |
| НУЖНА ПОМОЩЬ / NEEDS_HELP | Only an external story event, such as a twisted paw | Event → financial need → child decides where to obtain money. After resolution or event completion → NORMAL. Never caused by “bad spending.” |

## 4. Explicit event mappings and time rules

These are semantic event labels from the board, not API definitions.

| Event category | Event | Resulting visual state |
| --- | --- | --- |
| Story event | Food needed | ГОЛОДЕН |
| Story event | Important choice | ЗАДУМАЛСЯ |
| Story event | Unexpected problem | ОБЕСПОКОЕН |
| Story event | Additional work completed | УСТАЛ |
| Story event | Injury | НУЖНА ПОМОЩЬ |
| Event result | Goal reached | РАДУЕТСЯ |
| Event result | Goal postponed | РАССТРОЕН |

Game time and real-world clocks are unrelated. Do not use real-clock timers to
drive these conditions, automatic hunger after real hours, or deterioration
while the app is closed. Do not introduce a continuously depleting energy bar.
All pet states, including HAPPY and UPSET, change only through explicit event
updates. Animation completion, screen navigation, and elapsed time do not clear
them by themselves.

## 5. Priority and routing

The approved priority order is listed below; lower rank numbers have higher
priority. It does not authorize multiple active states.

The source board orders categories but leaves ties within a category open.
**Принято пользователем · D-003 · 2026-09-17:** the user specified the following
complete order, resolving those ties while preserving the board's category order.

| Rank | Category | State |
| --- | --- | --- |
| 1 | Special story condition | НУЖНА ПОМОЩЬ / NEEDS_HELP |
| 2 | Active need | ГОЛОДЕН / HUNGRY |
| 3 | Active need | УСТАЛ / TIRED |
| 4 | Decision state | ОБЕСПОКОЕН / WORRIED |
| 5 | Decision state | ЗАДУМАЛСЯ / THINKING |
| 6 | Event outcome | РАССТРОЕН / UPSET |
| 7 | Event outcome | РАДУЕТСЯ / HAPPY |
| 8 | Default appearance | ОБЫЧНОЕ / NORMAL |

**Принято пользователем · D-001 · 2026-09-17:** only one state can affect
Ryzhik at a time. The board's simultaneous-condition selection and fallback
rules are superseded. Do not retain a lower-priority condition to reveal later,
and do not suppress, queue, or replay reactions. Clearing the active condition
returns to NORMAL; it never reveals a previously hidden state.

The board also gives an explicit decision-result transition:

```text
ОБЕСПОКОЕН
  → child could not cover the need
  → ГОЛОДЕН
```

Such a transition replaces WORRIED with HUNGRY as the result of the current
quest's decision; it is not the activation of a hidden hunger condition. The
same replacement principle applies to HUNGRY → HAPPY in the food scenario.

Sequential event updates do not need a resolver over multiple active conditions.
Priority does not block an explicit update to a lower-priority state, including
NORMAL. It does not select quest outcomes or define a concurrent event-input
policy. Future event handlers decide their explicit outcome under approved
story rules; the state foundation only represents and applies that result.

## 6. Game-state ownership

**Принято пользователем · D-039–D-040, D-147–D-149 · 2026-09-20:**
the budget has four sections: «Нужно», «Хочу», «Коплю», and «Запас»
(previously «На всякий случай»). STATE/RANDOM/STORY expenses prioritize
Запас → Хочу → Коплю → Нужно; WANT prioritizes Хочу → Запас → Коплю → Нужно.
Feeding prioritizes Нужно → Запас → Хочу → Коплю. Goal parts can only be paid
from Коплю; insufficient funds grey out the button. Tapping it explains the
shortfall without purchasing (D-125). EARNING must neither spend nor require
money, on opening or in any choice; tests must enforce this rule (D-148).

По D-120–D-122 неизменяемый план заменяется реальными остатками статей.
Недельные 100 монет поступают нераспределёнными; специальное событие позволяет
однократно изменить распределение старых и новых денег. По D-142 ручное перераспределение доступно в любой момент. Доходы мини-игр и событий сразу идут в «Запас» (D-138).
Расход исчерпывает статьи по приоритету; нехватка всей допустимой суммы
не приводит к частичному списанию. Использование последующих статей сопровождается
пояснением о недостаточном планировании. Место и текст пояснения ещё проектируются.
По D-124/D-127 меню показывает четыре остатка в прежнем блоке монет
вертикально друг под другом;
по D-126 существующие итоги дня сохраняют своё поведение.
По D-128 до полного распределения денег нельзя подтвердить бюджет
или продолжить игру; выход с экрана не обходит это условие.
По D-143 выход из планирования запрещён, пока бюджет не готов; показывается пояснение.
Готовый бюджет при выходе подтверждается и сохраняется перед возвратом в меню.
«Продолжить день» снова открывает незавершённое планирование. Реконфигурация
не сбрасывает состояние. По D-132 будущая карточка недельного дохода ведёт
к планированию и также допускает выход в главное меню. Возврат в планирование
не повторяет начисление. Этап и суммы сохраняются через MVVM и агрегатный репозиторий Room по D-141.
По D-129 в недельном распределении «Нужно» должно содержать минимум 35 монет;
кнопки изменения суммы имеют шаг 5. Остальные статьи дополнительных лимитов
не имеют. Минимум не блокирует расходование уже распределённых денег ниже 35.
Подтверждение проверяет полное распределение и применимый минимум в домене.

Правила подключены к агрегатному сохранению и игровому движку; этап и черновик
хранятся в Room. См. [экономику бюджета](economy-budget.md).

The current persisted aggregate follows D-024, D-039, and D-040. Exact proposed
columns and foreign keys are defined in the [normalized schema](schema-normalization.md).
This layout does not prescribe merging the domain Kotlin classes.

```text
Persisted game / Сохраняемая игра
├── GAME_STATE
│   ├── selected look and visual state / образ и визуальное состояние
│   ├── satiety and fatigue / сытость и усталость
│   ├── common balance / общий денежный остаток
│   ├── four plan sections / четыре секции плана
│   └── story position and active event / прогресс и активное событие
├── OWNED_ITEM / принадлежащие вещи
└── PLAYER_DECISION / история решений
```

One outcome may change money, story position, ownership, and the visual state
together. These changes belong to one atomic saved-game update. The pet's
visual state is only one part of that aggregate.

**Принято пользователем · D-004 · 2026-09-17:** closing and reopening the app must
preserve the current game and pet state. Owned gear is also retained. Reopening
does not reset the pet to NORMAL or advance the story merely because time passed.
This includes HAPPY and UPSET: reopening restores the saved state until an
explicit event changes it. Room is the approved persistent-data technology in
AGENTS.md; this requirement does not define cloud backup or restore.

**Принято пользователем · D-007 · 2026-09-17 (initial menu scope):** the original
menu integration used an in-memory fixture without game restoration. The next
stage, **D-043 · Принято пользователем · 2026-09-19**, requests Room implementation
according to the current model. The menu now observes the saved game through a
repository. Initial data is inserted only when no save exists; errors do not
reset it. Quest progression and recurring income are not implemented by loading.

The [persistence contract](room-persistence.md) defines implemented transactions,
restoration and error handling. The [current model](game-data-schema.md) and
[normalized schema](schema-normalization.md) describe stored data and remaining
product questions. An active event ID still does not encode an event's detailed
stage or interruption context; these mechanics remain future work.

### Demo mini-games boundary

The Tasks section includes three standalone demos described in
[mini-games](mini-games.md). Their scores and short feedback delays do not update
persistent game state, grant actual money or alter the pet. They are not an
implementation of the earning-event scenario below. Integrating real rewards
requires authored effects and the still-open earning rules; restoring a demo
session must not execute a gameplay outcome.

## 7. Source scenarios

These scenarios describe successive states of a single quest. They do not
create concurrent or hidden conditions. Apply the subsequent decisions in
sections 1, 3, 5, and 6 when implementing them.

### 7.1 Hunger and cosmetic restoration

1. NORMAL: Ryzhik wears the saved bandana.
2. Food-needed event occurs.
3. HUNGRY: show hungry Ryzhik without the bandana.
4. Child buys food and covers the need.
5. An explicit food-result update changes the state to HAPPY.
6. A later explicit update returns to NORMAL and shows Ryzhik wearing the saved
   bandana, or the current selection if the player changed it in the meantime.

The shorter cosmetic example omits the reaction step; the full hunger example
explicitly includes it. Under the subsequent user decision, HAPPY remains until
another explicit event update; there is no timed reset.

### 7.2 Financial problem

1. NORMAL.
2. An unexpected expense occurs.
3. WORRIED.
4. Child chooses where to get the money.
5. Show UPSET or HAPPY depending on the result.
6. A later explicit event update returns to NORMAL.

The board does not define the calculation that classifies each financial result.

### 7.3 Additional earnings

1. NORMAL.
2. Child chooses additional work.
3. Child receives virtual money.
4. TIRED.
5. An explicitly defined fatigue-clearing story event or rest.
6. NORMAL.

**Принято пользователем · D-006 · 2026-09-17:** the board says "next story event / rest." The user has explicitly deferred the
identities of fatigue-clearing events to future story design. Do not infer that
an arbitrary next quest or opening another screen clears fatigue.

## 8. Unspecified decisions — do not invent product rules

The [decision register](decisions.md) contains current approvals. The
[data model](game-data-schema.md#5-открытые-вопросы) lists unresolved data and
gameplay rules. Weekly income is fixed at 100 coins; day and event counts remain
provisional.

- Detailed restoration boundaries for uncommitted UI input. Preservation of the
  current game, every pet state including HAPPY/UPSET, and owned gear is decided;
  cloud/device restore policy is a separate future concern.
- Item prices, reward amounts, budget-plan period and editing rules, and
  event-specific definitions of the fixed choice-impact ratings.
- Exact story-event identities and fatigue-clearing events, explicitly deferred
  to future story design. Their absence must not be filled by a generic rule.
- Asset files for each state; the board has labeled placeholders only.
- Navigation destinations and UI affordances for making each choice.

Use existing approved product decisions where available. If a change requires
one of these decisions, make the ambiguity explicit and obtain the missing
product rule instead of treating an assumption as a board requirement.

## 9. Derived verification checklist

These cases translate the explicit requirements into checks; they do not add
new state rules.

- NORMAL renders each of the five documented saved looks.
- Each of the seven special states replaces the selected cosmetic appearance.
- Entering/exiting a special state does not erase the saved look.
- Changing the selected look retains owned gear; NORMAL displays the current
  selection after the special state finishes.
- Hunger persists while the food need is unmet; buying food follows scenario 7.1.
- Extra work grants virtual money and produces fatigue as in scenario 7.3.
- Exactly one pet state is active; no hidden conditions or reaction queues exist.
- Explicit quest transitions replace the current state, including the source
  examples WORRIED → HUNGRY and HUNGRY → HAPPY; the previous state is not retained.
- Explicitly clearing the current condition returns to NORMAL without revealing
  another condition. An explicit state update can replace any current state,
  including with a lower-priority state.
- The documented priority order is NEEDS_HELP > HUNGRY > TIRED > WORRIED >
  THINKING > UPSET > HAPPY > NORMAL; it does not enable concurrent states.
- Reopening preserves the current saved game and pet state, including owned gear,
  without advancing the story or clearing the active state due to elapsed time.
- HAPPY and UPSET persist until an explicit event changes the state, including
  across reopening; no timer, animation completion, or generic Continue action
  clears them. UPSET does not represent punishment or moral blame.
- Injury comes from an external story event, never a spending penalty.
- Real elapsed time and time while the app is closed do not cause deterioration.
- No combined accessory-and-emotion state or continuous energy depletion exists.
- The saved aggregate contains the combined GAME_STATE, owned items and decisions;
  chapter and goal are derived through the normalized references.
- All three source scenarios remain supported as sequential states, subject to
  the approved subsequent decisions and future fatigue-event definitions.

## Appendix: complete source text

**Дополнение к применению спецификации · 2026-09-19:** исходные формулировки
и расшифровка ниже сохраняются без изменений. Последующие пояснения пользователя
D-045–D-065 находятся в [реестре решений](decisions.md) и дополнениях
[модели игры](game-data-schema.md). Они отдельно описывают фабрику событий,
шаг как выполнение события, 4–5 событий в дне, перенос лора при нехватке сил,
сроки отложенных дел, питание, подвижный бюджет и автоматическую трату на еду.
Новые числовые ресурсы не создают скрытых визуальных состояний питомца.
Точная связь ресурсов с визуальными переходами остаётся открытой.
Многошаговое устройство событий поставлено пользователем под вопрос;
его нельзя считать обязательным на основании примера агента.
Новые правила не реализованы автоматически существующим Room или навигацией.

**Последующее дополнение · 2026-09-19:** D-066 считает предложение дела шагом,
D-067 разрешает выполнить оставшееся короткое дело после основного расписания
до нажатия «Закончить день». Достижение 4–5 событий не завершает день само по
себе. Предложение D-068 уточняет обсуждение D-064: питомец просит покормить его
и объясняет расход накоплений; автоматическое списание не выводится из этой
реплики. Статусы и границы этих уточнений указаны в реестре решений.

The following entries preserve all 102 text nodes in board traversal order.
Node identifiers are provenance labels, not links. Russian wording is verbatim,
including grammatical inconsistencies in the source.

### 102:675 — Метка

```text
Продуктовая логика · редактируемая диаграмма
```

### 102:676 — Заголовок доски

```text
Рыжик — визуальная машина состояний
```

### 102:677 — Заголовок доски

```text
Временные состояния полностью заменяют выбранный косметический образ. После завершения состояния Рыжик возвращается к ранее выбранному образу
```

### 102:682 — PetLook

```text
А · Образ питомца
```

### 102:683 — PetLook

```text
Сохранённый косметический образ, НЕ состояние. выбранный образ хранится в памяти.
```

### 102:685 — PetVisualState

```text
Б · визуальное состояние
```

### 102:686 — PetVisualState

```text
Временное визуальное состояние. Специальное изображение полностью заменяет выбранный образ.
```

### 102:690 — Заголовок состояния

```text
ЗАДУМАЛСЯ
```

### 102:691 — Заголовок состояния

```text
Задумался
```

### 102:693 — Состояние

```text
[изображение ЗАДУМАЛСЯ]
```

### 102:694 — Состояние

```text
Перед важным финансовым выбором. Например: «Купить предмет сейчас или сохранить деньги на цель?»
```

### 102:696 — Условие выхода

```text
После решения: результат → ОБЫЧНОЕ или другое актуальное состояние.
```

### 102:699 — Заголовок состояния

```text
ГОЛОДЕН
```

### 102:700 — Заголовок состояния

```text
Голодный
```

### 102:702 — Состояние

```text
[изображение ГОЛОДЕН]
```

### 102:703 — Состояние

```text
Сюжетное событие сообщает, что нужна еда, или запланированная потребность в еде не покрыта. Управляется игровой логикой, не реальными часами.
```

### 102:705 — Условие выхода

```text
Остаётся активным, пока потребность не закрыта. Еда получена → положительная реакция → ОБЫЧНОЕ.
```

### 102:708 — Заголовок состояния

```text
УСТАЛ
```

### 102:709 — Заголовок состояния

```text
Уставший
```

### 102:711 — Состояние

```text
[изображение УСТАЛ]
```

### 102:712 — Состояние

```text
Следствие сюжетного решения, например дополнительной работы ради виртуальных денег. Нет постоянной шкалы энергии и расхода в реальном времени.
```

### 102:714 — Условие выхода

```text
Отдых или завершение события → ОБЫЧНОЕ.
```

### 102:715 — Машина состояний

```text
↓ важный выбор · появилась потребность · дополнительная работа ↓
```

### 102:719 — Заголовок состояния

```text
ОБЕСПОКОЕН
```

### 102:720 — Заголовок состояния

```text
Обеспокоен
```

### 102:722 — Состояние

```text
[изображение ОБЕСПОКОЕН]
```

### 102:723 — Состояние

```text
Перед выбором ребёнка: неожиданный обязательный расход, нехватка средств на потребность или финансовая сюжетная проблема.
```

### 102:725 — Условие выхода

```text
После решения: реакция → ОБЫЧНОЕ или другое актуальное состояние.
```

### 102:728 — Название

```text
ОБЫЧНОЕ
```

### 102:729 — Название

```text
Обычное состояние
```

### 102:731 — Обычное состояние

```text
[изображение ОБЫЧНОЕ]
```

### 102:732 — Обычное состояние

```text
Нет активного специального состояния; отображается сохранённый Образ питомца.
```

### 102:734 — Правило рендера

```text
отображение = выбранный образ
```

### 102:735 — Обычное состояние

```text
выбранный образ=БАНДАНА → Рыжик в бандане
выбранный образ=РЮКЗАК → Рыжик с рюкзаком
выбранный образ=ОБЫЧНЫЙ → обычный Рыжик
```

### 102:738 — Заголовок состояния

```text
РАДУЕТСЯ
```

### 102:739 — Заголовок состояния

```text
Радуется
```

### 102:741 — Состояние

```text
[изображение РАДУЕТСЯ]
```

### 102:742 — Состояние

```text
Короткая положительная реакция: цель достигнута, проблема решена, желанный предмет получен или этап приключения успешен. Никогда не постоянное.
```

### 102:744 — Условие выхода

```text
Подходящее событие → РАДУЕТСЯ → ОБЫЧНОЕ; ОБЫЧНОЕ восстанавливает выбранный образ.
```

### 102:745 — Машина состояний

```text
↓ неожиданная проблема ↔ ОБЫЧНОЕ ↔ цель достигнута ↓
```

### 102:749 — Заголовок состояния

```text
РАССТРОЕН
```

### 102:750 — Заголовок состояния

```text
Расстроен
```

### 102:752 — Состояние

```text
[изображение РАССТРОЕН]
```

### 102:753 — Состояние

```text
Короткая реакция: цель отложена, выбран менее выгодный вариант или часть приключения временно недоступна. Не наказание и не моральная оценка.
```

### 102:755 — Условие выхода

```text
Неблагоприятный результат → РАССТРОЕН → ОБЫЧНОЕ.
```

### 102:758 — Заголовок состояния

```text
НУЖНА ПОМОЩЬ
```

### 102:759 — Заголовок состояния

```text
Травмирован / нужна помощь
```

### 102:761 — Состояние

```text
[изображение НУЖНА ПОМОЩЬ]
```

### 102:762 — Состояние

```text
Только внешний Сюжетное событие, например Рыжик подвернул лапу. Никогда не следствие плохих трат. Событие → финансовая потребность → ребёнок решает, откуда взять деньги.
```

### 102:764 — Условие выхода

```text
После решения или завершения события → ОБЫЧНОЕ.
```

### 102:765 — Машина состояний

```text
цель отложена · внешнее событие · потребность закрыта · событие завершено → ОБЫЧНОЕ
```

### 102:767 — Правило маршрутизации

```text
Если после события актуально другое состояние, переходим сразу в него вместо ОБЫЧНОЕ
```

### 102:768 — Правило маршрутизации

```text
ОБЕСПОКОЕН → ребёнок не смог закрыть потребность → ГОЛОДЕН
```

### 102:769 — Правило маршрутизации

```text
Не создавать ГОЛОДЕН_С_БАНДАНОЙ, ГОЛОДЕН_С_РЮКЗАКОМ, РАДУЕТСЯ_В_ОЧКАХ и другие комбинированные состояния.
```

### 102:772 — Управление состояниями

```text
Что управляет состояниями
```

### 102:775 — Событие

```text
Сюжетное событие.Нужна еда → ГОЛОДЕН
```

### 102:777 — Событие

```text
Сюжетное событие.Важный выбор → ЗАДУМАЛСЯ
```

### 102:779 — Событие

```text
Сюжетное событие.Неожиданная проблема → ОБЕСПОКОЕН
```

### 102:781 — Событие

```text
Сюжетное событие.Дополнительная работа завершена → УСТАЛ
```

### 102:783 — Событие

```text
Сюжетное событие.Травма → НУЖНА ПОМОЩЬ
```

### 102:785 — Событие

```text
Результат события.Цель достигнута → РАДУЕТСЯ
```

### 102:787 — Событие

```text
Результат события.Цель отложена → РАССТРОЕН
```

### 102:789 — Запреты

```text
НЕ ИСПОЛЬЗУЕМ
```

### 102:790 — Запреты

```text
• таймеры реальных часов
• автоматический голод через несколько реальных часов
• ухудшение состояния, пока приложение закрыто
```

### 102:791 — Запреты

```text
Игровое время и реальные часы не связаны.
```

### 102:793 — Сохранённый образ

```text
Образ питомца — сохранённый внешний вид
```

### 102:796 — Образ

```text
ОБЫЧНЫЙ
```

### 102:798 — Образ

```text
БАНДАНА
```

### 102:800 — Образ

```text
РЮКЗАК
```

### 102:802 — Образ

```text
ОЧКИ
```

### 102:804 — Образ

```text
ШЛЯПА
```

### 102:805 — Сохранённый образ

```text
Образ питомца НЕ является частью машины состояний
```

### 102:807 — Правило отображения

```text
Если визуальное состояние = ОБЫЧНОЕ: показать выбранный образ
```

### 102:808 — Правило отображения

```text
Если визуальное состояние ≠ ОБЫЧНОЕ: показать изображение состояния
```

### 102:809 — Правило отображения

```text
После возврата в ОБЫЧНОЕ: снова показать выбранный образ.
```

### 102:811 — Пример с банданой

```text
выбранный образ = БАНДАНА
```

### 102:812 — Пример с банданой

```text
Рыжик в бандане
```

### 102:813 — Пример с банданой

```text
↓ Сюжетное событие.Нужна еда
```

### 102:814 — Пример с банданой

```text
Голодный Рыжик без аксессуара
```

### 102:815 — Пример с банданой

```text
↓ потребность закрыта
```

### 102:816 — Пример с банданой

```text
Рыжик снова в бандане
```

### 102:818 — Приоритет

```text
Приоритет визуального состояния
```

### 102:819 — Приоритет

```text
1. Специальное сюжетное состояние — НУЖНА ПОМОЩЬ
2. Активная потребность — ГОЛОДЕН / УСТАЛ
3. Состояние принятия решения — ЗАДУМАЛСЯ / ОБЕСПОКОЕН
4. Кратковременная реакция — РАДУЕТСЯ / РАССТРОЕН
5. ОБЫЧНОЕ — выбранный образ питомца
```

### 102:820 — Приоритет

```text
Если одновременно возможно несколько состояний, отображается наиболее приоритетное.
```

### 102:823 — Архитектура

```text
Архитектура состояния игры
```

### 102:826 — GameState

```text
Состояние игры
```

### 102:827 — GameState

```text
├── Состояние питомца
├── Состояние экономики
└── Состояние сюжета
```

### 102:829 — PetState

```text
Состояние питомца
```

### 102:830 — PetState

```text
выбранный образ
визуальное состояние
```

### 102:832 — EconomyState

```text
Состояние экономики
```

### 102:833 — EconomyState

```text
общий баланс
распределение бюджета
накопительная цель
резерв
расходы
```

### 102:835 — StoryState

```text
Состояние сюжета
```

### 102:836 — StoryState

```text
текущая глава
текущее событие
принятые решения
```

### 102:838 — Архитектурное предупреждение

```text
Деньги, бюджет и накопительная цель не должны храниться внутри Состояние питомца.
```

### 102:839 — Архитектурное предупреждение

```text
Сюжетное событие может одновременно: 1) изменить Состояние экономики; 2) изменить Состояние сюжета; 3) установить визуальное состояние.
```

### 102:841 — Примеры сценариев

```text
Три сквозных примера
```

### 102:844 — Пример

```text
Пример 1 · Голод
```

### 102:845 — Пример

```text
ОБЫЧНОЕ · Рыжик в бандане → нужна еда → ГОЛОДЕН без банданы → ребёнок покупает еду → РАДУЕТСЯ → ОБЫЧНОЕ → Рыжик снова в бандане.
```

### 102:847 — Пример

```text
Пример 2 · Финансовая проблема
```

### 102:848 — Пример

```text
ОБЫЧНОЕ → неожиданный расход → ОБЕСПОКОЕН → ребёнок выбирает, откуда взять деньги → РАССТРОЕН или РАДУЕТСЯ в зависимости от результата → ОБЫЧНОЕ.
```

### 102:850 — Пример

```text
Пример 3 · Дополнительный заработок
```

### 102:851 — Пример

```text
ОБЫЧНОЕ → ребёнок выбирает дополнительную работу → получает виртуальные деньги → УСТАЛ → следующее сюжетное событие / отдых → ОБЫЧНОЕ.
```



## Инвентарь — 2026-09-19

**Принято пользователем · MAIN-D-075:** «Снаряжение» открывает экран уже приобретённых
вещей с разделами «Сюжетные предметы» и «Аксессуары», без блоков глав.
Открытие, просмотр и возврат не меняют баланс, владение, выбранный образ,
состояние питомца или ход истории. Пустое владение отображается пустыми разделами;
выбранный начальный образ сам по себе не подтверждает наличие вещи.
Повторы и порядок экземпляров сохраняются внутри соответствующего раздела.
В текущей реализации покупка и смена косметики не вызываются этим экраном.
По D-116 неожиданное предложение должно продавать существующий аксессуар,
ещё не открытый игроку; покупка открывает возможность его надеть. Выбор
экипировки ещё предстоит подключить. См. [инвентарь](inventory.md).



## Подключённый игровой цикл — 2026-09-19

Это дополнение к исходному описанию. Основание: D-071–D-073 в
[реестре решений](decisions.md). Полный состав первого пакета и ограничения —
в [описании подключения](content/README.md).

Главное меню вызывает команды через ViewModel и GameSession. «Продолжить день»
начинает/открывает событие, «Закончить день» сохраняет итоги, а повторное открытие
активного события или итогов не меняет игру. Начало следующего дня — отдельное
явное действие в итогах. По D-085 оно сохраняет утро и возвращает в меню;
первое событие открывается отдельным нажатием «Продолжить день».
Навигационные ключи прежние; состояния игры в них нет.

Предложение дела само занимает шаг и сохраняет включительный срок. По D-076
«Выполнить дело» закрывает предложение и запускает мини-игру без выплаты и траты
сил. Только законченная партия сохраняет выполнение: ещё один шаг, уменьшенную
за ошибки награду и один расход сил (D-078). «Не сейчас» закрывает только
предложение; дело остаётся в «Делах». Три отдельные тренировки не меняют баланс.

«Вернуться позже» у активного события сохраняет PAUSED, без выбора, завершения,
расхода сил и награды. Следующее продолжение возвращает это событие; предложенные
дела можно выполнить отдельно. Начатое дело также можно оставить и продолжить
из списка в пределах его срока. Простая кнопка «Назад» только закрывает экран:
активное событие сохраняется, что не позволяет молча начать другое действие.

При раннем сне весь остаток плана переносится по D-073. Ещё не открытые события
становятся CARRIED, ранее открытые — CARRIED_ACTIVE. На следующем дне последние
возобновляются без повторения эффектов открытия. ID, порядок и повторяющиеся
определения событий сохраняются. Предложения дел, которые уже были показаны,
не получают новых сроков из-за сна.

По D-107 «Закончить день» доступно прямо в карточке, если сил нет или их не
хватает ни на один доступный по деньгам вариант действия. Если другой вариант
выполним, он остаётся доступным. Перед сном требуется приём пищи за сегодня:
вместо завершения дня сначала показывается одна кнопка «Покормить», даже если
обычный порог голода ещё не достигнут. Еда не выполняет событие автоматически.
После питания игрок явно завершает день. Закрытие карточки и перенос остатка
сохраняются одной командой `FinishDayFromEvent`, без отдельной промежуточной
записи PAUSED. Открывается обычный экран итогов; начало следующего дня возвращает
на главный экран, где следующее событие ждёт «Продолжить день» (D-085).

Для уже показанного предложения дела эта команда закрывает только предложение:
дело остаётся в списке с прежним включительным сроком, без награды, расхода сил,
повторного шага предложения или продления срока. Короткое дело истекает с этим
днём по обычному правилу. Незавершённая лоровая карточка переносится без решения
и продолжения истории. Уже сохранённый результат выполненного события не
используется как основание для сна из карточки.

Порог голода и питание — D-072. Кнопка заблокированного действия предлагает
покормить; после оплаты игрок продолжает действие явно. Еда не добавляет сил.
При нехватке на обычную еду доступна бесплатная столовая, с ограничением сил
только следующего утра. Автоматических списаний при отказе нет. Завершение дня
по-прежнему требует питания. Happy/UpSet не сбрасываются простым входом на экран.

По D-113 замена действия на «Покормить» или завершение дня из-за нехватки сил
сопровождается пояснением над кнопками: питомец проголодался или устал и что
нужно сделать сначала. Имя берётся из сохранения. Причина и кнопка вычисляются
из тех же проверок движка в одном UiState; отдельной задержки или подтверждения
перед заменой нет. Пояснение не затирается временным сообщением об ошибке.
После кормления голодное пояснение исчезает; если всё ещё нужен отдых,
показывается усталость. Обычное завершение полного плана само не означает усталость.

## Сон и содержимое итогов — 2026-09-20

По D-108 итог FINISHED показывает спящего лиса текущего сохранённого возраста
на сильно затемнённой сцене. Это представление сна, не новая запись visualState:
настроение и выбранный образ не затираются. Следующее утро по-прежнему создаёт
только явная команда BeginDay. Цвет сна берётся из PetState.color по D-109;
возраст следует правилу глав D-110.

По D-112 итоги сначала рассказывают, что сделали за день: выполненные дела,
сюжетные находки, покупки с ценами и питание. Описания в прошедшем времени
соответствуют конкретному выбранному действию. Предложение/откладывание дела
не считается выполнением; обход и пропуск не получают текст ремонта/находки.
Ниже — «Потрачено за день», «Получено за день» и текущий остаток без стрелок.
Расходы и поступления считаются отдельно; чистая разница не заменяет расходы. Пустых
разделов, сообщений «не было шагов сценария» и технического счётчика шагов нет.
Повторные обеды имеют отдельные записи, награда дела берётся после снижения
за ошибки. Эффекты открытия и выбора учитываются раздельно, даже когда их
суммарное изменение денег равно нулю; пауза и перенос не начисляют их снова.

Журнал — часть одного снимка, фиксируется в той же транзакции, что и действие.
Показ итогов ничего не списывает; записи сохраняются до начала следующего дня.
Для прежних дней выполненные действия восстанавливаются из COMPLETED-вхождений
текущего дня и соответствующих записанных решений, в порядке решений. Это
читаемая проекция, не новые записи и не повторное проведение последствий.
Размер старой награды дела и покупки без записи не угадываются по каталогу.
Если денежный журнал неполон, показывается известное чистое изменение словами
«За день монет стало меньше/больше на N» и пояснение о неполной детализации.
Эта разница не подписывается как валовые расходы. Раздела
«Другие изменения баланса» по-прежнему нет (D-111).
Кнопка итогов указывает следующий номер: «Начать день N», где N — завершённый
день плюс один. До начала игры — «Начать день 1». Сам переход не меняется:
новый день открывается на главном экране, событие ждёт «Продолжить день».

## Подписи состояния сил — 2026-09-19

По D-074–D-075 в [реестре решений](decisions.md) меню и событие показывают состояние
словами. Пять подписей UI: «Полон сил», «Немного устал», «Устал», «Сильно устал»,
«Без сил». Последняя соответствует нулевому запасу; для двух нижних
ненулевых значений используется «Сильно устал». Это проекция существующего
состояния, а не дополнительная машина состояний. Внутренняя шкала из пяти частей,
проверки возможности действий и сохраняемые значения остаются прежними.
Числовые затраты сил и завтрашний остаток после бесплатной еды в UI скрыты;
степень усилия и последствия описаны словами.

## Завершение без технического окна — 2026-09-19

По D-077 решение без отдельного содержательного продолжения сохраняется через
CompleteEvent: выбор, его последствия и закрытие события — одна транзакция.
После успешного сохранения UI возвращается прямо в главное меню. Уходящая
карточка сохраняет свой вид до удаления маршрута; ошибка записи оставляет её
открытой. Итоги дня и уже сохранённые результаты старого прохождения остаются
доступными и не подтверждаются автоматически при загрузке.

D-076 уточняет предыдущее описание мгновенного выполнения поручения: действие
запускает мини-игру. CompleteDeed проверяет тип игры и завершённый результат,
затем атомарно применяет награду, силы, шаг, решение и закрытие дела. Прямой
выбор ответа не позволяет обойти мини-игру. Успех возвращает в главное меню;
ошибка сохранения оставляет результат для явного повтора, без нового начисления.

Для мини-игры UI и системная кнопка «Назад» сохраняют PAUSED перед возвратом
в меню. Незаконченная партия не оплачивается и не тратит силы; предложение и
исходный срок остаются. Новое открытие начинает раскладку заново (D-079).
Восстановление маршрута уже завершённого дела только читает сохранение и
возвращает в меню. Привязка механик по D-080 описана в пакете контента.
Отложенные дела показывают включительный остаток срока; открытие списка его
не продлевает. После изменения сохранения устаревшие подсказки о голоде
сбрасываются. Основная кнопка кормления не дублируется дополнительной.

## Исчезающее подтверждение — 2026-09-19

По D-081 успешные CompleteEvent и CompleteDeed передают UI одноразовое
подтверждение вместе с выходом. Оно появляется на главном экране после перехода
и исчезает автоматически. Для дела сумма берётся из фактического изменения
баланса успешной команды, а не из максимума каталога. До успешного ответа
сохранения облачко не показывается. Повторное восстановление уже завершённого
дела только возвращает в меню, без повторной подсказки и выплаты.

Пауза, «Не сейчас», отказ и ошибки не означают выполнение. Их выход не содержит
подтверждения. Облачко — временное состояние UI, не новый статус события и не
часть сохранения; уход с меню убирает его. Исходные правила D-077 сохраняются.

## Кормление на карточке и начало следующего дня — 2026-09-19

По D-084 на карточках нет самостоятельной кнопки кормления. Диалог питания
открывается заменой действия при блокирующем голоде; после еды исходная
кнопка снова доступна и не выполняется автоматически. Добровольное кормление
находится на главном экране. При нехватке монет там же предлагается бесплатная
столовая с предупреждением о силах следующего утра; отмена ничего не списывает.

По D-085 «Начать день» из итогов использует BeginDay без открытия события.
После успешной записи UI возвращается на главный экран с нулём шагов и без
активного события. План и отложенные дела сохраняются по прежним правилам;
новое предложение ещё не показано. «Продолжить день» затем вызывает
OpenNextEvent. Недельный доход начисляется при пробуждении и не повторяется
при открытии карточки, восстановлении экрана или повторном нажатии старой кнопки.
Ошибка записи оставляет итоги открытыми с возможностью повтора.

## Первая большая цель — 2026-09-19

По D-087–D-089 экран «Цель» позволяет выбрать Ночь наблюдений, затем купить
четыре части за 24 / 36 / 90 / 30 монет. Выбор сам по себе не открывает событие:
вступление G1.01 запрашивается следующим явным продолжением дня. Уже открытое
событие, голод и необходимость сна не обходятся выбором цели.

Купленные части и счётчик в меню вычисляются из одного сохранения. Без цели
обычные, случайные и системные события сохраняются по D-082. Покупка не запускает
следующую карточку и не завершает главу; финал первой главы по источнику требует
также сюжетного ответа башни. В текущем пакете подключено вступление, полная
цепочка лора остаётся следующей работой с её реальными условиями.

[Правила покупки, предупреждения и границы реализации](first-goal.md).

## Имя и возраст — 2026-09-19

По D-090 имя игрока хранится в `GameState.pet.name`. Новая игра использует
`PetDefaults.FOX_NAME` («Рыжик»). По D-091 начальный `pet.age = CUB`: обычный
образ и все доступные эмоциональные изображения выбираются из детского набора.
Возраст и имя независимы от текущего визуального состояния и выбранного образа.
Сон, еда, смена эмоций и новое событие сохраняют оба поля.
По D-093/D-094 главное меню показывает имя под целью, справа от монет,
без текстовой подписи возраста и без действия по нажатию. Сохранённый возраст
продолжает определять набор изображений. По D-095 переход в деревню — картинка
`menu_map`, частично выступающая за правый край экрана; это прежний навигационный
переход без игровых эффектов.

`RenamePet(name, expectedName)` атомарно меняет только имя. До первого дня
engine остаётся null; после начала дня меняется лишь revision, без нового шага,
события, дохода или расхода. Команда сверяет актуальные revision и прежнее имя
в транзакции. `expectedName` защищает редакторы и до появления engine/revision.
Ошибка записи или проверки сохраняет прежний агрегат. Техническая валидация:
обрезать пробелы по краям, отклонить пустую строку, управляющие символы и
переносы строк; произвольные знаки и Unicode не интерпретируются как шаблон.

Новый контент использует `{petName}`. `renderPetText` подставляет имя при
построении UI из текущего снимка, не изменяя каталог. Совместимость с уже
установленным v1-текстом заменяет отдельное слово «Рыжик» в именительном падеже;
склонение произвольного имени не угадывается, фразы переписаны без этой нужды.
Исходные экспорты Figma остаются авторским материалом.

Технические коды возраста CUB/TEEN/ADULT/SENIOR соответствуют имеющимся наборам
изображений. По D-110 возраст меняется при завершении сюжетной главы;
перехода по числу дней нет. Возраст не меняет затраты, награды и запас сил.

## Начальный образ и размер — 2026-09-19

По D-092 новая игра использует NORMAL/CUB/PLAIN: лисёнок без аксессуаров.
У детского набора в меню масштаб 0,8 от прежнего, одинаковый для обычного
и эмоциональных образов; масштаб не меняет игровые параметры. Остальные
возрастные наборы сохраняют прежний размер. Тень и полный холст уменьшаются
совместно. Переходы состояний сохраняют выбранный образ по прежним правилам.
Совместимость старого стартового BACKPACK описана в миграции Room v9.

## Цвет и взросление — 2026-09-20

По D-109 PetState содержит color: COPPER, SAND или DARK_RUSSET. Умолчание —
COPPER, соответствующее прежнему изображению. Главное меню выбирает обычный
образ/эмоцию по age + color + appearance; итоги FINISHED — сон по age + color.
Сон не перезаписывает visualState. Неизвестный аксессуар и состояния без
согласованной иллюстрации сохраняют прежнее явное отсутствие изображения.

SetPetColor(color, expectedColor) проверяет revision и прежний цвет внутри
агрегатной транзакции. До первого дня engine остаётся null; во время игры
меняются только цвет и revision. Шаги, силы, деньги, события, журнал и цель
остаются прежними. Ошибка записи откатывает весь исход.

По D-110 последовательные главы общей истории задают возраст:

| Глава | Возраст |
| --- | --- |
| 1 | CUB — ребёнок |
| 2 | TEEN — подросток |
| 3 | TEEN — подросток |
| 4 | ADULT — взрослый |
| 5 и завершённая история | SENIOR — старик |

Возраст — поле StoryAct в каталоге. Успешный финал, ожидающий любую собранную
выбранную цель, атомарно записывает решение, завершение проекта, переход главы
и новый pet.age. Покупка, выбор цели, пропуск дополнительной карточки, сон,
пауза и заблокированное действие не меняют возраст. Имя, цвет и аксессуар
сохраняются при взрослении. Отдельного действия «повзрослеть» у UI нет.

Для прежних игр GameSession.prepare после установки каталога и
initializeIfAbsent сверяет возраст с сохранёнными финалами. При расхождении
движок читает актуальный агрегат в транзакции и меняет только pet.age и
revision существующего engine. Повторная подготовка согласованной игры
ничего не пишет. Ошибка распространяется, игра не обнуляется.
read/observe остаются чтением; UI не вычисляет отдельный возраст.
Каталог без правила petAge оставляет сохранённый возраст неизменным.

## Процедурное движение на главном экране — 2026-09-22

D-119 уточняет объём D-117/D-118 для текущего MR: только спокойное дыхание и
лёгкое покачивание на месте. Нажатие на фигуру не запускает действий; приветствие,
прыжки, искорки и моргание отложены. Это оформление исходного спрайта, не
переход PetVisualState и не команда GameEngine. Имя, возраст, окрас, аксессуар,
состояние питомца и шаг дня от движения не меняются.

Цикл работает только пока экран RESUMED; при уходе или сворачивании вычисление
кадров прекращается. Имя в HUD остаётся неинтерактивным (D-094). Привязка карты,
тень и навигационные кнопки не двигаются вместе с фигурой. Длительность
переходов и оптимизация UI сохраняются.
[Объём движения и отложенный прототип](pet-motion.md).

## Первый запуск и выбор спутника — 2026-09-19

По ONB-D-001–ONB-D-004 в [реестре](decisions.md), до первого входа в игру
показывается onboarding. Проверка существования сохранения ничего не создаёт.
Существующее сохранение открывает меню; ошибка чтения требует повтора.
Новый игрок сначала выбирает лисёнка. Кнопка до выбора приглушена и показывает
диалог с просьбой выбрать персонажа; после выбора открывает кастомизацию (CUST-D-004). Только её завершение
создаёт начальный снимок через атомарный initialize-if-absent и открывает меню.
Ошибка сохранения оставляет кастомизацию и введённые настройки для повтора. Сам выбор не запускает игровой день или событие.

Сова и аксолотль — только визуальные элементы; нажатия показывают подсказку,
не меняют выбор/игру. Текст «Откроется во время приключения» воспроизводит
макет, но не вводит механику разблокировки. Свечение, подсказка и диалог —
состояние UI, не PetState и не новая схема Room. [Подробности](onboarding.md).

## Явный отладочный сброс — 2026-09-19

По DBG-D-002 кнопка «Сбросить прогресс и перезапустить» в debug-панели удаляет
весь текущий агрегат сохранения и перезапускает приложение с чистым состоянием
UI и игровой сессии. После перезапуска выполняется обычный сценарий первого открытия из main;
начальное сохранение создаётся штатной инициализацией приложения. Сброс доступен и до создания игры.
Операция атомарна: ошибка не оставляет частично удалённый прогресс и показывает
повтор. В release этот переход недоступен.

## Завершение onboarding — 2026-09-20

По CUST-D-019 (2026-09-21): Choose → Customize → Accessories → GoalSelection → Introduction → Ready.
Промежуточные переходы сохраняют черновик. Только «В путь!» на
Introduction атомарно сохраняет игру
с именем, характером, окрасом, возрастом Cub, NORMAL и выбранным selectedLookId
(PLAIN/BACKPACK/BANDANA) и выбранной доступной целью, затем удаляет черновик. Закрытый аксессуар не допускает
завершения. Реализация Back из Accessories возвращает Customize, сохраняя настройки. Существующая игра имеет приоритет
над черновиком и не перезаписывается. Back из Customize очищает настройки и
возвращает Choose; обычное закрытие приложения сохраняет незавершённый шаг.
Переходы не запускают сюжет и не начисляют награды. Возраст и характер сами не
вызывают игровые переходы. Отладочный сброс удаляет также черновик.

CUST-D-014: новый черновик Customize создаётся с пустым именем; UI показывает
«Как меня зовут?». По CUST-D-015 кнопка доступна с пустым именем, но нажатие показывает
под полем «Введи имя спутника» и оставляет пользователя в Customize.
Восстановление существующего черновика не сбрасывает введённое имя.

GoalSelection и Introduction восстанавливаются из черновика вместе с goalId.
Без выбора одной из трёх стартовых целей по D-101 подтвердить выбор нельзя.
Back из Introduction возвращает GoalSelection; затем Back возвращает Accessories с
сохранённым выбором. Ошибка финального сохранения оставляет Introduction
для повтора, кнопки блокируются на время записи.

## Карта: выбор фона (MAP-D-005, 2026-09-20)

Кнопка карты меню открывает feature/map. Выбор доступного места сохраняет
LocationScene.location и после успешного commit возвращает в меню. Ошибка
оставляет карту открытой. Все места пока доступны; LocationAccessPolicy
будет проверять сюжетное прохождение после определения соответствия локаций.
Освещение DAY/EVENING меняется только явным программным вызовом setLighting.
Усталость и энергия не переключают фон. Оба действия не продвигают день,
не создают события, не расходуют ресурсы и не меняют ревизию движка.
Агрегат обновляется из актуального состояния, сохраняя параллельные изменения.


### Уточнение недельного бюджета в Preview — D-136

Принято пользователем 2026-09-21. До первого перехода с экрана поступления
на планирование «Продолжить день» из меню возвращает на экран поступления.
После нажатия «Распределить монеты» возвращает прямо к незавершённому бюджету;
повторно показывать поступление не требуется. Продолжение игры недоступно до
подтверждения полного распределения с минимумом «Нужно» 35 (D-128/D-129).
Показ и повторное открытие экрана не являются новым начислением.
Правило подключено к движку и Room по D-141; Preview использует отдельный локальный сценарий.


## Обязательное планирование — D-138–D-141, 2026-09-21

После Introduction онбординга атомарно создаётся игра со стартовыми 100 в
нераспределённых и сессией INITIAL/RECEIPT. Host открывает поступление; кнопка
переводит сессию в ALLOCATION, подтверждение удаляет её и возвращает в меню.
На дни 8,15,… BeginDay атомарно сохраняет день, доход и WEEKLY/RECEIPT; первое
событие не открывается до подтверждения бюджета. Старые статьи не очищаются.
MIGRATION/ALLOCATION сохраняет текущие день и событие без повторных эффектов.
Миграционная доплата до35 отдельно от заработка учитывается в итогах дня.

Любая gameplay-команда при открытой сессии возвращает BudgetPlanningRequired;
исключены только косметические RenamePet/SetPetColor. Демонстрационные мини-игры
также проверяют этот запрет. Меню, карта и просмотр информации разрешены.
Возврат с экрана бюджета ждёт завершения уже принятой записи; ошибка позволяет
повторить запись, не теряя сохранённого черновика. Устаревшие команды запрещены.

По D-142 «Монетки» всегда открывает редактор. MANUAL/ALLOCATION создаётся при первом изменении и сохраняет черновик; требует распределить все деньги, но не вводит недельный минимум35. Простое открытие ничего не начисляет и не блокирует игру. INITIAL/WEEKLY/MIGRATION сохраняют минимум35.


## Сохранение игровой семантики при экономике — D-144, 2026-09-22

При одинаковой доступности оплаты сохраняются прежние силы, шаги, питание, вещи,
решения, порядок событий и переносы. Проверка minimumEnergy использует новые
денежные операции, но прежний алгоритм выбора доступных по силам вариантов.
Предварительный расчет не пишет состояние и выбирает ту же следующую карточку.
Перераспределение меняет экономику и ревизию, сохраняя незавершенный экземпляр.
Открытие бюджета не требует PauseEvent или AcknowledgeResult: стадия остается
сохраненной до подтверждения бюджета, после чего исходный сценарий продолжается.
[Аудит кода и пределы проверки](economy-regression-review.md).
