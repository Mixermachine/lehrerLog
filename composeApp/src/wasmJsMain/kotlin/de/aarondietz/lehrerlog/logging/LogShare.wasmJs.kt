package de.aarondietz.lehrerlog.logging

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(title, text) => {
    if (navigator && navigator.share) {
        navigator.share({ title: title, text: text }).catch(() => {});
        return true;
    }
    return false;
}"""
)
external fun tryShareText(title: String, text: String): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(fileName, text) => {
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}"""
)
external fun downloadTextFile(fileName: String, text: String)

actual fun shareLogs(payload: LogSharePayload) {
    val shared = tryShareText(payload.title, payload.text)
    if (!shared) {
        downloadTextFile(payload.fileName, payload.text)
    }
}
