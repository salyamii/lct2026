package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "План · Макет", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun PlanReadyPreview() = BudgetPreview(BudgetUiState(40, 30, 50, 20, 0, 100))

@Preview(name = "План · Новые деньги", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun PlanIncomingPreview() = BudgetPreview(BudgetUiState(10, 5, 20, 5, 100, 100))

@Preview(name = "План · Осталось 3", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun PlanRemainderPreview() = BudgetPreview(BudgetUiState(40, 30, 47, 20, 3, 100))

@Preview(name = "План · Обычный телефон", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "План · Узкий экран", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "План · Крупный шрифт", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Preview(name = "План · Landscape", widthDp = 800, heightDp = 400, showBackground = true)
@Preview(name = "План · Планшет", widthDp = 800, heightDp = 1100, showBackground = true)
@Composable
private fun PlanAdaptivePreview() = BudgetPreview(BudgetUiState(40, 30, 50, 20, 0, 100))
