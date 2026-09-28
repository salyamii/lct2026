package ru.nksk.lctapp.data.telemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test

class TelemetryHttpInterceptorTest {
    private val exporter = InMemorySpanExporter.create()
    private val server = MockWebServer()
    private val sdk: OpenTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()
        )
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build()

    @Before fun setUp() {
        server.start()
        exporter.reset()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(TelemetryHttpInterceptor(sdk))
        .build()

    @Test
    fun successfulCallProducesClientSpanWithAttributesAndTraceparent() {
        server.enqueue(MockResponse().setResponseCode(200))
        client().newCall(
            Request.Builder().url(server.url("/api/v1/sync")).build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        val spans = exporter.finishedSpanItems
        assertEquals(1, spans.size)
        val span = spans[0]
        assertEquals(SpanKind.CLIENT, span.kind)
        assertEquals("HTTP GET ${server.hostName}", span.name)
        val attrs: Attributes = span.attributes
        assertEquals("GET", attrs.get(AttributeKey.stringKey("http.request.method")))
        assertEquals(200L, attrs.get(AttributeKey.longKey("http.response.status_code")))
        assertEquals(server.hostName, attrs.get(AttributeKey.stringKey("server.address")))

        val recorded = server.takeRequest()
        assertNotNull(recorded.getHeader("traceparent"))
    }

    @Test
    fun incomingTraceparentBecomesTheSpanParent() {
        server.enqueue(MockResponse().setResponseCode(200))
        val parentHeader = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
        client().newCall(
            Request.Builder().url(server.url("/api/v1/sync"))
                .header("traceparent", parentHeader)
                .build()
        ).execute().use { assertEquals(200, it.code) }

        val span: SpanData = exporter.finishedSpanItems.single()
        assertEquals("0af7651916cd43dd8448eb211c80319c", span.traceId)
        assertEquals("b7ad6b7169203331", span.parentSpanId)
    }

    @Test
    fun transportFailureRecordsErrorStatusAndException() {
        val closedPort = server.url("/").port
        server.shutdown()
        val failure = runCatching {
            client().newCall(
                Request.Builder().url("http://127.0.0.1:$closedPort/").build()
            ).execute()
        }
        assertEquals(true, failure.isFailure)

        val span = exporter.finishedSpanItems.single()
        assertEquals(StatusCode.ERROR, span.status.statusCode)
        assertEquals(1, span.events.size)
        assertEquals("exception", span.events[0].name)
    }
}
