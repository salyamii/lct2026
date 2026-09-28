package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.EventActionAudio

/** Authored paid choices only. Playing this cue never executes the payment itself. */
internal fun paymentActionAudio(eventId: String, paidChoiceKeys: List<String>): Map<String, EventActionAudio> =
    paidChoiceKeys.associate { "$eventId:$it" to EventActionAudio(soundCueKey = "sound.payment") }
