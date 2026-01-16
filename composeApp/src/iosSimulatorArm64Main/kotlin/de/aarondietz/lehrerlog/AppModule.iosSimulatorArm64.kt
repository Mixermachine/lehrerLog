package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.database.DatabaseDriverFactory
import de.aarondietz.lehrerlog.sync.ConnectivityMonitor
import org.koin.dsl.module
import org.koin.core.module.Module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(null) }
    single { ConnectivityMonitor(null) }
}
