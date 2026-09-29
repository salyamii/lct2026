package ru.nksk.lctapp.data.backend

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.backend.ParentMaterialsCatalog
import ru.nksk.lctapp.domain.backend.ParentMaterialsRepository
import ru.nksk.lctapp.domain.backend.ParentMaterialsState

/** Bundled editorial content works offline. A validated remote edition replaces it in memory. */
@Singleton
internal class RemoteParentMaterialsRepository internal constructor(
    private val backend: BackendConnection,
    private val readBundled: suspend () -> String,
) : ParentMaterialsRepository {
    @Inject constructor(@ApplicationContext context: Context, backend: BackendConnection) : this(
        backend,
        { withContext(Dispatchers.IO) {
            context.assets.open("parents/materials.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } },
    )

    private val state = MutableStateFlow(ParentMaterialsState())
    private val initialization = Mutex()
    private val refreshMutex = Mutex()
    private var initialized = false

    override fun observe() = flow {
        initialize()
        emitAll(state)
    }

    private suspend fun initialize() = initialization.withLock {
        if (initialized) return@withLock
        try {
            val bundled = withContext(Dispatchers.Default) {
                BackendJson.decodeFromString<ParentMaterialsCatalog>(readBundled())
            }
            state.value = ParentMaterialsState(catalog = bundled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.value = ParentMaterialsState(refreshFailed = true)
        }
        initialized = true
    }

    override suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        try {
            initialize()
            if (!backend.configured) return
            state.update { it.copy(isRefreshing = true, refreshFailed = false) }
            try {
                val remote = backend.api.parentMaterials()
                state.value = ParentMaterialsState(catalog = remote)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(refreshFailed = true) }
            } finally {
                state.update { it.copy(isRefreshing = false) }
            }
        } finally {
            refreshMutex.unlock()
        }
    }
}
