package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/** Shared onboarding item carousel: centered selection, neighbouring cards, swipe and page indicator. */
@Composable
fun <T> ItemCarousel(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: @Composable () -> Unit = {},
    itemContent: @Composable (T, Boolean, Modifier, Dp) -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = selectedIndex.coerceIn(items.indices), pageCount = { items.size })
    val latestOnSelect = rememberUpdatedState(onSelect)
    val blocked by rememberUpdatedState(!enabled)
    LaunchedEffect(selectedIndex) {
        if (pager.currentPage != selectedIndex && !pager.isScrollInProgress) pager.scrollToPage(selectedIndex)
    }
    LaunchedEffect(pager.currentPage) {
        if (!blocked) latestOnSelect.value(pager.currentPage)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        hint()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Center one page while fitting whole neighbours, including their shadow margins.
            val visiblePages = when {
                maxWidth >= 592.dp -> 5
                maxWidth >= 328.dp -> 3
                else -> 1
            }
            val pageWidth = if (visiblePages == 1) minOf(maxWidth, 208.dp)
                else (maxWidth - 8.dp * (visiblePages - 1)) / visiblePages
            val artworkSize = minOf(144.dp, pageWidth - 32.dp, maxHeight - 70.dp)
                .coerceAtLeast(64.dp)
            HorizontalPager(
                state = pager,
                userScrollEnabled = enabled,
                modifier = Modifier.fillMaxSize().testTag("accessory_pager"),
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(horizontal = (maxWidth - pageWidth) / 2),
                pageSpacing = 8.dp,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val item = items[page]
                // Transform the card and its shadow together, with room for the shadow.
                // Availability alone controls dimming; side pages keep their original colors.
                Box(Modifier.fillMaxWidth().graphicsLayer {
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val distance = offset.absoluteValue
                    scaleX = 1f - .14f * distance
                    scaleY = 1f - .14f * distance
                    translationY = 10.dp.toPx() * distance
                    rotationY = offset * 8f
                    cameraDistance = 12f * density
                    compositingStrategy = CompositingStrategy.Offscreen
                }.padding(8.dp)) {
                    itemContent(item, page == selectedIndex,
                        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp), clip = false), artworkSize)
                }
            }
        }
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            repeat(items.size) { index ->
                Box(Modifier.size(if (index == pager.currentPage) 8.dp else 6.dp)
                    .align(Alignment.CenterVertically)
                    .background(if (index == pager.currentPage) Color(0xff231942) else Color(0xffd3cfdd), CircleShape))
            }
            Text("${pager.currentPage + 1} из ${items.size}", color = Color(0xff79738b), fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}
