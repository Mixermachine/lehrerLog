package de.aarondietz.lehrerlog.logging

import platform.Foundation.*
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun shareLogs(payload: LogSharePayload) {
    val tempDir = NSTemporaryDirectory()
    val filePath = tempDir + payload.fileName
    val data = NSString.create(string = payload.text)
        .dataUsingEncoding(NSUTF8StringEncoding)
        ?: return
    NSFileManager.defaultManager.createFileAtPath(filePath, contents = data, attributes = null)

    val url = NSURL.fileURLWithPath(filePath)
    val controller = UIActivityViewController(listOf(url), null)
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(controller, true, null)
}
