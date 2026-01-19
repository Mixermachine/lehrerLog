package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.database.DatabaseDriverFactory
import de.aarondietz.lehrerlog.sync.ConnectivityMonitor
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(null) }
    single { ConnectivityMonitor(null) }
}
