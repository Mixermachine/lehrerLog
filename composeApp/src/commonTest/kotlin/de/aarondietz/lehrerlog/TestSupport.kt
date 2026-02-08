package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenStorage
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

private val testJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun createTestHttpClient(
    expectSuccess: Boolean = false,
    handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> HttpResponseData
): HttpClient {
    val config = MockEngineConfig().apply {
        reuseHandlers = true
        addHandler { request -> handler(request) }
    }
    return HttpClient(MockEngine(config)) {
        install(ContentNegotiation) {
            json(testJson)
        }
        this.expectSuccess = expectSuccess
    }
}

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
): HttpResponseData {
    return respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(block: suspend TestScope.() -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            block()
        }
    } finally {
        Dispatchers.resetMain()
    }
}

class InMemoryTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override fun saveAccessToken(token: String) {
        accessToken = token
    }

    override fun saveRefreshToken(token: String) {
        refreshToken = token
    }

    override fun getAccessToken(): String? = accessToken

    override fun getRefreshToken(): String? = refreshToken

    override fun clearTokens() {
        accessToken = null
        refreshToken = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.awaitUntil(timeoutMs: Long = 1_000, predicate: () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (!predicate()) {
        val elapsedMs = start.elapsedNow().inWholeMilliseconds
        if (elapsedMs >= timeoutMs) {
            throw AssertionError("Condition not met within ${timeoutMs}ms")
        }
        runCurrent()
        withContext(Dispatchers.Default) {
            delay(5)
        }
        yield()
    }
}
