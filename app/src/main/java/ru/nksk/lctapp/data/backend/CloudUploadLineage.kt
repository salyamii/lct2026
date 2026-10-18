package ru.nksk.lctapp.data.backend

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.backend.CloudLocalUploadException
import ru.nksk.lctapp.domain.history.CloudWorldAncestor
import ru.nksk.lctapp.domain.history.CloudWorldRead
import ru.nksk.lctapp.domain.history.WorldSnapshot
import ru.nksk.lctapp.domain.history.WorldSnapshotCodec

/** Explicit cloud replacement references, never authored campaign completions or learning evidence. */
@Serializable
internal data class CloudUploadLineage(
    val runId: String,
    val generation: String,
    val predecessors: List<CloudWorldAncestor>,
) {
    init {
        require(runId.isNotBlank() && generation.isNotBlank())
        require(predecessors.none { it.runId == runId })
        require(predecessors.map { it.runId }.distinct().size == predecessors.size)
    }

    fun applyTo(local: CloudWorldRead): CloudWorldRead {
        if (!(local.world.runId == runId && local.generation == generation) &&
            local.world.predecessors.none { it.runId == runId && it.generation == generation }) return local
        val merged = local.world.predecessors.associateByTo(linkedMapOf()) { it.runId }
        predecessors.forEach { merged[it.runId] = it }
        val world = local.world
        return local.copy(world = WorldSnapshotCodec.create(world.runId, world.state, world.historySequence,
            world.generation, world.parentRewards, merged.values.toList()))
    }

    companion object {
        fun replacing(local: CloudWorldRead, remote: WorldSnapshot?): CloudUploadLineage {
            val merged = local.world.predecessors.associateByTo(linkedMapOf()) { it.runId }
            remote?.predecessors?.forEach { merged[it.runId] = it }
            if (remote != null && remote.runId != local.world.runId) {
                merged[remote.runId] = CloudWorldAncestor(remote.runId, remote.generation, remote.historySequence)
            }
            if (local.world.runId in merged) {
                throw CloudLocalUploadException("Облако уже содержит следующее прохождение. Этот сервер не позволяет заменить его более ранним.")
            }
            return CloudUploadLineage(local.world.runId, local.generation, merged.values.toList())
        }
    }
}
