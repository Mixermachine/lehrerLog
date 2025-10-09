package de.aarondietz.lehrerlog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomBarEntry(val route: String, val title: String, val icon: ImageVector) {
    data object Home : BottomBarEntry("home", "Home", Icons.Default.Home)
    data object Tasks : BottomBarEntry("tasks", "Aufgaben", Icons.AutoMirrored.Filled.List)
    data object Students : BottomBarEntry("students", "Schüler", Icons.Default.People)
    data object Settings : BottomBarEntry("settings", "Einstellungen", Icons.Default.Settings)
}