# Текущее состояние игры — 2026-09-19

Единственный источник сохранённой игры — полный неизменяемый `GameState` из
Room через `GameRepository`. `GameSession` предоставляет прикладной доступ к
этому же репозиторию, без второго изменяемого хранилища или собственной копии.
Hilt создаёт одну сессию, репозиторий и базу на приложение.

Путь чтения: Room → GameRepository → GameSession → ViewModel → UiState → экран.
Путь действия: UI callback → ViewModel → GameSession.dispatch → GameEngine →
GameRepository.commit → одна транзакция Room со снимком, чеками и фактами.
Наблюдатели получают новый снимок. Отклонённая попытка записывается отдельно,
без изменения денег и прохождения.

## Какие значения использовать

| Значение | Источник |
| --- | --- |
| Имя, возраст | `game.pet.name`, `game.pet.age` |
| Доступные монеты | `game.economy.availableBalance` |
| Реальные накопления | `game.economy.savingsBalance` |
| Все деньги вместе | `game.economy.balance`, сумма двух счетов; не сумма, доступная для обычной покупки |
| План бюджета | `game.economy.plan` — план, не отдельные кошельки |
| Черновик плана | `game.economy.planning?.draft`; изменение не переводит деньги |
| Финансовый период и версии планов | `game.financial.currentPeriod`, `.periods`, `.plans` |
| Силы | `game.engine?.energy`; полная шкала — `session.catalog.rules.fullEnergy` |
| Поел сегодня | `game.engine?.ateToday` |
| Шаги, день, фаза | `game.engine?.steps`, `.day`, `.phase` |
| Может ли выполнить действие | `session.engine.blockReason(game, command)` |
| Текущее событие | `game.engine?.currentEvent`, вычисляется из экземпляров событий |
| Доступные дела | `session.engine.availableDeeds(game)`; срок хранится в предложении |
| Сюжетные решения, вещи, внешний вид | `game.story.decisions`, `.ownedItems`, `.pet` |
| Итоги завершённого дня | `session.engine.daySummary(game)` |

`GameState.satiety` и `fatigue` — прежние значения модели, сохранённые без
переинтерпретации для совместимости. Подключённый цикл и его UI не используют их
для голода или сил. Голод вычисляет движок из `ateToday`, `steps` и правил действия;
не нужно заводить свой `isHungry` в каждой feature. Подпись «Ещё не ел» не означает
блокирующий голод: до порога можно действовать. `engine == null` означает, что
игровой день ещё не начат; чтение не должно автоматически начинать его.

## Подписка из ViewModel

Получить `GameSession` через constructor injection. В lifecycle-aware entry
наблюдать готовый `UiState`, а экрану передавать значения и callbacks.

```kotlin
viewModelScope.launch {
    try {
        session.prepare() // Идемпотентная подготовка; существующую игру не заменяет.
        session.observe().collect { snapshot ->
            val game = checkNotNull(snapshot)
            mutableUiState.value = mapToUiState(game)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // Показать состояние ошибки и дать повтор, без подстановки новой игры.
    }
}
```

```kotlin
// В navigation entry; Screen не знает о репозитории или навигации.
val state by viewModel.uiState.collectAsStateWithLifecycle()
Screen(state = state, onAction = viewModel::onAction)
```

Подписка читает согласованный агрегат после изменений любой входящей в него
таблицы. Она не гарантирует доставку каждого промежуточного перехода и не
заменяет журнал событий. Несколько подписчиков допустимы; это не несколько
источников правды. `saved`/`game` внутри ViewModel — только последний наблюдаемый
снимок. `busy`, открытый диалог и ошибки — временное состояние представления.
Подсказки предыдущего действия сбрасываются, когда игровой снимок меняется.

## Получить текущий снимок один раз

```kotlin
viewModelScope.launch {
    val game: GameState? = session.read()
    // Один свежий согласованный снимок из Room; null, если сохранения пока нет.
}
```

`read()` — suspend, не требует подписки, не возвращает кэш экрана и не запускает
события. Если вызывающий код отвечает за подготовку новой игры, сначала вызвать
`session.prepare()`. Само чтение не инициализирует сохранение и не подавляет
ошибки хранения. На уровне data/domain доступен тот же контракт
`GameRepository.read()` / `observe()`; feature достаточно GameSession.

## Изменить игру

```kotlin
val game = session.read() ?: return
val result = session.dispatch(
    EngineRequest(
        id = UUID.randomUUID().toString(),
        expectedRevision = game.engine?.revision,
        command = EngineCommand.Feed(mealId),
    ),
)
// Обработать Applied / Blocked / ошибку записи.
```

Снимок может устареть сразу после `read()`. Поэтому `dispatch` внутри write-
транзакции повторно читает текущее состояние, сверяет revision и проверяет
доступность команды. Предварительный `blockReason` нужен для UI, не заменяет
эту проверку. При StaleRevision следует обновить UI; нельзя молча перезаписать
новое состояние копией старого или автоматически повторить любой выбор.

Нельзя менять `UiState.coins`, выполнять `repository.update { cachedGame.copy(...) }`
или записывать результат мини-игры прямо в баланс. Команда CompleteDeed применяет
весь исход вместе. Результат Applied тоже содержит сохранённый снимок для
немедленного ответа UI; источник остаётся тот же репозиторий.

## Финансовые действия, история и аналитика — 2026-09-24

`DepositSavings(amount)` действительно перемещает деньги в накопления.
`WithdrawSavings(amount, confirmed = true)` вызывается только после отдельного
подтверждения. `ConfirmBudget` сохраняет новую версию намерения; ничего не
переводит. `BuyGoalItem` оплачивается только из накоплений. Все команды проходят
через тот же `dispatch` и сохраняют один результат атомарно.

