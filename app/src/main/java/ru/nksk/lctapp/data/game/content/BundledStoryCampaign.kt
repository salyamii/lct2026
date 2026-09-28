package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.location.GameLocation

internal fun storyEventId(sourceId: String) = "campaign-choice-v1:$sourceId"
internal fun fact(id: String) = StoryCondition.Fact(id)
internal fun all(vararg conditions: StoryCondition) = StoryCondition.All(conditions.toList())
internal fun any(vararg conditions: StoryCondition) = StoryCondition.Any(conditions.toList())
internal fun unseen(sourceId: String) = StoryCondition.Not(StoryCondition.EventCompleted(storyEventId(sourceId)))

internal data class LoreOption(val key: String, val text: String, val facts: Set<String>, val energy: Int = 0, val moneyDelta: Long = 0,
    val gameKind: DeedGameKind? = null, val recap: String? = null,
    val gamePresentation: StoryGamePresentation? = null, val destination: GameLocation? = null)
internal data class LoreScene(
    val sourceId: String,
    val facts: Set<String> = emptySet(),
    val condition: StoryCondition = StoryCondition.Always,
    val action: String = "Продолжить историю",
    val energy: Int = 0,
    val title: String? = null,
    val body: String? = null,
    val scene: String = "observatory",
    val variants: List<EventCardVariant> = emptyList(),
    val options: List<LoreOption> = emptyList(),
    val requiredInOrder: Boolean = sourceLoreCards.firstOrNull { it.id == sourceId }?.optional != true,
    val gameKind: DeedGameKind? = null,
    val recap: String? = null,
    val gamePresentation: StoryGamePresentation? = null,
    val presentation: StoryPresentation? = null,
    val narrationCueKey: String? = null,
    val destination: GameLocation? = null,
)

private data class LoreChapter(val scenes: List<LoreScene>, val presentation: StoryChapterPresentation)

