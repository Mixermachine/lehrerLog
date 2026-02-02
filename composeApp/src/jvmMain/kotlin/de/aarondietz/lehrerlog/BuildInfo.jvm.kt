package de.aarondietz.lehrerlog

actual fun isDebugBuild(): Boolean {
    return System.getProperty("env", "production") == "development"
}
