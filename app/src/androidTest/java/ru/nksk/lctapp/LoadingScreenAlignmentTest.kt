package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.feature.menu.ui.MainMenuContent
import ru.nksk.lctapp.feature.menu.ui.MainMenuLoadState

/** Catches the former 80 dp coin and coin+label column shifting the menu loader upward. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class LoadingScreenAlignmentTest(private val width: Int, private val height: Int, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun prepare() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.mainClock.autoAdvance = false
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}dp font={2}")
        fun windows() = listOf(arrayOf<Any>(360, 640, 1f), arrayOf<Any>(640, 360, 2f))
    }

    @Test fun menuLoadingCoinOverlapsTheOriginalCanvasInsideTheSplashIcon() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                val density = LocalDensity.current
                fun px(dp: Int) = with(density) { dp.dp.roundToPx() }
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(24), 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(48)))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true).build()
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                    LCTAppTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // Android's no-background splash icon occupies a 288 dp square.
                            Image(painterResource(R.drawable.loading_coin_splash), null,
                                Modifier.size(288.dp).testTag("system_splash_icon"))
                            MainMenuContent(MainMenuLoadState.Loading, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        val splash = compose.onNodeWithTag("system_splash_icon").fetchSemanticsNode().boundsInRoot
        val coin = compose.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        )).fetchSemanticsNode().boundsInRoot
        // The original 480-unit canvas lies inside the system vector's 1152-unit viewport.
        assertEquals(splash.width * 5f / 12f, coin.width, 1f)
        assertEquals(splash.height * 5f / 12f, coin.height, 1f)
        assertEquals(splash.center.x, coin.center.x, 1f)
        assertEquals(splash.center.y, coin.center.y, 1f)
    }
}
