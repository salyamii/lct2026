package ru.nksk.lctapp.data.game

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetCosmetics

@RunWith(AndroidJUnit4::class)
class StarterAccessoryPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name = "starter-accessory-${java.util.UUID.randomUUID()}.db"
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository

    @Before fun open() { db = GameDatabase.open(context, name); games = RoomGameRepository(db) }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun initialAccessoryReturnsAfterALaterPurchaseAndPersistsWithoutDuplicatingHistory() = runBlocking {
        RoomStoryContentRepository(db).install(bundledGameCatalog().content)
        val initial = createInitialGameState().let { it.copy(pet = it.pet.copy(selectedLookId = "BANDANA")) }
        games.initializeIfAbsent(initial)
        val old = games.update { it.copy(pet = it.pet.copy(selectedLookId = "HAT"), ownedItems = listOf(
            OwnedItem("hat", "cosmetic-explorer-hat-v2"),
            OwnedItem("map-2", "stargazing-star-map-v1"), OwnedItem("map-1", "stargazing-star-map-v1"),
        )) }
        val oldHistory = games.readHistory()
        val reconciled = games.synchronizeStarterAccessory()
        assertEquals(old.ownedItems, reconciled.ownedItems.take(3))
        assertEquals(old, reconciled.copy(ownedItems = old.ownedItems))
        assertEquals("starter-bandana-v1", reconciled.ownedItems.last().itemId)
        assertTrue(PetCosmetics.canEquip(reconciled, "BANDANA"))
        assertFalse(PetCosmetics.canEquip(reconciled, "BACKPACK"))
        val history = games.readHistory()
        assertEquals(oldHistory, history.dropLast(1))
        assertEquals(AuditType.TECHNICAL_UPDATE, history.last().type)
        assertEquals(old, history.last().before)
        assertEquals(reconciled, history.last().after)
        assertTrue(history.last().facts.isEmpty())
        assertTrue(history.last().operations.isEmpty())
        assertTrue(games.pendingOutbox().any { it.id == history.last().id })
        assertEquals(reconciled, games.synchronizeStarterAccessory())
        assertEquals(history, games.readHistory())
        db.close(); db = GameDatabase.open(context, name); games = RoomGameRepository(db)
        assertEquals(reconciled, games.synchronizeStarterAccessory())
        assertEquals(history, games.readHistory())
        assertEquals(reconciled, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(games.exportSnapshot())).state)
    }

    @Test fun oldImportedSaveUsesOnlyItsKnownCurrentStarter() = runBlocking {
        RoomStoryContentRepository(db).install(bundledGameCatalog().content)
        val initial = createInitialGameState().let { it.copy(pet = it.pet.copy(selectedLookId = "BACKPACK")) }
        val baseline = AuditEntry("baseline", 1, "imported-run", AuditType.IMPORTED_BASELINE, after = initial)
        games.restoreSnapshot(HistoryCodec.snapshot("imported-run", initial, listOf(baseline)), RestoreGuard(null, 0))
        val restored = games.synchronizeStarterAccessory()
        assertEquals(listOf("starter-backpack-v1"), restored.ownedItems.map { it.itemId })
        assertEquals(initial.economy, restored.economy)
        assertFalse(PetCosmetics.canEquip(restored, "BANDANA"))
    }

    @Test fun missingCatalogDefinitionRollsBackOwnershipAndAuditTogether() = runBlocking {
        val initial = createInitialGameState().let { it.copy(pet = it.pet.copy(selectedLookId = "BANDANA")) }
        games.initializeIfAbsent(initial)
        val history = games.readHistory()
        assertNotNull("Missing accessory definition must fail", runCatching { games.synchronizeStarterAccessory() }.exceptionOrNull())
        assertEquals(initial, games.read())
        assertEquals(history, games.readHistory())
    }
}
