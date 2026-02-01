package de.aarondietz.lehrerlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.ui.theme.spacing

data class BarChartData(
    val label: String,
    val value: Int,
    val color: Color
)

@Composable
fun LateStatsBarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier,
    maxHeight: Float = 200f
) {
    val maxValue = data.maxOfOrNull { it.value } ?: 1
    val barWidth = 40.dp
    val spacing = MaterialTheme.spacing.sm

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chart area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Value label
                    Text(
                        text = item.value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Bar
                    val barHeight = if (maxValue > 0) {
                        (item.value.toFloat() / maxValue * maxHeight).coerceAtLeast(4f)
                    } else {
                        4f
                    }
                    Canvas(
                        modifier = Modifier
                            .width(barWidth)
                            .height(barHeight.dp)
                    ) {
                        drawRect(
                            color = item.color,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            data.forEach { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun StudentLateStatsChart(
    studentNames: List<String>,
    lateCounts: List<Int>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = MaterialTheme.colorScheme.tertiary

    val chartData = studentNames.zip(lateCounts).map { (name, count) ->
        BarChartData(
            label = name.take(10),
            value = count,
            color = when {
                count == 0 -> primaryColor
                count < 3 -> warningColor
                else -> errorColor
            }
        )
    }

    LateStatsBarChart(
        data = chartData,
        modifier = modifier
    )
}
