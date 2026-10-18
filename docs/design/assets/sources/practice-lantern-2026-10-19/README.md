# Целый походный фонарь — 2026-10-19

Создан встроенным image_gen по просьбе пользователя для практических игр.
Тёмно-синий металл, латунная защита, кожаная ручка, целое стекло и тёплый свет.
Это самостоятельная генерация, не экспорт Figma и не вариант разбитого фонаря.

- [PNG-оригинал](prop_practice_lantern.png).
- [Точный промпт](prompt.txt).
- [Android WebP](../../../../../app/src/main/res/drawable-nodpi/prop_practice_lantern.webp).
- Полный прозрачный холст без обрезки и масштабирования. Lossless exact WebP,
  Pillow 12.3.0, method=6; RGBA сверено с PNG побайтно.

Используется для PracticeItem.LANTERN и иллюстрации заимствованного фонаря
в FinancialTrapScreen. GameArtwork сохраняет существующие bounds и ContentScale.
Другие варианты фонарей остаются отдельными ресурсами.
