package de.aarondietz.lehrerlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

object FilePickerJvmTestHooks {
    @Volatile
    var launcherOverride: ((
        mimeTypes: List<String>,
        onFilePicked: (PickedFile) -> Unit,
        onCanceled: () -> Unit
    ) -> Unit)? = null
}

@Composable
actual fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit {
    val currentOnFilePicked by rememberUpdatedState(onFilePicked)
    val currentOnCanceled by rememberUpdatedState(onCanceled)
    val scope = rememberCoroutineScope()
    return {
        val override = FilePickerJvmTestHooks.launcherOverride
        if (override != null) {
            override(mimeTypes, currentOnFilePicked, currentOnCanceled)
        } else {
            scope.launch {
                val selected = withContext(Dispatchers.IO) {
                    selectFile(mimeTypes)
                }
                if (selected == null) {
                    currentOnCanceled()
                    return@launch
                }
                val bytes = selected.readBytes()
                val contentType = Files.probeContentType(selected.toPath()) ?: "application/octet-stream"
                currentOnFilePicked(PickedFile(selected.name, bytes, contentType))
            }
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
