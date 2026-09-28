package ru.nksk.lctapp.core.ui.media

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay

/** Complete 540 × 960 source frame over a small, once-decoded blurred backdrop. */
@Composable
internal fun IntroVideoPlayer(assetPath: String, positionMs: Long, soundEnabled: Boolean,
    onPositionChanged: (Long) -> Unit, onCompleted: () -> Unit, onError: () -> Unit, modifier: Modifier = Modifier) {
    val factory = mediaPlayerFactory()
    val backdrop by produceState<ImageBitmap?>(null, factory, assetPath) {
        value = factory.videoBackdrop(assetPath)
    }
    val completed by rememberUpdatedState(onCompleted)
    val failed by rememberUpdatedState(onError)
    val positionChanged by rememberUpdatedState(onPositionChanged)
    val player = remember(factory, assetPath) {
        factory.create(assetPath, video = true, positionMs = positionMs,
            onCompleted = { completed() }, onError = { failed() })
    }
    val playbackLifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycle by playbackLifecycle.currentStateAsState()
    SideEffect {
        player.setSoundEnabled(soundEnabled)
    }
    PlaybackLifecycleEffect(playbackLifecycle, player::setForeground)
    LaunchedEffect(player, lifecycle) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) while (true) {
            delay(1_000)
            positionChanged(player.positionMs())
        } else positionChanged(player.positionMs())
    }
    DisposableEffect(player) {
        onDispose { positionChanged(player.positionMs()); player.close() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        backdrop?.let { image ->
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .65f)
        }
        AndroidView(factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { player.attachSurface(holder) }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        positionChanged(player.positionMs())
                        player.attachSurface(null)
                    }
                })
            }
        }, modifier = Modifier.aspectRatio(9f / 16f), onRelease = { player.attachSurface(null) })
    }
}
