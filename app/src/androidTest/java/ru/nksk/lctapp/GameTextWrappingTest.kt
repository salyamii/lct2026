package ru.nksk.lctapp

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

class GameTextWrappingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longRussianWordDoesNotLeaveItsFinalLetterOnTheNextLine() {
        val word = "Картографический"
        val text = "$word стол"
        lateinit var narrow: TextLayoutResult
        lateinit var wide: TextLayoutResult
        compose.setContent {
            LCTAppTheme {
                val measurer = rememberTextMeasurer()
                val style = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 21.sp)
                // Reproduce the screenshot's boundary independently of device density/font metrics:
                // the complete word is just too wide, but all except its final letter would fit.
                val wordWidth = measurer.measure(word, style, softWrap = false).size.width
                val letterWidth = measurer.measure("й", style, softWrap = false).size.width
                val wrapped = measurer.measure(text, style,
                    constraints = Constraints(maxWidth = wordWidth - (letterWidth / 2).coerceAtLeast(1)))
                val full = measurer.measure(text, style, softWrap = false)
                SideEffect { narrow = wrapped; wide = full }
            }
        }
        compose.runOnIdle {
            assertEquals(text, narrow.layoutInput.text.text)
            assertTrue(narrow.lineCount > 1)
            assertTrue(narrow.getLineEnd(0, visibleEnd = true) in 1..word.length - 2)
            assertFalse(narrow.hasVisualOverflow)
            assertEquals(1, wide.lineCount)
        }
    }
}
