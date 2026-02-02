package de.aarondietz.lehrerlog.coroutines

import kotlinx.coroutines.CoroutineDispatcher

expect object AppDispatchers {
    val io: CoroutineDispatcher
}
