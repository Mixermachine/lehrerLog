package de.aarondietz.lehrerlog.ui.navigation

sealed class ScreenRoute(val route: String) {
    data object Home : ScreenRoute("home")
    data object Tasks : ScreenRoute("tasks")
    data object Students : ScreenRoute("pupils")
    data object Settings : ScreenRoute("settings")
}