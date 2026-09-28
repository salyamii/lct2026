package ru.nksk.lctapp.data.backend

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.nksk.lctapp.domain.parentlink.ParentLinkUnavailableException
import java.util.concurrent.TimeUnit

internal val BackendJson = Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "_type" }

internal class BackendConnection(baseUrl: String, private val createApi: (String) -> BackendApi = ::createBackendApi) {
    val baseUrl: String? = baseUrl.takeIf { it.isNotBlank() }?.toHttpUrl()?.also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null && it.encodedPath.endsWith("/")) {
            "Backend must use an HTTPS base URL without credentials, query or fragment"
        }
    }?.toString()
    val configured: Boolean get() = baseUrl != null

    val api: BackendApi by lazy {
        val url = baseUrl ?: throw ParentLinkUnavailableException()
        createApi(url)
    }
}

private fun createBackendApi(url: String): BackendApi {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // Keep world uploads on the configured host; the client sends no authorization headers.
        .followRedirects(false).followSslRedirects(false)
        .build()
    return Retrofit.Builder().baseUrl(url).client(client)
        .addConverterFactory(BackendJson.asConverterFactory("application/json".toMediaType()))
        .build().create(BackendApi::class.java)
}
