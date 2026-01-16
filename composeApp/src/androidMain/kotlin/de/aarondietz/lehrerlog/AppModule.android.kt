package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.database.DatabaseDriverFactory
import de.aarondietz.lehrerlog.sync.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { ConnectivityMonitor(androidContext()) }
}
