package de.aarondietz.lehrerlog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

sealed class ParentBottomBarEntry(
    val route: String,
    val titleRes: StringResource,
    val icon: ImageVector
) {
    data object Students : ParentBottomBarEntry(
        route = "parent_students",
        titleRes = Res.string.nav_parent_students,
        icon = Icons.Default.People
    )

    data object Assignments : ParentBottomBarEntry(
        route = "parent_assignments",
        titleRes = Res.string.nav_parent_assignments,
        icon = Icons.AutoMirrored.Filled.List
    )

    data object Submissions : ParentBottomBarEntry(
        route = "parent_submissions",
        titleRes = Res.string.nav_parent_submissions,
        icon = Icons.AutoMirrored.Filled.List
    )

    data object Settings : ParentBottomBarEntry(
        route = "settings",
        titleRes = Res.string.nav_settings,
        icon = Icons.Default.Settings
    )
}
