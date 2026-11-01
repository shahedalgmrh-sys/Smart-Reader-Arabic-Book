package com.example.readerarbicbook.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    PureDark,
    SepiaNight,
    DynamicAdaptive;

    companion object {
        fun fromPreference(value: String?): ThemeMode {
            return when ((value ?: "").trim().uppercase()) {
                "PURE_DARK" -> PureDark
                "SEPIA_NIGHT" -> SepiaNight
                "DYNAMIC_ADAPTIVE" -> DynamicAdaptive
                else -> DynamicAdaptive
            }
        }

        fun toPreference(mode: ThemeMode): String {
            return when (mode) {
                PureDark -> "PURE_DARK"
                SepiaNight -> "SEPIA_NIGHT"
                DynamicAdaptive -> "DYNAMIC_ADAPTIVE"
            }
        }
    }
}

// Deep Midnight Luxury Color Scheme
private val DeepMidnightDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6D5BD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3F2B96),
    onPrimaryContainer = Color(0xFFFFFFFF),
    
    secondary = Color(0xFF14B8A6),
    onSecondary = Color(0xFF031312),
    secondaryContainer = Color(0xFF0F766E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0B1B2B),
    tertiaryContainer = Color(0xFF1D4ED8),
    onTertiaryContainer = Color(0xFFFFFFFF),
    
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    
    surface = Color(0xFF121B2E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1B2740),
    onSurfaceVariant = Color(0xFFB7C3D6),
    
    surfaceTint = Color(0xFF6D5BD0),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF0B1220),
    
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFDC2626),
    onErrorContainer = Color(0xFFFFFFFF),
    
    outline = Color(0xFF334760),
    outlineVariant = Color(0xFF2A3B52),
    scrim = Color(0xFF000000)
)

private val PureDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6D5BD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3F2B96),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color(0xFF031312),
    secondaryContainer = Color(0xFF0F766E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0B1B2B),
    tertiaryContainer = Color(0xFF1D4ED8),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = Color(0xFFE5E7EB),
    surfaceTint = Color(0xFF6D5BD0),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFDC2626),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF3A3A3A),
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF7C2D12),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE9ECF6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

private val SepiaNightColorScheme = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9D8C1),
    onPrimaryContainer = Color(0xFF2B1E13),
    secondary = Color(0xFFB7791F),
    onSecondary = Color(0xFF1B120B),
    secondaryContainer = Color(0xFFF3D6A8),
    onSecondaryContainer = Color(0xFF2B1E13),
    tertiary = Color(0xFF2F855A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC6F6D5),
    onTertiaryContainer = Color(0xFF1C4532),
    background = Color(0xFFF1E1C8),
    onBackground = Color(0xFF2B1E13),
    surface = Color(0xFFF7EAD1),
    onSurface = Color(0xFF2B1E13),
    surfaceVariant = Color(0xFFE2CDAA),
    onSurfaceVariant = Color(0xFF2B1E13),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF2B8B5),
    onErrorContainer = Color(0xFF601410),
    outline = Color(0xFF9A7B53),
    outlineVariant = Color(0xFFC7AE86)
)

private data class AccentPalette(
    val id: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

private val AccentPalettes = listOf(
    AccentPalette(
        id = "DEFAULT",
        primary = Color(0xFF6D5BD0),
        secondary = Color(0xFF14B8A6),
        tertiary = Color(0xFF60A5FA)
    ),
    AccentPalette(
        id = "BLUE",
        primary = Color(0xFF3B82F6),
        secondary = Color(0xFF22C55E),
        tertiary = Color(0xFFA78BFA)
    ),
    AccentPalette(
        id = "TEAL",
        primary = Color(0xFF14B8A6),
        secondary = Color(0xFF60A5FA),
        tertiary = Color(0xFFF59E0B)
    ),
    AccentPalette(
        id = "ROSE",
        primary = Color(0xFFFB7185),
        secondary = Color(0xFF60A5FA),
        tertiary = Color(0xFF34D399)
    )
)

private fun accentPaletteFromPreference(value: String?): AccentPalette? {
    val normalized = (value ?: "").trim().uppercase()
    if (normalized.isBlank() || normalized == "DEFAULT") return null
    return AccentPalettes.firstOrNull { it.id == normalized }
}

private fun onColorFor(background: Color): Color {
    return if (background.luminance() < 0.45f) Color.White else Color(0xFF111827)
}

private fun containerFor(accent: Color, isDark: Boolean): Color {
    return if (isDark) lerp(accent, Color.Black, 0.42f) else lerp(accent, Color.White, 0.82f)
}

private fun applyAccentPalette(base: ColorScheme, palette: AccentPalette, isDark: Boolean): ColorScheme {
    val primaryContainer = containerFor(palette.primary, isDark)
    val secondaryContainer = containerFor(palette.secondary, isDark)
    val tertiaryContainer = containerFor(palette.tertiary, isDark)

    return base.copy(
        primary = palette.primary,
        onPrimary = onColorFor(palette.primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onColorFor(primaryContainer),
        secondary = palette.secondary,
        onSecondary = onColorFor(palette.secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onColorFor(secondaryContainer),
        tertiary = palette.tertiary,
        onTertiary = onColorFor(palette.tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColorFor(tertiaryContainer),
        surfaceTint = palette.primary
    )
}

// Glassmorphism colors with transparency
val GlassSurface = Color(0x1AFFFFFF) // 10% white transparency
val GlassBorder = Color(0x33FFFFFF) // 20% white transparency
val GlassBackground = Color(0x0DFFFFFF) // 5% white transparency
val GlassShadow = Color(0x4D000000) // 30% black shadow

// AI Feature colors
val AIPurpleGlow = Color(0xFF6D5BD0)
val AIGoldAccent = Color(0xFFE2B44A)
val AICyanAccent = Color(0xFF14B8A6)

// Shimmer colors
val ShimmerBaseColor = Color(0xFF121B2E)
val ShimmerHighlightColor = Color(0xFF1B2740)

// Gradient colors for backgrounds
val GradientStart = Color(0xFF0B1220)
val GradientEnd = Color(0xFF121B2E)
val GradientMid = Color(0xFF1B2740)

@Composable
fun SmartBookReaderTheme(
    themeMode: ThemeMode = ThemeMode.DynamicAdaptive,
    accentPalettePref: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.PureDark -> true
        ThemeMode.SepiaNight -> false
        ThemeMode.DynamicAdaptive -> isSystemDark
    }

    val baseScheme = when (themeMode) {
        ThemeMode.PureDark -> PureDarkColorScheme
        ThemeMode.SepiaNight -> SepiaNightColorScheme
        ThemeMode.DynamicAdaptive -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (useDark) DeepMidnightDarkColorScheme else LightColorScheme
            }
        }
    }

    val paletteOverride = accentPaletteFromPreference(accentPalettePref)
    val colorScheme = if (themeMode == ThemeMode.DynamicAdaptive || paletteOverride == null) {
        baseScheme
    } else {
        applyAccentPalette(baseScheme, paletteOverride, useDark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuxuryTypography,
        content = content
    )
}
