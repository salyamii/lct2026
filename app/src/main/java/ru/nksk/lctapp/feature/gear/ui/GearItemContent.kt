package ru.nksk.lctapp.feature.gear.ui

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.cosmeticArtwork
import ru.nksk.lctapp.core.ui.game.goalItemArtwork
import ru.nksk.lctapp.domain.pet.PetCosmetics

/** Read-only presentation; this catalog never grants an item or changes its saved definition. */
internal data class GearContentPage(
    val id: String,
    val title: String,
    @DrawableRes val imageRes: Int?,
    val body: String,
    val zoomable: Boolean = false,
)

internal data class GearItemContent(
    @DrawableRes val artworkRes: Int?,
    val pages: List<GearContentPage> = emptyList(),
)

/** Only exact known IDs have authored pages; every other item retains its real catalog description. */
internal fun gearItemContent(itemId: String, name: String, description: String): GearItemContent {
    val artwork = goalItemArtwork(itemId) ?: when (itemId) {
        "figma-2654-194-toy-boat-v1" -> R.drawable.prop_fair_toy_boat
        else -> PetCosmetics.forItem(itemId)?.lookId?.let(::cosmeticArtwork)
    }
    val pages = when (itemId) {
        "stargazing-star-map-v1" -> listOf(
            GearContentPage("orion-belt", "Пояс Ориона", R.drawable.collection_sky_orion,
                "Три яркие звезды в ряд — Альнитак, Альнилам и Минтака. Они образуют пояс в созвездии Ориона.\n\n" +
                    "Линии на схеме помогают узнать рисунок: в самом небе этих линий нет.", zoomable = true),
            GearContentPage("big-dipper", "Большая Медведица", R.drawable.collection_sky_ursa_major,
                "Семь ярких звёзд образуют Большой Ковш — узнаваемую часть созвездия Большой Медведицы. " +
                    "Четыре звезды составляют чашу, ещё три — ручку.\n\n" +
                    "Найди их на схеме и проследи рисунок от ручки до чаши.", zoomable = true),
            GearContentPage("milky-way", "Млечный Путь", R.drawable.collection_sky_milky_way,
                "Млечный Путь — наша галактика. С Земли мы видим её как светлую полосу: " +
                    "в ней сливается свет множества далёких звёзд.\n\n" +
                    "Это изображение помогает представить её вид в ночном небе.", zoomable = true),
        )
        "stargazing-telescope-v1" -> listOf(GearContentPage("telescope-guide", "Знакомимся с телескопом", artwork,
            "Телескоп помогает рассматривать далёкие объекты ночного неба.\n\n" +
                "Окуляр — часть, в которую смотрят.\n\n" +
                "Фокусировка помогает сделать изображение чётким.\n\n" +
                "Крепление удерживает телескоп на штативе и позволяет менять направление.", zoomable = true))
        "stargazing-tripod-v1" -> listOf(GearContentPage("tripod-guide", "Как поставить штатив", artwork,
            "1. Выбери ровное место и раздвинь ножки, чтобы опора стояла устойчиво.\n\n" +
                "2. Закрепи телескоп на площадке штатива. Попроси взрослого проверить крепление.\n\n" +
                "3. Проверь, что ножки не скользят, а телескоп держится ровно. Теперь можно готовиться к наблюдениям.",
            zoomable = true))
        else -> listOf(GearContentPage("item", name, artwork, description, zoomable = artwork != null))
    }
    return GearItemContent(artwork, pages)
}
