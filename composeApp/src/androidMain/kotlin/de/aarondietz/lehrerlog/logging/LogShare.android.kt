package de.aarondietz.lehrerlog.logging

import android.content.Context
import android.content.Intent
import java.io.File

private var androidShareContext: Context? = null

fun initAndroidLogSharing(context: Context) {
    androidShareContext = context.applicationContext
}

actual fun shareLogs(payload: LogSharePayload) {
    val context = androidShareContext ?: return
    val shareFile = File(context.cacheDir, payload.fileName)
    shareFile.writeText(payload.text)

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        shareFile
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, payload.title)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, payload.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
