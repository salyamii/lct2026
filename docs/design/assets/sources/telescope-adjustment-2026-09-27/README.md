# Звук настройки малого телескопа

**GENERATED**, 2026-09-27. Основание: [MEDIA-D-006](../../../decisions.md).
Это новый локально синтезированный эффект, не запись из пользовательского архива.
Внешние сэмплы, сервисы и дополнительные зависимости не использованы.

- [Генератор](generate.py): Python 3, только стандартная библиотека, seed `20260927`.
- [Исходник](telescope_adjustment.wav): mono PCM16, 44.1 kHz, 1.250 с.
- [Runtime MP3](../../../../../app/src/main/assets/media/audio/sound_telescope_adjustment.mp3):
  `libmp3lame`, 192 кбит/с, mono 44.1 kHz.
- Пять нерегулярных приглушённых щелчков ручки и тихое трение механизма.
  Атака скруглена на 4 мс, резонансы быстро затухают, в конце есть плавный спад.
  Речи, мелодии и непрерывной музыкальной подложки нет.
- Проверка декодированного MP3: средний уровень −26.8 dBFS, пик −9.3 dBFS;
  клиппинга нет. `silencedetect` при −50 dB не нашёл начальной паузы.
  Короткие промежутки между щелчками и хвост 109 мс сохраняются намеренно.

Воспроизведение: `sound.telescope_adjustment` через `appearanceCueKey` исходного
дела «Настроить малый телескоп»; его `balance-v2` наследует metadata.
Один проход за экземпляр события. Переход от предложения к мини-игре с тем же
occurrence ID не начинает звук заново. Звук подчиняется общему выключателю.
Это пятый короткий эффект в ограниченном предварительном прогреве, без загрузки
длинных реплик в кеш. Текст, награда, расход сил и результат работы не меняются.

Воспроизведение генерации из корня проекта:

```text
python docs/design/assets/sources/telescope-adjustment-2026-09-27/generate.py
ffmpeg -hide_banner -loglevel error -y -i docs/design/assets/sources/telescope-adjustment-2026-09-27/telescope_adjustment.wav -map_metadata -1 -c:a libmp3lame -b:a 192k -compression_level 0 -ar 44100 -ac 1 app/src/main/assets/media/audio/sound_telescope_adjustment.mp3
```

Точные хэши генератора, исходного WAV и runtime MP3 записаны в
[media manifest](../../media-manifest.json). Версия FFmpeg также записана там;
побайтовое совпадение MP3 между разными версиями энкодера не предполагается.
