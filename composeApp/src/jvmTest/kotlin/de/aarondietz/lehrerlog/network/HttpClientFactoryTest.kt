package de.aarondietz.lehrerlog.network

import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlin.test.Test
import kotlin.test.assertNotNull

class HttpClientFactoryTest {

    @Test
    fun createHttpClientConfiguresJson() {
        val client = createHttpClient()
        try {
            assertNotNull(client.plugin(ContentNegotiation))
        } finally {
            client.close()
        }
    }
}
