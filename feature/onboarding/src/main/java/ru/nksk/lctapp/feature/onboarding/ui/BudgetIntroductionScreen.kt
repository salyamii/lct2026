package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Illustration slots are supplied by app composition, independently of the economy feature. */
enum class OnboardingBudgetCategory { Needs, Wants, Savings, Reserve }

@Composable
fun BudgetIntroductionScreen(
    artwork: CustomizationArtwork,
    categoryArtwork: @Composable (OnboardingBudgetCategory, Modifier) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(GoalCream)) {
        Image(painterResource(artwork.background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize()) {
            CustomizationHeader(onBack, artwork, saving = saving, title = "Монеты для приключения",
                backDescription = "Назад к выбору цели")
            Spacer(Modifier.height(12.dp))
            Surface(Modifier.weight(1f).fillMaxWidth(), color = GoalCream,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
                        BudgetIntroductionContent(artwork, categoryArtwork, saveFailed)
                        GoalFooter("Распределить монеты", artwork, !saving, onContinue)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.BudgetIntroductionContent(
    artwork: CustomizationArtwork,
    categoryArtwork: @Composable (OnboardingBudgetCategory, Modifier) -> Unit,
    saveFailed: Boolean,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GoalHeading("На что оставим монеты?", artwork)
        BudgetIntroBody("Гильдия Смотрителей каждую неделю даёт нам 100 монет " +
            "на исследования и всё необходимое. То, что не потратим, останется у нас.", artwork)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetCategoryCard(OnboardingBudgetCategory.Needs, "Нужно",
                "Нам нужно есть хотя бы раз в день.",
                Color(0xffe4f2ed), artwork, categoryArtwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Wants, "Хочу",
                "На угощения, игрушки и другие радости.",
                Color(0xffe8effb), artwork, categoryArtwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Savings, "В копилку",
                "Монеты, которые хотим сберечь для большой цели.",
                Color(0xfffff0d3), artwork, categoryArtwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Reserve, "Запас",
                "Пригодится, если в пути что-нибудь сломается.",
                Color(0xffeee6f7), artwork, categoryArtwork)
        }
        BudgetIntroBody("Когда решишь отложить монеты, переложи их в копилку.", artwork)
    }
    if (saveFailed) {
        Text("Не удалось сохранить игру. Попробуй ещё раз.", Modifier.padding(horizontal = 22.dp),
            color = MaterialTheme.colorScheme.error, fontFamily = artwork.bodyFont, fontSize = 14.sp)
    }
}

@Composable
private fun BudgetIntroBody(text: String, artwork: CustomizationArtwork) {
    Text(text, color = GoalInk.copy(alpha = .85f), fontFamily = artwork.bodyFont,
        fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
private fun BudgetCategoryCard(
    category: OnboardingBudgetCategory,
    title: String,
    description: String,
    tint: Color,
    artwork: CustomizationArtwork,
    illustration: @Composable (OnboardingBudgetCategory, Modifier) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().background(tint.copy(alpha = .5f), shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        illustration(category, Modifier.size(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, Modifier.semantics { heading() }, color = GoalInk,
                fontFamily = artwork.titleFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            BudgetIntroBody(description, artwork)
        }
    }
}
