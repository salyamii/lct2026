package ru.nksk.lctapp.data.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * One CLIENT span per backend call. The W3C traceparent rides on the outgoing
 * request, so traces continue on the server once it runs OpenTelemetry too.
 */
class TelemetryHttpInterceptor(private val openTelemetry: OpenTelemetry) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val propagator = openTelemetry.propagators.textMapPropagator
        val parentContext = propagator.extract(Context.current(), request, RequestTextMapGetter)
        val span = openTelemetry.getTracer("ru.nksk.lctapp.http")
            .spanBuilder("HTTP ${request.method} ${request.url.host}")
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("http.request.method", request.method)
            .setAttribute("url.full", request.url.toString())
            .setAttribute("server.address", request.url.host)
            .setAttribute("server.port", request.url.port.toLong())
            .startSpan()
        val scope = span.makeCurrent()
        return try {
            val outgoing = request.newBuilder()
            propagator.inject(Context.current(), outgoing, RequestTextMapSetter)
            val response = chain.proceed(outgoing.build())
            span.setAttribute("http.response.status_code", response.code.toLong())
            response
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

private object RequestTextMapGetter : TextMapGetter<Request> {
    override fun keys(carrier: Request): Iterable<String> = carrier.headers.names()

    override fun get(carrier: Request?, key: String): String? =
        carrier?.headers?.get(key)
}

private object RequestTextMapSetter : TextMapSetter<Request.Builder> {
    override fun set(carrier: Request.Builder?, key: String, value: String) {
        carrier?.header(key, value)
    }
}
