package de.aarondietz.lehrerlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.aarondietz.lehrerlog.auth.UserRole
import de.aarondietz.lehrerlog.ui.navigation.BottomBarEntry
import de.aarondietz.lehrerlog.ui.navigation.ParentBottomBarEntry
import de.aarondietz.lehrerlog.ui.screens.auth.*
import de.aarondietz.lehrerlog.ui.screens.home.HomeScreen
import de.aarondietz.lehrerlog.ui.screens.parent.ParentAssignmentsScreen
import de.aarondietz.lehrerlog.ui.screens.parent.ParentStudentsScreen
import de.aarondietz.lehrerlog.ui.screens.parent.ParentSubmissionsScreen
import de.aarondietz.lehrerlog.ui.screens.settings.SettingsScreen
import de.aarondietz.lehrerlog.ui.screens.students.StudentsScreen
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksScreen
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    KoinApplication(application = {
        modules(commonModule, platformModule)
    }) {
        LehrerLogTheme {
            AppContent()
        }
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
            val user = (authState as AuthState.Authenticated).user
            if (user.role == UserRole.PARENT.name) {
                ParentAppContent(authViewModel = authViewModel)
            } else {
                MainAppContent(authViewModel = authViewModel, schoolId = user.schoolId)
            }
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
                onNavigateToParentInvite = {
                    navController.navigate("parent_invite") {
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
        composable("parent_invite") {
            ParentInviteRedeemScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                viewModel = authViewModel
            )
        }
    }
}

@Composable
private fun MainAppContent(authViewModel: AuthViewModel, schoolId: String?) {
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
            composable(BottomBarEntry.Home.route) { HomeScreen(schoolId = schoolId) }
            composable(BottomBarEntry.Tasks.route) { TasksScreen() }
            composable(BottomBarEntry.Students.route) { StudentsScreen() }
            composable(BottomBarEntry.Settings.route) { SettingsScreen(onLogout = { authViewModel.logout() }) }
        }
    }
}

@Composable
private fun ParentAppContent(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val items = listOf(
        ParentBottomBarEntry.Students,
        ParentBottomBarEntry.Assignments,
        ParentBottomBarEntry.Submissions,
        ParentBottomBarEntry.Settings
    )

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
            startDestination = ParentBottomBarEntry.Students.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(ParentBottomBarEntry.Students.route) { ParentStudentsScreen() }
            composable(ParentBottomBarEntry.Assignments.route) { ParentAssignmentsScreen() }
            composable(ParentBottomBarEntry.Submissions.route) { ParentSubmissionsScreen() }
            composable(ParentBottomBarEntry.Settings.route) {
                SettingsScreen(onLogout = { authViewModel.logout() })
            }
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    LehrerLogTheme {
        App()
    }
}
