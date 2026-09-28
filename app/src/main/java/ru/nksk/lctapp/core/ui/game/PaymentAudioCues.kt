package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.engine.AppliedGameCommand
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog

/** Cue selection is read-only. Only an actual decrease of total money constitutes a payment. */
internal fun paymentAudioCues(applied: AppliedGameCommand, catalog: GameCatalog): List<String> {
    if (applied.after.economy.balance >= applied.before.economy.balance) return emptyList()
    val choiceId = when (val command = applied.request.command) {
        is EngineCommand.CompleteEvent -> command.choiceId
        is EngineCommand.CompleteStoryGame -> command.choiceId
        is EngineCommand.Choose -> command.choiceId
        else -> null
    }
    val choice = choiceId?.let { id -> catalog.content.choices.find { it.id == id } }
    val audio = choice?.let { catalog.cards[it.eventId]?.presentation?.media?.actionAudio?.get(it.id) }
    return listOfNotNull(audio?.soundCueKey ?: "sound.payment", audio?.voiceCueKey).distinct()
}
