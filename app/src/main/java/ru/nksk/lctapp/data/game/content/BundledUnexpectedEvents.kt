package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Source copy/prices: Figma snapshot 2026-09-24. Guards and legacy effort adaptation are documented separately. */
private data class UnexpectedCard(
    val node: String, val title: String, val body: String, val price: Long,
    val alternative: String?, val alternativeEnergy: Int, val condition: StoryCondition,
    val scene: String, val blocksStory: Boolean, val family: String, val cooldown: Int,
    val alternativeGame: DeedGameKind? = null,
    val gamePresentation: StoryGamePresentation? = null,
    val narrationCueKey: String? = null,
)
private val unexpectedCards get() = listOf(
    UnexpectedCard("2164:46", "Колесо треснуло",
        "Тележка встала у причала. Пока колесо не починить, дальше по маршруту не пройти.", 18, null, 0, fact("helped_dock"), "pier", true, "wheel", 14, narrationCueKey = "narration.unexpected.wheel_cracked"),
    UnexpectedCard("2164:92", "Фонарь разбился",
        "До сумерек недалеко. Без нового фонаря закрыта тропа к обсерватории, и сюжет не продолжится.", 15, null, 0, StoryCondition.OwnsItem("$TOWER_GOAL:lantern"), "trail", true, "light", 14, narrationCueKey = "narration.unexpected.lantern_broken"),
    UnexpectedCard("2164:139", "{petName}: заболел живот",
        "Питомцу нездоровится. Лекарь рядом, но приём стоит 12 монет. Пока не поправится, путешествие придётся отложить. Еда и отдых по-прежнему доступны.", 12, null, 0, StoryCondition.Always, "city", true, "health", 14, narrationCueKey = "narration.unexpected.stomach_ache"),
    UnexpectedCard("2297:2", "Лямка не выдержала",
        "{petName} заметил разрыв лямки своего рюкзака. Ремонт стоит 8 монет. Можно зашить самому, но это отнимет силы.", 8, "Зашить самому", 2, StoryCondition.EquippedLook("BACKPACK"), "pier", true, "backpack", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.SEW_BACKPACK, narrationCueKey = "narration.unexpected.backpack_strap"),
    UnexpectedCard("2308:2", "Карта намокла",
        "Вода размыла часть маршрута. Луна сделает новую копию за 6 монет. Можно самому восстановить её по карте в архиве.", 6, "Перерисовать", 1, all(fact("luna_npc_met"), fact("map_layers_known")), "workshop", true, "map", 7, alternativeGame = DeedGameKind.MEMORY, gamePresentation = StoryGamePresentation.RESTORE_WET_MAP, narrationCueKey = "narration.unexpected.wet_map"),
    UnexpectedCard("2313:2", "Рюкзак испачкан смолой",
        "Рыжик прислонился к свежим доскам на пристани. Смола испачкала рюкзак и склеила лямку. Очистка стоит 4 монеты. Можно почистить самому, но это отнимет силы.", 4, "Почистить самому", 2, StoryCondition.EquippedLook("BACKPACK"), "pier", false, "backpack", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.CLEAN_RESIN, narrationCueKey = "narration.unexpected.resin_backpack"),
    UnexpectedCard("2320:2", "Верёвка перетёрлась",
        "Перед выходом Рыжик заметил, что верёвка распускается посередине. Для похода нужна новая.", 6, null, 0, StoryCondition.OwnsItem("$TOWER_GOAL:fastenings"), "trail", true, "rope", 10, narrationCueKey = "narration.unexpected.frayed_rope"),
    UnexpectedCard("2320:50", "Штатив шатается",
        "Крепление штатива треснуло. Телескоп качается, и поймать звезду в объектив не получается.", 5, "Починить самому", 2, StoryCondition.OwnsItem("stargazing-tripod-v1"), "observatory", true, "telescope", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.TIGHTEN_TRIPOD, narrationCueKey = "narration.unexpected.loose_tripod"),
    UnexpectedCard("2320:98", "Оправа погнулась",
        "При сборке телескопа обнаружилось, что старая оправа больше не удерживает линзу. Нужна подгонка.", 4, null, 0, StoryCondition.OwnsItem("stargazing-telescope-v1"), "observatory", true, "telescope", 10, narrationCueKey = "narration.unexpected.bent_frame"),
    UnexpectedCard("2320:146", "Фонарь коптит",
        "Вместо ровного света фонарь даёт дым. Горелка засорилась - перед входом в башню её нужно очистить.", 3, "Очистить самому", 1, StoryCondition.OwnsItem("$TOWER_GOAL:lantern"), "trail", true, "light", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.CLEAN_LANTERN, narrationCueKey = "narration.unexpected.smoky_lantern"),
    UnexpectedCard("2320:194", "Верстак просел",
        "У старого верстака раскололась опора. Работать за ним пока нельзя - понадобятся доска и крепления.", 7, "Укрепить самому", 3, all(fact("workbench_access_ready"), StoryCondition.Not(fact("research_station_ready"))), "workshop", true, "workshop", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.REPAIR_WORKBENCH, narrationCueKey = "narration.unexpected.workbench_support"),
    UnexpectedCard("2320:242", "Линза на крыше перекосилась",
        "После сильного ветра сигнальная линза смотрит мимо обсерватории. Тико обнаружил треснувшее крепление.", 5, null, 0, all(fact("calibration_station_known"), StoryCondition.Not(fact("research_station_ready"))), "workshop", true, "lens", 10, narrationCueKey = "narration.unexpected.tilted_roof_lens"),
    UnexpectedCard("2320:290", "Пластина поцарапана",
        "На прозрачной картографической пластине глубокие царапины. Через них не разобрать линии нижней карты.", 4, "Перенести линии вручную", 2, all(fact("map_layers_known"), StoryCondition.Not(fact("research_map_complete"))), "workshop", true, "map", 7, alternativeGame = DeedGameKind.MEMORY, gamePresentation = StoryGamePresentation.RESTORE_MAP_LINES, narrationCueKey = "narration.unexpected.scratched_plate"),
    UnexpectedCard("2320:338", "Стрелка застряла",
        "Компас перестал поворачиваться: внутрь набилась мелкая пыль. Перед дальней дорогой его нужно разобрать и очистить.", 5, "Очистить самому", 2, StoryCondition.OwnsItem("$EXPEDITION_GOAL:compass"), "trail", true, "compass", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.UNJAM_COMPASS, narrationCueKey = "narration.unexpected.stuck_compass"),
    UnexpectedCard("2320:386", "Шестерня без зубца",
        "Тико открыл старое реле и нашёл причину остановки: у шестерни отломился зубец. Потребуется новая деталь.", 8, "Идти по карте Луны", 2, all(fact("relay_station_found"), fact("tiko_npc_met"), StoryCondition.Not(fact("final_relay_restored"))), "workshop", false, "station", 10, narrationCueKey = "narration.unexpected.missing_gear_tooth"),
    UnexpectedCard("2326:16", "Ключ погнулся",
        "Найденный в башне ключ погнулся и не входит в замок старого прибора. Тико может осторожно выправить его.", 4, null, 0, all(fact("chronoscope_key_part"), fact("tiko_npc_met")), "workshop", true, "key", 10, narrationCueKey = "narration.unexpected.bent_key"),
    UnexpectedCard("2326:64", "Свиток рассыпается",
        "Края полуобгоревшего свитка крошатся при разворачивании. Нужны защитная подложка и закрепление бумаги.", 5, "Сначала переписать", 2, fact("second_path_scroll"), "workshop", false, "scroll", 7, alternativeGame = DeedGameKind.MEMORY, gamePresentation = StoryGamePresentation.PRESERVE_SCROLL, narrationCueKey = "narration.unexpected.fragile_scroll"),
    UnexpectedCard("2326:112", "Журнал потерял переплёт",
        "У старого грузового журнала лопнули нитки. Страницы с маршрутами выпадают и путаются.", 6, "Сшить самому", 2, fact("old_route_hint"), "pier", false, "journal", 7, alternativeGame = DeedGameKind.MEMORY, gamePresentation = StoryGamePresentation.BIND_JOURNAL, narrationCueKey = "narration.unexpected.unbound_journal"),
    UnexpectedCard("2326:160", "Пластина покрылась налётом",
        "На звёздной пластине выступил плотный налёт. Обычная салфетка его не берёт - нужен очищающий состав.", 3, null, 0, fact("plate_found"), "observatory", true, "plate", 10, narrationCueKey = "narration.unexpected.tarnished_plate"),
    UnexpectedCard("2326:208", "Линза помутнела",
        "На старой линзе телескопа обнаружились пятна, которые мешают наблюдению. Смотритель советует отдать её на полировку.", 6, "Использовать малый телескоп", 0, all(StoryCondition.OwnsItem("stargazing-telescope-v1"), fact("observatory_known")), "observatory", false, "telescope", 7, narrationCueKey = "narration.unexpected.cloudy_lens"),
    UnexpectedCard("2326:256", "Заело сигнальный рычаг",
        "Рычаг дорожного поста застрял на полпути. Внутри заржавела втулка - её придётся заменить или очистить.", 5, "Разобрать и очистить", 3, fact("signal_post_found"), "trail", true, "signal", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.UNJAM_LEVER, narrationCueKey = "narration.unexpected.stuck_lever"),
    UnexpectedCard("2326:304", "Крыша пропускает воду",
        "После дождя в мастерской появилась лужа прямо у верстака. Для заплатки нужны доски и смола.", 8, "Перенести рабочее место", 2, fact("researcher_home_open"), "workshop", false, "workshop", 7, narrationCueKey = "narration.unexpected.leaking_roof"),
    UnexpectedCard("2326:352", "Ящик заклинило",
        "Плоский ящик с картографическими пластинами разбух от сырости. Если тянуть сильнее, можно повредить находки.", 4, "Подогнать самому", 2, fact("map_layers_known"), "workshop", true, "map", 7, alternativeGame = DeedGameKind.MEMORY, gamePresentation = StoryGamePresentation.FIT_CRATE_LID, narrationCueKey = "narration.unexpected.jammed_crate"),
    UnexpectedCard("2326:400", "Кольцо не вращается",
        "Латунное кольцо с делениями заедает в креплении. Тико заметил на внутренней стороне заусенец.", 5, null, 0, all(fact("chronoscope_service_part"), fact("tiko_npc_met")), "workshop", true, "ring", 10, narrationCueKey = "narration.unexpected.stuck_ring"),
    UnexpectedCard("2326:448", "Окно станции разбито",
        "Через разбитое окно релейной станции дождь попадает на механизм. Перед восстановлением нужно закрыть проём.", 7, "Заколотить досками", 2, all(fact("relay_station_found"), StoryCondition.Not(fact("final_relay_restored"))), "workshop", true, "station", 7, alternativeGame = DeedGameKind.PRECISION, gamePresentation = StoryGamePresentation.REPAIR_WINDOW, narrationCueKey = "narration.unexpected.broken_window"),
)

