# Android ↔ backend: профиль устройства, сохранение и награды

Контракт v1, **2026-09-27**. Сервер команды: `https://fin-api.mortypython.ru/`.
Backend реализует JSON из этого пакета. Наличие клиентского кода не подтверждает
совместимость текущего развёртывания: успешный обмен с живым сервером ещё не проверен.

- [OpenAPI](openapi.yaml) и [схемы аналитики](analytics.openapi.yaml).
- [Готовые запросы и ответы](implementation-handoff.md).
- [Места вызова и расписание Android](client-sync.md).
- [Полный архив мира](world-snapshot.md), [аналитика](analytics.md),
  [доставка родительских наград](parent-rewards.md).
- [Генератор проверенных snapshot/analytics примеров](tools/README.md).

## Идентификатор в JSON

Во всех семи запросах Android передаёт `deviceId` **в теле JSON**.
Профиль определяется этой сохранённой строкой. В путях и заголовках детских
методов идентификатора нет; отдельного аккаунта контракт не вводит.

| Значение | Назначение |
| --- | --- |
| `deviceId` | Сохранённая строка AndroidID новой установки; у обновлённого старого клиента — прежний UUID профиля без изменения значения. Backend принимает непустую строку, а не только UUID. |
| `gameRunId` | Отдельный ID игрового прохождения; в snapshot называется `runId`. |
| `profileId` в ответах наград | Сохранённое имя поля совместимости. Значение **равно deviceId**, отдельного аккаунта нет. |
| `requestId`, `uploadId`, `batchId`, `Idempotency-Key` | Идентификаторы запроса/повтора и диагностики. Не обозначают доступ к профилю. |

Android читает `Settings.Secure.ANDROID_ID` при создании новой локальной
идентичности и сохраняет его. Пустое значение даёт явную ошибку, случайный
запасной ID не создаётся. Существующая запись мигрирует с прежним UUID;
повторная регистрация **того же deviceId** сохраняет облачный профиль,
архивы, аналитику и журнал подарков. Backend не создаёт новый профиль и
не стирает старый при таком повторе. Замороженные данные питомца сохраняются;
из-за нового JSON клиент один раз меняет регистрационный ключ повтора.

QR создаётся локально, до регистрации и без сети. Его содержимое — ровно
UTF-8 строка сохранённого deviceId, например [profile-id-qr.txt](examples/profile-id-qr.txt):

```text
9f1c2d3e4a5b6078
```

Это не URL и не JSON; срока действия нет. Другой родитель использует ту же
строку для того же профиля. Android не хранит список родителей и не требует
дополнительного подтверждения сканирования. Встроенный родительский режим по
PARENT-MODE-D-001/002 читает текущую локальную игру; сканирование и
загрузка сессии другого устройства в него не перенесены. По PARENT-MODE-D-003
при входе после PIN он отправляет текущую аналитику и получает серверные
оценки навыков для своего deviceId и gameRunId.

Предоставленная сервером родительская ручка `GET /api/parents/{petId}`
использует `petId=deviceId`; встроенный режим её не дублирует. Имя и баланс
он берёт локально, серверные статусы и policyVersion — из существующего
`POST /v1/profiles/skills/query` после `POST /v1/profiles/analytics`.
Самостоятельный родительский клиент продолжает использовать свою схему отчёта.
Предложенная выдача подарка — `POST /v1/parent-profiles/rewards`, deviceId в JSON.

## Готовность Android и границы переноса

Подключены регистрация, выгрузка snapshot/аналитики, загрузка серверных оценок,
чтение подарков, локальное применение поддержанных наград и ACK. Room остаётся
источником текущей игры. Запросы повторяются из сохранённых неизменных тел;
ручные и фоновые проходы сериализованы. В настройках есть предпросмотр
облачного архива и явное восстановление с проверкой актуальности локального мира.

Новые известные аксессуары применяются без автоматического надевания.
Денежные подарки и новые дубликаты аксессуаров пока ожидают продуктового решения:
они не применяются и не получают ACK. Неизвестные аксессуары также откладываются.