/** Executable adaptation of the reviewed 60-card campaign. Source transcripts are kept unchanged. */
internal fun GameCatalog.withStoryCampaign(): GameCatalog {
    val oldIntroduction = introductionId
    val chapters = listOf(
        LoreChapter(firstStoryAct(), StoryChapterPresentation.OBSERVATORY),
        LoreChapter(secondStoryAct(), StoryChapterPresentation.TOWER),
        LoreChapter(thirdStoryAct(), StoryChapterPresentation.WORKSHOP),
        LoreChapter(fourthStoryAct(), StoryChapterPresentation.MAP),
        LoreChapter(fifthStoryAct(), StoryChapterPresentation.EXPEDITION),
    )
    val titles = listOf("Башня отвечает", "Свиток второго пути", "Станция калибровки", "Пустое место на карте", "Последний Смотритель")
    val ages = listOf(PetAge.CUB, PetAge.TEEN, PetAge.TEEN, PetAge.ADULT, PetAge.SENIOR)
    val chapterGoals = listOf(STARS_GOAL, TOWER_GOAL, HOME_GOAL, MAP_GOAL, EXPEDITION_GOAL)
    val oldChapter = content.days.single { it.id == storyDayId }.chapterId
    val referenceGoal = content.chapters.single { it.id == oldChapter }.goalId
    val chapterIds = listOf(oldChapter) + (2..5).map { "campaign-choice-v1:act-$it" }
    val dayIds = listOf(storyDayId) + (2..5).map { "campaign-choice-v1:act-$it:day" }
    val events = mutableListOf<EventDefinition>()
    val choices = mutableListOf<EventChoiceDefinition>()
    val newPolicies = mutableMapOf<String, EventPolicy>()
    val newCards = mutableMapOf<String, EventCardCopy>()
    val acts = chapters.mapIndexed { index, chapter ->
        val chapterScenes = chapter.scenes
        val actId = "campaign-choice-v1:act-${index + 1}"
        var previousCore: String? = null
        chapterScenes.forEachIndexed { position, scene ->
            val source = sourceLoreCards.firstOrNull { it.id == scene.sourceId }
            val id = storyEventId(scene.sourceId)
            val finale = scene.sourceId == "G${index + 1}.12"
            val requirements = mutableListOf(scene.condition)
            previousCore?.let { requirements += StoryCondition.EventCompleted(it) }
            if (source?.optional == true) {
                chapterScenes.drop(position + 1).firstOrNull { it.requiredInOrder }?.let { requirements += unseen(it.sourceId) }
            }
            val options = scene.options.ifEmpty {
                listOf(LoreOption("continue", scene.action, scene.facts, scene.energy, gameKind = scene.gameKind, recap = scene.recap, gamePresentation = scene.gamePresentation, destination = scene.destination)) +
                    if (source?.optional == true) listOf(LoreOption("skip", "Пропустить", emptySet())) else emptyList()
            }
            val presentation = scene.presentation?.forEvent(id, options.map { it.key }) ?: EventPresentation()
            val gameMedia = options.mapNotNull { it.gamePresentation }.distinct().singleOrNull()?.media
            val event = EventDefinition(id, EventType.STORY, scene.title ?: checkNotNull(source).title,
                scene.body ?: checkNotNull(source).body, null, null, null, 0, null,
                chapterIds.getOrNull(index + 1).takeIf { finale })
            val compiled = EventSpec(event,
                choices = options.map { option -> EventChoiceSpec(option.key, option.text, option.moneyDelta,
                    energyCost = option.energy, gameKind = option.gameKind, facts = option.facts,
                    destination = option.destination,
                    disabled = source?.optional == true && scene.options.isEmpty() && option.key == "skip",
                    recap = option.recap ?: if (option.key == "skip") "Пропустили дополнительную историю «${event.title}»" else null) },
                policy = EventPolicy(scene.energy, condition = StoryCondition.All(requirements),
                    storyActId = actId, finishesStoryAct = finale,
                    chapterEntryDayId = dayIds.getOrNull(index + 1).takeIf { finale }),
                card = EventCardCopy(if (source?.optional == true) "Дополнительная история" else "История",
                if (finale) "Нужен собранный комплект этой главы" else "",
                when (options.maxOf { it.energy }) { 0 -> "Без траты сил"; 1 -> "Немного устанет"; 2 -> "Устанет"; else -> "Сильно устанет" },
                "Вернуться позже", "Глава ${index + 1} · ${titles[index]}", source?.sourceUrl.orEmpty(), scene.scene,
                null, scene.variants, presentation = presentation.copy(
                    media = presentation.media.copy(game = gameMedia ?: presentation.media.game,
                        musicCueKey = chapter.presentation.musicCueKey,
                        narrationCueKey = scene.narrationCueKey ?: presentation.media.narrationCueKey,
                        actionAudio = presentation.media.actionAudio + paymentActionAudio(id,
                            options.filter { it.moneyDelta < 0 }.map { it.key })))),
            ).compile()
            events += compiled.definition
            choices += compiled.choices
            newPolicies[id] = compiled.policy
            newCards[id] = compiled.card
            if (scene.requiredInOrder) previousCore = id
        }
        StoryAct(actId, titles[index], dayIds[index], chapterScenes.map { storyEventId(it.sourceId) },
            storyEventId("G${index + 1}.12"), petAge = ages[index], goalId = chapterGoals[index])
    }
    // These facts describe actual completed mini-games, never merely seeing a job offer.
    val deedFacts = policies.mapValues { (id, policy) ->
        val facts = when {
            id == oldIntroduction -> setOf("observatory_known")
            id == "figma-2163-43-v1" -> setOf("keeper_trust", "telescope_tuned")
            id in deedPool && cards[id]?.scene == "pier" -> setOf("helped_dock")
            id in deedPool && cards[id]?.scene == "observatory" -> setOf("keeper_trust")
            else -> emptySet()
        }
        if (facts.isEmpty()) policy else policy.copy(factsByChoiceId = content.choices.filter { it.eventId == id }
            .associate { it.id to facts })
    }
    return copy(
        content = content.copy(
            chapters = content.chapters + (1..4).map { ChapterDefinition(chapterIds[it], titles[it], referenceGoal) },
            days = content.days + (1..4).map { GameDayDefinition(dayIds[it], chapterIds[it], 1) },
            events = content.events + events, choices = content.choices + choices,
        ),
        introductionId = storyEventId("G1.01"),
        policies = deedFacts + newPolicies, cards = cards + newCards,
        storyCampaign = StoryCampaign(acts, mapOf(storyEventId("G1.01") to
            goals.flatMap { it.legacyAcceptanceChoiceIds }.toSet()), deedHints = listOf(
                StoryDeedHint(all(fact("observatory_known"), StoryCondition.Not(fact("helped_dock"))),
                    deedPool.first { cards[it]?.scene == "pier" }),
                StoryDeedHint(all(fact("tower_map"), StoryCondition.Not(fact("keeper_trust"))), "figma-2238-120-v1"),
                StoryDeedHint(all(fact("north_tower_riddle"), StoryCondition.Not(fact("telescope_tuned"))), "figma-2163-43-v1"),
            )),
    )
}

