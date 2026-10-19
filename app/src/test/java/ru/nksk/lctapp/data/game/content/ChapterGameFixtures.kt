package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.chapterone.*

/** Scenario tests need completed physical work, not fabricated precision scores. */
internal fun completedChapterGameScore(kind: DeedGameKind): DeedGameScore = checkNotNull(when (kind) {
    DeedGameKind.OBSERVATORY_OPENING, DeedGameKind.PLATE_HAND, DeedGameKind.PLATE_SOLUTION,
    DeedGameKind.LIGHT_INSCRIPTION, DeedGameKind.SERVICE_EMBLEM, DeedGameKind.SIGNAL_POST,
    DeedGameKind.CHAPTER_FINALE -> DeedGameScore.fromDiscovery(completedDiscoveryBoard(kind))
    DeedGameKind.RING_TOSS -> DeedGameScore.fromRingToss((0 until RingToss.THROW_COUNT).fold(RingToss()) { state, i ->
        state.throwAtPeg(i % RingToss.pegs.size)
    })
    DeedGameKind.TRIP_PACKING -> DeedGameScore.fromTripPacking(TripSupply.entries.filter { it.required }
        .fold(TripPacking()) { state, supply -> state.toggle(supply) }.finish())
    DeedGameKind.SHOPPING_COMPARISON -> completedShoppingGameScore(ShoppingComparison.rounds.size)
    DeedGameKind.RECORD_JOURNAL -> DeedGameScore.fromRecordStudy(RecordStudyGame.cargoSteps.fold(
        RecordStudy(RecordStudyKind.CARGO_JOURNAL), RecordStudyGame::visit))
    DeedGameKind.RECORD_LENSES -> DeedGameScore.fromRecordStudy(SignalLight.entries.fold(
        RecordStudy(RecordStudyKind.THREE_LENSES)
    ) { state, light -> RecordStudyGame.placeLens(RecordStudyGame.selectLens(state, light), light) })
    DeedGameKind.RECORD_LIGHTS -> DeedGameScore.fromRecordStudy(SignalGame.code.fold(
        RecordStudy(RecordStudyKind.LIGHT_ORDER), RecordStudyGame::stamp))
    DeedGameKind.TELESCOPE -> DeedGameScore.fromTelescope(TelescopeAssembly()
        .install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
        .install(TelescopePart.LENS)
        .adjustFocus(TelescopeAssembly.FOCUS_TARGET).observe())
    DeedGameKind.MARK -> DeedGameScore.fromMark(MarkGame.strokes.flatten().fold(
        MarkGame.dust.fold(MarkState()) { state, p -> MarkGame.touch(state, p.x, p.y) }
    ) { state, p -> MarkGame.touch(state, p.x, p.y) })
    DeedGameKind.WHEEL -> DeedGameScore.fromWheel((0 until WheelRepair.SECTION_COUNT).fold(WheelRepair()) { state, _ ->
        state.rotateBy(-state.angle).secure()
    })
    DeedGameKind.STRAP -> DeedGameScore.fromStrap((0 until StrapRepair.STITCH_COUNT).fold(StrapRepair().align()) { state, point ->
        state.stitch(point)
    }.tighten())
    DeedGameKind.SIGNAL -> DeedGameScore.fromSignal(SignalGame.send(SignalGame.code.fold(SignalState(), SignalGame::choose)))
    DeedGameKind.ROUTE_MAP -> DeedGameScore.fromRouteMap(RouteMapGame.points.indices.fold(StarChartState(), RouteMapGame::select))
    DeedGameKind.BOAT -> DeedGameScore.fromBoat(BoatJourney(phase = BoatPhase.ARRIVED,
        plan = BoatPlan.RESERVE, distance = 1f))
    else -> error("Not a chapter game: $kind")
})

private fun completedDiscoveryBoard(kind: DeedGameKind): ChapterDiscovery {
    val scene = when (kind) {
        DeedGameKind.OBSERVATORY_OPENING -> DiscoveryScene.OBSERVATORY
        DeedGameKind.PLATE_HAND -> DiscoveryScene.PLATE_HAND
        DeedGameKind.PLATE_SOLUTION -> DiscoveryScene.PLATE_SOLUTION
        DeedGameKind.LIGHT_INSCRIPTION -> DiscoveryScene.INSCRIPTION
        DeedGameKind.SERVICE_EMBLEM -> DiscoveryScene.EMBLEM
        DeedGameKind.SIGNAL_POST -> DiscoveryScene.SIGNAL_POST
        DeedGameKind.CHAPTER_FINALE -> DiscoveryScene.FINALE
        else -> error("Not a discovery board: $kind")
    }
    var state = ChapterDiscovery(scene)
    fun act(action: DiscoveryAction) { state = ChapterDiscoveryGame.reduce(state, action) }
    when (scene) {
        DiscoveryScene.OBSERVATORY -> { act(DiscoveryAction.OpenDome); act(DiscoveryAction.RemoveCover) }
        DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION -> ChapterDiscoveryGame.dust.forEach { act(DiscoveryAction.Clean(it.x, it.y)) }
        DiscoveryScene.INSCRIPTION -> (0..2).forEach { act(DiscoveryAction.Illuminate(it)) }
        DiscoveryScene.EMBLEM -> { act(DiscoveryAction.FlipEmblem); act(DiscoveryAction.MatchEmblem) }
        DiscoveryScene.SIGNAL_POST -> ChapterDiscoveryGame.reflectionTargets.forEach {
            act(DiscoveryAction.Aim(it)); act(DiscoveryAction.CaptureReflection)
        }
        DiscoveryScene.FINALE -> {
            (0..2).forEach { act(DiscoveryAction.SelectFinding(it)); act(DiscoveryAction.PlaceFinding(it)) }
            ChapterDiscoveryGame.networkEdges.forEach { (from, to) -> act(DiscoveryAction.SelectPoint(from)); act(DiscoveryAction.SelectPoint(to)) }
        }
    }
    return state.also { check(it.complete) }
}

internal fun completedShoppingGameScore(correct: Int): DeedGameScore {
    // The equal-price round accepts either seller, so every complete board has at least one correct answer.
    require(correct in 1..ShoppingComparison.rounds.size)
    val state = ShoppingComparison.rounds.foldIndexed(ShoppingComparison()) { index, board, round ->
        val cheaper = if (round.leftPrice <= round.rightPrice) ShoppingSide.LEFT else ShoppingSide.RIGHT
        val side = if (index < correct - 1) cheaper
            else if (cheaper == ShoppingSide.LEFT) ShoppingSide.RIGHT else ShoppingSide.LEFT
        board.choose(side).next()
    }
    return checkNotNull(DeedGameScore.fromShoppingComparison(state))
}
