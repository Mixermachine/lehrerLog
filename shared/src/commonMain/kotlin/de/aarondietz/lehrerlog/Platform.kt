package de.aarondietz.lehrerlog

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform