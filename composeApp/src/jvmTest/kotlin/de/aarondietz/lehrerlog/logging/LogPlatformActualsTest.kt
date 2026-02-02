package de.aarondietz.lehrerlog.logging

import de.aarondietz.lehrerlog.SharedTestFixtures
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogPlatformActualsTest {

    @Test
    fun currentLogTimestampIsIsoInstant() {
        val timestamp = currentLogTimestamp()
        Instant.parse(timestamp)
    }

    @Test
    fun shareLogsWritesTempFile() {
        val fileName = "share-${System.currentTimeMillis()}.log"
        val payload = LogSharePayload(
            title = SharedTestFixtures.testLogTag,
            fileName = fileName,
            text = SharedTestFixtures.testLogMessage
        )

        val originalHeadless = System.getProperty("java.awt.headless")
        System.setProperty("java.awt.headless", "true")
        try {
            shareLogs(payload)
        } finally {
            if (originalHeadless == null) {
                System.clearProperty("java.awt.headless")
            } else {
                System.setProperty("java.awt.headless", originalHeadless)
            }
        }

        val tempFile = File(System.getProperty("java.io.tmpdir"), fileName)
        try {
            assertTrue(tempFile.exists())
            assertEquals(SharedTestFixtures.testLogMessage, tempFile.readText())
        } finally {
            tempFile.delete()
        }
    }
}
