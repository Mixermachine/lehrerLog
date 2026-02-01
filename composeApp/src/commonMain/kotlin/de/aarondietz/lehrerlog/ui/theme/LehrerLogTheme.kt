package de.aarondietz.lehrerlog.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

data class Spacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp
)

data class LayoutMetrics(
    val compactWidth: Dp
)

private val DefaultSpacing = Spacing(
    xs = 4.dp,
    sm = 8.dp,
    md = 16.dp,
    lg = 24.dp,
    xl = 32.dp
)

private val DefaultLayoutMetrics = LayoutMetrics(
    compactWidth = 360.dp
)

private val LocalSpacing = staticCompositionLocalOf { DefaultSpacing }
private val LocalLayoutMetrics = staticCompositionLocalOf { DefaultLayoutMetrics }

val MaterialTheme.spacing: Spacing
    @Composable get() = LocalSpacing.current

val MaterialTheme.layoutMetrics: LayoutMetrics
    @Composable get() = LocalLayoutMetrics.current

@Composable
fun LehrerLogTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalSpacing provides DefaultSpacing,
            LocalLayoutMetrics provides DefaultLayoutMetrics
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun LehrerLogThemePreview() {
    LehrerLogTheme {
        Box(modifier = Modifier)
    }
}
