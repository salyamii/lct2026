package ru.nksk.lctapp.core.ui.location

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.location.*

@DrawableRes
fun locationArtwork(scene: LocationScene): Int {
    val evening = scene.lighting == LocationLighting.EVENING
    return when (scene.location) {
        GameLocation.CITY -> if (evening) R.drawable.location_city_evening else R.drawable.location_city
        GameLocation.GATES -> if (evening) R.drawable.location_gates_evening else R.drawable.location_gates
        GameLocation.WINDMILL -> if (evening) R.drawable.location_windmill_evening else R.drawable.location_windmill
        GameLocation.WORKSHOP -> if (evening) R.drawable.location_workshop else R.drawable.location_workshop_day
        GameLocation.HILL -> if (evening) R.drawable.location_hill else R.drawable.location_hill_day
        GameLocation.TRAIL -> if (evening) R.drawable.location_trail else R.drawable.location_trail_day
        GameLocation.FAIR -> if (evening) R.drawable.location_fair else R.drawable.location_fair_day
        GameLocation.PIER -> if (evening) R.drawable.location_pier else R.drawable.location_pier_day
        // Only the original night illustration exists for the observatory.
        GameLocation.OBSERVATORY -> R.drawable.location_observatory
    }
}
