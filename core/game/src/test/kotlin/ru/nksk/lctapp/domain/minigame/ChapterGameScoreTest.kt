package ru.nksk.lctapp.domain.minigame

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.chapterone.*

class ChapterGameScoreTest {
    private fun telescope() = TelescopeAssembly().install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
        .install(TelescopePart.LENS)
        .adjustFocus(TelescopeAssembly.FOCUS_TARGET).observe()

    private fun mark(): MarkState = MarkGame.strokes.flatten().fold(
        MarkGame.dust.fold(MarkState()) { state, p -> MarkGame.touch(state, p.x, p.y) }
    ) { state, p -> MarkGame.touch(state, p.x, p.y) }

    private fun wheel() = (0 until WheelRepair.SECTION_COUNT).fold(WheelRepair()) { state, _ ->
        state.rotateBy(-state.angle).secure()
    }

    private fun strap() = (0 until StrapRepair.STITCH_COUNT).fold(StrapRepair().align()) { state, p ->
        state.stitch(p)
    }.tighten()

    @Test fun incompletePhysicalWorkCannotBeSubmitted() {
        assertNull(DeedGameScore.fromTelescope(TelescopeAssembly()))
        assertNull(DeedGameScore.fromMark(MarkState()))
        assertNull(DeedGameScore.fromWheel(WheelRepair()))
        assertNull(DeedGameScore.fromStrap(StrapRepair()))
        assertNull(DeedGameScore.fromSignal(SignalState(SignalGame.code)))
        assertNull(DeedGameScore.fromRouteMap(StarChartState(setOf(0, 1))))
        assertNull(DeedGameScore.fromBoat(BoatJourney()))
    }

    @Test fun completedPhysicalWorkPaysAuthoredMaximumWithoutMotorSkillPenalty() {
        val scores = listOf(
            DeedGameScore.fromTelescope(telescope()),
            DeedGameScore.fromMark(mark()),
            DeedGameScore.fromWheel(wheel()),
            DeedGameScore.fromStrap(strap()),
            DeedGameScore.fromSignal(SignalGame.send(SignalGame.code.fold(SignalState(), SignalGame::choose))),
            DeedGameScore.fromRouteMap(RouteMapGame.points.indices.fold(StarChartState(), RouteMapGame::select)),
            DeedGameScore.fromBoat(BoatJourney(phase = BoatPhase.ARRIVED, plan = BoatPlan.RESERVE,
                distance = 1f, touchedRocks = setOf(1, 2), manualRepairs = 2, repair = BoatRepair.MANUAL, knotPoints = 3)),
        ).map(::checkNotNull)
        assertEquals(setOf(DeedGameKind.TELESCOPE, DeedGameKind.MARK, DeedGameKind.WHEEL, DeedGameKind.STRAP,
            DeedGameKind.SIGNAL, DeedGameKind.ROUTE_MAP, DeedGameKind.BOAT), scores.map { it.kind }.toSet())
        scores.forEach { score ->
            assertEquals(5L, score.reward(5))
            assertEquals(0L, score.reward(0))
            val decoded = Json.decodeFromString<DeedGameScore>(Json.encodeToString(score))
            assertEquals(score.kind, decoded.kind)
            assertEquals(1, decoded.correct)
            assertEquals(1, decoded.attempts)
        }
    }

    @Test fun completedFlagsDoNotHideInvalidModels() {
        assertNull(DeedGameScore.fromTelescope(telescope().copy(firstFastener = 0f)))
        assertNull(DeedGameScore.fromTelescope(telescope().copy(focus = Float.NaN)))
        assertNull(DeedGameScore.fromTelescope(telescope().copy(installedCount = 4)))
        assertNull(DeedGameScore.fromMark(mark().copy(cleaned = setOf(-1))))
        assertNull(DeedGameScore.fromMark(mark().copy(point = 2)))
        assertNull(DeedGameScore.fromWheel(wheel().copy(selectedSection = -1)))
        assertNull(DeedGameScore.fromWheel(wheel().copy(angle = Float.NaN)))
        assertNull(DeedGameScore.fromRouteMap(StarChartState((1..7).toSet())))
        assertNull(DeedGameScore.fromRouteMap(StarChartState((0..6).toSet(), selected = 0)))
        assertNull(DeedGameScore.fromBoat(BoatJourney(phase = BoatPhase.ARRIVED, plan = BoatPlan.RESERVE, distance = .6f)))
        assertNull(DeedGameScore.fromBoat(BoatJourney(phase = BoatPhase.ARRIVED, plan = BoatPlan.RESERVE,
            distance = 1f, touchedRocks = setOf(0))))
    }

    @Test fun restoredCompletedWheelStillProducesItsResult() {
        val finished = wheel()
        assertNotNull(DeedGameScore.fromWheel(WheelRepair.restore(finished.fixedMask, finished.selectedSection, finished.angle)))
    }

    @Test fun serializedChapterScoresCannotClaimPartialOrRepeatedRounds() {
        for (kind in listOf("TELESCOPE", "MARK", "WHEEL", "STRAP", "SIGNAL", "ROUTE_MAP", "BOAT")) {
            for ((correct, attempts) in listOf(0 to 1, 2 to 2, 1 to 2)) {
                assertTrue(runCatching {
                    Json.decodeFromString<DeedGameScore>("""{"kind":"$kind","correct":$correct,"attempts":$attempts}""")
                }.isFailure)
            }
        }
    }
}
