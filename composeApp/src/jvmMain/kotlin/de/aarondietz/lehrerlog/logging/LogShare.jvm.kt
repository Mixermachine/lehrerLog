package de.aarondietz.lehrerlog.logging

import java.awt.Desktop
import java.io.File

actual fun shareLogs(payload: LogSharePayload) {
    val tempFile = File(System.getProperty("java.io.tmpdir"), payload.fileName)
    tempFile.writeText(payload.text)
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(tempFile)
    }
}
