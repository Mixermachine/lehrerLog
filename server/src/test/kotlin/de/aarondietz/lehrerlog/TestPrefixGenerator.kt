package de.aarondietz.lehrerlog

import java.util.concurrent.atomic.AtomicLong

object TestPrefixGenerator {
    private val counter = AtomicLong()

    fun next(): String {
        val suffix = counter.incrementAndGet()
        val random = (10000..99999).random()
        val pid = ProcessHandle.current().pid()
        return "testing${random}_${pid}_$suffix"
    }
}
