package de.aarondietz.lehrerlog.logging

import de.aarondietz.lehrerlog.SharedTestFixtures
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class LogFileWriterTest {
    private lateinit var logWriter: LogFileWriter

    @BeforeTest
    fun setup() {
        logWriter = LogFileWriter()
        logWriter.initialize(maxFileSizeMB = 1, maxFiles = 2, maxAgeDays = 1)
        logWriter.clearLogs()
    }

    @AfterTest
    fun tearDown() {
        logWriter.clearLogs()
    }

    @Test
    fun writesAndReadsLogs() {
        logWriter.writeLog(
            timestamp = SharedTestFixtures.testLogTimestamp,
            level = SharedTestFixtures.testLogLevel,
            tag = SharedTestFixtures.testLogTag,
            message = SharedTestFixtures.testLogMessage,
            throwable = null
        )

        val files = logWriter.getLogFiles()
        assertTrue(files.isNotEmpty())

        val content = logWriter.readLogFile(files.first().path)
        assertTrue(content.contains(SharedTestFixtures.testLogMessage))
        assertTrue(logWriter.getCurrentLogSize() > 0L)
    }

    @Test
    fun rotateNowCreatesNewFile() {
        logWriter.writeLog(
            timestamp = SharedTestFixtures.testLogTimestamp,
            level = SharedTestFixtures.testLogLevel,
            tag = SharedTestFixtures.testLogTag,
            message = SharedTestFixtures.testLogMessage,
            throwable = null
        )
        logWriter.rotateNow()
        val files = logWriter.getLogFiles()
        assertTrue(files.isNotEmpty())
    }
}
