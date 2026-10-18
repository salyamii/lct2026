package ru.nksk.lctapp.data.backend

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.history.CloudWorldAncestor
import ru.nksk.lctapp.domain.history.CloudWorldRead
import ru.nksk.lctapp.domain.history.WorldSnapshotCodec

class CloudUploadLineageTest {
    @Test fun anExplicitChoiceCannotAttachToAnUnrelatedRunOrARestoredGeneration() {
        val local = world("local", "local-generation")
        val cloud = world("cloud", "cloud-generation")
        val choice = CloudUploadLineage.replacing(local, cloud.world)
        val reset = world("reset", "reset-generation")
        val restored = world("local", "restored-generation")
        assertEquals(reset, choice.applyTo(reset))
        assertEquals(restored, choice.applyTo(restored))
        val linked = choice.applyTo(local)
        assertEquals(local.world.state, linked.world.state)
        assertEquals(local.world.runId, linked.world.runId)
        assertEquals(local.world.generation, linked.world.generation)
        assertEquals(local.world.parentRewards, linked.world.parentRewards)
    }

    @Test fun serverLineageIsRetainedExactlyWithoutCopyingItsGameState() {
        val local = world("local", "local-generation")
        val ancestor = CloudWorldAncestor("server-ancestor", "server-generation", 81)
        val cloud = WorldSnapshotCodec.create("cloud", createInitialGameState().copy(
            pet = createInitialGameState().pet.copy(name = "Облачная игра")), 90, "cloud-generation",
            predecessors = listOf(ancestor))
        val linked = CloudUploadLineage.replacing(local, cloud).applyTo(local)
        assertEquals(local.world.state, linked.world.state)
        assertEquals(listOf(ancestor, CloudWorldAncestor("cloud", "cloud-generation", 90)), linked.world.predecessors)
        WorldSnapshotCodec.validate(linked.world)
    }

    private fun world(runId: String, generation: String) = CloudWorldRead(
        WorldSnapshotCodec.create(runId, createInitialGameState(), 1, generation), 1, "$runId:initial")
}
