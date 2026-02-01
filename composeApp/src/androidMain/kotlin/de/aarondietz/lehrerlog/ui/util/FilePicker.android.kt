package de.aarondietz.lehrerlog.ui.util

import android.content.ContentResolver
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onCanceled()
            return@rememberLauncherForActivityResult
        }

        val fileName = resolver.getDisplayName(uri) ?: "upload.bin"
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            onCanceled()
            return@rememberLauncherForActivityResult
        }

        onFilePicked(PickedFile(fileName, bytes, contentType))
    }

    return {
        launcher.launch(mimeTypes.toTypedArray())
    }
}

private fun ContentResolver.getDisplayName(uri: android.net.Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) cursor.getString(index) else null
        } else {
            null
        }
    } finally {
        cursor?.close()
    }
}