Восстановление мира не меняет deviceId. Автоматическая привязка нового телефона
к чужому сохранённому deviceId и интерфейс ввода прежнего ID сейчас отсутствуют.
После удаления приложения новый клиент читает текущий AndroidID; автоматически
вернуть утраченный legacy UUID он не умеет. Для восстановления нужен уже
загруженный архив под используемым ID. Дополнительных сетевых процедур доступа
этот контракт не вводит; перенос прежнего ID на другой телефон остаётся
не реализованным клиентским сценарием.

## Общие правила HTTP

- Только HTTPS; `Content-Type: application/json`. JSON — camelCase и точный
  регистр enum; `_type` у аналитических фактов, `type` у payload награды.
- Суммы и позиции истории — целые int64, без округления через JS Number.
- Изменяющие операции используют `Idempotency-Key`: регистрация, upload
  snapshot/аналитики, ACK и выдача родительской награды. Читающие POST
  download/query/pull не изменяют мир и не требуют такого ключа.
- Сервер связывает ключ с `deviceId + method + path + неизменное тело`.
  Другой body под тем же ключом — `409 IDEMPOTENCY_CONFLICT`.
- Повтор после timeout использует исходные ключ и body и возвращает прежний
  результат. Snapshot: ключ равен `uploadId`; аналитика: `batchId`.
  Регистрационный request ID сохраняется до подтверждённого ответа.
- Тело регистрации с питомцем заморожено до ответа. Повторная регистрация
  не редактирует питомца; его текущее состояние находится в snapshot.
- Предлагаемый срок кеша HTTP-ответов — минимум 7 суток. Бизнес-уникальность
  выдачи подарка по `(deviceId, Idempotency-Key)` сохраняется весь срок
  журнала: после очистки кеша старый повтор не создаёт второй подарок.
- `429` содержит `Retry-After`; временные 5xx допускают повтор с задержкой.
  Ошибки формы/версии/конфликта не исправляются удалением локальной игры.

## Семь методов детского клиента

| Метод | Request DTO | Основные поля ответа |
| --- | --- | --- |
| `POST /api/pets` | `RegisterProfileRequest(deviceId, pet, schemaVersion)` | `deviceId` |
| `PUT /v1/profiles/snapshot` | `SnapshotUploadRequest`, включая deviceId | uploadId, gameRunId, serverRevision, checksum |
| `POST /v1/profiles/snapshot/download` | deviceId, schemaVersion | gameRunId, serverRevision, currentContentFingerprint, snapshotJson, schemaVersion |
| `POST /v1/profiles/analytics` | `AnalyticsUploadRequest`, включая deviceId | batchId, gameRunId, acceptedThroughHistorySequence, acceptedEventIds, schemaVersion |
| `POST /v1/profiles/skills/query` | deviceId, gameRunId, schemaVersion | gameRunId, basedOnHistorySequence, skills (policyVersion внутри каждой оценки), schemaVersion |
| `POST /v1/profiles/rewards/pull` | deviceId, gameRunId, afterSequence, limit, schemaVersion | profileId (= deviceId), gameRunId, rewards, nextAfterSequence, hasMore, schemaVersion |
| `POST /v1/profiles/rewards/ack` | deviceId, gameRunId, receipts, schemaVersion | gameRunId, acceptedApplicationIds, schemaVersion |

`schemaVersion=1`; размер страницы наград по умолчанию 50, допустимо 1…100.
`afterSequence` обязателен; новый полный обход начинается с 0. Успешные
изменяющие запросы возвращают 200/201, ACK — 200; читающие запросы — 200.
Регистрация отвечает **только deviceId**. Точные поля и ограничения — в OpenAPI.

Источники DTO: [ProfileContract](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/backend/ProfileContract.kt),
[SnapshotContract](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/backend/SnapshotContract.kt),
[AnalyticsContract](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/backend/AnalyticsContract.kt),
[ParentRewardsContract](../../core/game/src/main/kotlin/ru/nksk/lctapp/domain/backend/ParentRewardsContract.kt).

