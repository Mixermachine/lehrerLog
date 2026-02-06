package de.aarondietz.lehrerlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader

@Composable
actual fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onFilePicked)
    val currentOnCanceled by rememberUpdatedState(onCanceled)
    val acceptTypes = mimeTypes.joinToString(",")
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        if (acceptTypes.isNotBlank()) {
            input.accept = acceptTypes
        }

        input.onchange = onChange@{
            val file = input.files?.item(0)
            if (file == null) {
                currentOnCanceled()
                input.remove()
                return@onChange
            }
            readFile(file, currentOnPicked, currentOnCanceled)
            input.remove()
        }

        document.body?.appendChild(input)
        input.click()
    }
}

private fun readFile(
    file: File,
    onPicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
) {
    val reader = FileReader()
    reader.onload = onLoad@{
        val buffer = reader.result as? ArrayBuffer
        if (buffer == null) {
            onCanceled()
            return@onLoad
        }
        val bytes = arrayBufferToByteArray(buffer)
        val contentType = if (file.type.isNotBlank()) file.type else "application/octet-stream"
        onPicked(PickedFile(file.name, bytes, contentType))
    }
    reader.onerror = {
        onCanceled()
    }
    reader.readAsArrayBuffer(file)
}

private fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
    val uint8 = newUint8Array(buffer)
    val length = uint8.length
    val bytes = ByteArray(length)
    for (index in 0 until length) {
        bytes[index] = uint8At(uint8, index).toByte()
    }
    return bytes
}

private external interface Uint8ArrayView {
    val length: Int
}

private fun newUint8Array(buffer: ArrayBuffer): Uint8ArrayView = js("new Uint8Array(buffer)")

private fun uint8At(array: Uint8ArrayView, index: Int): Int = js("array[index]")
