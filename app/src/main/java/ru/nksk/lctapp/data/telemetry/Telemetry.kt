package ru.nksk.lctapp.data.telemetry

import android.content.Context
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * Process-wide OpenTelemetry agent. Spans export to Jaeger over OTLP/HTTP when the
 * supplied endpoint is configured; otherwise every call stays a no-op.
 * Initialization is best effort: telemetry failures must never break the app.
 */
object Telemetry {
    private const val INSTRUMENTATION_SCOPE = "ru.nksk.lctapp"

    @Volatile private var rum: OpenTelemetryRum? = null

    /** The endpoint is an HTTPS base URL; the agent appends the OTLP signal path itself. */
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
                }
            }.getOrNull()
        }
    }

    val openTelemetry: OpenTelemetry
        get() = rum?.openTelemetry ?: OpenTelemetry.noop()

    fun tracer(): Tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)
}
