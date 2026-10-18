package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.annotation.StringRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.BalanceNeed
import ru.nksk.lctapp.domain.minigame.BalanceScenario
import ru.nksk.lctapp.domain.minigame.NeedWantSituation

internal val NeedWantSituation.promptRes: Int
    @StringRes get() = when (this) {
        NeedWantSituation.NIGHT_WALK -> R.string.practice_need_night_context
        NeedWantSituation.DAY_WALK -> R.string.practice_need_day_context
        NeedWantSituation.SUNNY_DAY -> R.string.practice_need_sun_context
        NeedWantSituation.SPARE_CAP -> R.string.practice_need_cap_context
        NeedWantSituation.HOT_WALK -> R.string.practice_need_water_context
        NeedWantSituation.NO_SNACK -> R.string.practice_need_snack_context
        NeedWantSituation.PACKED_LUNCH -> R.string.practice_need_lunch_context
        NeedWantSituation.TOY_ON_WALK -> R.string.practice_need_toy_context
    }

internal val NeedWantSituation.explanationRes: Int
    @StringRes get() = when (this) {
        NeedWantSituation.NIGHT_WALK -> R.string.practice_need_lantern
        NeedWantSituation.DAY_WALK -> R.string.practice_want_day_lantern
        NeedWantSituation.SUNNY_DAY -> R.string.practice_need_sun_cap
        NeedWantSituation.SPARE_CAP -> R.string.practice_want_cap
        NeedWantSituation.HOT_WALK -> R.string.practice_need_water
        NeedWantSituation.NO_SNACK -> R.string.practice_need_bun
        NeedWantSituation.PACKED_LUNCH -> R.string.practice_want_extra_bun
        NeedWantSituation.TOY_ON_WALK -> R.string.practice_want_boat
    }

internal val BalanceScenario.promptRes: Int
    @StringRes get() = when (this) {
        BalanceScenario.PICNIC -> R.string.practice_balance_picnic
        BalanceScenario.NIGHT_WALK -> R.string.practice_balance_night
        BalanceScenario.WORKBENCH -> R.string.practice_balance_workbench
    }

internal val BalanceNeed.explanationRes: Int
    @StringRes get() = when (this) {
        BalanceNeed.FOOD -> R.string.practice_balance_need_food
        BalanceNeed.DRINK -> R.string.practice_balance_need_drink
        BalanceNeed.ONE_GAME -> R.string.practice_balance_need_game
        BalanceNeed.LIGHT -> R.string.practice_balance_need_light
        BalanceNeed.WARMTH -> R.string.practice_balance_need_warmth
        BalanceNeed.BOARD -> R.string.practice_balance_need_board
        BalanceNeed.FASTENERS -> R.string.practice_balance_need_fasteners
    }
