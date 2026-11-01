package com.example.readerarbicbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    blurRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassSurface = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val glassBackground = if (isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
    val glassBorder = if (isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    val glassShadow = Color.Black.copy(alpha = if (isDark) 0.30f else 0.08f)

    Box(
        modifier = modifier
            .clip(shape)
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = glassShadow,
                spotColor = glassShadow
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glassSurface,
                        glassBackground
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glassBorder,
                        Color.Transparent,
                        glassBorder
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun GlassSearchBar(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        blurRadius = 12.dp,
        borderWidth = 0.5.dp,
        shadowElevation = 8.dp
    ) {
        content()
    }
}

@Composable
fun GlassBottomSheet(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassSurface = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.80f)
    val glassBackground = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.72f)
    val glassBorder = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glassSurface,
                        glassBackground.copy(alpha = 0.8f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glassBorder,
                        Color.Transparent,
                        glassBorder
                    )
                ),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
    ) {
        content()
    }
}

@Composable
fun GlassFloatingActionButton(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(50),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
            .blur(2.dp)
    ) {
        content()
    }
}

@Composable
fun GlassBookCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        blurRadius = 8.dp,
        borderWidth = 0.5.dp,
        shadowElevation = 12.dp
    ) {
        content()
    }
}

// Extension function for neon glow effect
fun Modifier.neonBorder(
    color: Color,
    width: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(32.dp)
): Modifier = this.drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        color = color,
        style = Stroke(width.toPx())
    )
}

// Extension function for gradient background
fun Modifier.luxuryGradientBackground(): Modifier = composed {
    this.background(
        brush = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    )
}
