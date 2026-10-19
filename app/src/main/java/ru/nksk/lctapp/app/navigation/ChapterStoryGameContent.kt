package ru.nksk.lctapp.app.navigation

import androidx.compose.runtime.Composable
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudyKind
import ru.nksk.lctapp.domain.minigame.chapterone.DiscoveryScene
import ru.nksk.lctapp.feature.storygames.ui.*
import ru.nksk.lctapp.feature.tasks.navigation.ChapterDeedContent
import ru.nksk.lctapp.feature.tasks.navigation.ChapterRepairPayment

/** Cross-feature composition stays in app. Each board returns its validated, finished model. */
@Composable
internal fun ChapterStoryGameContent(host: ChapterDeedContent) {
    when (host.kind) {
        DeedGameKind.RING_TOSS -> RingTossGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromRingToss(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.OBSERVATORY_OPENING, DeedGameKind.PLATE_HAND, DeedGameKind.PLATE_SOLUTION,
        DeedGameKind.LIGHT_INSCRIPTION, DeedGameKind.SERVICE_EMBLEM, DeedGameKind.SIGNAL_POST,
        DeedGameKind.CHAPTER_FINALE -> DiscoveryGameEntry(when (host.kind) {
            DeedGameKind.OBSERVATORY_OPENING -> DiscoveryScene.OBSERVATORY
            DeedGameKind.PLATE_HAND -> DiscoveryScene.PLATE_HAND
            DeedGameKind.PLATE_SOLUTION -> DiscoveryScene.PLATE_SOLUTION
            DeedGameKind.LIGHT_INSCRIPTION -> DiscoveryScene.INSCRIPTION
            DeedGameKind.SERVICE_EMBLEM -> DiscoveryScene.EMBLEM
            DeedGameKind.SIGNAL_POST -> DiscoveryScene.SIGNAL_POST
            else -> DiscoveryScene.FINALE
        }, host.onExit,
            onCompleted = { DeedGameScore.fromDiscovery(it)?.let(host.onCompleted) },
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.TRIP_PACKING -> TripPackingGameEntry(host.onExit,
            onCompleted = host.onPackingCompleted,
            interactionBlocked = host.interactionBlocked)
        DeedGameKind.SHOPPING_COMPARISON -> ShoppingComparisonGameEntry(host.onExit,
            onCompleted = { DeedGameScore.fromShoppingComparison(it)?.let(host.onCompleted) },
            onProgress = host.onShoppingProgress, interactionBlocked = host.interactionBlocked)
        DeedGameKind.RECORD_JOURNAL, DeedGameKind.RECORD_LENSES, DeedGameKind.RECORD_LIGHTS ->
            RecordGameEntry(when (host.kind) {
                DeedGameKind.RECORD_JOURNAL -> RecordStudyKind.CARGO_JOURNAL
                DeedGameKind.RECORD_LENSES -> RecordStudyKind.THREE_LENSES
                else -> RecordStudyKind.LIGHT_ORDER
            }, host.onExit,
                onCompleted = { DeedGameScore.fromRecordStudy(it)?.let(host.onCompleted) },
                interactionBlocked = host.interactionBlocked)
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
