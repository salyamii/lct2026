# Музыка глав, озвучка и вступительный ролик - 2026-09-27

[Основной каталог](catalog.md) · [Машиночитаемый media manifest](media-manifest.json) · [Правила событий](../event-authoring.md)

Источник импорта: пользовательский архив `drive-download-20260927T131348Z-1-001.zip`. Архив SHA-256: `693be7c43972f1dd9ccbdcd2f71a8c9379d3542c9b1fe998306c9fb9585f0ea9`. Все импортированные MP3/MP4, кроме одного портового эффекта, сохранены побайтно. `ambient.port` усилен на 10 дБ от исходного `Порт.mp3` и перекодирован в MP3 192 кбит/с при прежних 44.1 kHz stereo; остальные импортированные файлы не обработаны. Контрольные суммы, исходные имена, размеры и сведения ffprobe находятся в manifest.

Импортированы все 94 смысловых аудиоключа и один ролик. Девять точных повторов с одинаковым SHA-256 ссылаются на общий файл: 85 физических MP3 и один MP4, суммарно 46 316 763 байт. Все пять `Глава */Главная Тема.mp3` включены как фоновая музыка приложения для соответствующих глав. Исключённых файлов нет.

Отдельно добавлен **GENERATED** `sound.telescope_adjustment` по MEDIA-D-006:
локально синтезированный механический эффект, 1.250 с. Он не входит в архив.
[Генератор, исходный WAV и параметры](sources/telescope-adjustment-2026-09-27/README.md)
сохранены отдельно. С ним manifest содержит 96 записей: 95 архивных и одну
сгенерированную; runtime - 87 файлов (86 MP3 и один MP4).

## Привязки и воспроизведение

По MEDIA-D-007 существующий `story.chapter_1` также звучит до создания мира:
на выборе персонажа и всех шагах после ролика. На `IntroVideo` фоновая музыка
отключена; собственный звук видео сохраняется с учётом общего выключателя.
Новые файлы и изменения импортированных ресурсов для этого не требуются.

- `story.chapter_1`…`story.chapter_5` заданы через `StoryChapterPresentation` у авторских `LoreChapter`. Компилятор сохраняет ключ в `EventMedia.musicCueKey` каждой сюжетной карточки акта. Приложение выбирает музыку текущего акта через `storyProgress`, поэтому один трек продолжается между меню и другими экранами; смена главы меняет трек. Музыка приглушается под голос и подчиняется общему выключателю звука.
- 60 сюжетных реплик заданы в `LoreScene.narrationCueKey` у авторских сцен. Сохранённая старая версия вступительной «Ночи наблюдений» тоже использует её запись.
- 25 реплик неожиданных событий заданы в `UnexpectedCard.narrationCueKey`. Версии очистки пластины наследуют свою реплику, сохраняя актуальные action ID.
- `sound.payment` находится в `actionAudio` только платных вариантов событий; отказ, ручная работа и бесплатные действия не получают этот звук. Проигрывание не выполняет платежи.
- `sound.purchase_appears` находится в `PurchasePresentation.media.appearanceCueKey`.
- `sound.telescope_adjustment` находится в `appearanceCueKey` только дела «Настроить малый телескоп», включая унаследованную версию `balance-v2`. Один проход за экземпляр события; пятый короткий эффект в ограниченном прогреве.
- `ambient.port` связан с пятью исходными карточками «Короткое дело · Порт» и первым делом «Смотритель просит помочь» у причала; `ambient.butcher_shop` - с пятью карточками «Короткое дело · Мясная лавка». Реплики места не назначаются всем карточкам ярмарки или новым событиям по строковому ID.
- Короткий клип места проигрывается один раз при открытии события (MEDIA-D-010). Рекомпозиции не начинают его заново; бесконечного повтора нет.
- `video.intro_fox` доступен платформенному intro flow через `INTRO_VIDEO_ASSET`.

Ключи разрешаются в `core/ui/media/BundledMediaCatalog`; domain хранит только строки metadata. Ни имя файла, ни проигрывание не меняют игровые правила. Gameplay fingerprint не учитывает `EventCardCopy.presentation`.

