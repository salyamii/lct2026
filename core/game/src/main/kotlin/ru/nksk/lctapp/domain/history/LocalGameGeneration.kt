package ru.nksk.lctapp.domain.history

/** A restore, including one of this same run, invalidates in-flight responses from the replaced world. */
fun localGameGeneration(runId: String, latestRestoreEntryId: String?): String = HistoryCodec.sha256(
    listOf(runId, latestRestoreEntryId.orEmpty()).joinToString("") { "${it.length}:$it" },
)

fun GameSnapshot.localGeneration(): String = localGameGeneration(runId,
    history.lastOrNull { it.type == AuditType.RESTORED }?.id)
