# Контракт финансовой аналитики

Дата: 2026-09-27. Это подготовленный контракт Android → backend для согласования.
Имена `SkillId` и `SkillStatus` переданы пользователем; URL и политика серверной
оценки ещё не подтверждены работающим backend. Kotlin DTO находятся в
[AnalyticsContract.kt](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/backend/AnalyticsContract.kt).

## Что рассчитывает приложение

Приложение сохраняет факты действий и ответов, затем восстанавливает наблюдения
по двенадцати навыкам: факт → эпизод → основание вывода. Оно уже различает
успешное наблюдение, затруднение, нейтральную ситуацию и нехватку контекста,
учитывает помощь, незавершённые эпизоды и повторные ответы.

Android отправляет **факты и вычисленные основания по каждому навыку**, а не
готовое заключение об освоении. `SUPPORTED` у одного эпизода не означает
`MASTERED` у навыка, а один неверный ответ не означает `HAS_PROBLEM`.
Существующая [модель оценки](../design/financial-engine-implementation.md)
не задаёт численных порогов освоения (FINANCE-D-004). Новых порогов этот
контракт не вводит.

Предлагаемый владелец итоговых статусов — backend. Он проверяет факты, применяет
согласованную и версионированную политику, возвращает статусы родительскому
интерфейсу и при необходимости Android. Приложение не превращает отсутствие
серверной оценки в `NO_DATA`: при неподготовленной политике сервер возвращает
ошибку `ASSESSMENT_NOT_READY`, а принятые факты сохраняются.

## Передача

`POST /v1/profiles/{profileId}/analytics`

- `Authorization: Bearer <deviceCredential>`; идентификатор сам по себе не даёт доступа.
- `Idempotency-Key: <batchId>`; значение совпадает с телом запроса.
- `Content-Type: application/json`.
- JSON использует camelCase, точный регистр enum и `_type` для полиморфных фактов.
- `profileId` относится к облачному профилю; `gameRunId` — к прохождению.
  В локальном `GameSnapshot` тот же идентификатор называется `runId`.

`AnalyticsUploadRequest` — **полная проекция** фактов одного прохождения до
`throughHistorySequence`, а не добавочные счётчики. Она формируется из одного
проверенного `GameSnapshot`, поэтому cutoff совпадает с границей backup.
Формат этого архива описан в [контракте сохранения мира](world-snapshot.md).
Для MVP целостный payload проще проверять и повторять; пакетная доставка
крупных историй может быть введена отдельной версией контракта.

| Поле | Тип | Значение |
| --- | --- | --- |
| `schemaVersion` | integer | Версия внешнего контракта, сейчас `1` |
| `batchId` | string | Уникальная идентичность отправки, повтор использует тот же ID и тело |
| `gameRunId` | string | Идентификатор прохождения из сохранения |
| `throughHistorySequence` | int64 | Последняя включённая позиция локального audit; `0` допустим для пустой истории |
| `projectionVersion` | integer | Версия восстановления интервальных фактов, сейчас `4` |
| `evaluatorVersion` | integer | Версия набора правил клиента, сейчас `1`; не является политикой mastery |
| `facts` | array | Полный набор `AnalyticsFact`, включая восстановленные интервальные факты |
| `skills` | array | Ровно 12 `SkillEvidenceDto`, по одному на каждый FIN-код |

Структура фактов уже задана в
[AnalyticsFact.kt](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/analytics/AnalyticsFact.kt).
Ключевые поля: `eventId`, `gameRunId`, `episodeId`, `actionId`, `sequence`,
`detail`, `context`, `mode`, `actor`, `learningContext`, `contextFamily`,
`contentVersion`, `gameRulesVersion`, `schemaVersion`.
Поле `detail._type` определяет конкретный вид, например `optional_purchase`,
`budget_confirmed`, `question_answer`, `saving_movement`.
Контекст несёт реально показанную информацию, доступность альтернативы,
состояние до/после, помощь и игровой день; пропуски не заполняются догадками.

Каждый `SkillEvidenceDto` содержит `skillId`, `observations` и счётчики:
`completedEpisodes`, `supportedEpisodes`, `difficultyEpisodes`,
`neutralEpisodes`, `pendingEpisodes`, `incompleteEpisodes`,
`supportedWithoutGameHints`, `assistedEpisodes`. Это существующие проекции
`SkillProfile`; счётчики пересекаются, их нельзя складывать в общий балл.
Наблюдение сохраняет `sourceEventIds`, eligibility/completion/outcome/reason,
помощь, контексты, измерения и `ruleVersion`. Без фактов сервер не должен
принимать одни клиентские счётчики за доказательство навыка.

Полный валидный пример нового прохождения:
[analytics-empty-request.json](examples/analytics-empty-request.json).

Успешный ответ `200` или `201` (`AnalyticsUploadResponse`):

