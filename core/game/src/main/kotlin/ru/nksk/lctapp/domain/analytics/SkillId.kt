package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Backend skill identities. JSON uses the FIN code, independently of the Kotlin enum name. */
@Serializable
enum class SkillId(val id: String) {
    @SerialName("FIN-01") COMPARE_AMOUNTS("FIN-01"),
    @SerialName("FIN-02") PLAN_BUDGET("FIN-02"),
    @SerialName("FIN-03") PRIORITIZE_NEEDS("FIN-03"),
    @SerialName("FIN-04") MAKE_MONEY_LAST("FIN-04"),
    @SerialName("FIN-05") SAVE_FOR_GOAL("FIN-05"),
    @SerialName("FIN-06") DELAY_PURCHASE("FIN-06"),
    @SerialName("FIN-07") BUILD_EMERGENCY_FUND("FIN-07"),
    @SerialName("FIN-08") ADAPT_AFTER_EXPENSE("FIN-08"),
    @SerialName("FIN-09") COMPARE_COSTS("FIN-09"),
    @SerialName("FIN-10") PLAN_EXTRA_INCOME("FIN-10"),
    @SerialName("FIN-11") RECONSIDER_DECISION("FIN-11"),
    @SerialName("FIN-12") UNDERSTAND_INCOME_AND_EXPENSES("FIN-12"),
}
