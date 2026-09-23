package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    AdventureGoalLayout(artwork, artwork.background, "План для приключения",
        "Назад к выбору цели", onBack, saving = saving, stage = {}) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GoalHeading("Большие планы начинаются с маленьких решений", artwork)
            BudgetIntroBody("Цель выбрана! Теперь давай подготовимся к путешествию.", artwork)
            Column(
                Modifier.fillMaxWidth().background(Color(0xffefebfb), RoundedCornerShape(20.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("100 монет", color = GoalPurple, fontFamily = artwork.titleFont,
                    fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                BudgetIntroBody("В начале игры ты получишь 100 монет, а через каждые 7 игровых дней — ещё 100. " +
                    "Монетки, которые ты не потратишь, останутся у тебя.", artwork)
            }
            BudgetIntroBody("Перед началом пути реши, сколько монет на что отложить. " +
                "Это и есть планирование бюджета. У твоих монет будет четыре назначения:", artwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Needs, "Нужно",
                "На еду для твоего спутника. При планировании недели оставь здесь хотя бы 35 монет.",
                Color(0xffe4f2ed), artwork, categoryArtwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Wants, "Хочу",
                "На приятные покупки и маленькие радости.",
                Color(0xffe8effb), artwork, categoryArtwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Savings, "Коплю",
                "На вещи для большой цели. Их можно купить только на монеты из этой части бюджета.",
                Color(0xfffff0d3), artwork, categoryArtwork)
            BudgetCategoryCard(OnboardingBudgetCategory.Reserve, "Запас",
                "На неожиданности в пути, например ремонт сломанной вещи.",
                Color(0xffeee6f7), artwork, categoryArtwork)
            BudgetIntroBody("Если потратить всё на желания, на важную покупку может не хватить. " +
                "А если только копить — можно забыть о нужном и приятном. Постарайся найти свой баланс!", artwork)
            Text("Распредели все монетки между четырьмя категориями. " +
                "Если планы изменятся, распределение тоже можно изменить.",
                color = GoalPurple, fontFamily = artwork.bodyFont, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, lineHeight = 25.sp)
        }
        if (saveFailed) {
            Text("Не удалось сохранить игру. Попробуй ещё раз.", Modifier.padding(horizontal = 22.dp),
                color = MaterialTheme.colorScheme.error, fontFamily = artwork.bodyFont, fontSize = 14.sp)
        }
        GoalFooter("Дальше", artwork, !saving, onContinue)
    }
}

@Composable
private fun BudgetIntroBody(text: String, artwork: CustomizationArtwork) {
    Text(text, color = GoalInk.copy(alpha = .85f), fontFamily = artwork.bodyFont,
        fontSize = 17.sp, lineHeight = 25.sp)
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
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .8f), shape)
            .border(2.dp, tint, shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(60.dp).background(tint, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            illustration(category, Modifier.size(60.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, Modifier.semantics { heading() }, color = GoalInk,
                fontFamily = artwork.titleFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            BudgetIntroBody(description, artwork)
        }
    }
}
