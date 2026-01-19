package de.aarondietz.lehrerlog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

sealed class BottomBarEntry(val route: String, val titleRes: StringResource, val icon: ImageVector) {
    data object Home : BottomBarEntry("home", Res.string.nav_home, Icons.Default.Home)
    data object Tasks : BottomBarEntry("tasks", Res.string.nav_tasks, Icons.AutoMirrored.Filled.List)
    data object Students : BottomBarEntry("students", Res.string.nav_students, Icons.Default.People)
    data object Settings : BottomBarEntry("settings", Res.string.nav_settings, Icons.Default.Settings)
}