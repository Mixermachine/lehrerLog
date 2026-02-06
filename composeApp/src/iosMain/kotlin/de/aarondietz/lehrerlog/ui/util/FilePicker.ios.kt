package de.aarondietz.lehrerlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberFilePickerLauncher(
    mimeTypes: List<String>,
    onFilePicked: (PickedFile) -> Unit,
    onCanceled: () -> Unit
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onFilePicked)
    val currentOnCanceled by rememberUpdatedState(onCanceled)
    val delegate = remember { DocumentPickerDelegate() }
    SideEffect {
        delegate.onPicked = currentOnPicked
        delegate.onCanceled = currentOnCanceled
    }
    val contentTypes = remember(mimeTypes) { mimeTypesToContentTypes(mimeTypes) }
    return launch@{
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = true)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        val presenter = topViewController()
        if (presenter == null) {
            currentOnCanceled()
            return@launch
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

private class DocumentPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol, UINavigationControllerDelegateProtocol {
    var onPicked: (PickedFile) -> Unit = {}
    var onCanceled: () -> Unit = {}

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onCanceled()
            return
        }
        val accessGranted = url.startAccessingSecurityScopedResource()
        val data = url.path?.let { NSFileManager.defaultManager.contentsAtPath(it) }
        if (accessGranted) {
            url.stopAccessingSecurityScopedResource()
        }
        if (data == null) {
            onCanceled()
            return
        }
        val bytes = data.toByteArray()
        val name = url.lastPathComponent ?: "upload.bin"
        val contentType = if (name.endsWith(".pdf", ignoreCase = true)) {
            "application/pdf"
        } else {
            "application/octet-stream"
        }
        onPicked(PickedFile(name, bytes, contentType))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCanceled()
    }
}

private fun mimeTypesToContentTypes(mimeTypes: List<String>): List<UTType> {
    if (mimeTypes.isEmpty()) return listOf(UTTypeItem)
    return mimeTypes.map { mime ->
        when (mime.lowercase()) {
            "application/pdf" -> UTTypePDF
            "*/*" -> UTTypeItem
            else -> UTTypeItem
        }
    }.distinct()
}

private fun topViewController(): UIViewController? {
    val window = keyWindow() ?: return null
    return findTopViewController(window.rootViewController)
}

private fun keyWindow(): UIWindow? {
    val app = UIApplication.sharedApplication
    val key = app.keyWindow
    if (key != null) return key
    return app.windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
}

private fun findTopViewController(root: UIViewController?): UIViewController? {
    var current = root
    while (true) {
        when (val controller = current) {
            is UINavigationController -> current = controller.visibleViewController
            is UITabBarController -> current = controller.selectedViewController
        }
        val presented = current?.presentedViewController ?: break
        current = presented
    }
    return current
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = length.toInt()
    if (length == 0) return ByteArray(0)
    val result = ByteArray(length)
    val source = bytes ?: return ByteArray(0)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), source, length.toULong())
    }
    return result
}
