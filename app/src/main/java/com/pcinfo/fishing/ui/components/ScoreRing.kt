package com.pcinfo.fishing.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.model.AdviceLevel

/** 等级配色：绿=适合，橙=一般，红=不宜 */
fun levelColor(level: AdviceLevel): Color = when (level) {
    AdviceLevel.EXCELLENT -> Color(0xFF1B8A3C)
    AdviceLevel.GOOD -> Color(0xFF43A047)
    AdviceLevel.FAIR -> Color(0xFFEF6C00)
    AdviceLevel.POOR -> Color(0xFFE65100)
    AdviceLevel.BAD -> Color(0xFFC62828)
}

@Composable
fun ScoreRing(
    score: Int,
    level: AdviceLevel,
    modifier: Modifier = Modifier,
    boxSize: Dp = 148.dp
) {
    val color = levelColor(level)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(boxSize)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            val diameter = size.width.coerceAtMost(size.height) - stroke.width
            val topLeft = Offset(stroke.width / 2f, stroke.width / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = level.label,
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
            Text(
                text = level.emoji,
                fontSize = 18.sp
            )
        }
    }
}