private fun firstStoryAct() = listOf(
    LoreScene("G1.01", setOf("observatory_known"), body = "Смотритель готовит общую Ночь наблюдений. «Приходи, {petName}. Свой большой проект ты выбрал сам, а старые загадки мы будем разгадывать вместе». Базовые дела обсерватории уже доступны.", action = "Познакомиться с обсерваторией", recap = "Познакомились с обсерваторией и Смотрителем", presentation = StoryPresentation.OBSERVATORY_INVITATION, variants = listOf(EventCardVariant(StoryCondition.Always, "Смотритель приглашает {petName} на Ночь наблюдений. Для неё нужно собрать карту звёзд, штатив, телескоп и оплатить поездку. Выбери, на что копить сначала, а по дороге помогай жителям и разгадывай старые загадки.")), narrationCueKey = "narration.story.night_observation", destination = GameLocation.OBSERVATORY),
    LoreScene("G1.02", setOf("tower_mark", "mark_sketch"), fact("helped_dock"), scene = "pier", action = "Зарисовать знак", recap = "Зарисовали загадочный знак на причале", narrationCueKey = "narration.story.crate_mark"),
    LoreScene("G1.03", setOf("plate_found", "plate_symbol"), fact("tower_mark"), "Очистить пластину", CampaignBalance.SMALL_WORK, gameKind = DeedGameKind.PRECISION, recap = "Очистили найденную пластину и изучили символ", gamePresentation = StoryGamePresentation.CLEAN_PLATE, narrationCueKey = "narration.story.marked_plate"),
    LoreScene("G1.04", setOf("old_route_hint"), fact("helped_dock"), scene = "pier", action = "Изучить запись", recap = "Нашли запись о старом маршруте", narrationCueKey = "narration.story.cargo_journal"),
    LoreScene("G1.05", setOf("north_tower_riddle"), all(fact("plate_symbol"), StoryCondition.DayStepsAtLeast(2)), "Прочитать надпись", recap = "Прочитали надпись о северной башне", narrationCueKey = "narration.story.three_answers"),
    LoreScene("N1.WHEEL", setOf("wheel_repaired"), fact("north_tower_riddle"), "Помочь с колесом", CampaignBalance.SMALL_WORK,
        "Колесо старой телеги", "На Причале телега застряла из-за расшатавшегося колеса. Бобёр просит помочь закрепить его. После работы он обещает показать старую карту маршрутов.", "pier", gameKind = DeedGameKind.PRECISION, recap = "Помогли бобру починить колесо телеги", gamePresentation = StoryGamePresentation.REPAIR_WHEEL),
    LoreScene("G1.06", setOf("tower_map", "old_road_visible", "tower_location_known"), all(fact("wheel_repaired"), fact("tower_mark")), "Рассмотреть карту", scene = "pier", recap = "Рассмотрели старую карту и нашли путь к башне", narrationCueKey = "narration.story.old_map"),
    LoreScene("G1.07", setOf("keepers_service_hint"), all(fact("tower_map"), fact("keeper_trust")), "Осмотреть эмблему", recap = "Изучили эмблему службы Смотрителей", narrationCueKey = "narration.story.service_emblem"),
    LoreScene("G1.08", setOf("clue_three_lights", "old_road_post_unlocked"), all(fact("tower_map"), fact("keeper_trust")), "Изучить схему", recap = "Разобрались в схеме трёх сигнальных огней", narrationCueKey = "narration.story.three_lenses"),
    LoreScene("G1.09", setOf("clue_light_order"), fact("clue_three_lights"), "Сходить к дорожному посту", CampaignBalance.TRAVEL, scene = "trail", recap = "Сходили к дорожному посту и узнали порядок огней", narrationCueKey = "narration.story.stone_post", destination = GameLocation.TRAIL),
    LoreScene("G1.10", setOf("three_signal_lights_known", "clue_light_order"), all(fact("north_tower_riddle"), fact("telescope_tuned")), "Записать порядок огней",
        variants = listOf(EventCardVariant(StoryCondition.Not(fact("clue_light_order")), "После настройки малого телескопа {petName} находит в журнале порядок трёх сигнальных огней. Если дорожный пост ещё не исследован, эта запись помогает восстановить код. Теперь его можно проверить.")), recap = "Записали порядок трёх сигнальных огней", narrationCueKey = "narration.story.telescope_journal"),
    LoreScene("G1.11", setOf("tower_signal", "tower_alive_confirmed"), all(fact("telescope_tuned"), fact("clue_light_order"), fact("three_signal_lights_known"), StoryCondition.DayStepsAtLeast(2)), "Проверить ответ башни", recap = "Получили ответный сигнал от башни", narrationCueKey = "narration.story.tower_reply"),
    LoreScene("G1.12", setOf("watchers_of_paths_known"), fact("tower_signal"), "Завершить главу",
        body = "Твой личный комплект собран. Смотритель приглашает на общую Ночь наблюдений и предоставляет телескоп обсерватории. Старые метки складываются в сеть маршрутов. «Это была служба Смотрителей путей, а башня — один из её узлов. Теперь мы знаем, зачем туда идти». Свой комплект наблюдений ты сможешь собрать отдельно.",
        variants = listOf(EventCardVariant(StoryCondition.GoalCollected(STARS_GOAL), "Личный комплект наблюдений готов. На Ночи наблюдений {petName} помогает сопоставить старые метки: они образуют сеть маршрутов. Смотритель объясняет, что башня была узлом службы Смотрителей путей. Теперь у героев есть причина исследовать её.")), recap = "Провели Ночь наблюдений и узнали о Смотрителях путей", narrationCueKey = "narration.story.observation_network"),
)

