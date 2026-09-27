# Контракт для backend

Обновлено **2026-09-27**. Базовый адрес: `https://fin-api.mortypython.ru/`.
Android уже вызывает семь методов ниже. Backend должен реализовать эти тела и
ответы; совместимость текущего сервера не подтверждена — предыдущие проверки
из среды разработки завершились тайм-аутом. [OpenAPI](openapi.yaml).

## Идентификатор и повтор запросов

Каждый запрос Android содержит `deviceId` в JSON-теле. Авторизации, Bearer,
паролей, секретов и отдельного `installationId` в этом контракте нет.
QR содержит ровно сохранённый `deviceId`, обычным текстом, без ссылки и срока
действия. На новых установках это Android ID; существующий сохранённый UUID
остаётся прежним ID для совместимости. Backend принимает ID как непустую строку,
а не требует UUID. Подробнее: [идентичность](README.md).

Тела и ответы: `Content-Type: application/json`. Четыре операции записи
(регистрация, snapshot, аналитика, ACK) также передают `Idempotency-Key`.
Это ID операции для защиты от повторного выполнения при сетевом сбое, не токен
доступа. Область ключа — HTTP-метод, маршрут и deviceId. Одинаковый ключ с тем же
телом возвращает прежний результат; другое тело — `409 IDEMPOTENCY_CONFLICT`.
Для snapshot ключ равен `uploadId`, для аналитики — `batchId`.
Читающие POST не требуют этого заголовка и не меняют игровой мир.

`gameRunId` обозначает конкретное прохождение, чтобы награда или аналитика не
попали в другую игру. Поле `profileId` в существующих ответах подарков содержит
то же значение, что `deviceId`; отдельный профиль создавать не нужно.

## 1. Регистрация питомца

`POST https://fin-api.mortypython.ru/api/pets`

**Когда:** после создания локального мира перед синхронизацией и при показе
кода для родителей, пока сервер не подтвердил регистрацию. Питомец и ключ
заморожены до успешного ответа; переименование далее передаётся в snapshot.

```json
{
  "deviceId": "9f1c2d3e4a5b6078",
  "pet": {
    "name": "Рыжик",
    "age": "CUB",
    "color": "COPPER",
    "temperament": "Curious",
    "selectedLookId": "PLAIN"
  },
  "schemaVersion": 1
}
```

Ответ `201`, при повторе `200` или исходный сохранённый `201`:

```json
{"deviceId":"9f1c2d3e4a5b6078"}
```

Сервер возвращает переданный ID. Повтор регистрации того же устройства не
создаёт второго питомца и не сбрасывает сохранённый мир.
[Полный запрос](examples/register-profile.json),
[ответ](examples/register-profile-response.json).

## 2. Отправка полного snapshot

`PUT https://fin-api.mortypython.ru/v1/profiles/snapshot`

**Когда:** синхронизация обнаружила изменения мира или журнала, ещё не принятые
сервером. Первый принятый snapshot также регистрирует gameRunId для подарков.

**Тело:** `deviceId`, `uploadId`, `expectedServerRevision`, `gameRunId`,
`throughHistorySequence`, `currentContentFingerprint`, `snapshotFormatVersion`,
`checksum`, `snapshotJson`, `schemaVersion`.
[Полный запрос](examples/snapshot-upload.json),
[ответ](examples/snapshot-upload-response.json).

Ответ `201` для первой записи / `200` для последующих: `uploadId`, `gameRunId`,
`serverRevision`, `checksum`. Первая ревизия — 1; принятая новая запись увеличивает
её на один. Повтор прежней операции ревизию не увеличивает.

- `expectedServerRevision=null` означает отсутствие серверного архива.
- Иначе ожидаемая ревизия должна совпасть с текущей; конфликт —
  `409 SNAPSHOT_REVISION_CONFLICT`, без замены архива.
- `snapshotJson` хранится и возвращается **неизменённой строкой**. Внутри полный
  текущий мир, журнал, история решений и квитанции подарков. Сервер не сортирует
  массивы, не округляет числа и не пересобирает JSON.
