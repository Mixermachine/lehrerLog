package de.aarondietz.lehrerlog

import co.touchlab.kermit.Severity
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.auth.createTokenStorage
import de.aarondietz.lehrerlog.data.repository.*
import de.aarondietz.lehrerlog.logging.LogFileWriter
import de.aarondietz.lehrerlog.logging.LogRepository
import de.aarondietz.lehrerlog.logging.LoggerConfig
import de.aarondietz.lehrerlog.network.createHttpClient
import de.aarondietz.lehrerlog.ui.screens.auth.AuthViewModel
import de.aarondietz.lehrerlog.ui.screens.home.HomeViewModel
import de.aarondietz.lehrerlog.ui.screens.late_periods.LatePeriodManagementViewModel
import de.aarondietz.lehrerlog.ui.screens.parent.ParentAssignmentsViewModel
import de.aarondietz.lehrerlog.ui.screens.parent.ParentStudentsViewModel
import de.aarondietz.lehrerlog.ui.screens.parent.ParentSubmissionsViewModel
import de.aarondietz.lehrerlog.ui.screens.parent_management.ParentInviteManagementViewModel
import de.aarondietz.lehrerlog.ui.screens.settings.SettingsViewModel
import de.aarondietz.lehrerlog.ui.screens.students.StudentsViewModel
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val commonModule = module {
    // Logging
    single { LogFileWriter().apply { initialize() } }
    single(createdAtStart = true) {
        LoggerConfig.apply {
            initialize(
                fileWriter = get(),
                minSeverity = if (isDebugBuild()) Severity.Debug else Severity.Info,
                enableFileLogging = true,
                enableConsoleLogging = isDebugBuild()
            )
        }
    }
    single { LogRepository(get()) }

    single { createHttpClient() }
    single<TokenStorage> { createTokenStorage() }
    single { AuthRepository(get(), get()) }

    // Repositories
    single { StudentRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { SchoolClassRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { SchoolRepository(get(), ServerConfig.SERVER_URL) }
    single { TaskRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { ParentRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { ParentInviteRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { ParentLinksRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { ParentSelectionRepository() }
    single { LateStatsRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { PunishmentRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { StorageRepository(get(), get(), ServerConfig.SERVER_URL) }
    single { LatePeriodRepository(get(), get(), ServerConfig.SERVER_URL) }

    viewModel { AuthViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { StudentsViewModel(get(), get(), get(), get(), get()) }
    viewModel { TasksViewModel(get(), get(), get(), get()) }
    viewModel { ParentStudentsViewModel(get(), get()) }
    viewModel { ParentAssignmentsViewModel(get(), get()) }
    viewModel { ParentSubmissionsViewModel(get(), get()) }
    viewModel { ParentInviteManagementViewModel(get(), get()) }
    viewModel { LatePeriodManagementViewModel(get()) }
}

// For platform-specific dependencies
expect val platformModule: Module
