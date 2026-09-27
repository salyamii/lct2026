package ru.nksk.lctapp.feature.day.ui

/** Current presentation of the archived instruments; immutable story IDs and discoveries remain intact. */
internal data class StoryAbilityCopy(val title: String, val body: String, val action: String? = null)

internal fun storyAbilityCopy(eventId: String, petName: String): StoryAbilityCopy? = when (eventId) {
    "campaign-choice-v1:G2.12" -> StoryAbilityCopy("Зал других путей",
        "Свиток и башенный ключ открывают нижний зал. Здесь Смотрители сохраняли увиденные пути. $petName уже умеет замечать их — теперь можно узнать, что исследовали до нас.",
        "Исследовать зал")
    "campaign-choice-v1:G3.05" -> StoryAbilityCopy("Деталь под верстаком",
        "Под верстаком лежит кольцо от старого прибора Смотрителей. На нём — метки калибровки и номер этого дома. Здесь обслуживали устройства, в которых хранили записи о других путях.")
    "campaign-choice-v1:G4.07" -> StoryAbilityCopy("Два старых маршрута",
        "Луна находит записи двух маршрутов. $petName помогает увидеть различия: время в пути, нужный запас и места для остановок. У каждого пути свои возможности.")
    "campaign-choice-v1:G5.02" -> StoryAbilityCopy("Быстро или надёжно",
        "$petName видит два пути. Короткий быстрее, но требует больше запаса. Длинный дешевле и безопаснее, зато займёт ещё день. Как отправимся?")
    "campaign-choice-v1:G5.04" -> StoryAbilityCopy("Возможность, а не обещание",
        "В журнале Смотрителей есть важная заметка: другие пути помогают понять последствия выбора. Они не обещают, что будущее обязательно сложится именно так.")
    else -> null
}
