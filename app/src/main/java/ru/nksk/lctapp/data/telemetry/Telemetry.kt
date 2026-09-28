package ru.nksk.lctapp.data.telemetry

import android.content.Context
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide OpenTelemetry agent. Spans export to Jaeger over OTLP/HTTP when the
 * supplied endpoint is configured; otherwise every call stays a no-op.
 * Initialization is best effort: telemetry failures must never break the app.
 */
object Telemetry {
    private const val INSTRUMENTATION_SCOPE = "ru.nksk.lctapp"

    /** Attached once the DataStore identity loads; appended to every span at start. */
    private val deviceId = AtomicReference<String?>(null)

    @Volatile private var rum: OpenTelemetryRum? = null

    val enabled: Boolean get() = rum != null

    fun init(context: Context, endpoint: String) {
        if (endpoint.isBlank()) return
        synchronized(this) {
            if (rum != null) return
            rum = runCatching {
                OpenTelemetryRumInitializer.initialize(context) {
                    httpExport { baseUrl = endpoint.trim() }
                    // Jaeger ingests traces only; metrics and logs stay local.
                    disableMetrics()
                    disableLogging()
                    resource { put("service.name", "lctapp") }
                    val deviceAttributes = {
                        val key = AttributeKey.stringKey("device.id")
                        deviceId.get()?.let { Attributes.of(key, it) } ?: Attributes.empty()
                    }
                    globalAttributesSupplier { deviceAttributes }
                }
            }.getOrNull()
        }
    }

    /** Stamps every subsequently started span with the persisted device identity. */
    fun attachDeviceId(id: String) {
        deviceId.set(id.trim().takeIf(String::isNotBlank))
    }

    val openTelemetry: OpenTelemetry
        get() = rum?.openTelemetry ?: OpenTelemetry.noop()

    fun tracer(): Tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)

    /**
     * An INTERNAL span for a longer local operation (a sync phase, a restore). Inline on
     * purpose: the body may return non-locally and its suspension points stay at the caller.
     */
    inline fun <T> traced(name: String, vararg attributes: Pair<String, String>, block: () -> T): T {
        if (!enabled) return block()
        val span = tracer().spanBuilder(name)
            .setAllAttributes(attributes.fold(Attributes.builder()) { builder, (key, value) ->
                builder.put(key, value)
            }.build())
            .startSpan()
        val scope = span.makeCurrent()
        return try {
            block()
        } catch (t: Throwable) {
            span.recordException(t)
            span.setStatus(StatusCode.ERROR, t.message)
            throw t
        } finally {
            scope.close()
            span.end()
        }
    }
}