private fun secondStoryAct() = listOf(
    LoreScene("G2.01", setOf("tower_route_planning", "shared_light_prepared"), fact("watchers_of_paths_known"), "Подготовить общий выход",
        body = "Сигнал башни подтверждён. Смотритель собирает исследовательскую группу: «Я подготовлю общий свет и приборы, а ты помоги проверить дорогу. Свой личный проект продолжай в удобном темпе». Снаряжение группы не становится личными вещами {petName}.", recap = "Подготовили общий выход к башне", variants = listOf(EventCardVariant(StoryCondition.Always, "Сигнал башни подтверждён. Теперь цель — подготовиться к её исследованию. Собери карту подходов, фонарь, крепления, дорожный набор и подготовь перевозку. Смотритель поможет проверить дорогу.")), narrationCueKey = "narration.story.quiet_tower_expedition"),
    LoreScene("G2.02", setOf("ferry_backup_hint"), fact("helped_dock"), "Расспросить бобра", scene = "pier", recap = "Расспросили бобра о запасном пути", narrationCueKey = "narration.story.last_tower_cargo"),
    LoreScene("G2.03", condition = fact("tower_route_planning"), title = "Закрытый мост", scene = "trail",
        body = "Группа подходит к повреждённому мосту. Бобёр помогает проверить крепления и указывает безопасный обход. Можно укрепить переход вместе или потратить силы на обход; вернуться позже можно без прохождения маршрута.",
        options = listOf(LoreOption("repair", "Помочь укрепить переход", setOf("bridge_problem_known", "bridge_repaired", "bridge_passed"), CampaignBalance.BRIDGE_WORK, gameKind = DeedGameKind.PRECISION, recap = "Помогли укрепить мост и перешли на другой берег", gamePresentation = StoryGamePresentation.REINFORCE_BRIDGE, destination = GameLocation.TRAIL),
            LoreOption("detour", "Пройти по обходу", setOf("bridge_problem_known", "ferry_route", "bridge_passed"), CampaignBalance.DETOUR, recap = "Добрались на другой берег по обходному пути", destination = GameLocation.TRAIL)), narrationCueKey = "narration.story.closed_bridge"),
    LoreScene("G2.04", setOf("tower_entry_protocol_known", "signal_post_found", "tower_access_reached"), fact("bridge_passed"), "Дойти до сигнального поста", CampaignBalance.TRAVEL, scene = "trail", recap = "Добрались до сигнального поста у башни", narrationCueKey = "narration.story.signal_post", destination = GameLocation.TRAIL),
    LoreScene("G2.05", setOf("tower_recent_activity"), fact("tower_entry_protocol_known"), "Осмотреть двор", scene = "", recap = "Нашли во дворе башни следы недавней активности", narrationCueKey = "narration.story.recent_repairs"),
    LoreScene("G2.06", setOf("tower_inner_hall_open"), all(fact("tower_access_reached"), fact("clue_light_order"),
        any(fact("shared_light_prepared"), StoryCondition.OwnsItem("$TOWER_GOAL:lantern"))), "Применить код и войти", scene = "", recap = "Применили код и вошли во внутренний зал башни", narrationCueKey = "narration.story.three_lights"),
    LoreScene("G2.07", setOf("tower_closed_deliberately", "last_keeper_hint"), fact("tower_inner_hall_open"), "Изучить журнал", scene = "", recap = "Изучили журнал и узнали, что башню закрыли намеренно", narrationCueKey = "narration.story.duty_room"),
    LoreScene("G2.08", setOf("second_path_device_hint"), fact("watchers_of_paths_known"), "Осмотреть оборудование", scene = "", recap = "Осмотрели старое оборудование Смотрителей", narrationCueKey = "narration.story.fork_symbol"),
    LoreScene("G2.09", setOf("second_path_scroll", "chronoscope_term_unlocked", "chronoscope_key_part"), fact("tower_inner_hall_open"), "Открыть найденный тайник", scene = "", recap = "Открыли тайник и нашли Свиток второго пути и часть ключа", narrationCueKey = "narration.story.second_path_scroll"),
    LoreScene("G2.10", setOf("lower_observatory_hint", "tower_receiver_active"), all(fact("second_path_scroll"), fact("chronoscope_key_part")), "Проверить приёмник", scene = "", recap = "Запустили приёмник и нашли подсказку о нижнем зале", narrationCueKey = "narration.story.observatory_receiver"),
    LoreScene("G2.11", setOf("two_way_signal_confirmed"), all(fact("tower_receiver_active"), StoryCondition.DayStepsAtLeast(2)), "Отправить обратный код", scene = "", recap = "Отправили обратный код и подтвердили связь с башней", narrationCueKey = "narration.story.return_code"),
    LoreScene("N2.RETURN", setOf("returned_with_tower_key"), fact("lower_observatory_hint"), "Вернуться со Свитком", CampaignBalance.TRAVEL,
        "Обратная дорога", "{petName} и Смотритель готовы вернуться в обсерваторию со Свитком и башенным ключом.", "trail", recap = "Вернулись в обсерваторию со Свитком и башенным ключом", destination = GameLocation.OBSERVATORY),
    LoreScene("G2.12", setOf("chronoscope_unlocked"), all(fact("second_path_scroll"), fact("chronoscope_key_part"), fact("returned_with_tower_key")), "Открыть зал Хроноскопа", recap = "Открыли зал Хроноскопа", presentation = StoryPresentation.HALL_OF_PATHS, narrationCueKey = "narration.story.hall_of_paths"),
)