У дел «Настроить малый телескоп» и «Линзы любят чистоту» в предоставленном архиве
нет отдельных реплик или звука обсерватории. По MEDIA-D-006 у телескопа теперь
есть отдельный сгенерированный звук настройки; у линз остаётся музыка главы.
«Малый телескоп и старый журнал.mp3» озвучивает сюжетную сцену G1.10 после работы
с телескопом, а не предложение выполнить это дело. Девять связующих сцен N*
также не имеют отдельных записей в архиве. Чужие реплики им не подставляются.
Проверка полноты отдельно учитывает 95 архивных записей и сгенерированные,
сравнивает все записи manifest с путями и контрольными
суммами ресурсов, а озвучку - с исходными событиями и их актуальными версиями.

## Форматы и соответствие исходникам

Обработка `ambient.port`: исходный средний уровень −29.3 dB и пик −17.4 dB;
после `volume=10dB` и качественного `libmp3lame` 192 кбит/с - средний −19.6 dB,
пик −7.6 dB. Клиппинга нет, длительность осталась 2.600 с. Manifest v2 хранит
для этого файла раздельные `source_bytes`/`source_sha256` и
`runtime_bytes`/`runtime_sha256`, а также исходный и итоговый probe/уровни.
У неизменённых ресурсов `bytes`/`sha256` по-прежнему относятся одновременно
к архиву и runtime. Проверка APK должна использовать runtime-поля, если они есть.

ffprobe проверил все 86 импортированных файлов. Короткие клипы: MP3, 44.1 kHz mono/stereo (один клип 48 kHz stereo), длительность 0.484–18.16 с. Пять тем: MP3 48 kHz stereo, длительность 188.454–212.774 с; встроенный JPEG помечен `attached_pic=1` и является обложкой. Ролик: H.264 High / yuv420p, 540 × 960, AAC LC 44.1 kHz stereo, 79.969 с.

Сгенерированный эффект телескопа также проверен: MP3 44.1 kHz mono, 1.250 с,
31 391 байт, средний уровень −26.8 dBFS, пик −9.3 dBFS, без клиппинга и
начальной паузы при пороге −50 dB. Все 87 runtime-файлов занимают 46 348 154 байта.

Имена сопоставлены исходным сюжетным карточкам, включая сокращения («Деталь Хроноскопа», «Меняй только одно», «Первый маршрут»). Записи могут использовать прежнее название истории и фиксированное имя Рыжик; они не синтезируются из текущего имени питомца или изменённого текста UI.

Исходное `Колесо не вращается.mp3` сопоставлено единственной подходящей карточке `Кольцо не вращается` (`2326:400`) как опечатка исходного имени; файл точно совпадает со `Стрелка застряла.mp3`. Исходное имя и факт совпадения сохранены в manifest; игровые названия и правила не изменены. Аналогично `Линза на крыше переколилась.mp3` сопоставлена карточке «Линза на крыше перекосилась».

## Каталог

| Сгенерированный ключ | Происхождение | Runtime | Длительность, с |
| --- | --- | --- | --- |
| `sound.telescope_adjustment` | **GENERATED**, [локальный синтез](sources/telescope-adjustment-2026-09-27/README.md), MEDIA-D-006 | [asset](../../../app/src/main/assets/media/audio/sound_telescope_adjustment.mp3) | 1.250 |

Архивные записи:

