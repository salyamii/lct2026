# Ryzhik: app state-machine specification

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

**Approved product decision (2026-09-17):** changing cosmetics updates the
selected look; items already owned remain in the player's gear. Changing the
selection does not remove previously owned items. The rendering contract below
still applies: during a special state, the selected look is saved, and NORMAL
displays the current selection.

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

**Approved product decision (2026-09-17):** only one pet state is active at a
time. There are no hidden conditions, suppressed reactions, or pending reaction
queues. Explicitly clearing the current condition returns the pet to NORMAL.
A quest may explicitly transition between
its decision, need, and result states as in the source scenarios; each new state
replaces the previous one rather than hiding it. See section 5 for the distinction
between an explicit transition and the board's superseded fallback rule.

**Subsequent approved product decision (2026-09-17):** the current pet state,
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
**Approved product decision (2026-09-17):** the user specified the following
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

**Subsequent approved product decision (2026-09-17):** only one state can affect
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

```text
GameState / Состояние игры
├── PetState / Состояние питомца
│   ├── selectedLook / выбранный образ
│   └── visualState / визуальное состояние
├── EconomyState / Состояние экономики
│   ├── total balance / общий баланс
│   ├── budget allocation / распределение бюджета
│   ├── savings goal / накопительная цель
│   ├── reserve / резерв
│   └── expenses / расходы
└── StoryState / Состояние сюжета
    ├── current chapter / текущая глава
    ├── current event / текущее событие
    └── decisions made / принятые решения
```

Money, budget, and the savings goal must not live inside PetState.
One story event may change EconomyState, change StoryState, and set the
visual state together. Visual state is therefore not the whole game state.

The board defines these responsibilities but does not prescribe data types,
storage technology, reducer interfaces, transaction mechanics, or navigation.

**Approved product decision (2026-09-17):** closing and reopening the app must
preserve the current game and pet state. Owned gear is also retained. Reopening
does not reset the pet to NORMAL or advance the story merely because time passed.
This includes HAPPY and UPSET: reopening restores the saved state until an
explicit event changes it. Room is the approved persistent-data technology in
AGENTS.md; this requirement does not define cloud backup or restore.

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

The board says "next story event / rest." The user has explicitly deferred the
identities of fatigue-clearing events to future story design. Do not infer that
an arbitrary next quest or opening another screen clears fatigue.

## 8. Unspecified decisions — do not invent product rules

- Detailed restoration boundaries for uncommitted UI input. Preservation of the
  current game, every pet state including HAPPY/UPSET, and owned gear is decided;
  cloud/device restore policy is a separate future concern.
- Financial amounts, budgets, reward formulas, item costs, goal thresholds, and
  the conditions distinguishing advantageous from unfavorable outcomes.
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
- Economy data belongs to EconomyState; story progression belongs to StoryState.
- All three source scenarios remain supported as sequential states, subject to
  the approved subsequent decisions and future fatigue-event definitions.

## Appendix: complete source text

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

