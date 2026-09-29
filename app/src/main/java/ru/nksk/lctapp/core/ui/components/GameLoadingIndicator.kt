package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import ru.nksk.lctapp.R
import kotlinx.coroutines.delay

/** Bundled, cached animation. Its lifetime follows the caller's loading state. */
@Composable
internal fun GameLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 80.dp, isPlaying: Boolean = true,
    delayMillis: Long = 0) {
    // Short writes keep their existing scene without a one-frame spinner. This changes only
    // the feedback: the caller still blocks duplicate input immediately.
    var visible by remember(delayMillis) { mutableStateOf(delayMillis <= 0) }
    LaunchedEffect(delayMillis) {
        if (delayMillis > 0) delay(delayMillis)
        visible = true
    }
    if (!visible) return
    val composition = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_coin))
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val progress = animateLottieCompositionAsState(
        composition = composition.value,
        isPlaying = isPlaying && lifecycle.isAtLeast(Lifecycle.State.RESUMED),
        restartOnPlay = false,
        iterations = LottieConstants.IterateForever,
    )
    val loading = stringResource(R.string.loading_in_progress)
    Box(modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        stateDescription = loading
    }, contentAlignment = Alignment.Center) {
        if (composition.value == null) {
            // Also covers a parse failure: loading remains visible and accessible.
            Image(painterResource(R.drawable.loading_coin_frame), contentDescription = null,
                modifier = Modifier.size(size), contentScale = ContentScale.Fit)
        } else {
            LottieAnimation(composition.value, progress = { progress.value },
                modifier = Modifier.size(size), contentScale = ContentScale.Fit)
        }
    }
}
