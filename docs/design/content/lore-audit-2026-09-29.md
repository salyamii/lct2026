# Проверка лоровых карточек - 2026-09-29

Проверены 60 карточек (по 12 в каждой из пяти глав) в текущем Design-файле
и все 60 соответствующих карточек FigJam. Это чтение текущего состояния Figma;
скрытые текстовые и графические слои не считаются видимой карточкой.

- [Текущие видимые слои Design](campaign-screens-2026-09-29.figma.json):
  Figma Design `bAod1cKtTX9Q8omQ067q3q`, главы `2363:2`, `2450:2`,
  `2476:2`, `2558:2`, `2574:2`.
- [Текущий FigJam](campaign-lore-2026-09-29.figma.json):
  `mlTEA1hlLJPYf1yRX5a739`; тексты совпадают с предыдущим
  [исходным снимком](campaign-lore.figma.json). Условия, цены и переходы
  из FigJam не изменялись.
- [Проверка сюжетных предметов](../assets/story-object-audit-2026-09-29.json):
  все 42 активных IMAGE hash из каталога `3238:421` совпали с manifest.
  Недостающих или изменившихся предметов в этом каталоге нет.
- [Кепка](../assets/cap-refresh-2026-09-29.json): проверены 12 узлов
  четырёх возрастов и трёх цветов. Обновлены три взрослых варианта;
  два дополнительно выгруженных детских варианта совпали по пикселям.
- [Последующая полная проверка взрослого и старшего героя](../assets/age-runtime-refresh-2026-09-29.json):
  сверены все 90 используемых кадров ADULT/SENIOR. В актуальном взрослом паке
  изменена сама фигура. Обновлены ещё 81 изображение, всего с кепками - 84
  полных экспорта 512 × 512. Шесть оригинальных кадров сна 1024 × 1024 совпали
  по активному Figma IMAGE hash и пикселям каталога; их повторная замена не нужна.

## Отличия видимой копии Design от исходного FigJam

| Карточка | Отличается | Источник |
| --- | --- | --- |
| G1.01 | Текст | [2363:4](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2363-4) |
| G1.02 | Заголовок, Текст | [2371:2](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2371-2) |
| G1.05 | Заголовок | [2386:2](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2386-2) |
| G1.10 | Текст | [2397:206](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2397-206) |
| G1.11 | Текст | [2397:257](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2397-257) |
| G2.04 | Текст | [2451:163](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-163) |
| G2.05 | Текст | [2451:215](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-215) |
| G2.07 | Текст | [2451:319](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-319) |
| G2.10 | Текст | [2451:475](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-475) |
| G3.05 | Заголовок | [2479:56](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2479-56) |
| G3.06 | Заголовок | [2479:74](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2479-74) |
| G3.07 | Заголовок | [2480:2](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-2) |
| G3.08 | Заголовок | [2480:20](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-20) |
| G3.09 | Заголовок | [2480:38](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-38) |
| G3.11 | Заголовок | [2480:74](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-74) |
| G3.12 | Заголовок, Текст | [2480:92](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-92) |
| G4.01 | Текст | [2558:7](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2558-7) |
| G4.12 | Текст | [2558:205](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2558-205) |
| G5.01 | Текст | [2574:7](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2574-7) |
| G5.02 | Заголовок, Текст | [2574:8](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2574-8) |

`CurrentLoreCopy` подключает текущую видимую копию через `EventPresentation`.
Неизменяемые определения событий, выборов и записанная история остаются исходными.
Явно адаптированные игровые тексты `LoreScene.title/body`, условные варианты
`variants` и готовые presentation overrides имеют приоритет. Например, описание
платного пути/обхода G5.02 не заменяется короткой фразой из макета; главы по-прежнему
используют утверждённые требования к снаряжению. Имена героя в импортированной
копии подставляются через `{petName}`.

После сверки проведена редактура понятности по EDITORIAL-D-001: длинные и
неясные фразы, подписи действий и условные описания переписаны через
`EventPresentation`, в том числе `bodyVariants` для восьми условных историй.
Экран сначала проверяет `presentation.bodyVariants`, затем прежние `card.variants`.
`bodyVariants` исключены из fingerprint вместе со всем presentation; legacy variants
остаются неизменными. В `FAST_OR_RELIABLE` убрано обещание
«потерять день», которого нет в фактическом переходе: описание соответствует
выбору маршрута и его действительным затратам. Это правка отображения;
ID, числовые эффекты, immutable definitions и прошлые записи не переписываются.

Изменённые подписи не подтверждают новые продуктовые правила. Текущая озвучка
остаётся из предоставленного пользователем архива; новые аудиофайлы для
обновлённой копии Figma не предоставлялись.