internal fun GameCatalog.withEverydayEvents(): GameCatalog {
    val legacy = withLegacyEverydayEvents()
    val specs = unexpectedCards.map { source ->
        val id = "figma-${source.node.replace(':', '-')}-v2"
        EventSpec(
            definition = EventDefinition(id, EventType.RANDOM, source.title, source.body, null, null, null, 0, null, null),
            choices = buildList {
                add(EventChoiceSpec("pay", "Оплатить · ${source.price}", -source.price,
                    recap = "${source.title}: оплатили помощь"))
                source.alternative?.let { add(EventChoiceSpec("work", it, energyCost = source.alternativeEnergy,
                    gameKind = source.alternativeGame, recap = "${source.title}: справились без оплаты")) }
            },
            policy = EventPolicy(0, condition = source.condition,
                requiresPetHelp = source.family == "health",
                scheduling = EventSchedulingPolicy(source.cooldown, source.family, EverydayEventKind.UNEXPECTED,
                    mandatoryUnexpected = source.blocksStory, blocksStoryUntilResolved = source.blocksStory,
                    previousEventIds = if (source.node == "2313:2") setOf("figma-2313-2-v1") else emptySet(), earliestDay = 3)),
            card = EventCardCopy("Неожиданная трата", "Стоимость: ${source.price} монет",
                when (source.alternativeEnergy) { 1 -> "Самому: немного устанет"; 2 -> "Самому: средне устанет"; 3 -> "Самому: сильно устанет"; else -> "" },
                "Вернуться завтра", "Неожиданное событие",
                "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=${source.node.replace(':', '-')}", source.scene, null,
                presentation = EventPresentation(media = EventMedia(game = source.gamePresentation?.media,
                    narrationCueKey = source.narrationCueKey, actionAudio = paymentActionAudio(id, listOf("pay"))))),
        )
    }
    return legacy.withEventSpecs(specs).copy(
        // Keep installed v1 definitions readable, but offer only the new guarded versions.
        dailyEventPool = specs.map { it.definition.id }, oneTimeEventIds = emptySet(),
    ).withEverydayPurchases()
}
