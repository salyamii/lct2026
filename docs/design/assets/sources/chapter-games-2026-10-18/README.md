# Изображения для игр первой главы

Созданы встроенным imagegen 2026-10-18 для CHAPTER-GAMES-D-001.
Точные запросы, локальные исходники генератора и изображения-образцы:
[prompts.json](prompts.json). Это генерации, не экспорты Figma.

| Ресурс | Назначение | Размер |
| --- | --- | --- |
| `story_game_telescope_tripod` | Отдельный штатив с креплением | 1254 × 1254 |
| `story_game_telescope_tube` | Отдельная труба | 1254 × 1254 |
| `story_game_telescope_lens` | Съёмный окуляр | 1254 × 1254 |
| `story_game_wheel` | Целое фронтальное колесо, игровые сектора выделяются в Compose | 1254 × 1254 |
| `story_game_boat` | Лодка сверху с лисёнком | 1254 × 1254 |

PNG-оригиналы лежат рядом. Ресурсы приложения:
`app/src/main/res/drawable-nodpi/<resource>.webp`.
Полные полотна и альфа сохранены без обрезки, масштабирования или перекрашивания;
детали телескопа размещаются в квадратных bounds на общей сцене.
WebP: Pillow 10.2.0, `lossless=True, exact=True, method=6`.
Декодированные размеры и все RGBA-байты совпадают с PNG. Размеры файлов,
SHA-256, RGBA SHA-256, alpha bounds и происхождение:
[manifest.json](../../manifest.json).

Образец телескопа: `story_telescope_parts`, [Figma 3238:477](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=3238-477).
Образец колеса: `event_broken_wheel`, [Figma 2164:86](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2164-86).
Лодка создана по текстовому описанию. Исходные изображения не заменялись.
