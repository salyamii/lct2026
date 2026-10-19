package ru.nksk.lctapp.feature.tasks.navigation

import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingProgress
import ru.nksk.lctapp.domain.minigame.chapterone.TripPacking

/** App supplies the illustrated board; Tasks retains admission, payment and outcome ownership. */
data class ChapterDeedContent(
    val kind: DeedGameKind,
    val interactionBlocked: Boolean,
    val repairCoins: Int,
    val onCompleted: (DeedGameScore) -> Unit,
    val onPayRepair: suspend (requestId: String, rockIndex: Int) -> ChapterRepairPayment,
    val onExit: () -> Unit,
    val onShoppingProgress: (ShoppingProgress) -> Unit = {},
    val onPackingCompleted: (TripPacking) -> Unit = {},
    val onHome: () -> Unit = onExit,
    val homeEnabled: Boolean = !interactionBlocked,
)

enum class ChapterRepairPayment { PAID, NOT_ENOUGH_COINS, FAILED }