```json
{
  "schemaVersion": 1,
  "batchId": "5e06cb94-48c8-4a14-9c9c-c0f7c0a40720",
  "gameRunId": "73d831fd-cf83-4a8c-9e99-973fe16fef4e",
  "acceptedThroughHistorySequence": 0,
  "acceptedEventIds": []
}
```

Сервер подтверждает только записанные факты. `acceptedEventIds` содержит их
стабильные ID, включая производные факты; локальная очередь подтверждается
только для совпавших исходных ID. Внутренняя позиция очереди и граница audit
не подменяют друг друга. HTTP `2xx` без подходящего ack не разрешает удалять
локальные данные или очередь. При новом payload создаётся новый `batchId`.

## Идемпотентность и версии

1. Повтор одного ключа и того же тела возвращает тот же ack. Тот же ключ с другим
   телом — `409 IDEMPOTENCY_CONFLICT`.
2. Уникальность исходного факта — `(profileId, gameRunId, eventId)`. Повтор
   одинакового факта не увеличивает счётчики; конфликт содержимого исходной
   audit-записи — `409 FACT_CONFLICT`.
3. Несколько фактов могут иметь один `sequence`, а некоторые audit-записи не
   имеют фактов. Разрыв между sequence фактов сам по себе не ошибка.
4. Проекция хранится отдельно для `(gameRunId, projectionVersion,
   evaluatorVersion, throughHistorySequence)`. Производные факты имеют ID
   `derived:<projectionVersion>:<gameRunId>:<identity>` и могут уточняться при
   увеличении границы истории. Их новое содержимое нельзя считать изменением
   исходного действия: backend заменяет полную проекцию, не суммирует её со
   старой. Проверка неизменности исходных фактов опирается на audit из snapshot.
5. Запоздавшая отправка не понижает текущую серверную границу. Сервер хранит
   новую ревизию отдельно или отвечает `409 STALE_ANALYTICS` с текущей границей.
6. Неизвестные версии/FIN-коды/варианты фактов — явный `422 UNSUPPORTED_SCHEMA`,
   без тихого пропуска. Обновление evaluator требует новой версии, изменение
   порогов итогового статуса — новой `policyVersion`.
7. `DEMO`/`SIMULATION` не являются результатом настоящей игры. Реальный ответ
   на вопрос о другом пути обозначается `mode=REAL`,
   `learningContext=COUNTERFACTUAL`; сам запуск симуляции не даёт успеха.

## Итоговые статусы

`GET /v1/profiles/{profileId}/skills?gameRunId=<runId>` с тем же Bearer-доступом.
Ответ `SkillAssessmentsResponse` содержит границу `basedOnHistorySequence`
и ровно один результат для каждого навыка. Оценка может отставать от последнего
upload; граница позволяет честно показать это. Одна только регистрация профиля
не означает, что статус уже рассчитан.

| Enum `SkillStatus` | Смысл |
| --- | --- |
| `MASTERED` | Навык освоен по согласованной серверной политике |
| `PRACTICING` | Навык в процессе освоения |
| `NO_DATA` | Нет достаточных подходящих данных для оценки |
| `HAS_PROBLEM` | Политика выявила устойчивое затруднение; это не диагноз |

| `SkillId` | Имя enum |
| --- | --- |
| `FIN-01` | `COMPARE_AMOUNTS` |
| `FIN-02` | `PLAN_BUDGET` |
| `FIN-03` | `PRIORITIZE_NEEDS` |
| `FIN-04` | `MAKE_MONEY_LAST` |
| `FIN-05` | `SAVE_FOR_GOAL` |
| `FIN-06` | `DELAY_PURCHASE` |
| `FIN-07` | `BUILD_EMERGENCY_FUND` |
| `FIN-08` | `ADAPT_AFTER_EXPENSE` |
| `FIN-09` | `COMPARE_COSTS` |
| `FIN-10` | `PLAN_EXTRA_INCOME` |
| `FIN-11` | `RECONSIDER_DECISION` |
| `FIN-12` | `UNDERSTAND_INCOME_AND_EXPENSES` |

Иллюстративный ответ со всеми двенадцатью навыками и четырьмя допустимыми
значениями enum: [skill-assessments-response.json](examples/skill-assessments-response.json).
Это пример формы данных, а не оценки конкретного ребёнка или заданных порогов.

Новые DTO не меняют формат старого сохранения, Room и прежние enum исходов
эпизода. История и её checksum не переписываются ради backend-представления.

## Покупка цели из двух счетов — 2026-09-27

По ADVENTURE-D-018 один BuyGoalItem может дать SAVINGS_EXPENSE и
AVAILABLE_EXPENSE. Сумма обеих квитанций — цена предмета; каждая сверяется
со своим счётом. Реальных DEPOSIT/WITHDRAWAL при этом нет. Прямая оплата
текущими деньгами не служит свидетельством фактического накопления.
Формат команды и snapshot не меняется; текущая версия переходов — 9.