```kotlin
val history = session.history()       // Отдельное разовое чтение журнала.
val historyFlow = session.observeHistory()
val profiles = session.skillProfiles() // Все FIN-01–FIN-12, факты и достаточность данных.
val backup = session.exportSnapshot()  // Снимок + история + версии + checksum.
```

Журнал не входит в горячую подписку игрового экрана. Профили пересчитываются из
фактов; не записывайте проценты навыков в `GameState`. Отсутствие подходящего
эпизода не означает слабый навык. Численные пороги освоения ещё не утверждены.
`DecisionContext` передаёт реально показанную информацию и помощь. Один клик или
неполное старое сохранение не дают права объявить контекст полным.

Машина времени доступна через `session.timeMachine.availableDecisions()` и
`simulate(TimeMachineRequest(...))`. Она использует чистые переходы на копии;
альтернативные деньги не сохраняются в реальную игру. `quiz` строится только
по проверенному пересчёту, `submitQuiz` сохраняет реальный ответ отдельно.
Повтор после неопределённого результата записи должен сохранить `submissionId`.

Подробные границы, источники навыков и контракт передачи на сервер:
[реализация финансового цикла](financial-engine-implementation.md).

## Границы текущей реализации

Раскладка мини-игры и её ошибки живут в отдельном состоянии партии с
SavedStateHandle. Это ещё не глобальная награда или расход сил. Только после
завершения CompleteDeed изменяет GameState; тренировки этого не делают.

Счётчик первой большой цели в меню и покупки её частей уже вычисляются из
сохранения и каталога по D-087–D-089; см. [экран цели](first-goal.md).
Пять актов лора и выбор проектов подключены по D-103; свободные перемещения
по карте остаются отдельной задачей.
Старые поля модели и исходные авторские документы не удалены; при их дальнейшем
развитии требуется явная политика совместимости, а не второй расчёт сил/голода.

Проверка этого изменения: сборка приложения и AndroidTest APK, компиляция JVM-
тестов. Добавлены исходники проверок устаревшего предупреждения о голоде,
отсутствия второй кнопки, атомарной выплаты, выхода, повтора и восстановления
завершённого дела. Приложение и тесты не запускались по указанию пользователя.

## Имя и возраст питомца

И при подписке, и при разовом `session.read()` используются `game.pet.name`
и `game.pet.age`. Собственный кэш имени/возраста не нужен. Экран получает имя
в UiState, изображения выбираются с учётом возраста и визуального состояния.

```kotlin
val game = session.read() ?: return
val text = renderPetText("{petName} готов продолжить день.", game.pet.name)
val result = session.dispatch(EngineRequest(
    id = UUID.randomUUID().toString(),
    expectedRevision = game.engine?.revision,
    command = EngineCommand.RenamePet(name = enteredName, expectedName = game.pet.name),
))
```

В главном меню имя только отображается (D-094); приведённая команда остаётся
доступна для будущего сценария ввода имени. При подключении редактора
`expectedName` нужно фиксировать при его открытии, а не при сохранении старого
черновика: чужое переименование отклоняется даже до начала первого дня.
Команда проверяет и сохраняет новое имя; UI обрабатывает Applied/Blocked/ошибку.
`{petName}` подставляется только при показе, имя пользователя повторно как шаблон
не обрабатывается. Склонять произвольные имена автоматически не нужно.
Подстановка делает один проход по исходной строке без регулярных выражений.
В общем файле проверки имени нет статической инициализации шаблонов, способной
прервать создание PetState ещё до открытия экрана.
Возраст меняется внутри движка по завершении главы (D-110); UI его только читает.


## Общая история и проекты

```kotlin
val snapshot = session.read() ?: return
val story = session.catalog.storyProgress(snapshot)
val currentAct = story.currentAct
val knownFacts = story.facts
val goal = session.catalog.goals.selectedGoal(snapshot)
val completedProjects = snapshot.completedGoalProjects
```

При подписке те же проекции строятся для каждого нового snapshot из observe().
Не сохраняй отдельный кэш фактов/счётчиков в другом репозитории. Карточки и
финалы проверяют условия повторно при dispatch; просмотр проекции не даёт
гарантии, что устаревшая команда пройдёт. [Полная модель](campaign-choice.md).

## Цвет питомца

Подписка session.observe() и разовый session.read() возвращают одинаковый
GameState.pet с name, age, color, selectedLookId и visualState. Команду выбора
цвета можно подключить к будущему экрану настройки:

```kotlin
val game = session.read() ?: return
val result = session.dispatch(EngineRequest(
    id = UUID.randomUUID().toString(),
    expectedRevision = game.engine?.revision,
    command = EngineCommand.SetPetColor(
        color = PetColor.SAND,
        expectedColor = game.pet.color,
    ),
))
```

expectedColor фиксируется при открытии редактора, как expectedName. Новый цвет
доступен через Applied.state.pet.color и очередной снимок observe().
Команда не тратит монеты, силы или шаг. UI обрабатывает Applied/Blocked/ошибку.
Цвета: COPPER — медный, SAND — песочный, DARK_RUSSET — тёмно-рыжий.
Возраст не нужно записывать вручную: главы 1/2/3/4/5 дают CUB/TEEN/TEEN/ADULT/SENIOR.
Общий адаптер core/ui/game/PetArtwork выбирает ресурсы по сохранённым age и color;
состояние/аксессуар выбирают нужный вариант внутри этого набора.
