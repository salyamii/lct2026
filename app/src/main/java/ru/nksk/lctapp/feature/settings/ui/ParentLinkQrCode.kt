package ru.nksk.lctapp.feature.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GameInk
import io.nayuki.qrcodegen.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

internal data class ParentLinkQrMatrix(val size: Int, val darkModules: List<Boolean>) {
    init { require(size in 21..177 && (size - 21) % 4 == 0 && darkModules.size == size * size) }
    fun dark(x: Int, y: Int): Boolean = darkModules[y * size + x]
}

internal suspend fun encodeParentLinkQr(payload: String): ParentLinkQrMatrix = withContext(Dispatchers.Default) {
    require(payload.isNotBlank())
    val qr = QrCode.encodeText(payload, QrCode.Ecc.MEDIUM)
    ParentLinkQrMatrix(qr.size, List(qr.size * qr.size) { index -> qr.getModule(index % qr.size, index / qr.size) })
}

internal data class QrGeometry(val modulePixels: Int, val leadingPixels: Int, val totalModules: Int)
internal fun qrGeometry(sidePixels: Int, modules: Int): QrGeometry? {
    require(modules > 0)
    val total = modules + 8 // Four white modules on every side, as required by the QR format.
    if (sidePixels < total) return null
    val module = sidePixels / total
    return QrGeometry(module, (sidePixels - total * module) / 2, total)
}

@Composable
internal fun ParentLinkQrCode(matrix: ParentLinkQrMatrix, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.widthIn(max = 288.dp).fillMaxWidth().aspectRatio(1f).background(Color.White)) {
        val side = with(LocalDensity.current) { min(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        if (qrGeometry(side, matrix.size) == null) {
            Text("Для кода нужно больше места", color = GameInk)
        } else Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Код для подключения родителя" }) {
            val geometry = qrGeometry(min(size.width, size.height).toInt(), matrix.size) ?: return@Canvas
            val module = geometry.modulePixels.toFloat()
            val origin = geometry.leadingPixels + 4 * geometry.modulePixels
            for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                if (matrix.dark(x, y)) drawRect(Color.Black,
                    topLeft = Offset(origin + x * module, origin + y * module), size = Size(module, module))
            }
        }
    }
}
