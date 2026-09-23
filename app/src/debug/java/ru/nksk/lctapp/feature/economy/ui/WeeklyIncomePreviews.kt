package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Новая неделя · Полный сценарий", widthDp = 402, heightDp = 874, showBackground = true)
@Preview(name = "Новая неделя · Обычный телефон", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "Новая неделя · Узкий экран", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Новая неделя · Крупный шрифт", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Preview(name = "Новая неделя · Landscape", widthDp = 800, heightDp = 400, showBackground = true)
@Composable
private fun WeeklyIncomePreview() = BudgetPreview(
    initial = BudgetUiState(10, 5, 20, 5, unallocated = 100, weeklyIncome = 100),
    showIncomeFirst = true,
)
