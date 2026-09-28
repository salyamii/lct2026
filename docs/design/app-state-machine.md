> **Подключение родителя, 2026-09-27, PARENT-LINK-D-001/002.** Настройки —
> отдельный доступный без игровых переходов маршрут. Открытие может создать
> локальную идентичность профиля, но не меняет GameState, деньги, дни и события.
> «Показать код для родителей» строит QR ровно из сохранённого UUID, без ссылки,
> токена и срока действия. Состояния: загрузка → готово / ошибка чтения или
> построения. После показа при наличии URL отдельно отправляется регистрация;
> сетевой сбой не убирает QR и допускает повтор отправки. Повторное открытие
> не меняет ID. Облачное восстановление ещё не подключено к навигации;
> существующий локальный restore не запускается автоматически.
> [Предлагаемый контракт](../backend/README.md).

> **Болезнь и её изображение, 2026-09-27, ADVENTURE-D-010.** Показанное
> нерешённое событие с `requiresPetHelp` держит `NEEDS_HELP` в статусах
> ACTIVE, PAUSED и CARRIED_ACTIVE. Запланированное, но ещё не открытое событие
> болезнь не включает. Успешное лечение снимает условие; затем проверяются
> обычные голод и усталость. Еда и переход к следующему дню не лечат болезнь.
> Для сохранений с уже открытой карточкой UI применяет тот же признак без
> записи в базу. Используются импортированные прозрачные `state_sick`.
> Временный показ позы по ADVENTURE-D-011 не снимает условие болезни.

# Ryzhik: app state-machine specification

