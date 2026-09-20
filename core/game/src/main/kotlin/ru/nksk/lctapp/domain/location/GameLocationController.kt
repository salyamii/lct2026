package ru.nksk.lctapp.domain.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

/** Explicit stable codes are shared by storage and map metadata, not enum ordinals. */
enum class GameLocation(val code: String) {
    CITY("city"), WORKSHOP("workshop"), GATES("gates"), WINDMILL("windmill"),
    HILL("hill"), TRAIL("trail"), FAIR("fair"), PIER("pier"), OBSERVATORY("observatory");

    companion object {
        fun fromCode(code: String): GameLocation = requireNotNull(entries.find { it.code == code }) {
            "Unknown location code: $code"
        }
    }
}

enum class LocationLighting { DAY, EVENING }

data class LocationScene(
    val location: GameLocation = GameLocation.CITY,
    val lighting: LocationLighting = LocationLighting.DAY,
)

data class LocationMapState(val scene: LocationScene, val availableLocations: Set<GameLocation>)

/** Future story rules plug in here; evaluating access is pure and never advances the story. */
fun interface LocationAccessPolicy {
    fun isAvailable(location: GameLocation, game: GameState): Boolean
}

object AllLocationsAvailable : LocationAccessPolicy {
    override fun isAvailable(location: GameLocation, game: GameState) = true
}

class LocationUnavailableException(val location: GameLocation) : IllegalStateException("Location is locked: ${location.code}")

/** Explicit API for application/domain callers. There is no fatigue, clock or day-phase policy. */
interface GameLocationController {
    fun observe(): Flow<LocationMapState?>
    suspend fun selectLocation(location: GameLocation)
    suspend fun setLighting(lighting: LocationLighting)
}

class DefaultGameLocationController(
    private val games: GameRepository,
    private val access: LocationAccessPolicy,
) : GameLocationController {
    override fun observe(): Flow<LocationMapState?> = games.observe().map { game ->
        game?.let { LocationMapState(it.locationScene, GameLocation.entries.filter { location ->
            access.isAvailable(location, it)
        }.toSet()) }
    }.distinctUntilChanged()

    override suspend fun selectLocation(location: GameLocation) {
        games.update { latest ->
            if (!access.isAvailable(location, latest)) throw LocationUnavailableException(location)
            latest.copy(locationScene = latest.locationScene.copy(location = location))
        }
    }

    override suspend fun setLighting(lighting: LocationLighting) {
        games.update { latest -> latest.copy(locationScene = latest.locationScene.copy(lighting = lighting)) }
    }
}
