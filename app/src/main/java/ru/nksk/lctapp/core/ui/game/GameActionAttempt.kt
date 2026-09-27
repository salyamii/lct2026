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
    val before: GameState,
    val request: EngineRequest,
) {
    suspend fun submit(session: GameSession) = session.dispatch(request)

    /** A deduplicated request returns the latest world; feedback must describe its own receipt. */
    suspend fun committedState(session: GameSession, current: GameState): GameState? {
        val revision = request.expectedRevision
        if (revision != null && current.engine?.revision == revision + 1) return current
        return try {
            session.history().lastOrNull { it.request?.id == request.id &&
                it.type == ru.nksk.lctapp.domain.history.AuditType.COMMAND }?.after
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The write already succeeded. Missing feedback is not another failed write.
            null
        }
    }

    companion object {
        fun prepare(before: GameState, command: EngineCommand, context: DecisionContext? = null) =
            GameActionAttempt(before, EngineRequest(UUID.randomUUID().toString(), before.engine?.revision, command, context))
    }
}
