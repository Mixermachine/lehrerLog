package de.aarondietz.lehrerlog

import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.auth.createTokenStorage
import de.aarondietz.lehrerlog.data.api.SyncApi
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.SchoolRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import de.aarondietz.lehrerlog.data.repository.TaskRepository
import de.aarondietz.lehrerlog.database.DatabaseManager
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
    single { DatabaseManager(get()) }

    // Repositories
    single { StudentRepository(get(), get(), get(), ServerConfig.SERVER_URL, get()) }
    single { SchoolClassRepository(get(), get(), get(), ServerConfig.SERVER_URL, get()) }
    single { SchoolRepository(get(), ServerConfig.SERVER_URL) }
    single { TaskRepository(get(), get(), ServerConfig.SERVER_URL) }

    // Sync
    single { SyncApi(get(), get(), ServerConfig.SERVER_URL) }
    single { SyncManager(get(), get(), get(), get()) }

    viewModel { AuthViewModel(get(), get(), get()) }
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { StudentsViewModel(get(), get(), get(), get()) }
    viewModel { TasksViewModel(get(), get(), get(), get()) }
}

// For platform-specific dependencies (includes ConnectivityMonitor)
expect val platformModule: Module
