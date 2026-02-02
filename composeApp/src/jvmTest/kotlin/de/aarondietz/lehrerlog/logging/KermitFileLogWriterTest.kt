package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.Severity
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.awaitUntil
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class KermitFileLogWriterTest {

    @Test
    fun logsWriteToFile() = runTest {
        val tempDir = Files.createTempDirectory("lehrerlog-logs")
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())

        try {
            val writer = LogFileWriter().apply {
                initialize(maxFileSizeMB = 1, maxFiles = 1, maxAgeDays = 1)
                clearLogs()
            }
            val logWriter = KermitFileLogWriter(writer)

            logWriter.log(
                severity = Severity.Info,
                message = SharedTestFixtures.testLogMessage,
                tag = SharedTestFixtures.testLogTag,
                throwable = null
            )

            awaitUntil {
                writer.getLogFiles().any { entry ->
                    writer.readLogFile(entry.path).contains(SharedTestFixtures.testLogMessage)
                }
            }

            assertTrue(writer.getLogFiles().isNotEmpty())
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", originalHome)
            }
        }
    }
}
