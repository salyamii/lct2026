package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keep actions outside a scrolling body while both have a usable viewport.
 * Measure actions at their natural height first: a constrained Column otherwise
 * gives its final buttons zero remaining height. If actions do not fit, the same
 * body and actions form one scrollable panel, without shrinking their contents.
 */
@Composable
internal fun AdaptiveActionPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    contentSpacing: Dp = 12.dp,
    actionSpacing: Dp = 8.dp,
    sectionSpacing: Dp = 12.dp,
    fillBody: Boolean = false,
    contentScrollState: ScrollState? = null,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bodyScroll = contentScrollState ?: rememberScrollState()
    val overflowScroll = key(bodyScroll) { rememberScrollState() }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (!constraints.hasBoundedHeight) {
            Column(Modifier.fillMaxWidth().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(sectionSpacing)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(actionSpacing), content = actions)
            }
        } else {
            val viewportHeight = constraints.maxHeight
            val verticalPadding = with(density) {
                (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()).roundToPx()
            }
            val minimumBodyViewport = with(density) { 64.dp.roundToPx() }
            SubcomposeLayout(Modifier.fillMaxWidth().verticalScroll(overflowScroll).padding(contentPadding)) { childConstraints ->
                val unbounded = childConstraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                val actionPlaceable = subcompose("actions") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(actionSpacing), content = actions)
                }.single().measure(unbounded)
                val gap = if (actionPlaceable.height > 0) sectionSpacing.roundToPx() else 0
                val usableHeight = (viewportHeight - verticalPadding).coerceAtLeast(0)
                val keepActionsOutsideScroll = actionPlaceable.height + gap + minimumBodyViewport <= usableHeight
                val bodyHeight = (usableHeight - actionPlaceable.height - gap).coerceAtLeast(0)
                val bodyPlaceable = subcompose("body") {
                    Column(Modifier.fillMaxWidth().then(
                        if (keepActionsOutsideScroll) Modifier.verticalScroll(bodyScroll) else Modifier
                    ), verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
                }.single().measure(unbounded.copy(
                    minHeight = if (keepActionsOutsideScroll && fillBody) bodyHeight else 0,
                    maxHeight = if (keepActionsOutsideScroll) bodyHeight else Constraints.Infinity,
                ))
                val naturalHeight = bodyPlaceable.height + gap + actionPlaceable.height
                layout(childConstraints.maxWidth, naturalHeight.coerceAtLeast(childConstraints.minHeight)) {
                    bodyPlaceable.placeRelative(0, 0)
                    actionPlaceable.placeRelative(0, bodyPlaceable.height + gap)
                }
            }
        }
    }
}
