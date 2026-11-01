package com.example.readerarbicbook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.readerarbicbook.R

// Arabic font families - we'll use system fonts for now
// In a real app, you would add custom Arabic fonts to res/font/
val CairoFontFamily = FontFamily.Default
val AmiriFontFamily = FontFamily.Default  
val AlmaraiFontFamily = FontFamily.Default

// Luxury Typography for Arabic text
val LuxuryTypography = Typography(
    // Display Large - For main titles
    displayLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    
    // Display Medium - For section headers
    displayMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    
    // Display Small - For card titles
    displaySmall = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    
    // Headline Large - For book titles
    headlineLarge = TextStyle(
        fontFamily = AmiriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    
    // Headline Medium - For chapter titles
    headlineMedium = TextStyle(
        fontFamily = AmiriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    
    // Headline Small - For section headers
    headlineSmall = TextStyle(
        fontFamily = AmiriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    
    // Title Large - For important UI elements
    titleLarge = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    
    // Title Medium - For medium importance
    titleMedium = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    
    // Title Small - For small titles
    titleSmall = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    
    // Body Large - For main reading text
    bodyLarge = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp, // 1.5x line height for comfortable reading
        letterSpacing = 0.5.sp
    ),
    
    // Body Medium - For secondary text
    bodyMedium = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp, // 1.43x line height
        letterSpacing = 0.25.sp
    ),
    
    // Body Small - For captions
    bodySmall = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp, // 1.33x line height
        letterSpacing = 0.4.sp
    ),
    
    // Label Large - For buttons
    labelLarge = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    
    // Label Medium - For chips
    labelMedium = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    
    // Label Small - For tiny labels
    labelSmall = TextStyle(
        fontFamily = AlmaraiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Extended typography for luxury components
val LuxuryTypographyExtended = LuxuryTypography.copy(
    // Custom styles for luxury components
    headlineLarge = LuxuryTypography.headlineLarge.copy(
        fontSize = 34.sp,
        lineHeight = 42.sp // 1.7x line height for luxury feel
    ),
    
    bodyLarge = LuxuryTypography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 31.sp // 1.7x line height for comfortable reading
    ),
    
    bodyMedium = LuxuryTypography.bodyMedium.copy(
        fontSize = 16.sp,
        lineHeight = 27.sp // 1.7x line height
    )
)