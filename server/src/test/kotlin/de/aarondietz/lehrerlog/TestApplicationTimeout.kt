package de.aarondietz.lehrerlog

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.runTestApplication
import io.ktor.test.dispatcher.runTestWithRealTime
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun testApplicationWithTimeout(
    timeout: Duration = 3.minutes,
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    runTestWithRealTime(EmptyCoroutineContext, timeout) {
        runTestApplication(EmptyCoroutineContext, block)
    }
}
