package ru.nksk.lctapp.core.ui.game

import java.util.UUID
import kotlinx.coroutines.CancellationException
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameState

/** One user intent. An uncertain write must keep its original identity, revision and evidence. */
internal class GameActionAttempt private constructor(
    private val originalBefore: GameState?,
    val request: EngineRequest,
) {
    /** A restored request has no historical snapshot; callers must read its indexed receipt. */
    val before: GameState get() = checkNotNull(originalBefore) { "A restored attempt has no original world" }

    suspend fun submit(session: GameSession) = session.dispatch(request)

    /** A deduplicated request returns the latest world; feedback must describe its own receipt. */
    suspend fun committedState(session: GameSession, current: GameState): GameState? {
        val revision = request.expectedRevision
        if (revision != null && current.engine?.revision == revision + 1) return current
        return try {
            session.commandReceipt(request.id)?.after
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The write already succeeded. Missing feedback is not another failed write.
            null
        }
    }

    companion object {
        /** Restore only the original intent, never substitute today's world for its historical before. */
        fun restore(request: EngineRequest) = GameActionAttempt(null, request)

        fun prepare(before: GameState, command: EngineCommand, context: DecisionContext? = null, demoMode: Boolean = false) =
            GameActionAttempt(before, EngineRequest(UUID.randomUUID().toString(), before.engine?.revision, command, context, demoMode))
    }
}
