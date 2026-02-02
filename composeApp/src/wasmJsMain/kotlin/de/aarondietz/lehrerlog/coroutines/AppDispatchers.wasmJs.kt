package de.aarondietz.lehrerlog.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object AppDispatchers {
    actual val io: CoroutineDispatcher = Dispatchers.Default
}