- Архив и его новая ревизия сохраняются атомарно. Клиент не заменяет свой
  более свежий мир серверным при обычной синхронизации.

Как получить настоящее тело в Android:

```kotlin
val body = snapshotUploadRequest(
    deviceId = identity.deviceId,
    snapshot = session.exportSnapshot(),
    uploadId = UUID.randomUUID().toString(),
    expectedServerRevision = null, // только при отсутствии серверной копии
    currentContentFingerprint = session.contentFingerprint,
)
val bodyJson = BackendJson.encodeToString(body)
```

[Формат и проверка архива](world-snapshot.md).

## 3. Получение snapshot

`POST https://fin-api.mortypython.ru/v1/profiles/snapshot/download`

```json
{"deviceId":"9f1c2d3e4a5b6078","schemaVersion":1}
```

**Когда:** сверка неизвестной серверной ревизии и действие восстановления в
настройках. Ответ `200`: `gameRunId`, `serverRevision`,
`currentContentFingerprint`, `snapshotJson`, `schemaVersion`.
Если архива нет — `404` с `{"code":"SNAPSHOT_NOT_FOUND"}`.
[Запрос](examples/snapshot-download-request.json),
[ответ](examples/snapshot-download-response.json).

Скачивание только показывает предпросмотр. Отдельное подтверждение восстанавливает
проверенный архив, если локальная история после предпросмотра не изменилась.
Перенос идентификатора через родительское приложение ещё не реализован в Android.

## 4. Получение подарков

`POST https://fin-api.mortypython.ru/v1/profiles/rewards/pull`

```json
{
  "deviceId":"9f1c2d3e4a5b6078",
  "gameRunId":"73d831fd-cf83-4a8c-9e99-973fe16fef4e",
  "afterSequence":0,
  "limit":50,
  "schemaVersion":1
}
```

**Когда:** каждый проход синхронизации после регистрации прохождения. Это
проверка по запросу клиента; Firebase Messaging не используется.
Ответ `200`: `profileId` (равен deviceId), `gameRunId`, `rewards`,
`nextAfterSequence`, `hasMore`, `schemaVersion`.
[Запрос](examples/pull-parent-rewards-request.json),
[ответ](examples/parent-rewards-response.json).

Журнал неизменяемый, sequence непрерывен с 1 для каждого устройства/прохождения.
Страница начинается после afterSequence; limit — 1…100, Android запрашивает 50.
nextAfterSequence — номер последней записи либо исходный курсор пустой страницы.
У пустой страницы hasMore=false. Незарегистрированное прохождение —
`409 GAME_RUN_NOT_REGISTERED`. Записи остаются доступными после ACK, поскольку
восстановленный старый архив может ещё не содержать их применения.

Поддерживаемые payload: `{"type":"COINS","amount":20}` и
`{"type":"ACCESSORY","itemId":"cosmetic-explorer-hat-v2"}`.
**Текущее применение:** новые известные аксессуары добавляются без автоматического
надевания. Новые монетные награды, неизвестные и повторные аксессуары пока ждут
без ACK; бюджетная политика монет и дубликатов ещё не согласована.
Просрочки наград нет. Это не отменяет загрузку и применение следующих подходящих
аксессуаров. [Полные правила](parent-rewards.md).

## 5. Подтверждение подарков

`POST https://fin-api.mortypython.ru/v1/profiles/rewards/ack`

**Когда:** только после атомарного сохранения эффекта, grant и receipt в Room,
а также при повторной доставке уже сохранённой квитанции после сетевого сбоя.

**Тело:** `deviceId`, `gameRunId`, `receipts`, `schemaVersion`.
В receipt: `rewardId`, `applicationId`, `historyEntryId`, `historySequence`,
`outcome` (`APPLIED` или `ALREADY_OWNED`).
Ответ `200`: `gameRunId`, `acceptedApplicationIds`, `schemaVersion`.
[Запрос](examples/ack-parent-rewards-request.json),
[ответ](examples/ack-parent-rewards-response.json).

