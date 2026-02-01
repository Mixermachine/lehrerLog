package de.aarondietz.lehrerlog.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit {
    return {
        onCanceled()
    }
}
