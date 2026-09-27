package ru.nksk.lctapp

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.feature.settings.sharing.createParentCodeShareIntent
import ru.nksk.lctapp.feature.settings.ui.encodeParentLinkQr
import ru.nksk.lctapp.feature.settings.ui.qrGeometry

@RunWith(AndroidJUnit4::class)
class ParentCodeSharingTest {
    @Suppress("DEPRECATION")
    @Test fun exportedPngPreservesQrAndGrantsOnlyTemporaryReadAccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matrix = encodeParentLinkQr("ad64c0e4-2731-4c1e-9fc9-bac812fd5995")
        val send = createParentCodeShareIntent(context, matrix)
        val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.parent-qr", uri.authority)
        assertEquals(uri, send.clipData?.getItemAt(0)?.uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags)
        assertEquals(uri, createParentCodeShareIntent(context, matrix).getParcelableExtra<Uri>(Intent.EXTRA_STREAM))

        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(checkNotNull(it)) }
        checkNotNull(bitmap)
        try {
            assertEquals(1024, bitmap.width)
            assertEquals(1024, bitmap.height)
            val geometry = checkNotNull(qrGeometry(bitmap.width, matrix.size))
            val module = geometry.modulePixels
            val origin = geometry.leadingPixels + 4 * module
            for (pixel in 0 until bitmap.width) {
                assertEquals(Color.WHITE, bitmap.getPixel(pixel, origin - 1))
                assertEquals(Color.WHITE, bitmap.getPixel(origin - 1, pixel))
                assertEquals(Color.WHITE, bitmap.getPixel(pixel, origin + matrix.size * module))
                assertEquals(Color.WHITE, bitmap.getPixel(origin + matrix.size * module, pixel))
            }
            for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                assertEquals(if (matrix.dark(x, y)) Color.BLACK else Color.WHITE,
                    bitmap.getPixel(origin + x * module + module / 2, origin + y * module + module / 2))
            }
        } finally { bitmap.recycle() }
    }

    @Suppress("DEPRECATION")
    @Test fun providerCannotExposeOtherPrivateOrCacheFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = "${context.packageName}.parent-qr"
        val provider = checkNotNull(context.packageManager.resolveContentProvider(authority, 0))
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        for (file in listOf(File(context.cacheDir, "unrelated.bin"), File(context.filesDir, "private.bin"))) {
            try {
                FileProvider.getUriForFile(context, authority, file)
                fail("Provider exposed a file outside the QR directory")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
