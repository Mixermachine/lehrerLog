package de.aarondietz.lehrerlog

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRouteEndToEndTest {

    @Test
    fun `user route responds to list get and create`() = testApplication {
        application { module() }

        val listResponse = client.get("/users")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(listResponse.bodyAsText().contains("Get all users"))

        val detailResponse = client.get("/users/123")
        assertEquals(HttpStatusCode.OK, detailResponse.status)
        assertTrue(detailResponse.bodyAsText().contains("Get user 123"))

        val createResponse = client.post("/users")
        assertEquals(HttpStatusCode.OK, createResponse.status)
        assertTrue(createResponse.bodyAsText().contains("Create user"))
    }
}
