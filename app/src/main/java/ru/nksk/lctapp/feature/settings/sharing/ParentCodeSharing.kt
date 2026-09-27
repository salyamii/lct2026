package ru.nksk.lctapp.feature.settings.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AtomicFile
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.feature.settings.ui.ParentLinkQrMatrix
import ru.nksk.lctapp.feature.settings.ui.qrGeometry

/** Only this cache subdirectory is exposed by the manifest's provider. */
class ParentQrFileProvider : FileProvider()

private val qrShareCacheMutex = Mutex()

/** Shares the displayed public-ID matrix; no identity secret, snapshot or network request is involved. */
internal suspend fun createParentCodeShareIntent(context: Context, matrix: ParentLinkQrMatrix): Intent =
    withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "parent_qr")
        val digest = MessageDigest.getInstance("SHA-256").digest(
            ByteArray(matrix.darkModules.size) { if (matrix.darkModules[it]) 1 else 0 })
        // Repeated sharing reuses one immutable PNG rather than overwriting a file another app is reading.
        val filename = "parent-code-" + digest.joinToString("") { "%02x".format(it) } + ".png"
        val file = File(directory, filename)
        // An old entry's synchronous PNG write can still finish after its coroutine was cancelled.
        qrShareCacheMutex.withLock {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot prepare QR cache" }
            if (!file.isFile) writeQrPng(file, matrix)
            check(file.isFile && file.length() > 0) { "Cannot publish QR image" }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.parent-qr", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "Код для родителей")
            clipData = ClipData.newRawUri("Код для родителей", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

private fun writeQrPng(file: File, matrix: ParentLinkQrMatrix) {
    val side = 1024
    val geometry = checkNotNull(qrGeometry(side, matrix.size))
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        val module = geometry.modulePixels
        val origin = geometry.leadingPixels + 4 * module
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix.dark(x, y)) {
                val left = origin + x * module
                val top = origin + y * module
                canvas.drawRect(left.toFloat(), top.toFloat(), (left + module).toFloat(), (top + module).toFloat(), paint)
            }
        }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Cannot encode QR image" }
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    } finally {
        bitmap.recycle()
    }
}