## Гарантии сохранения, аналитики и подарков

Snapshot — полная непрозрачная строка `HistoryCodec.encodeSnapshot`.
Backend хранит и возвращает её без пересборки. `expectedServerRevision=null`
допустим только при отсутствии архива; иначе требуется совпадение ревизии.
Весь архив и новая серверная ревизия публикуются атомарно. Идемпотентный
повтор не увеличивает ревизию. При 409 локальный мир, history, receipts и
ожидающие запросы сохраняются. Автоматического слияния или перезаписи по
времени нет. Сам download не восстанавливает игру.

Аналитика — полная проекция одного run до `throughHistorySequence`, а не
приращение счётчиков. Исходные факты неизменны; производные версии проекции
могут уточняться. Полученные факты и итоговая оценка навыка различаются:
если политика ещё не рассчитана, ответ — `ASSESSMENT_NOT_READY`, не выдуманный
`NO_DATA`. Численных порогов освоения контракт не назначает.

Награды — добавляемый неизменяемый журнал для пары deviceId/gameRunId.
Первый проверенный snapshot регистрирует run; ранее выдача возвращает
`GAME_RUN_NOT_REGISTERED`. ACK не удаляет подарок. Эффект, полный grant,
receipt и audit коммитятся вместе над последним локальным миром, затем
можно отправить ACK. После восстановления старого snapshot журнал снова
доступен целиком, включая ACK-нутые записи; receipt в восстановленном мире
предотвращает повторный эффект. Cursor страницы не является receipt.
Полные правила курсоров, поколений restore и ветвей — в [parent-rewards.md](parent-rewards.md).

## Ошибки и согласуемые лимиты

`BackendError`: `code` обязателен, `message` и `requestId` необязательны.
Клиент принимает решение по HTTP/code, не по тексту message.

| HTTP | Примеры code |
| --- | --- |
| 400 | INVALID_REQUEST |
| 404 | PROFILE_NOT_FOUND, SNAPSHOT_NOT_FOUND |
| 409 | IDEMPOTENCY_CONFLICT, SNAPSHOT_REVISION_CONFLICT, GAME_RUN_CONFLICT, GAME_RUN_NOT_REGISTERED, FACT_CONFLICT, STALE_ANALYTICS, ASSESSMENT_NOT_READY, REWARD_RECEIPT_CONFLICT |
| 413 | PAYLOAD_TOO_LARGE |
| 422 | UNSUPPORTED_SCHEMA, SNAPSHOT_INVALID, INVALID_REWARD, UNKNOWN_ACCESSORY, INVALID_REWARD_CURSOR, INVALID_REWARD_RECEIPT |
| 429 | RATE_LIMITED |
| 5xx | TEMPORARILY_UNAVAILABLE |

Предлагаемые, ещё согласуемые ограничения: 16 KiB для регистрации,
25 MiB распакованного snapshot request, 10 MiB analytics request.
Превышение даёт 413/429; нельзя молча обрезать историю. Серверные retention,
сжатие и пределы подарков согласуются отдельно. Текущий клиентский timeout —
45 секунд; более долгая операция потребует иной версии протокола.

## Пример вызова

Из каталога `docs/backend`; ID в файлах синтетические.

```sh
BASE_URL='https://fin-api.mortypython.ru/'

curl --fail-with-body "${BASE_URL}api/pets" \
  -H 'Idempotency-Key: 0bcab230-62c7-4e57-90b3-bd393f9bf3a5' \
  -H 'Content-Type: application/json' --data-binary @examples/register-profile.json

curl --fail-with-body -X PUT "${BASE_URL}v1/profiles/snapshot" \
  -H 'Idempotency-Key: 851818aa-f937-4a24-b588-00a36b6085a0' \
  -H 'Content-Type: application/json' --data-binary @examples/snapshot-upload.json

curl --fail-with-body "${BASE_URL}v1/profiles/snapshot/download" \
  -H 'Content-Type: application/json' --data-binary @examples/snapshot-download-request.json
```
