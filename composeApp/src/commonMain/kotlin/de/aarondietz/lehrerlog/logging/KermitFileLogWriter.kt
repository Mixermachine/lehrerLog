package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import de.aarondietz.lehrerlog.coroutines.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KermitFileLogWriter(
    private val fileWriter: LogFileWriter
) : LogWriter() {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val timestamp = currentLogTimestamp()
        val level = severity.name.uppercase()
        scope.launch {
            fileWriter.writeLog(
                timestamp = timestamp,
                level = level,
                tag = tag,
                message = message,
                throwable = throwable
            )
        }
    }
}
