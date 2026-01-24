package de.aarondietz.lehrerlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.aarondietz.lehrerlog.ui.navigation.BottomBarEntry
import de.aarondietz.lehrerlog.ui.screens.auth.AuthState
import de.aarondietz.lehrerlog.ui.screens.auth.AuthViewModel
import de.aarondietz.lehrerlog.ui.screens.auth.LoginScreen
import de.aarondietz.lehrerlog.ui.screens.auth.RegisterScreen
import de.aarondietz.lehrerlog.ui.screens.home.HomeScreen
import de.aarondietz.lehrerlog.ui.screens.settings.SettingsScreen
import de.aarondietz.lehrerlog.ui.screens.students.StudentsScreen
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksScreen
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    KoinApplication(application = {
        modules(commonModule, platformModule)
    }) {
        AppContent()
    }
}

@Composable
private fun AppContent(authViewModel: AuthViewModel = koinViewModel()) {
    val authState by authViewModel.authState.collectAsState()

    var showInstallButton by remember { mutableStateOf(false) }
    var showInstallIos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        initInstallPrompt { canInstall ->
            showInstallButton = canInstall && !isStandalone()
        }

        if (isIos() && !isStandalone()) {
            showInstallIos = true
        }
    }

    when (authState) {
        is AuthState.Initial, is AuthState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Unauthenticated, is AuthState.Error -> {
            AuthNavigation(authViewModel = authViewModel)
        }
        is AuthState.Authenticated -> {
            MainAppContent(authViewModel = authViewModel)
        }
    }
}

@Composable
private fun AuthNavigation(authViewModel: AuthViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate("register") {
                        launchSingleTop = true
                    }
                },
                viewModel = authViewModel
            )
        }
        composable("register") {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                viewModel = authViewModel
            )
        }
    }
}

@Composable
private fun MainAppContent(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val items = listOf(BottomBarEntry.Home, BottomBarEntry.Tasks, BottomBarEntry.Students, BottomBarEntry.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    val title = stringResource(screen.titleRes)
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = title) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = BottomBarEntry.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(BottomBarEntry.Home.route) { HomeScreen() }
            composable(BottomBarEntry.Tasks.route) { TasksScreen() }
            composable(BottomBarEntry.Students.route) { StudentsScreen() }
            composable(BottomBarEntry.Settings.route) { SettingsScreen(onLogout = { authViewModel.logout() }) }
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    MaterialTheme {
        App()
    }
}