ACK сам не начисляет награду и не удаляет её. Сервер дедуплицирует applicationId.
После восстановления архива до применения подарка новая квитанция того же rewardId
может иметь другой applicationId. Неизвестные/отложенные награды не подтверждаются.
Пример `ALREADY_OWNED` описывает формат, не включает ещё не согласованную политику.

## 6. Передача финансовых навыков

`POST https://fin-api.mortypython.ru/v1/profiles/analytics`

**Когда:** изменились сохранённые данные аналитики. Отчёт строится на той же
границе истории, что полный snapshot.

**Тело:** `deviceId`, `batchId`, `gameRunId`, `throughHistorySequence`, `facts`,
`skills`, `schemaVersion`, `projectionVersion`, `evaluatorVersion`.
В skills все 12 FIN-навыков, счётчики и наблюдения. Это полный отчёт на указанную
границу, поэтому счётчики новой партии нельзя прибавлять к предыдущим.
Сервер дедуплицирует факты и сам вычисляет статусы по своей версии правил.
[Полный запрос](examples/analytics-upload.json),
[ответ](examples/analytics-upload-response.json).

Ответ `200`/`201`: `batchId`, `gameRunId`, `acceptedThroughHistorySequence`,
`acceptedEventIds`, `schemaVersion`. Клиент проверяет границу и принятие всех
отправленных fact.eventId. FIN-коды, типы фактов и наблюдений:
[analytics.md](analytics.md), [схемы](analytics.openapi.yaml).

## 7. Получение оценок навыков

`POST https://fin-api.mortypython.ru/v1/profiles/skills/query`

```json
{
  "deviceId":"9f1c2d3e4a5b6078",
  "gameRunId":"73d831fd-cf83-4a8c-9e99-973fe16fef4e",
  "schemaVersion":1
}
```

**Когда:** после отправки актуальной аналитики. Ответ `200`: `gameRunId`,
`basedOnHistorySequence`, `skills`, `schemaVersion`. В каждой из 12 записей:
`skillId` (FIN-01…FIN-12), `status`, `policyVersion`.
Статусы: `MASTERED`, `PRACTICING`, `NO_DATA`, `HAS_PROBLEM`.
[Запрос](examples/skill-assessments-request.json),
[ответ для демонстрационного мира](examples/skill-assessments-fixture-response.json).
Смена статуса не меняет деньги, сюжет или ответы ребёнка.

## Родитель и расписание обмена

Родительское приложение выдаёт подарок через
`POST /v1/parent-profiles/rewards`: `deviceId`, `gameRunId`, `reward`,
`schemaVersion` в JSON, `Idempotency-Key` для одной выдачи. Авторизации нет.
[Монеты](examples/create-parent-reward-coins.json),
[аксессуар](examples/create-parent-reward-accessory.json),
[ответ](examples/parent-reward.json). Детский Android эту выдачу не вызывает.
Существующий серверный `GET /api/parents/{petId}` использует petId=deviceId;
это отчёт родителя, тоже не вызов детского клиента.

Клиент запускает обмен после локальных изменений с задержкой 5 секунд,
при возвращении в приложение/появлении сети, раз в минуту в foreground и
периодически через WorkManager (15 минут с ограничениями Android).
Ручная синхронизация доступна в настройках. Запросы выполняются последовательно;
без сети игра продолжает сохраняться локально. [Триггеры](client-sync.md).

## Проверяемые примеры

Основные snapshot/analytics JSON сгенерированы настоящими Kotlin DTO и
HistoryCodec из **синтетического минимального мира**. В нём запись INITIALIZED
с sequence=1 и валидный checksum; это транспортная фикстура, не данные ребёнка.
Fingerprint принадлежит отдельному синтетическому каталогу, не bundled-каталогу
приложения. [world-snapshot.json](examples/world-snapshot.json),
[генератор](tools/README.md).
Примеры запросов содержат полные JSON-тела и готовы для разбора на сервере.
