package de.aarondietz.lehrerlog.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme

data class PickedFile(
    val name: String,
    val bytes: ByteArray,
    val contentType: String
)

@Composable
expect fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit

@Preview
@Composable
private fun FilePickerPreview() {
    val launchPicker = rememberFilePickerLauncher(
        mimeTypes = emptyList(),
        onFilePicked = {},
        onCanceled = {}
    )

    LehrerLogTheme {
        Box(modifier = Modifier)
    }

    launchPicker
}
