package ru.nksk.lctapp.app.navigation

import androidx.compose.runtime.Composable
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.feature.storygames.ui.*
import ru.nksk.lctapp.feature.tasks.navigation.ChapterDeedContent
import ru.nksk.lctapp.feature.tasks.navigation.ChapterRepairPayment

/** Cross-feature composition stays in app. Each board returns its validated, finished model. */
@Composable
internal fun ChapterStoryGameContent(host: ChapterDeedContent) {
    when (host.kind) {
        DeedGameKind.TELESCOPE -> TelescopeGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromTelescope(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.WHEEL -> WheelGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromWheel(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.STRAP -> StrapGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromStrap(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.MARK -> MarkGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromMark(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.SIGNAL -> SignalGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromSignal(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.ROUTE_MAP -> StarChartGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromRouteMap(it)?.let(host.onCompleted) },
            routeMap = true, interactionBlocked = host.interactionBlocked)
        DeedGameKind.BOAT -> BoatGameEntry(host.onExit, repairCoins = host.repairCoins,
            onPaidRepair = { request ->
                when (host.onPayRepair(request.requestId, request.rockIndex)) {
                    ChapterRepairPayment.PAID -> BoatRepairPaymentResult.Paid
                    ChapterRepairPayment.NOT_ENOUGH_COINS -> BoatRepairPaymentResult.NotEnoughCoins
                    ChapterRepairPayment.FAILED -> BoatRepairPaymentResult.Failed
                }
            },
            onArrived = { DeedGameScore.fromBoat(it)?.let(host.onCompleted) },
            startPrepared = true, interactionBlocked = host.interactionBlocked)
        else -> error("Not a chapter game: ${host.kind}")
    }
}
