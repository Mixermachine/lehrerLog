package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.auth.createTokenStorage
import de.aarondietz.lehrerlog.network.createHttpClient
import de.aarondietz.lehrerlog.ui.screens.auth.AuthViewModel
import de.aarondietz.lehrerlog.ui.screens.home.HomeViewModel
import de.aarondietz.lehrerlog.ui.screens.settings.SettingsViewModel
import de.aarondietz.lehrerlog.ui.screens.students.StudentsViewModel
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val commonModule = module {
    single { createHttpClient() }
    single<TokenStorage> { createTokenStorage() }
    single { AuthRepository(get(), get()) }

    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::StudentsViewModel)
    viewModelOf(::TasksViewModel)
}

// For platform-specific dependencies
expect val platformModule: Module
