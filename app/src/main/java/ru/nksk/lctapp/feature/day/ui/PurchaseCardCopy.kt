package ru.nksk.lctapp.feature.day.ui

/** Screen copy for existing offers; immutable prices and saved choice definitions stay intact. */
internal data class PurchaseCardCopy(val title: String, val body: String, val action: String = "Купить")

internal fun purchaseCardCopy(eventId: String, petName: String): PurchaseCardCopy? = when (eventId) {
    "figma-2654-2-purchase-v2" -> PurchaseCardCopy("Ароматная булочка",
        "Заменяет обычный приём пищи. $petName будет доволен: она гораздо вкуснее обычного обеда.")
    "figma-2164-2-v1", "figma-2654-50-purchase-v2" -> PurchaseCardCopy("Кепка исследователя",
        "$petName примеряет кепку путешественника. Её можно надеть и носить в новых приключениях.")
    "figma-2654-98-purchase-v2" -> PurchaseCardCopy("Кольцеброс",
        "Бросим кольца и проверим меткость? Это весёлое развлечение без денежных призов.", "Сыграть")
    "figma-2654-146-purchase-v2" -> PurchaseCardCopy("Компасный брелок",
        "Маленький компас украсит походный образ. Его можно надеть в снаряжении.")
    "figma-2654-194-purchase-v2" -> PurchaseCardCopy("Игрушечный кораблик",
        "Кораблик с маленьким парусом пополнит нашу коллекцию. Он останется в снаряжении.")
    "figma-56-55-purchase-v2" -> PurchaseCardCopy("Очки пилота",
        "$petName примеряет очки пилота. С ними можно отправиться на прогулку в новом образе.")
    "figma-56-49-purchase-v2" -> PurchaseCardCopy("Нашивка путешественника",
        "Нашивка с дорожным знаком украсит походный образ.")
    "figma-56-64-purchase-v2" -> PurchaseCardCopy("Бинокль исследователя",
        "Бинокль дополнит образ исследователя. Его можно надеть в снаряжении.")
    else -> null
}
