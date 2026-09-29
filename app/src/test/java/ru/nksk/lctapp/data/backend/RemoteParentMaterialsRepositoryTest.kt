package ru.nksk.lctapp.data.backend

import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.backend.ParentMaterialsCatalog
import ru.nksk.lctapp.domain.backend.ParentSkillMaterialDto

class RemoteParentMaterialsRepositoryTest {
    @Test fun bundledQuestionsAreAvailableWithoutBackendAndWithoutAssessments() = runTest {
        val bundled = catalogue("bundled")
        val repository = RemoteParentMaterialsRepository(BackendConnection("")) { BackendJson.encodeToString(bundled) }
        assertEquals(bundled, repository.observe().first().catalog)
        repository.refresh()
        assertEquals(bundled, repository.observe().first().catalog)
        assertFalse(repository.observe().first().refreshFailed)
    }

    @Test fun remoteFailureRetainsCompleteBundledCatalogue() = runTest {
        val bundled = catalogue("bundled")
        val repository = RemoteParentMaterialsRepository(connection { throw IOException("offline") }) {
            BackendJson.encodeToString(bundled)
        }
        repository.refresh()
        assertEquals(bundled, repository.observe().first().catalog)
        assertTrue(repository.observe().first().refreshFailed)
        assertFalse(repository.observe().first().isRefreshing)
    }

    @Test fun successfulRefreshReplacesCatalogueAndCancellationDoesNotMarkItFailed() = runTest {
        val remote = catalogue("remote")
        var cancelled = false
        val repository = RemoteParentMaterialsRepository(connection {
            if (cancelled) throw CancellationException()
            remote
        }) { BackendJson.encodeToString(catalogue("bundled")) }
        repository.refresh()
        assertEquals(remote, repository.observe().first().catalog)
        cancelled = true
        try { repository.refresh(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(remote, repository.observe().first().catalog)
        assertFalse(repository.observe().first().refreshFailed)
        assertFalse(repository.observe().first().isRefreshing)
    }

    @Test fun malformedRemoteJsonDoesNotReplaceOfflineMaterials() = runTest {
        val bundled = catalogue("bundled")
        val repository = RemoteParentMaterialsRepository(connection {
            BackendJson.decodeFromString<ParentMaterialsCatalog>("""{"schemaVersion":1,"contentVersion":"bad","skills":[]}""")
        }) { BackendJson.encodeToString(bundled) }
        repository.refresh()
        assertEquals(bundled, repository.observe().first().catalog)
        assertTrue(repository.observe().first().refreshFailed)
    }

    @Test fun unpublishedBundleAndRemoteAreValidUntilMaterialsAreAuthored() = runTest {
        val unpublished = ParentMaterialsCatalog("unpublished", emptyList(), publicationStatus =
            ru.nksk.lctapp.domain.backend.ParentMaterialsPublicationStatus.UNPUBLISHED)
        val repository = RemoteParentMaterialsRepository(connection { unpublished }) {
            BackendJson.encodeToString(unpublished)
        }
        assertEquals(unpublished, repository.observe().first().catalog)
        assertFalse(repository.observe().first().refreshFailed)
        repository.refresh()
        assertEquals(unpublished, repository.observe().first().catalog)
        assertFalse(repository.observe().first().refreshFailed)
    }

    private fun connection(load: () -> ParentMaterialsCatalog) = BackendConnection("https://example.test/") {
        Proxy.newProxyInstance(BackendApi::class.java.classLoader, arrayOf(BackendApi::class.java)) { _, method, _ ->
            check(method.name == "parentMaterials")
            load()
        } as BackendApi
    }

    private fun catalogue(version: String) = ParentMaterialsCatalog(version, (1..12).map {
        ParentSkillMaterialDto("FIN-%02d".format(it), "Цель", "История", "Пример", List(5) { "Вопрос $it?" },
            "Вывод", "Основание", listOf("https://example.org/source"))
    })
}