> **Единые правила питания и повтора записи, 2026-09-27, ARCH-D-004/005.**
> [MealPolicy](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/engine/MealPolicy.kt)
> задаёт обычный обед, бесплатную альтернативу, последствия питания и расчёт
> оставшейся потребности в еде. Движок, аналитика, бюджет, цель и экраны питания
> используют одну политику. Неопределённая запись повторяется исходным запросом;
> подтверждённый отказ и ошибка записи различаются. Подробности — в разделе
> [питание и повтор действий](#питание-и-повтор-действий--2026-09-27).

> **Оплата предметов цели, 2026-09-27, ADVENTURE-D-018.** Можно сразу оплатить
> предмет реальными накоплениями и текущими деньгами вместе. Расчёт показывает
> расход копилки и каждой затронутой текущей статьи; недостаток на еду до
> следующей недели требует явного подтверждения риска. До подтверждения
> нет записи в игру. Старое подтверждение не применяется к новой ревизии.
> BuyGoalItem атомарно записывает предмет и фактические расходы двух счетов.
> Переводы в копилку и из неё из этой покупки не создаются; аналитика не
> приписывает прямой оплате факт накопления. Старые квитанции остаются читаемыми.

> **Лоровая пластина, 2026-09-27, ADVENTURE-D-015.** Сюжетный G1.03
> «Пластина с тем же знаком» отличается от случайного налёта ниже.
> Новый `campaign-choice-v2:G1.03` завершается напрямую: состав за 3 монеты
> без сил или ручная очистка за прежнюю 1 силу без монет. Мини-игры нет.
> Обе ветки открывают `plate_found` и `plate_symbol` и разрешают следующий
> сюжетный шаг. «Вернуться позже» оставляет событие нерешённым и ничего
> не списывает. Старый пройденный G1.03 засчитывается через completionAliases;
> нерешённый экземпляр получает новую версию при prepare/restore.

> **Очистка пластины, 2026-09-27, ADVENTURE-D-012.** Варианты нового
> `figma-2326-160-v3`: состав за 3 монеты без расхода сил или «Почистить самому»
> через мини-игру точности за 2 силы без монет. Открытие мини-игры не завершает
> событие и не списывает ресурсы. Успешное завершение применяет последствия
> один раз и снимает блокировку сюжета; нехватка сил блокирует ручную работу.
> Перенос на завтра остаётся откладыванием, без фиктивной очистки.
> При подготовке/восстановлении сохранения нерешённые экземпляры v2 получают
> ссылку на v3; RESULT/COMPLETED и экземпляры с решением остаются прежними.
> Это согласование версий, без хода дня, трат или переписывания истории.

> **Тренировки и пересмотр, 2026-09-27, LEARNING-D-001/002.**
> [Текущий сценарий](skills-and-reflection.md): непрерывная практика в «Делах»
> с внутренними наборами по четыре вопроса; ошибка — объяснение и повтор,
> верный ответ — следующий вопрос. Сохранённые вопросы не теряются при выходе.
> «А что, если…» открывается из итогов и не меняет настоящую игру.
> Общие финансовые сводки всех событийных карточек сняты ADVENTURE-D-010;
> скрытые сведения не записываются как показанные.

> **Герой в текущем игровом цикле, 2026-09-26, ADVENTURE-D-001/003.**
> По ADVENTURE-D-009 отдельные подписи эмоций в меню убраны. Сохранённые
> радость/огорчение сменяются при начале нового события; перед выбором герой
> задумывается. Голод, усталость, питание и отдых обновляют состояние по
> фактическим переходам дня, с существующими порогами еды и сил.
> Повторный показ карточки, возвращение из копилки и обычная навигация
> состояние не сбрасывают. Одновременно активно только одно состояние.
> По ADVENTURE-D-011 от 2026-09-27 особая поза текущего питомца показывается
> несколько секунд и плавно сменяется обычным выбранным образом. Это таймер
> представления: сохранённые состояние, одежда, потребности и история остаются.
> Сон в кровати и исторические/смоделированные учебные сцены отделены от него.
> По LEARNING-D-004 от 2026-09-27 в учебном сравнении покупки аксессуара
> герой показан в приобретённом аксессуаре; без покупки — в выбранном образе
> своей ветви. Нематериальная покупка сохраняет реакцию. Показ не меняет
> настроение или надетую вещь ни в снимке ветви, ни в настоящей игре.

> **Постоянные приобретения и страницы вещей, 2026-09-26, FINANCE-UI-D-021.**
> Купленные предметы остаются в инвентаре после выполнения цели и перехода
> главы, включая покупки существующего сохранения. Предмет открывается на
> полноэкранной иллюстрированной странице; карта звёзд содержит темы Ориона,
> Большой Медведицы и Млечного Пути, телескоп — характеристики, штатив —
> инструкцию. Аксессуары идут ниже, их можно надевать. Просмотр и перелистывание
> ничего не выдают, не списывают и не продвигают сюжет. Это уточняет подачу
> MAIN-D-075 и исключает купленные вещи из возможной потери по D-029.
> Точные рисунки, справочные тексты и навигация — выбор реализации, без новых
> цен, бонусов и изменений схемы владения. [Контракт инвентаря](inventory.md).

> **Булочка и подписи событий, 2026-09-26, FINANCE-UI-D-019.** Карточка булочки
> показывает изображение, короткий текст с именем питомца и два действия:
> купить или пройти мимо. Повторной сводки денег и потребностей и третьего
> варианта отказа ради цели нет; источники оплаты остаются человеческой фразой
> с фактическими суммами. Служебные нижние подписи убраны со всех карточек.
> Цена 6, существующий приём пищи и HAPPY, правила списания и аналитики
> сохраняются. Это отображение, без изменения метаданных старого каталога.

> **Единая страница копилки, 2026-09-26, FINANCE-UI-D-017.** Пользователь
> потребовал убрать лишние окна, скачки кнопок и пустоту, исправить фон,
> пропорции и движение лисёнка, вернуть уголок реплики. Выбор реализации —
> одна страница с режимами пополнения и снятия, вводом и последствиями;
> подтверждение, предупреждение о еде и результат находятся внутри неё.
> Back отменяет открытое подтверждение или предупреждение, иначе закрывает
> раздел. Во время записи выход и повторная команда заблокированы.
> Это локальные состояния UI: отдельного обзора и экрана RESULT нет,
> команды, правила денег и обязательный контекст решения не меняются.

> **Текущий бюджет, 2026-09-26, FINANCE-UI-D-015.** Четыре статьи показывают
> оставшиеся доступные монеты. Их сумма вместе с нераспределённым остатком равна
> `availableBalance`; реальная копилка `savingsBalance` находится отдельно.
> Покупки, заработок и переводы меняют текущие статьи, первоначальное намерение
> сохраняется в `FinancialProgress.plans`. Статья «В копилку» содержит ещё
> доступные деньги; пополнение требует отдельного действия. Первая правка
> открывает MANUAL с текущими остатками, без пустого плана. Прежний режим
> исторических сумм FINANCE-UI-D-014 на основном экране заменён этой моделью.
> Пояснение движений использует проверенную историю; разность остатков не
> подменяет расход. FINANCE-UI-D-016 убирает автоматическую фразу «Монеты не
> потратили» после бесплатного события, сохраняя фактические траты и доходы.

> **Уточнение карточек и практики, 2026-09-25, FINANCE-UI-D-013.** Общая
> сводка дня/сил/балансов убрана из карточек; условия конкретного решения и
> причины блокировки остаются. Практика показывает вопрос отдельно от истории.
> Её UI учитывает существующий запрет при незавершённом плане. При ошибке
> отправки повторяется тот же запрос с прежними ID, ревизией и контекстом;
> другой ответ до разрешения неопределённого результата не отправляется.
> Отказ обновляет показанное состояние без автоматического повторного ответа.

> **Подача первого вступления, 2026-09-25, FINANCE-UI-D-012.** Единственное
> бесплатное сюжетное действие без затрат сил не показывает повторную денежную
> сводку и не передаёт факт её показа как финансовый контекст. Денежные действия,
> работа и альтернативы сохраняют контекст. Новые композиции вступления и
> копилки не меняют команды, guards, подтверждение снятия или ход истории.
> [Описание экранов](financial-adventure-ui-2026-09-25.md).

> **Подключение игровых экранов · 2026-09-25 · FINANCE-UI-D-007.**
> [Копилка, задания, бюджет и Хроноскоп в Android](financial-adventure-ui-2026-09-25.md)
> используют текущий агрегат и команды. Подтверждение снятия содержит ожидаемые
> остатки и подцель, которые проверяются внутри записи, включая состояние
> до первого дня без ревизии. Отмена не вызывает команду. Хроноскоп имеет один
> вопрос после сравнения; метаданные расчёта не изменяют реальные игровые
> последствия. Учебные записи проверяют исходный сегмент истории атомарно.

> **Актуальная финансовая модель · FINANCE-D-001–008 и FINANCE-UI-D-015.**
> Последующие подтверждённые правила имеют приоритет над описанием экономики
> v16 ниже и над историческими примерами общего баланса: два реальных остатка
> «Доступно» и «Копилка»; четыре текущие статьи распределяют доступные деньги.
> Статья «В копилку» ещё не является накоплениями. Подтверждение бюджета не переводит
> деньги; по ADVENTURE-D-018 покупка цели может дополнить копилку текущими деньгами, снятие подтверждается
> отдельно. Пять больших целей задают пять финансовых периодов. Текущая
> реализация — Room v20; текущий бюджет введён переносом 19 → 20,
> выбранная подцель — 18 → 19. Технические версии: формат снимка 4,
> отпечаток переходов 10 (сюжетное перемещение по MAP-D-006). Старые записи
> Хроноскопа не исполняются по новым правилам.
> [Подробная модель и статус](requirements/engine-refactor-plan-2026-09-24.md),
> [карточки для дизайнера](requirements/designer-handoff-2026-09-24.md).
> Авторская расшифровка Figma в приложении этого документа не переписана.


> Последовательные главы, 2026-09-24, CAMPAIGN-D-001: Ночь наблюдений →
> Башня → Дом → Карта → Экспедиция. Внутри главы выбирается накопительная
> подцель; для старта первой главы доступны четыре. Финал требует комплект
> своей главы, сюжетные открытия и финансовые этапы. Смена подцели не меняет
> деньги или период. [Точные переходы и совместимость](campaign-choice.md).
> Исходные материалы Figma сохранены без изменений.

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
> unsupported/open operations. It lives in `:core:game`; Room v2 was the kernel's
> initial persistence version, and the current finance migration is v16 → v17.
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
| `PetVisualState` | Saved current state, changed by gameplay; live presentation briefly shows its special artwork under ADVENTURE-D-011 | Eight states in section 3 |

The selected look remains remembered while a special state is displayed. The
live presentation returns to that look after its brief reaction, without
changing the saved state. A gameplay transition to ОБЫЧНОЕ also shows that look.
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

**Принято пользователем · D-030–D-032 · 2026-09-19; уточнено CAMPAIGN-D-001 · 2026-09-24:**
Each chapter binds its own large goal through StoryAct.goalId. Chapters and kits
advance in the authored order. The player chooses a saving target within that
kit; changing it preserves savings, the plan and the current financial period.
A finale needs the complete kit of its own chapter, the required discoveries
and the financial milestones. A different kit cannot satisfy this guard.
The authored finale advances the chapter; buying the last item does not.
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
event when money is insufficient for a particular action. The later
FINANCE-D-002 rule separates available money from real savings. Automatic acceptance, repeat offers, and returning to the
original action remain unspecified.

**Принято пользователем · D-038 · 2026-09-19:** goal-required items are not
awarded by story-progression events; they can be purchased in the goal tab.
This refines D-036 and restricts the general event-item consequences from D-017
for this combination of event and item. Story progress and buying goal items
are separate actions. FINANCE-D-002 now requires payment from real savings;
current goal availability follows CAMPAIGN-D-001; temporary prices follow D-105/D-106. Do not infer
additional ways to obtain goal items from the historical common-balance example.

## 2. Rendering contract

```text
if displaying the sleeping day recap:
    display the age/color sleeping artwork with its bed
else if live presentation's reaction has finished, or visualState == ОБЫЧНОЕ:
    display the saved selectedLook
else:
    display the dedicated image for visualState
    fully replace the cosmetic appearance, including accessories
```

Examples in ОБЫЧНОЕ: БАНДАНА displays Ryzhik wearing a bandana; РЮКЗАК
displays Ryzhik wearing a backpack; ОБЫЧНЫЙ displays plain Ryzhik.
A hungry Ryzhik briefly has no selected accessory visible, but the selected
look is retained and reappears when the presentation reaction finishes.
Hunger and its guards remain unchanged until a gameplay action resolves them.

**Принято пользователем · ADVENTURE-D-011 · 2026-09-27:** the current pet's special
pose lasts a few seconds and smoothly returns to the ordinary selected look.
The implementation uses a single application-scoped 4-second reaction clock
and a 600 ms crossfade in the shared artwork renderer; these durations are
engineering choices. The display uses NORMAL after the deadline, while the
saved `PetVisualState`, current needs, selected look, owned gear and history
remain untouched. No game command or persistence write accompanies the timer.

A new actual visual state replaces the current reaction and its deadline;
NORMAL immediately selects the ordinary look. The first observed special state
after process launch also gets a brief reaction because the clock is not persisted.
Rotation and navigation retain the same clock. Redisplaying the same event and
changing name, color, accessory, day or revision without a different visual
state do not restart it. When the selected look changes during the reaction, its
current value is used on return. This clock follows only the live game;
historical and simulated learning snapshots neither advance nor replace it.
The sleeping pet with its bed in the day recap has its own presentation and
must not turn into a standing pet when the reaction expires. The shared
crossfade includes each pose's shadows; it does not move the screen layout,
invent missing artwork or change source canvases. Live menu, event, savings and
budget scenes opt into the clock; historical learning artwork keeps its own mapper.

## 3. State catalog and lifecycle

**Принято пользователем · D-001 · 2026-09-17:** only one pet state is active at a
time. There are no hidden conditions, suppressed reactions, or pending reaction
queues. Explicitly clearing the current condition returns the pet to NORMAL.
A quest may explicitly transition between
its decision, need, and result states as in the source scenarios; each new state
replaces the previous one rather than hiding it. See section 5 for the distinction
between an explicit transition and the board's superseded fallback rule.

**Принято пользователем · D-002 · 2026-09-17; уточнено ADVENTURE-D-003 · 2026-09-26
и ADVENTURE-D-011 · 2026-09-27:**
the saved pet state remains until an explicit gameplay transition updates it.
HAPPY and UPSET last until the next newly opened event. A pending decision enters
THINKING; the existing food/energy thresholds enter HUNGRY/TIRED, and feeding or
rest clears the corresponding need. A neutral new event can return a reaction
to NORMAL. Authored state effects remain explicit transitions. There is no saved-state reset
after a duration, animation, ordinary navigation, or merely redisplaying/resuming
an already opened event. No previous reaction is retained underneath the current
state. These rules describe the domain state. The temporary live artwork and
smooth return in section 2 supersede the previous event-only display lifetime;
they do not resolve a need or rewrite a result.

| Source state / English alias | Entry condition | Saved-state duration and exit; live artwork follows section 2 |
| --- | --- | --- |
| ОБЫЧНОЕ / NORMAL | No active special state | Display the saved cosmetic look until a relevant event activates another state. |
| ЗАДУМАЛСЯ / THINKING | Before an important financial decision; for example, buy an item now or save money toward a goal | After the decision, an explicit outcome can replace it with another state or NORMAL. No hidden decision state remains. |
| ГОЛОДЕН / HUNGRY | A story event says food is needed, or the existing day's food threshold is reached without a meal | Remains active until feeding resolves the need. An authored meal may specify HAPPY; ordinary feeding clears HUNGRY, while exhausted energy can enter TIRED. Controlled by game logic, never real elapsed time. |
| УСТАЛ / TIRED | A consequence of a story decision, such as additional work for virtual money | Rest or an explicitly defined fatigue-clearing event → NORMAL. Exact events are deferred; do not make every next event clear fatigue. No continuous energy meter or real-time energy consumption. |
| ОБЕСПОКОЕН / WORRIED | Before the child's choice, when an unexpected mandatory expense, insufficient funds for a need, or a financial story problem occurs | After the decision, follow its explicit result transition, such as a reaction followed by NORMAL or the source example leading to HUNGRY. WORRIED does not remain hidden afterward. |
| РАДУЕТСЯ / HAPPY | Authored positive outcome, such as a desired item received | Remains saved until a new event or actual need updates it under ADVENTURE-D-003. Ordinary navigation preserves it; ADVENTURE-D-011 independently restores the selected look on screen. |
| РАССТРОЕН / UPSET | Authored outcome of an adventure | Remains until a new event or actual need updates the state under ADVENTURE-D-003. Not punishment for spending or a moral judgment. |
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
All saved pet states, including HAPPY and UPSET, change only through explicit
event updates. Animation completion, screen navigation, and elapsed time do not
clear them. The live artwork timer in section 2 changes only presentation.

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

**Принято пользователем · FINANCE-D-001–008, уточнения FINANCE-UI-D-015 от 2026-09-26 и ADVENTURE-D-018 от 2026-09-27:**

- Реальные деньги принадлежат `EconomyState.availableBalance` и
  `savingsBalance`. Общая сумма — их сумма. `unallocated` — часть доступных
  денег, пока не включённая в текущий план; отдельным остатком сверху она не
  является.
- `economy.plan` содержит оставшиеся доступные монеты по статьям
  `needs/wants/savings/reserve`. Вне редактирования `plan.total + unallocated =
  availableBalance`; при открытом черновике эту сумму задаёт `displayPlan`.
  Незавершённая редакция хранится в `BudgetPlanning.draft` со своей базой и
  ревизией. Подтверждённые намерения сохраняются отдельно в
  `FinancialProgress.plans` и не переписываются расходами.
  Редактирование и подтверждение бюджета не переводят деньги в копилку.
- `DepositSavings` явно переводит доступные монеты в накопления.
  `WithdrawSavings` требует отдельного подтверждения и уменьшает накопления
  на показанную сумму. Оба перевода сохраняют общую сумму и не являются новым
  доходом или потребительским расходом. Пополнение уменьшает доступные статьи
  по порядку В копилку → Запас → Хочу → Нужно; снятие увеличивает «Запас».
- Еда, обычные покупки и платные варианты событий используют только доступные
  монеты. Части большой цели по ADVENTURE-D-018 оплачиваются реальными
  накоплениями и текущими деньгами вместе: сначала копилка, затем доступные
  статьи В копилку → Запас → Хочу → Нужно. Предварительный перевод не нужен.
  До подтверждения показываются источники оплаты и недостаток на еду после
  покупки; риск требует явного подтверждения. Если общей суммы не хватает,
  нет частичного списания. При нехватке на обычную еду и наличии денег
  в копилке интерфейс предлагает открыть бюджет; автоматического снятия нет.
- `EARNING` по D-148 не требует денег и не списывает их ни при предложении,
  ни при выборе. Фактическая награда пополняет доступные монеты и «Запас».
- Обычный расход использует Запас → Хочу → В копилку → Нужно; WANT —
  Хочу → Запас → В копилку → Нужно; FEEDING — Нужно → Запас → Хочу → В копилку.
  Название «В копилку» здесь обозначает доступную статью, а не реальную
  копилку. Только покупка части цели уменьшает `savingsBalance` напрямую.
- Недельные 100 поступают один раз в доступные деньги. INITIAL/WEEKLY проходят
  `RECEIPT → ALLOCATION → подтверждено`; повторный просмотр ничего не
  начисляет. Планируются доступные сейчас деньги, не будущие возможные награды.
  WEEKLY сохраняет текущие статьи, новые 100 становятся нераспределёнными.
  Минимум «Нужно» для INITIAL/WEEKLY — `min(35, база)`, для MANUAL/MIGRATION —
  `min(оставшаяся потребность в еде, база)` по FINANCE-UI-D-015.
  Полное распределение проверяется в домене.
- «Наш бюджет» всегда показывает текущие статьи. Первая прямая правка
  создаёт MANUAL на их основе; просмотр не пишет игру. Активный черновик
  восстанавливается из сохранения. Первая отложенная правка проверяет
  показанную доступную сумму перед отправкой, не подстраиваясь незаметно под
  новый доход. После еды просмотр оставшихся «Нужно» не заставляет вернуть
  потраченные монеты до 35. Вне редактирования «Готово» закрывает экран без
  новой записи плана, в том числе при нулевом доступном остатке.
- Изменение плана учитывает причину: забытая известная нужда — возможная
  трудность планирования; неожиданная трата/новый доход — адаптация;
  неизвестная причина — факт без оценки. Само изменение не означает неуспех.
- Gameplay при незавершённом плане блокируется `BudgetPlanningRequired`.
  Команды самого планирования и косметики разрешены. Просмотр меню, истории
  и карты не выполняет событие и не начисляет деньги. Выход из редактора
  сохраняет прежние проверки полного распределения; ошибка записи не теряет
  сохранённый черновик.

Преемственность UI: четыре иллюстрированные статьи, строка нераспределённого,
ввод суммы и overlay «Монетки» сохранены. В overlay реальные доступные монеты и
сумма в копилке отделены от текущего распределения доступных денег. Исторические
намерения находятся в истории. Приоритеты текущих статей задаёт FINANCE-UI-D-015;
автоматическое расходование реальной копилки запрещено FINANCE-D-002. Остальные правила
игрового дня и запрет платного заработка сохраняются.

### Интерфейс копилки — реализация FINANCE-UI-D-017

`Savings` остаётся одним маршрутом. Переключатели «Пополнить» / «Взять»,
сумма и последствия находятся на одной странице. Их просмотр и изменение
сами по себе не переводят монеты. Снятие по-прежнему требует отдельного
явного подтверждения показанной суммы. При предупреждении о нехватке на еду
пополнение также ждёт явного решения; предупреждение не скрывается ради
упрощения композиции. Эти состояния показаны внутри страницы, а не в цепочке
отдельных окон. До решения доступны реальные остатки, известные расходы
на еду и последствия для выбранной подцели. Факт показа финансового контекста
по-прежнему требует его полной видимости.

Back отменяет открытое подтверждение снятия или предупреждение о еде,
не вызывая денежную команду; иначе возвращает на предыдущий маршрут.
Применённая операция показывает результат на этой же странице и актуальные
остатки, без отдельного шага RESULT и без повторной команды при его показе.
Ошибка записи не изображается успешным переводом: сохраняется явный повтор
исходного запроса. Во время записи остаётся защита от повторного действия
и выхода. Проверка ожидаемых остатков, подцели и ревизии внутри агрегатной
записи сохранена. Упрощение UI не меняет FINANCE-D-002 и FINANCE-UI-D-015.

The current persisted aggregate follows D-024 and the later FINANCE decisions.
Columns and foreign keys are documented in the [normalized schema](schema-normalization.md).
This layout does not prescribe merging the domain Kotlin classes.

```text
Persisted game / Сохраняемая игра
├── GAME_STATE
│   ├── selected look and visual state / образ и визуальное состояние
│   ├── satiety and fatigue / сытость и усталость
│   ├── available balance and savings / два реальных остатка
│   ├── current allocations and separate draft / текущие статьи и черновик
│   └── story position and active event / прогресс и активное событие
├── OWNED_ITEM / принадлежащие вещи
├── PLAYER_DECISION / сюжетные решения
├── FINANCIAL_PERIOD / периоды и учебные этапы
├── BUDGET_PLAN_REVISION / история намерений
└── GAME_AUDIT / команды, снимки, финансовые операции и факты
```

One outcome may change money, story position, ownership, and the visual state
together. These changes belong to one atomic saved-game update. The pet's
visual state is only one part of that aggregate.

**Принято пользователем · D-004 · 2026-09-17:** closing and reopening the app must
preserve the current game and pet state. Owned gear is also retained. Reopening
does not reset the saved pet to NORMAL or advance the story merely because time passed.
This includes HAPPY and UPSET: reopening restores the saved state until an
explicit event changes it. The presentation clock of ADVENTURE-D-011 is not part
of the saved aggregate and must not alter restoration or historical snapshots.
Room is the approved persistent-data technology in
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
6. The live presentation smoothly returns to the saved bandana after the brief
   reaction, or the current selection if it changed in the meantime. A later
   explicit update may change the saved state to NORMAL.

The shorter cosmetic example omits the reaction step; the full hunger example
explicitly includes it. HAPPY remains saved until another explicit event update;
ADVENTURE-D-011 gives its live artwork a brief duration without a saved-state reset.

### 7.2 Financial problem

1. NORMAL.
2. An unexpected expense occurs.
3. WORRIED.
4. Child chooses where to get the money.
5. Show UPSET or HAPPY depending on the result.
6. The live presentation returns to the selected look after its brief reaction;
   a later explicit event update returns the saved state to NORMAL.

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
- Prices/rewards beyond the approved temporary catalog, precise pedagogical
  thresholds of mastery, and event-specific interpretations not covered by the
  current financial contract. Financial periods, editable plans and savings
  payment rules are already resolved by FINANCE-D-001–FINANCE-D-008.
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
- Each available special-state artwork briefly replaces the selected cosmetic
  appearance in the live game, then crossfades back to the current selected look.
- Entering/exiting a special state does not erase the saved look.
- Changing the selected look retains owned gear; NORMAL displays the current
  selection after the special state finishes.
- Hunger persists in the game while the food need is unmet, even after its
  artwork returns to the selected look; buying food follows scenario 7.1.
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
- Saved HAPPY and UPSET persist until an explicit event changes the state, including
  across reopening; no timer, animation completion, or generic Continue action
  clears them. UPSET does not represent punishment or moral blame.
- The live reaction returns smoothly after 4 seconds (600 ms crossfade); a new
  actual state replaces its deadline. Navigation and cosmetic changes do not
  restart it. The timer writes no pet, needs, money, story, ownership or history.
- Initial observation after process launch may briefly show the saved reaction;
  rotation/navigation retain its deadline rather than replaying it.
- Historical/simulated learning snapshots do not drive the live reaction clock;
  the sleeping day recap keeps the bed when a reaction deadline passes.
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



## Инвентарь — актуализация 2026-09-26

**Принято пользователем · MAIN-D-075, BALANCE-D-002, FINANCE-UI-D-021:**
«Снаряжение» открывает уже приобретённые вещи, ниже — аксессуары, без блоков глав.
Открытие, просмотр и возврат не меняют баланс, владение, выбранный образ,
состояние питомца или ход истории. Пустое владение отображается пустыми разделами;
выбранный начальный образ сам по себе не подтверждает наличие вещи.
Повторы и порядок экземпляров сохраняются внутри соответствующего раздела.
Купленные вещи не расходуются при выполнении цели и не удаляются при переходе
главы. Старые приобретения отображаются по сохранённым `OwnedItem`, независимо
от текущей главы. Иллюстрированные страницы выбираются по точному `itemId`,
а не по названию или ID операции; новые страницы не требуют новой покупки.
Сведения о созвездиях, характеристиках и использовании — справочная подача,
без скрытого изменения игровых правил.

Покупка остаётся на экране цели или в игровом предложении. Инвентарь позволяет
явно надеть приобретённый аксессуар через `SetPetLook`: внутри актуальной
транзакции проверяются владение и ожидаемый предыдущий образ. Меняется только
выбранный образ; деньги, шаги, силы и список вещей сохраняются. Снятие возвращает
базовый образ и не уничтожает вещь. Никакая экранная страница сама не исполняет
команду надевания. По D-116 неожиданное предложение продаёт существующий ещё
не открытый аксессуар. См. [инвентарь](inventory.md).

Текущий движок уже добавляет владение при `BuyGoalItem` и сохраняет его в
финале главы. REMOVE в существующем контенте отсутствует; неподдерживаемый
предметный REMOVE отклоняется целиком. Новое требование постоянства покупок
не требует миграции Room или восстановления владения по косвенным признакам.



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
сегодня и следующего утра по ADVENTURE-D-009: после бесплатной еды текущая
энергия равна нулю, следующее утро использует прежнее ограничение сил. Автоматических списаний при отказе нет. Завершение дня
по-прежнему требует питания. Happy/UpSet не сбрасываются простым входом на экран.

По D-113 замена действия на «Покормить» или завершение дня из-за нехватки сил
сопровождается пояснением над кнопками: питомец проголодался или устал и что
нужно сделать сначала. Имя берётся из сохранения. Причина и кнопка вычисляются
из тех же проверок движка в одном UiState; отдельной задержки или подтверждения
перед заменой нет. Пояснение не затирается временным сообщением об ошибке.
После кормления голодное пояснение исчезает; если всё ещё нужен отдых,
показывается усталость. Обычное завершение полного плана само не означает усталость.

## Питание и повтор действий — 2026-09-27

По **ARCH-D-004** из [реестра решений](decisions.md) `MealPolicy` — общий
источник правил еды для `GameEngine`, аналитики и экранов меню, событий, дел,
бюджета и цели. Обычный обед — самый дешёвый платный вариант каталога;
перестановка элементов не меняет его цену и порог предложения бесплатной еды.
В текущем каталоге это 5 монет. Бесплатная альтернатива предлагается при
нехватке доступных монет; предложение в меню также требует начатого,
не законченного и ещё не накормленного дня. Платные варианты остаются видимыми
при нехватке, чтобы интерфейс мог показать причину недоступности.

Потребность до конца недели рассчитывается по той же цене и числу оставшихся
приёмов пищи: сегодняшний входит в расчёт, пока питомец не поел; до первого
дня учитываются семь обедов. Это предупреждение о будущих нуждах, а не отдельный
кошелёк или автоматическая бронь денег. Его используют планирование,
подтверждение пополнения копилки и смешанной покупки цели.

Оплаченный обед уменьшает доступные деньги по существующему приоритету FEEDING,
отмечает питание и не восстанавливает силы. Бесплатный обед не меняет деньги,
обнуляет сегодняшние силы и сохраняет авторское ограничение следующего утра
(сейчас 3 из 5 по ADVENTURE-D-009). Подтверждение команды по-прежнему проверяет
текущий агрегат в движке; показ вариантов и расчёт потребности игру не меняют.
Вынос политики не добавляет новых условий прохождения, таблиц или полей снимка.

По **ARCH-D-005** один пользовательский выбор образует одну попытку записи.
[GameActionAttempt](../../app/src/main/java/ru/nksk/lctapp/core/ui/game/GameActionAttempt.kt)
сохраняет исходные команду, `request.id`, ожидаемую ревизию, контекст решения
и состояние до действия. При исключении результат считается неопределённым:
явный повтор отправляет тот же запрос, а другая команда не подменяет его.
Полученный `EngineResult.Blocked` — определённый отказ; попытка завершается,
экран показывает причину и актуальные возможности. Новая ревизия не становится
основанием незаметно повторить прежний выбор как новую команду.

Успешный повтор может вернуть уже более новое состояние мира. Сообщение
о выполненном действии относится к его собственному сохранённому результату:
если ближайшего результата нет в ответе, используется checkpoint команды
из истории. Разность с поздним текущим балансом не выдаётся за награду или
цену старого действия. Если запись прошла, а её пояснение прочитать не удалось,
это не объявляется новой ошибкой записи и не запускает повторное списание.

Сохраняются действующие правила подтверждения денежных действий и жизненный
цикл незавершённой мини-игры. Это контракт повторной отправки и обратной связи,
а не автоматическое выполнение нового действия при загрузке экрана.

## Сон и содержимое итогов — актуализация 2026-09-26

По FINANCE-UI-D-020 пользователь просит сделать сухие итоги дня более игровыми и выразительными.
По уточнению FINANCE-UI-D-022 `DaySummaryScreen` возвращает прежнюю композицию:
спящий герой в кроватке на отдельной ночной сцене сверху, светлая панель итогов
снизу. Существующие записи с иконками сохраняют порядок журнала. Переходы дня,
денежные операции и сохраняемые факты не меняются.

По D-108 итог FINISHED показывает спящего лиса текущего сохранённого возраста
на сильно затемнённой сцене. Это представление сна, не новая запись visualState:
настроение и выбранный образ не затираются. Следующее утро по-прежнему создаёт
только явная команда BeginDay. Цвет сна берётся из PetState.color по D-109;
возраст следует правилу глав D-110.

По D-112 итоги сначала рассказывают, что сделали за день: выполненные дела,
сюжетные находки, покупки с ценами и питание. Описания в прошедшем времени
соответствуют конкретному выбранному действию. Предложение/откладывание дела
не считается выполнением; обход и пропуск не получают текст ремонта/находки.
Ниже — «Потратили» и «Получили», только при полной сверке журнала. Отдельный
блок «Монеты сейчас» показывает доступные деньги и реальную копилку.
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

Дополнение реализации 2026-09-26: практические STORY-ветки и явные ручные
ремонты RANDOM требуют завершённой мини-игры. Привязки 13 сюжетных работ и
13 ручных ремонтов (плюс сохранённая прежняя версия очистки рюкзака), допуск, пауза, восстановление и
отсутствие автоматического успеха описаны в
[игровом выполнении сюжетных работ](story-work-minigames.md). Это не превращает
диалоги, чтение или поездки в оплачиваемые EARNING-поручения.

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
находится на главном экране. По D-150 (2026-09-23) оно доступно сразу после
начала дня, включая нулевой шаг, пока питомец ещё не ел; завершённый день
и незавершённое планирование бюджета по-прежнему исключают кормление.
При нехватке монет там же предлагается бесплатная
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

Возраст — поле StoryAct в каталоге. Успешный финал, ожидающий собранный комплект
своей главы, атомарно записывает решение, завершение проекта, переход главы
и новый pet.age. Покупка, выбор цели, пропуск дополнительной карточки, сон,
пауза и заблокированное действие не меняют возраст. Имя, цвет и аксессуар
сохраняются при взрослении. Отдельного действия «повзрослеть» у UI нет.

Для прежних игр GameSession.prepare после установки каталога и
initializeIfAbsent сверяет возраст с сохранёнными финалами. При расхождении
движок читает актуальный агрегат в транзакции и согласует pet.age и
привязку активного комплекта по CAMPAIGN-D-001, повышая revision существующего engine. Повторная подготовка согласованной игры
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
тень и навигационные кнопки не двигаются вместе с фигурой. По ADVENTURE-D-011
добавлена плавная смена иллюстрации за 600 мс; дыхание и оптимизация UI сохраняются.
[Объём движения и отложенный прототип](pet-motion.md).

## Первый запуск и выбор спутника — 2026-09-19

По ONB-D-001–ONB-D-004 в [реестре](decisions.md), до первого входа в игру
показывается onboarding. Проверка существования сохранения ничего не создаёт.
Существующее сохранение открывает меню; ошибка чтения требует повтора.
Новый игрок сначала выбирает лисёнка. Кнопка до выбора приглушена и показывает
диалог с просьбой выбрать персонажа. После выбора «Начать приключение» сохраняет
Profile-черновик и открывает вступительный ролик, затем кастомизацию по MEDIA-D-002.
Только финальное подтверждение Introduction после остальных шагов onboarding
создаёт начальный снимок через атомарный initialize-if-absent. Ошибка записи
оставляет соответствующий шаг для повтора; сам выбор не запускает день или событие.

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

По CUST-D-019–CUST-D-021 и MEDIA-D-002 (2026-09-27):
Choose → IntroVideo → Customize → Accessories → GoalBriefing → GoalSelection → Introduction → Ready.
Перед IntroVideo сохраняется черновик `OnboardingStep.Profile`. IntroVideo —
временное состояние представления: завершение, «Пропустить» и Back открывают
Customize с тем же профилем, без записи Room и без команды движка. При холодном
запуске Profile-черновик восстанавливается сразу в Customize. Позиция видео
сохраняется только в живом ViewModel; ошибка воспроизведения оставляет
«Продолжить». Остальные промежуточные шаги сохраняют черновик. Только «Дальше» на
Introduction с объяснением бюджета атомарно сохраняет игру
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

GoalBriefing, GoalSelection и Introduction восстанавливаются из черновика вместе с savingItemId.
На GoalBriefing кнопка «Дальше» открывает GoalSelection; сама цель не назначается.
Без выбора одной из четырёх накопительных подцелей первой главы по CAMPAIGN-D-001 подтвердить выбор нельзя.
Back из Introduction возвращает GoalSelection, затем GoalBriefing и Accessories с
сохранённым выбором. Ошибка финального сохранения оставляет Introduction
для повтора, кнопки блокируются на время записи.

## Звук и музыка глав — 2026-09-27

MEDIA-D-001–MEDIA-D-004 в [реестре](decisions.md) относятся к представлению.
`EventMedia` задаёт semantic keys реплики события, короткого появления покупки,
места и звука оплаченного варианта. Проигрывание не выполняет выбор, не списывает
монеты и не создаёт награду. Звук оплаты следует подтверждённому действию;
отказ и ручная работа его не получают. Рекомпозиция не повторяет ту же реплику.

Пять тем из архива — общий фон приложения по текущему сюжетному акту.
`StoryChapterPresentation` у `LoreChapter` задаёт один `musicCueKey` для карточек
акта; app читает его через текущий `storyProgress`. Музыка продолжается при
переходах между экранами и меняется при смене главы, приглушаясь под голос.
Отдельные неожиданные события и покупки не назначают приложению новую главу.

Общий выключатель звука доступен в настройках и на вступительном видео.
Оба элемента изменяют один persistent preference в DataStore, который применяется
к фону, озвучке, эффектам и видео после перезапуска. Это не переход `GameState`,
не игровая ревизия и не поле Room/world snapshot. Вне foreground воспроизведение
приостанавливается. Открытие медиа и завершение видео не продвигают игровой день.
[Контракт intro](intro-video.md), [описание событий](event-authoring.md),
[каталог ресурсов](assets/media-catalog.md).

## Карта: выбор фона (MAP-D-005, 2026-09-20)

Кнопка карты меню открывает feature/map. Выбор доступного места сохраняет
LocationScene.location и после успешного commit возвращает в меню. Ошибка
оставляет карту открытой. Все места пока доступны; LocationAccessPolicy
будет проверять сюжетное прохождение после определения соответствия локаций.
Освещение DAY/EVENING меняется только явным программным вызовом setLighting.
Усталость и энергия не переключают фон. Оба действия не продвигают день,
не создают события, не расходуют ресурсы и не меняют ревизию движка.
Агрегат обновляется из актуального состояния, сохраняя параллельные изменения.

### Сюжетный переход в локацию (MAP-D-006, 2026-09-27)

Авторский вариант `EventChoiceSpec.destination: GameLocation?` обозначает
место прибытия после выбранного действия. Компилятор передаёт его в
`EventPolicy.choiceDestinations`; `EventFactory` проверяет принадлежность
choice ID событию. `EventCardCopy.scene` и presentation описывают показ
карточки и не используются как правило перемещения. Например, обратная
дорога показана на тропе, но её destination — обсерватория.

`Choose`, `CompleteEvent` и завершение `CompleteStoryGame` применяют destination
в общем пути выбора после проверок доступности. Сохранённое `locationScene.location`
меняется атомарно вместе с решением, деньгами, силами и прочими последствиями;
`locationScene.lighting` сохраняется. Дополнительной цены или расхода сил нет.
Открытие карточки, начало мини-игры, пауза, отказ/ошибка записи и выбор без
destination оставляют локацию прежней. Предпросмотр вычисляет другой путь без
записи в текущий мир. Повтор уже принятой команды распознаётся по receipt,
не применяет результат заново и не отменяет более позднее перемещение.

| Сюжетное действие | Сохранённая локация после успеха |
| --- | --- |
| G1.01: принять приглашение «В обсерваторию» | OBSERVATORY |
| G1.09: сходить к дорожному посту | TRAIL |
| G2.03: укрепить мост и перейти / пройти по обходу | TRAIL |
| G2.04: дойти до сигнального поста | TRAIL |
| N2.RETURN: вернуться со Свитком | OBSERVATORY |
| G3.01: осмотреть старую мастерскую | WORKSHOP |
| G4.03: сходить за береговыми картами | PIER |
| G4.05: сходить к горному указателю; G4.06: исследовать лесной путь | TRAIL |
| N4.TRAVEL: дойти до края известных дорог | TRAIL |

Обычное чтение/исследование карточки само по себе не задаёт destination.
У башни, отдельных релейных станций и штормового убежища пока нет места в
`GameLocation`; их сюжетные действия сохраняют текущую локацию, не выбирая
другую по сходству иллюстрации. Это граница текущей карты, не новое ограничение
доступности сюжетных действий.

Поля локации уже входят в [агрегат и snapshot](game-data-schema.md#выбранная-локация-и-освещение),
новых таблиц/полей и миграции нет. Старые решения, IDs и тексты установленных
определений сохраняются. При загрузке история не пересчитывает местоположение:
новое правило применяется к следующему успешному выбору. Destination входит в
gameplay fingerprint; версия переходов повышена до 10. Исторический replay с
несовместимым fingerprint не интерпретирует старые решения по новым правилам.


### Уточнение недельного бюджета в Preview — D-136

Принято пользователем 2026-09-21. До первого перехода с экрана поступления
на планирование «Продолжить день» из меню возвращает на экран поступления.
После нажатия «Распределить монеты» возвращает прямо к незавершённому бюджету;
повторно показывать поступление не требуется. Продолжение игры недоступно до
подтверждения полного распределения с минимумом «Нужно» `min(35, база)`
(D-128/D-129, уточнение FINANCE-UI-D-015).
Показ и повторное открытие экрана не являются новым начислением.
Правило подключено к движку и Room по D-141; Preview использует отдельный локальный сценарий.


## Обязательное планирование — актуализация 2026-09-26

Начальный путь D-138–D-143 и CUST-D-021 сохранён. После Introduction создаётся
игра со 100 доступными монетами, маркером нераспределённого 100 и
INITIAL/RECEIPT. Меню через «Продолжить день» или «Монетки» открывает поступление.
«Распределить» переводит в ALLOCATION; подтверждение завершает сессию и
возвращает в меню. На дни 8, 15, … BeginDay атомарно создаёт новый день,
зачисляет недельный доход и открывает WEEKLY/RECEIPT; событие ждёт бюджета.

С FINANCE-UI-D-015 текущие статьи, черновик и история намерений разделены.
WEEKLY сохраняет остатки статей в черновике; новые 100 добавляются в доступный
баланс и нераспределённую часть. Реальная копилка не перераспределяется.
Ручная сессия начинается при первой правке текущих статей, без их обнуления;
простое открытие не блокирует игру. Обычный заработок увеличивает доступный
баланс и «Запас», не открывая обязательное распределение. Ни один из этих
переходов не переводит монеты в копилку.

По FINANCE-UI-D-015, уточняющему FINANCE-UI-D-008, INITIAL/WEEKLY проверяют
`min(35, база)`, MANUAL/MIGRATION — `min(оставшаяся потребность в еде, база)`.
Ограничение базой не создаёт монеты. Частичные увеличения недостаточной статьи
«Нужно» допустимы; уменьшение ниже минимума, ручной ввод меньшей суммы и
подтверждение недостаточного черновика запрещены. Покупка еды уменьшает
текущую статью; простой выход из бюджета без редактирования не проверяет
заново недельные 35. Первоначальные намерения в `FinancialProgress.plans`
не переписываются. Цветные сегменты показывают текущее распределение
доступных монет или открытый черновик, не фактический баланс копилки.
Редактор и подтверждение используют ревизию сессии; очередь относительных
изменений берёт актуальный черновик. Все записи проходят через
`GameSession.dispatch`, без самостоятельного сохранения копии состояния из UI.

Техническая версия переходов отпечатка каталога — 5. История с прежним
отпечатком недоступна для пересчёта Хроноскопом: новые списания по статьям
не применяются к старым событиям молча.

Миграция 16 → 17 сохраняет реальные savings v16 в `savingsBalance`,
а needs+wants+reserve+unallocated — в `availableBalance`. Открытый старый
черновик переносится на доступную базу, его savings-намерение начинается с нуля
без обнуления уже накопленных денег. Нового подарка, повторного дохода,
сброса дня или события миграция не создаёт.

Текущий перенос 19 → 20 выполняется один раз и сохраняет оба реальных
баланса, прежнее намерение в истории и исходный снимок в аудите. Допустимый
активный черновик сохраняется; иначе создаётся MIGRATION/ALLOCATION на реальные
доступные монеты без начисления и без снятия копилки. При нулевом остатке
текущие статьи обнуляются без обязательной сессии. Расходы по разнице
не реконструируются.
Технические версии: Room 20, формат снимка 4. Детали хранения описаны в
[контракте сохранения](room-persistence.md).

## Сохранение игровой семантики при экономике — D-144, 2026-09-22

При одинаковой доступности оплаты сохраняются прежние силы, шаги, питание, вещи,
решения, порядок событий и переносы. Проверка minimumEnergy использует новые
денежные операции, но прежний алгоритм выбора доступных по силам вариантов.
Предварительный расчет не пишет состояние и выбирает ту же следующую карточку.
Перераспределение меняет экономику и ревизию, сохраняя незавершенный экземпляр.
Открытие бюджета не требует PauseEvent или AcknowledgeResult: стадия остается
сохраненной до подтверждения бюджета, после чего исходный сценарий продолжается.
[Аудит кода и пределы проверки](economy-regression-review.md).

## Финансовые периоды, практика и аналитика — 2026-09-24

По FINANCE-D-001–FINANCE-D-006 выбранная большая цель связывается с финансовым
периодом; недели и дни находятся внутри него. Выбор следующей цели открывает
следующий период, финал главы закрывает текущий. До выбора цели обычные
события и история операций сохраняются, но вымышленный завершённый период
не создаётся. Onboarding предлагает четыре накопительные подцели первой главы (CAMPAIGN-D-001).

По FINANCE-D-009 новый период проверяет питание, повторяемые самостоятельные
накопления и реальный разбор плана по категориям. Временный конфиг требует
пополнения в двух разных игровых днях после разных поступлений; дробление и
круговые переводы не создают регулярность. При нехватке практики доступен
явно учебный разбор без выдачи вымышленного финансового успеха родителю.
Соблюдение плана проверяется по известным фактам; при превышении требуется
разобрать разницу и подтвердить реалистичный новый план. Адаптация после
неожиданной траты или дохода не является автоматическим провалом.
Если при собранной цели и сюжетных предпосылках этапы не пройдены, финал
возвращает `FinancialPracticeRequired`; карточка показывает «К практике».
Сохранённые уже начатые периоды отмечаются импортированными и не получают
задним числом обязательный новый барьер. Возраст меняется при успешном финале
по последовательности CUB → TEEN → TEEN → ADULT → SENIOR.

Это технические условия игрового продвижения, **не численные пороги освоения
12 навыков**. Родительские уровни и достаточность повторений не утверждены.
Правила наблюдений описаны в
[контракте FIN-01–FIN-12](requirements/financial-analytics-coverage-2026-09-24.md).

Одно финансовое действие атомарно сохраняет агрегат, историческую команду,
денежные операции и связанные факты. История переживает смену дня. Повтор
того же идентификатора команды не должен давать вторую награду. Переводы
отделены от трат. Открытие экрана или готовая цифра не являются самостоятельным
свидетельством навыка; важны показанные условия, доступная альтернатива,
ответ/действие и помощь.

Показанная информация финансового события связывается с конкретными
occurrence/revision; невидимые или устаревшие сведения не делают контекст полным.
По FINANCE-UI-D-019 карточка булочки не повторяет сводку доступных монет,
накоплений и еды до следующей недели. Она сохраняет цену и фактические источники
списания в понятной фразе. Это не факт показа отсутствующей полной сводки.
У булочки только покупка и «Пройти мимо»; обычный отказ не означает FIN-06
или намерение сохранить деньги на цель. Там, где другой сценарий предлагает
отдельное явное решение «Хочу позже — сначала цель», приоритет учитывается
только после выбора; дальнейшая история проверяется отдельно.
«Заработать на цель» связывает принятие дела с выбранной целью; максимальная
награда остаётся возможной, а ловкость и скорость мини-игры не становятся
финансовыми успехами.

Для FIN-09 карточка с доступными альтернативами «монеты / силы» может показать
конкретное незавершённое дело со сроком до конца сегодня, обе цены/затраты
и checkbox «Хочу оставить силы на …». Предложение появляется только если
после хотя бы одного допустимого выбора движок допускает это дело. Намерение
не выбирается автоматически, передаёт идентификатор реального предложения
и сбрасывается при смене события или ревизии. Без явного приоритета вывод
остаётся нейтральным; интерфейс не придумывает будущий план ребёнка.

## Родительские награды — 2026-09-27

По PARENT-REWARD-D-001 родитель может подарить монеты или аксессуар. В текущем
этапе подготовлен [сетевой контракт](../backend/parent-rewards.md); исполняемый
переход начисления ещё не подключён. Предлагается отдельная атомарная операция
над актуальным сохранением с проверкой прохождения и квитанции награды.
По PARENT-REWARD-D-002 доставка проектируется без Firebase Messaging, через
получение серверного журнала при возвращении связи/приложения и повторные
проверки. Поздняя награда применяется в момент получения к текущему миру:
прошедшие дни, покупки и решения не пересчитываются. Запись о выдаче на сервере
сама по себе не меняет деньги на офлайн-клиенте. Результат родительского действия не считается
самостоятельным заработком ребёнка или свидетельством освоения навыка.

## Хроноскоп и границы сетевой части — 2026-09-24

По FINANCE-D-007 машина времени работает с копией прошлого состояния:
выбор доступной альтернативы → повтор сохранённой последовательности →
сравнение → один вопрос → объяснение → возврат в настоящее.
Ровно один вопрос после сравнения закреплён FINANCE-UI-D-001, 2026-09-25.
Повторная попытка относится к тому же вопросу, а не открывает серию вопросов.
По LEARNING-D-002 от 2026-09-27 способность видеть другие пути доступна с начала
игры. Вход «А что, если…» находится в итогах дня; сюжетная находка прибора
не является условием доступа. Поддержанные воспоминания — оплаченные
необязательные покупки и ремонт с доступной тогда самостоятельной работой. Еда исключена.
Текущий базовый UI сравнивает один изменённый выбор до конца сохранённой
части того же дня. Если прежний следующий шаг больше недоступен, расчёт
останавливается и объясняет границу. Несовместимый контент или отсутствующая
история не заменяются выдуманным исходом.

Реальные деньги, вещи, сюжет и награды от пересчёта не меняются. Сохраняется
учебный ответ с признаком симуляции и поддержки. Просмотр готового сравнения
сам по себе не доказывает FIN-11/12, раскрытый ответ не выдаётся за
самостоятельное решение. Ежедневный обязательный квиз не вводится.
[Контракт пересчёта](requirements/time-machine-contract-2026-09-24.md).

## Повседневные ситуации и экипировка — 2026-09-24

По BALANCE-D-001–002 новые планы используют весь адаптированный пул из25
непредвиденных трат Figma,5 покупок Figma и3 временных предложений существующих
аксессуаров. Планировщик учитывает предпосылки и историю действительных показов,
паузы между повторами, одну новую неожиданную ситуацию за день и промежуток
между обязательными расходами. Перенесённый остаток имеет приоритет и не теряется.
Настройки частоты и соответствия исходников описаны в
[каталоге адаптации](requirements/everyday-balance-2026-09-24.md).

PauseEvent для неожиданной ситуации откладывает её до завтра без исполнения,
списаний и выдачи награды. Уже показанная критическая проблема удерживает лор,
но не блокирует дела, бюджет и заботу о питомце. До первого показа PENDING-событие
не создаёт поломку. Покупка еды внутри карточки удовлетворяет дневной приём пищи,
не возвращая силы. Покупка аксессуара сохраняет владение; SetPetLook проверяет
владение и ожидаемый текущий lookId. Надевание/снятие не тратит деньги или шаг.
Цвет/возраст/эмоция сохраняются, взрослые и детские варианты берутся из каталога.

Офлайн-источник состояния — Room. В коде есть локальные снимки, проверяемое
восстановление и очередь исходящих записей. HTTP-доставка, серверный перенос,
код устройства, родительский UI и подтверждение домашних подарков ещё не
подключены. Их наличие в целевом описании не означает готовую сетевую функцию.

## Накопительная подцель — CAMPAIGN-D-001, 2026-09-24

SelectSavingGoal(goalId, itemId, firstDay?) проверяет текущую главу, принадлежность
предмета комплекту и отсутствие покупки. При первом выборе активирует главу,
открывает её план периода и при необходимости готовит день без открытия события.
В активной главе меняет только selectedSavingItemId и revision. Денег/шага нет.
BuyGoalItem требует этот предмет выбранным; по ADVENTURE-D-018 списывает цену
из копилки и недостающую часть из текущих статей,
добавляет OwnedItem и шаг, очищает selectedSavingItemId атомарно. Все прежние
ограничения еды, подтверждений и ревизии сохраняются. Повторный запрос через
агрегатный репозиторий остаётся идемпотентным; новая покупка уже имеющегося
предмета блокируется. После финала selectedGoalId и selectedSavingItemId пусты,
следующая доступная глава определяется её финальным событием.

В старых сохранениях completedGoalProjects остаётся историей того, какой
проект был завершён. Она не определяет, какую главу пропустить. Согласование
при prepare/restore сохраняет покупки, решения и текущий финансовый прогресс,
не назначает подцель за игрока. Точные правила миграции —
[в текущей модели кампании](campaign-choice.md#код-ui-и-хранение).


### Уточнения подачи событий — 2026-09-26

По ADVENTURE-D-004 подпись состояния NORMAL не показывается; другие подписи
эмоций и потребностей остаются проекцией сохранённого состояния. Карточка
при подтверждении не меняет размер и не исчезает до завершения навигации.
Это локальное поведение UI, не дополнительная стадия игрового события.

Правила D-062/D-076/D-079 остаются: предложенное дело можно выполнить позже
в пределах исходного включительного срока. Исправлен ошибочный флаг удаления
предложения настройки телескопа при «Не сейчас» для исходного и balance-v2
контента. Пауза мини-игры не выдаёт награды, не продлевает срок и при новом
открытии начинает раскладку заново. Сюжетные работы сохраняют существующую
паузу и перенос лора, без нового искусственного срока истечения.


### Первое распределение и владение аксессуаром — 2026-09-27

CUST-D-022: после бюджетного объяснения новый агрегат создаётся сразу с
INITIAL/ALLOCATION. Экран распределения открывается до главного меню, без
повторного RECEIPT; Back и запуск дня сохраняют действующие ограничения
незавершённого бюджета. После подтверждения открывается меню. Выбранные ранее
selectedGoalId и selectedSavingItemId сохраняются; цель заново не выбирается.
Повторное завершение onboarding не перезаписывает существующую игру и не
выдаёт деньги второй раз. Ранее сохранённый RECEIPT остаётся совместимым.

ADVENTURE-D-005: только выбранный стартовый BANDANA/BACKPACK становится
принадлежащим предметом. PLAIN не создаёт предмет. Снятие меняет только образ;
для повторного надевания проверяется владение. Старым сохранениям принадлежность
согласуется технической транзакцией по исходному аксессуару, без изменения
игровых денежных фактов или состава прежних приобретений.

ADVENTURE-D-006: автоматически добавленный generic :skip у открытой лоровой
карточки недоступен. Авторские варианты исхода, условия появления OPTIONAL
сцен и пауза сохраняются. Старые решения остаются в истории. Бесплатные лоровые
действия не показывают и не маркируют как показанный полный денежный контекст.
Покупка показывает только цену и действительные источники оплаты одной фразой;
обычный отказ не записывается как намеренное откладывание ради цели.
