package ru.nksk.lctapp.data.backend

import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Converter
import retrofit2.Retrofit
import ru.nksk.lctapp.domain.backend.SnapshotDownloadRequest
import ru.nksk.lctapp.domain.backend.SnapshotUploadRequest
import ru.nksk.lctapp.domain.backend.analyticsUploadRequest

class StreamingUploadConverterTest {
    private val mediaType = "application/json".toMediaType()
    private val factory = StreamingUploadConverter(mediaType)
    private val retrofit = Retrofit.Builder().baseUrl("https://backend.example.test/").build()

    @Test fun largeOpaqueSnapshotStreamsTheSameJsonOnEveryAttemptWithoutClosingTheSink() {
        val snapshotJson = "{\"history\":\"" + "Лисёнок 🦊 \\\"\n\t".repeat(20_000) + "\"}"
        val request = SnapshotUploadRequest("device", "frozen-upload", 7, "run", 900,
            "fingerprint", 5, "checksum", snapshotJson)
        val body = body(request)
        assertEquals(mediaType, body.contentType())
        assertFalse(body.isOneShot())
        val expected = BackendJson.encodeToString(request)
        assertEquals(expected, encodeSnapshotUpload(request))
        repeat(2) {
            val buffer = Buffer()
            body.writeTo(buffer)
            assertEquals(expected, buffer.readUtf8())
            buffer.writeUtf8("still open")
            assertEquals("still open", buffer.readUtf8())
        }
    }

    @Test fun opaqueStringBoundariesPreserveEscapesAndSurrogatePairs() {
        for (tail in listOf("🦊", "\\\"\n\t\b\r", "\u0000\u001f", "\ud800", "\udfff")) {
            val request = SnapshotUploadRequest("device", "upload", null, "run", 1, "content", 5,
                "checksum", "x".repeat(8_191) + tail + "y".repeat(8_192))
            val expected = BackendJson.encodeToString(request)
            assertEquals(expected, encodeSnapshotUpload(request))
            val buffer = Buffer()
            body(request).writeTo(buffer)
            assertEquals(Buffer().writeUtf8(expected).readByteString(), buffer.readByteString())
        }
    }

    @Test fun analyticsUsesTheSameStableEncodingAndOtherCallsKeepTheStandardConverter() {
        val request = analyticsUploadRequest("device", "frozen-batch", "run", 1, emptyList())
        val buffer = Buffer()
        body(request).writeTo(buffer)
        assertEquals(BackendJson.encodeToString(request), buffer.readUtf8())
        assertTrue(request.skills.isNotEmpty())
        assertNull(factory.requestBodyConverter(SnapshotDownloadRequest::class.java,
            emptyArray(), emptyArray(), retrofit))
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> body(value: T): RequestBody = checkNotNull(
        (factory.requestBodyConverter(T::class.java, emptyArray(), emptyArray(), retrofit)
            as Converter<T, RequestBody>).convert(value))
}
