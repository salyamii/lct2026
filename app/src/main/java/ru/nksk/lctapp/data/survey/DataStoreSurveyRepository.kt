package ru.nksk.lctapp.data.survey

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository
import ru.nksk.lctapp.domain.survey.SurveyInvitation
import ru.nksk.lctapp.domain.survey.SurveyInvitationPolicy
import ru.nksk.lctapp.domain.survey.SurveyRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SurveyStore

internal class DataStoreSurveyRepository @Inject constructor(
    @param:SurveyStore private val store: DataStore<Preferences>,
    private val profiles: ParentLinkRepository,
    private val games: GameRepository,
) : SurveyRepository {
    override fun observeCompleted() = store.data.map { it[Completed] ?: false }.distinctUntilChanged()

    override suspend fun markCompleted() { store.edit { it[Completed] = true } }

    override suspend fun claimInvitation(): SurveyInvitation? {
        val head = games.readSnapshotHead() ?: return null
        val day = head.state.engine?.takeIf { it.phase == DayPhase.FINISHED }?.day ?: return null
        val lastDay = intPreferencesKey("last_invited_day_${head.runId}")
        var invitation: SurveyInvitation? = null
        store.edit { saved ->
            if (SurveyInvitationPolicy.canInvite(saved[Completed] ?: false, day, saved[lastDay])) {
                saved[lastDay] = day
                invitation = SurveyInvitation(head.runId, day)
            }
        }
        return invitation
    }

    override suspend fun formUrl(): String {
        val profile = profiles.profile()
        val head = checkNotNull(games.readSnapshotHead()) { "No active game for the questionnaire" }
        require(profile.profileId.isNotBlank() && head.runId.isNotBlank())
        return FormUrl.toHttpUrl().newBuilder()
            .addQueryParameter("deviceId", profile.profileId)
            .addQueryParameter("gameRunId", head.runId)
            .build().toString()
    }

    private companion object {
        const val FormUrl = "https://forms.yandex.ru/u/6ad49a05852af395fcc7a0af/"
        val Completed = booleanPreferencesKey("completed")
    }
}
