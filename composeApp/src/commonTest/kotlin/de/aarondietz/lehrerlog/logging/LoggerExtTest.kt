package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import de.aarondietz.lehrerlog.SharedTestFixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class LoggerExtTest {
    private val tags = mutableListOf<String>()
    private val severities = mutableListOf<Severity>()

    @BeforeTest
    fun setup() {
        tags.clear()
        severities.clear()
        LoggerConfig.initialize(
            fileWriter = null,
            minSeverity = Severity.Debug,
            enableFileLogging = false,
            enableConsoleLogging = false,
            extraLogWriters = listOf(TestLogWriter(tags, severities))
        )
    }

    @Test
    fun usesClassNameAsTag() {
        val subject = TestLoggerSubject()
        subject.logger().i { SharedTestFixtures.testLogMessage }
        val expectedTag = TestLoggerSubject::class.simpleName ?: ""
        assertTrue(tags.contains(expectedTag))
    }

    @Test
    fun usesCustomTag() {
        Any().logger(SharedTestFixtures.testLogTag).i { SharedTestFixtures.testLogMessage }
        assertTrue(tags.contains(SharedTestFixtures.testLogTag))
    }

    @Test
    fun filtersBySeverity() {
        LoggerConfig.initialize(
            fileWriter = null,
            minSeverity = Severity.Warn,
            enableFileLogging = false,
            enableConsoleLogging = false,
            extraLogWriters = listOf(TestLogWriter(tags, severities))
        )

        val logger = Any().logger(SharedTestFixtures.testLogTag)
        logger.i { SharedTestFixtures.testLogMessage }
        logger.w { SharedTestFixtures.testLogMessage }

        assertTrue(severities.contains(Severity.Warn))
        assertTrue(severities.none { it == Severity.Info })
    }

    private class TestLogWriter(
        private val tags: MutableList<String>,
        private val severities: MutableList<Severity>
    ) : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            tags.add(tag)
            severities.add(severity)
        }
    }

    private class TestLoggerSubject
}
