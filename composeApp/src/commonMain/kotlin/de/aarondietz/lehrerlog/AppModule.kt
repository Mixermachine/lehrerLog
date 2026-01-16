package de.aarondietz.lehrerlog

import co.touchlab.kermit.Logger
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.platformLogWriter
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.auth.createTokenStorage
import de.aarondietz.lehrerlog.data.api.SyncApi
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import de.aarondietz.lehrerlog.database.createDatabase
import de.aarondietz.lehrerlog.network.createHttpClient
import de.aarondietz.lehrerlog.sync.SyncManager
import de.aarondietz.lehrerlog.ui.screens.auth.AuthViewModel
import de.aarondietz.lehrerlog.ui.screens.home.HomeViewModel
import de.aarondietz.lehrerlog.ui.screens.settings.SettingsViewModel
import de.aarondietz.lehrerlog.ui.screens.students.StudentsViewModel
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val commonModule = module {
    // Logging
    single {
        Logger.withTag("LehrerLog")
    }

    single { createHttpClient() }
    single<TokenStorage> { createTokenStorage() }
    single { AuthRepository(get(), get()) }

    // Database
    single { createDatabase(get()) }

    // Repositories
    single { StudentRepository(get(), get(), SERVER_URL, get()) }
    single { SchoolClassRepository(get(), get(), SERVER_URL, get()) }

    // Sync
    single { SyncApi(get(), SERVER_URL) }
    single { SyncManager(get(), get(), get(), get()) }

    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { StudentsViewModel(get(), get(), get(), get()) }
    viewModelOf(::TasksViewModel)
}

// For platform-specific dependencies (includes ConnectivityMonitor)
expect val platformModule: Module
