package ru.nksk.lctapp.feature.tasks.navigation

import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore

/** App supplies the illustrated board; Tasks retains admission, payment and outcome ownership. */
data class ChapterDeedContent(
    val kind: DeedGameKind,
    val interactionBlocked: Boolean,
    val repairCoins: Int,
    val onCompleted: (DeedGameScore) -> Unit,
    val onPayRepair: suspend (requestId: String, rockIndex: Int) -> ChapterRepairPayment,
    val onExit: () -> Unit,
)

enum class ChapterRepairPayment { PAID, NOT_ENOUGH_COINS, FAILED }
