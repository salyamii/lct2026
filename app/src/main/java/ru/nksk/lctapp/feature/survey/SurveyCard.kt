package ru.nksk.lctapp.feature.survey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.GameInk

@Composable
internal fun SurveyCard(state: SurveyUiState, invitation: Boolean, onOpen: () -> Unit,
    onCompleted: () -> Unit, onRetry: () -> Unit = {}, onLater: () -> Unit = {}) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, GameInk.copy(alpha = .12f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (invitation) "Как тебе игра?" else "Опрос и подарок", color = GameInk,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AdventureBody("Расскажи о своих впечатлениях и получи кепку для питомца в подарок. Выбрать кепку можно в опроснике.")
            if (state.completed == true) {
                AdventureBody("Спасибо за ответы! Больше не будем напоминать об этом опросе.")
            } else {
                AdventureBody("После отправки анкеты вернись в игру с интернетом, чтобы получить подарок. Затем нажми «Я прошёл опрос», чтобы отключить напоминания.")
            }
            GameActionButton(if (state.busy) "Подождём…" else "Открыть опрос", onOpen,
                enabled = state.ready,
                style = if (state.completed == true) GameActionStyle.SECONDARY else GameActionStyle.PRIMARY)
            if (state.completed == false) {
                GameActionButton("Я прошёл опрос", onCompleted, enabled = state.ready,
                    style = GameActionStyle.SECONDARY)
            }
            state.error?.let { error ->
                Text(when (error) {
                    SurveyError.READ -> "Не удалось прочитать отметку об опросе. Попробуй ещё раз."
                    SurveyError.SAVE -> "Не удалось сохранить отметку. Нажми «Я прошёл опрос» ещё раз."
                    SurveyError.OPEN -> "Не удалось открыть опрос. Проверь, что установлен браузер, и попробуй ещё раз."
                }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = GameInk,
                    style = MaterialTheme.typography.bodyMedium)
                if (error == SurveyError.READ) GameActionButton("Повторить", onRetry,
                    style = GameActionStyle.SECONDARY, enabled = !state.busy)
            }
            if (invitation) GameActionButton("В другой раз", onLater, enabled = !state.busy,
                style = GameActionStyle.QUIET)
        }
    }
}