| Смысловой ключ | Исходник в архиве | Файл / точный повтор | Длительность, с |
| --- | --- | --- | --- |
| `ambient.butcher_shop` | Мясная лавка.mp3 | [asset](../../../app/src/main/assets/media/audio/ambient_butcher_shop.mp3) | 1.552 |
| `ambient.port` | Порт.mp3 | [asset](../../../app/src/main/assets/media/audio/ambient_port.mp3) | 2.600 |
| `narration.story.blank_region` | Глава 4/На карте появляется пустая область.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_blank_region.mp3) | 17.120 |
| `narration.story.border_beacon` | Глава 4/Пограничный маяк.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_border_beacon.mp3) | 16.240 |
| `narration.story.calibration_station` | Глава 3/Станция калибровки.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_calibration_station.mp3) | 10.480 |
| `narration.story.cargo_journal` | Глава 1/Запись в журнале грузов.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_cargo_journal.mp3) | 10.640 |
| `narration.story.cellar_crate` | Глава 3/Ящик из подвала.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_cellar_crate.mp3) | 16.080 |
| `narration.story.central_hub` | Глава 5/Центральный узел маршрутов.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_central_hub.mp3) | 16.560 |
| `narration.story.change_one` | Глава 3/Меняй только одно.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_change_one.mp3) | 12.320 |
| `narration.story.chronoscope_part` | Глава 3/Деталь Хроноскопа.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_chronoscope_part.mp3) | 12.720 |
| `narration.story.closed_bridge` | Глава 2/Закрытый мост.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_closed_bridge.mp3) | 11.040 |
| `narration.story.coastal_fragment` | Глава 4/Береговой фрагмент.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_coastal_fragment.mp3) | 13.600 |
| `narration.story.crate_mark` | Глава 1/На ящике странная метка.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_crate_mark.mp3) | 8.880 |
| `narration.story.duty_room` | Глава 2/Комната дежурного.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_duty_room.mp3) | 17.360 |
| `narration.story.first_route` | Глава 5/Первый маршрут.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_first_route.mp3) | 13.440 |
| `narration.story.forest_stone` | Глава 4/Лесной путевой камень.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_forest_stone.mp3) | 16.400 |
| `narration.story.fork_symbol` | Глава 2/Знак двойной развилки.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_fork_symbol.mp3) | 9.840 |
| `narration.story.great_expedition` | Глава 5/Большая экспедиция.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_great_expedition.mp3) | 15.040 |
| `narration.story.hall_of_paths` | Глава 2/Хроноскоп.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_hall_of_paths.mp3) | 15.120 |
| `narration.story.house_base` | Глава 3/Дом становится базой.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_house_base.mp3) | 17.920 |
| `narration.story.keeper_seal` | Глава 3/Печать Смотрителей на двери.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_keeper_seal.mp3) | 14.080 |
| `narration.story.kingdom_map` | Глава 4/Карта королевства собрана.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_kingdom_map.mp3) | 15.520 |
| `narration.story.last_keeper_message` | Глава 5/Послание последнего Смотрителя.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_last_keeper_message.mp3) | 14.000 |
| `narration.story.last_relay` | Глава 5/Тико восстанавливает последнее реле.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_last_relay.mp3) | 12.480 |
| `narration.story.last_station` | Глава 5/Луна находит последнюю станцию.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_last_station.mp3) | 14.240 |
| `narration.story.last_tower_cargo` | Глава 2/Последний груз к башне.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_last_tower_cargo.mp3) | 17.280 |
| `narration.story.luna_damaged_map` | Глава 4/Луна приносит повреждённую карту.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_luna_damaged_map.mp3) | 16.560 |
| `narration.story.luna_note` | Глава 4/Лунина старая заметка.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_luna_note.mp3) | 16.000 |
| `narration.story.map_table` | Глава 4/Картографический стол.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_map_table.mp3) | 15.680 |
| `narration.story.marked_plate` | Глава 1/Пластина с тем же знаком.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_marked_plate.mp3) | 10.080 |
| `narration.story.missing_road` | Глава 4/Архивная копия без одной дороги.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_missing_road.mp3) | 17.760 |
| `narration.story.mountain_sign` | Глава 4/Горный указатель двух путей.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_mountain_sign.mp3) | 16.240 |
| `narration.story.network_lights` | Глава 5/Огни сети возвращаются.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_network_lights.mp3) | 13.280 |
| `narration.story.network_shutdown` | Глава 5/Почему сеть закрыли.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_network_shutdown.mp3) | 16.000 |
| `narration.story.new_map` | Глава 5/Новая карта начинается за краем старой.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_new_map.mp3) | 12.480 |
| `narration.story.night_observation` | Глава 1/Ночь наблюдений.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_night_observation.mp3) | 9.360 |
| `narration.story.not_prediction` | Глава 5/Хроноскоп не предсказывал будущее.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_not_prediction.mp3) | 12.080 |
| `narration.story.observation_network` | Глава 1/Ночь наблюдений раскрывает сеть.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_observation_network.mp3) | 17.760 |
| `narration.story.observatory_receiver` | Глава 2/Приёмник указывает на Обсерваторию.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_observatory_receiver.mp3) | 13.520 |
| `narration.story.old_map` | Глава 1/Старая карта.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_old_map.mp3) | 8.720 |
| `narration.story.quiet_tower_expedition` | Глава 2/Экспедиция к погасшей башне.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_quiet_tower_expedition.mp3) | 14.640 |
| `narration.story.recent_repairs` | Глава 2/Следы недавнего ремонта.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_recent_repairs.mp3) | 15.760 |
| `narration.story.relay_station` | Глава 5/Заброшенная релейная станция.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_relay_station.mp3) | 17.520 |
| `narration.story.researcher_house` | Глава 3/Дом исследователя.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_researcher_house.mp3) | 15.360 |
| `narration.story.return_code` | Глава 2/Башня посылает обратный код.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_return_code.mp3) | 12.640 |
| `narration.story.route_archive` | Глава 3/Ящики маршрутов.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_route_archive.mp3) | 14.240 |
| `narration.story.route_comparison` | Глава 4/Хроноскоп сравнивает маршруты.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_route_comparison.mp3) | 18.080 |
| `narration.story.route_token` | Глава 4/Обратная сторона маршрутного жетона.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_route_token.mp3) | 18.160 |
| `narration.story.second_path_scroll` | Глава 2/Свиток второго пути.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_second_path_scroll.mp3) | 16.320 |
| `narration.story.service_emblem` | Глава 1/Старая эмблема службы.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_service_emblem.mp3) | 12.160 |
| `narration.story.signal_post` | Глава 2/Сигнальный пост за мостом.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_signal_post.mp3) | 14.160 |
| `narration.story.stone_post` | Глава 1/Каменный пост.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_stone_post.mp3) | 7.840 |
| `narration.story.storm_shelter` | Глава 5/Запись в штормовом убежище.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_storm_shelter.mp3) | 11.600 |
| `narration.story.telescope_journal` | Глава 1/Малый телескоп и старый журнал.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_telescope_journal.mp3) | 9.680 |
| `narration.story.tester` | Глава 3/Тико запускает тестер.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_tester.mp3) | 16.240 |
| `narration.story.three_answers` | Глава 1/Три ответа - в одном свете.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_three_answers.mp3) | 12.160 |
| `narration.story.three_lenses` | Глава 1/Схема трёх линз.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_three_lenses.mp3) | 8.720 |
| `narration.story.three_lights` | Глава 2/Механизм трёх огней.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_three_lights.mp3) | 10.880 |
| `narration.story.tiko_arrives` | Глава 3/Тико приходит помочь.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_tiko_arrives.mp3) | 15.600 |
| `narration.story.tower_reply` | Глава 1/Башня отвечает.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_tower_reply.mp3) | 7.280 |
| `narration.story.unsigned_letter` | Глава 3/Письмо без отправителя.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_unsigned_letter.mp3) | 12.880 |
| `narration.story.workshop_photo` | Глава 3/Старая фотография.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_story_workshop_photo.mp3) | 14.000 |
| `narration.unexpected.backpack_strap` | Непредвиденные траты/Лямка не выдержала.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_frayed_rope.mp3); alias → `narration.unexpected.frayed_rope` | 1.158 |
| `narration.unexpected.bent_frame` | Непредвиденные траты/Оправа погнулась.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_bent_key.mp3); alias → `narration.unexpected.bent_key` | 1.016 |
| `narration.unexpected.bent_key` | Непредвиденные траты/Ключ погнулся.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_bent_key.mp3) | 1.016 |
| `narration.unexpected.broken_window` | Непредвиденные траты/Окно станции разбито.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_broken_window.mp3) | 1.706 |
| `narration.unexpected.cloudy_lens` | Непредвиденные траты/Линза помутнела.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_cloudy_lens.mp3) | 1.163 |
| `narration.unexpected.fragile_scroll` | Непредвиденные траты/Свиток рассыпается.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_unbound_journal.mp3); alias → `narration.unexpected.unbound_journal` | 1.110 |
| `narration.unexpected.frayed_rope` | Непредвиденные траты/Веревка перетерлась.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_frayed_rope.mp3) | 1.158 |
| `narration.unexpected.jammed_crate` | Непредвиденные траты/Ящик заклинило.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_jammed_crate.mp3) | 0.929 |
| `narration.unexpected.lantern_broken` | Непредвиденные траты/Фонарь разбился.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_lantern_broken.mp3) | 0.715 |
| `narration.unexpected.leaking_roof` | Непредвиденные траты/Крыша пропускает воду.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_leaking_roof.mp3) | 0.484 |
| `narration.unexpected.loose_tripod` | Непредвиденные траты/Штатив шатается.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_workbench_support.mp3); alias → `narration.unexpected.workbench_support` | 1.083 |
| `narration.unexpected.missing_gear_tooth` | Непредвиденные траты/Шестерня без зубца.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_missing_gear_tooth.mp3) | 2.534 |
| `narration.unexpected.resin_backpack` | Непредвиденные траты/Рюкзак испачканный смолой.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_resin_backpack.mp3) | 0.558 |
| `narration.unexpected.scratched_plate` | Непредвиденные траты/Пластина поцарапана.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_cloudy_lens.mp3); alias → `narration.unexpected.cloudy_lens` | 1.163 |
| `narration.unexpected.smoky_lantern` | Непредвиденные траты/Фонарь коптит.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_tilted_roof_lens.mp3); alias → `narration.unexpected.tilted_roof_lens` | 5.358 |
| `narration.unexpected.stomach_ache` | Непредвиденные траты/У Рыжика заболел живот.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_stomach_ache.mp3) | 1.400 |
| `narration.unexpected.stuck_compass` | Непредвиденные траты/Стрелка застряла.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_stuck_ring.mp3); alias → `narration.unexpected.stuck_ring` | 1.656 |
| `narration.unexpected.stuck_lever` | Непредвиденные траты/Заело рычаг.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_stuck_lever.mp3) | 1.280 |
| `narration.unexpected.stuck_ring` | Непредвиденные траты/Колесо не вращается.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_stuck_ring.mp3) | 1.656 |
| `narration.unexpected.tarnished_plate` | Непредвиденные траты/Пластина покрылась налетом.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_tarnished_plate.mp3) | 2.391 |
| `narration.unexpected.tilted_roof_lens` | Непредвиденные траты/Линза на крыше переколилась.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_tilted_roof_lens.mp3) | 5.358 |
| `narration.unexpected.unbound_journal` | Непредвиденные траты/Журнал потерял переплет.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_unbound_journal.mp3) | 1.110 |
| `narration.unexpected.wet_map` | Непредвиденные траты/Карта намокла.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_unbound_journal.mp3); alias → `narration.unexpected.unbound_journal` | 1.110 |
| `narration.unexpected.wheel_cracked` | Непредвиденные траты/Колесо треснуло.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_workbench_support.mp3); alias → `narration.unexpected.workbench_support` | 1.083 |
| `narration.unexpected.workbench_support` | Непредвиденные траты/Верстак просел.mp3 | [asset](../../../app/src/main/assets/media/audio/narration_unexpected_workbench_support.mp3) | 1.083 |
| `sound.payment` | Оплата чего-либо.mp3 | [asset](../../../app/src/main/assets/media/audio/sound_payment.mp3) | 3.524 |
| `sound.purchase_appears` | Появление необязательных покупок.mp3 | [asset](../../../app/src/main/assets/media/audio/sound_purchase_appears.mp3) | 4.029 |
| `video.intro_fox` | Заставка Лисенка.mp4 | [asset](../../../app/src/main/assets/media/video/intro_fox.mp4) | 79.969 |
| `story.chapter_1` | Глава 1/Главная Тема.mp3 | [asset](../../../app/src/main/assets/media/audio/story_chapter_1.mp3) | 212.453 |
| `story.chapter_2` | Глава 2/Главная Тема.mp3 | [asset](../../../app/src/main/assets/media/audio/story_chapter_2.mp3) | 194.974 |
| `story.chapter_3` | Глава 3/Главная Тема.mp3 | [asset](../../../app/src/main/assets/media/audio/story_chapter_3.mp3) | 207.293 |
| `story.chapter_4` | Глава 4/Главная Тема.mp3 | [asset](../../../app/src/main/assets/media/audio/story_chapter_4.mp3) | 212.774 |
| `story.chapter_5` | Глава 5/Главная Тема.mp3 | [asset](../../../app/src/main/assets/media/audio/story_chapter_5.mp3) | 188.453 |

