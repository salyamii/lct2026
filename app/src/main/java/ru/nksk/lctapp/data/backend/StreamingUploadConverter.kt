package ru.nksk.lctapp.data.backend

import java.lang.reflect.Type
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.Converter
import retrofit2.Retrofit
import ru.nksk.lctapp.domain.backend.AnalyticsUploadRequest
import ru.nksk.lctapp.domain.backend.SnapshotUploadRequest

/** Large uploads write JSON to OkHttp's bounded sink instead of allocating a second full UTF-8 body. */
internal class StreamingUploadConverter(private val mediaType: MediaType) : Converter.Factory() {
    override fun requestBodyConverter(type: Type, parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>, retrofit: Retrofit): Converter<*, RequestBody>? = when (type) {
        SnapshotUploadRequest::class.java -> Converter<SnapshotUploadRequest, RequestBody> { value ->
            object : RequestBody() {
                override fun contentType() = mediaType
                override fun writeTo(sink: BufferedSink) = writeSnapshotUpload(value) { sink.writeUtf8(it) }
            }
        }
        AnalyticsUploadRequest::class.java -> converter(AnalyticsUploadRequest.serializer())
        else -> null
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> converter(serializer: SerializationStrategy<T>): Converter<T, RequestBody> = Converter { value ->
        object : RequestBody() {
            override fun contentType() = mediaType
            override fun writeTo(sink: BufferedSink) {
                // Do not close OkHttp's sink; immutable request values also support identical retries.
                BackendJson.encodeToStream(serializer, value, sink.outputStream())
            }
        }
    }
}

/** The standard stream serializer still copies one whole quoted String; snapshotJson is unusually large. */
internal fun encodeSnapshotUpload(request: SnapshotUploadRequest): String = buildString {
    writeSnapshotUpload(request) { append(it) }
}

private fun writeSnapshotUpload(request: SnapshotUploadRequest, write: (String) -> Unit) {
    val envelope = BackendJson.encodeToString(request.copy(snapshotJson = ""))
    val marker = "\"snapshotJson\":\"\""
    val position = envelope.indexOf(marker)
    check(position >= 0)
    val contentStart = position + marker.length - 1
    write(envelope.substring(0, contentStart))
    val value = request.snapshotJson
    var start = 0
    while (start < value.length) {
        var end = minOf(start + 8_192, value.length)
        // Keep a valid UTF-16 pair in the same piece before OkHttp encodes that piece as UTF-8.
        if (end < value.length && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
        val quoted = BackendJson.encodeToString(value.substring(start, end))
        write(quoted.substring(1, quoted.lastIndex))
        start = end
    }
    write(envelope.substring(contentStart))
}
