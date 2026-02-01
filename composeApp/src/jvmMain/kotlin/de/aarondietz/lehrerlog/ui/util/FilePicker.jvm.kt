package de.aarondietz.lehrerlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

@Composable
actual fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val selected = withContext(Dispatchers.IO) {
                selectFile(mimeTypes)
            }
            if (selected == null) {
                onCanceled()
                return@launch
            }
            val bytes = selected.readBytes()
            val contentType = Files.probeContentType(selected.toPath()) ?: "application/octet-stream"
            onFilePicked(PickedFile(selected.name, bytes, contentType))
        }
    }
}

private fun selectFile(mimeTypes: List<String>): File? {
    val dialog = FileDialog(null as Frame?, "Select file", FileDialog.LOAD)
    if (mimeTypes.any { it == "application/pdf" }) {
        dialog.filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".pdf", ignoreCase = true)
        }
    }
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    return File(dialog.directory, fileName)
}
