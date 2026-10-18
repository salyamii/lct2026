package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.chapterone.*

/** Scenario tests need completed physical work, not fabricated precision scores. */
internal fun completedChapterGameScore(kind: DeedGameKind): DeedGameScore = checkNotNull(when (kind) {
    DeedGameKind.TELESCOPE -> DeedGameScore.fromTelescope(TelescopeAssembly()
        .install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
        .turnFastener(0, 180f).turnFastener(1, 180f).secureMount()
        .install(TelescopePart.LENS).turnEyepiece(270f).secureEyepiece()
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
