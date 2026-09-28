package ru.nksk.lctapp.app.parents

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed

/** Launch ownership stays in app composition. Repeated presses cannot stack parent Activities. */
@Composable
internal fun rememberParentsLauncher(): () -> Unit {
    val context = LocalContext.current
    var opening by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        opening = false
    }
    return dropUnlessResumed {
        if (!opening) {
            opening = true
            launcher.launch(Intent(context, ParentsActivity::class.java))
        }
    }
}
