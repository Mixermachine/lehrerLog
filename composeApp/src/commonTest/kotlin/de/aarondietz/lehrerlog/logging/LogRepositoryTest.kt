package de.aarondietz.lehrerlog.logging

import de.aarondietz.lehrerlog.SharedTestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LogRepositoryTest {
    private lateinit var logWriter: LogFileWriter
    private lateinit var repository: LogRepository

    @BeforeTest
    fun setup() {
        logWriter = LogFileWriter().apply {
            initialize(maxFileSizeMB = 1, maxFiles = 2, maxAgeDays = 1)
            clearLogs()
        }
        repository = LogRepository(logWriter)
    }

    @AfterTest
    fun tearDown() {
        logWriter.clearLogs()
    }

    @Test
    fun loadOverviewIncludesPreviewLines() = runTest {
        logWriter.writeLog(
            timestamp = SharedTestFixtures.testLogTimestamp,
            level = SharedTestFixtures.testLogLevel,
            tag = SharedTestFixtures.testLogTag,
            message = SharedTestFixtures.testLogMessage,
            throwable = null
        )

        val overview = repository.loadOverview(maxPreviewLines = 1).getOrThrow()
        assertEquals(1, overview.previewLines.size)
        assertTrue(overview.previewLines.first().contains(SharedTestFixtures.testLogMessage))
    }

    @Test
    fun buildSharePayloadCombinesLogs() = runTest {
        logWriter.writeLog(
            timestamp = SharedTestFixtures.testLogTimestamp,
            level = SharedTestFixtures.testLogLevel,
            tag = SharedTestFixtures.testLogTag,
            message = SharedTestFixtures.testLogMessage,
            throwable = null
        )

        val payload = repository.buildSharePayload().getOrThrow()
        assertTrue(payload.text.contains(SharedTestFixtures.testLogMessage))
        assertTrue(payload.title.isNotBlank())
        assertTrue(payload.fileName.isNotBlank())
    }
}
