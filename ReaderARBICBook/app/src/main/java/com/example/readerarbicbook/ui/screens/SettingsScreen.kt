package com.example.readerarbicbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.viewmodel.BookViewModel

import androidx.compose.ui.platform.LocalContext
import com.example.readerarbicbook.ui.theme.ThemeMode

import com.example.readerarbicbook.data.DevicePreferences
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val themeModePref by DevicePreferences.getThemeMode(context).collectAsState(initial = "DYNAMIC_ADAPTIVE")
    val themeMode = remember(themeModePref) { ThemeMode.fromPreference(themeModePref) }
    val accentPalettePref by DevicePreferences.getAccentPalette(context).collectAsState(initial = "DEFAULT")
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val accentPaletteId = remember(accentPalettePref) { accentPalettePref.trim().uppercase() }
    
    var showContactDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryGradientBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isArabic) "الإعدادات" else "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Language Section
                    Text(
                        text = if (isArabic) "اللغة" else "Language",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isArabic,
                            onClick = { scope.launch { DevicePreferences.setLanguage(context, "English") } },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(if (isArabic) "إنجليزي" else "English", color = MaterialTheme.colorScheme.onSurface)
                        
                        Spacer(modifier = Modifier.width(24.dp))
                        
                        RadioButton(
                            selected = isArabic,
                            onClick = { scope.launch { DevicePreferences.setLanguage(context, "Arabic") } },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(if (isArabic) "عربي" else "Arabic", color = MaterialTheme.colorScheme.onSurface)
                    }
                    
                    Divider(
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    
                    // Theme Section
                    Text(
                        text = if (isArabic) "المظهر" else "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.DarkMode,
                            title = if (isArabic) "الوضع\nالداكن" else "Dark\nMode",
                            selected = themeMode == ThemeMode.PureDark,
                            containerColor = androidx.compose.ui.graphics.Color(0xFF0B0B0B),
                            contentColor = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            onClick = {
                                scope.launch {
                                    DevicePreferences.setThemeMode(context, ThemeMode.toPreference(ThemeMode.PureDark))
                                }
                            }
                        )
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.NightsStay,
                            title = if (isArabic) "الوضع\nالليلي" else "Night\nMode",
                            selected = themeMode == ThemeMode.SepiaNight,
                            containerColor = androidx.compose.ui.graphics.Color(0xFF9A6B3A),
                            contentColor = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            onClick = {
                                scope.launch {
                                    DevicePreferences.setThemeMode(context, ThemeMode.toPreference(ThemeMode.SepiaNight))
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Palette,
                            title = if (isArabic) "تخصيص" else "Customize",
                            selected = false,
                            containerColor = androidx.compose.ui.graphics.Color(0xFF4C7188),
                            contentColor = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            onClick = { showCustomizeDialog = true }
                        )
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.AutoMode,
                            title = if (isArabic) "الوضع\nالتلقائي" else "Auto\nMode",
                            selected = themeMode == ThemeMode.DynamicAdaptive,
                            containerColor = androidx.compose.ui.graphics.Color(0xFFF4E8D7),
                            contentColor = androidx.compose.ui.graphics.Color(0xFF1F1A14),
                            onClick = {
                                scope.launch {
                                    DevicePreferences.setThemeMode(context, ThemeMode.toPreference(ThemeMode.DynamicAdaptive))
                                }
                            }
                        )
                    }
                    
                    Divider(
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    // Links
                    SettingsButton(if (isArabic) "من نحن" else "About Us", onClick = { showAboutDialog = true })
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsButton(if (isArabic) "اتصل بنا" else "Contact Us", onClick = { showContactDialog = true })
                }
            }
        }
    }
    
    // Contact Dialog
    if (showContactDialog) {
        ContactDialog(
            isArabic = isArabic,
            onDismiss = { showContactDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            isArabic = isArabic,
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showCustomizeDialog) {
        val palettes = remember {
            listOf(
                AccentPaletteUi(
                    id = "DEFAULT",
                    titleAr = "افتراضي",
                    titleEn = "Default",
                    colors = listOf(Color(0xFF6D5BD0), Color(0xFF14B8A6), Color(0xFF60A5FA))
                ),
                AccentPaletteUi(
                    id = "BLUE",
                    titleAr = "أزرق",
                    titleEn = "Blue",
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFA78BFA))
                ),
                AccentPaletteUi(
                    id = "TEAL",
                    titleAr = "تركواز",
                    titleEn = "Teal",
                    colors = listOf(Color(0xFF14B8A6), Color(0xFF60A5FA), Color(0xFFF59E0B))
                ),
                AccentPaletteUi(
                    id = "ROSE",
                    titleAr = "وردي",
                    titleEn = "Rose",
                    colors = listOf(Color(0xFFFB7185), Color(0xFF60A5FA), Color(0xFF34D399))
                )
            )
        }

        AlertDialog(
            onDismissRequest = { showCustomizeDialog = false },
            title = {
                Text(
                    text = if (isArabic) "تخصيص الألوان" else "Accent Colors",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isArabic) {
                            "اختر ألوان الواجهة (يُطبق على الوضع الداكن والوضع الليلي)."
                        } else {
                            "Choose app accent colors (applies to Dark & Night modes)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    palettes.forEach { palette ->
                        AccentPaletteRow(
                            title = if (isArabic) palette.titleAr else palette.titleEn,
                            colors = palette.colors,
                            selected = accentPaletteId == palette.id,
                            onClick = {
                                scope.launch { DevicePreferences.setAccentPalette(context, palette.id) }
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomizeDialog = false }) {
                    Text(if (isArabic) "تم" else "Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { DevicePreferences.setAccentPalette(context, "DEFAULT") }
                    }
                ) {
                    Text(if (isArabic) "إعادة ضبط" else "Reset")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class AccentPaletteUi(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val colors: List<Color>
)

@Composable
private fun ThemeModeCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selected: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 6.dp else 0.dp,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AccentPaletteRow(
    title: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        tonalElevation = if (selected) 1.dp else 0.dp,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.take(3).forEach { c ->
                    Surface(
                        modifier = Modifier.size(14.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = c
                    ) {}
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isArabic) "اتصل بنا" else "Contact Us",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (isArabic) "نحن هنا لمساعدتك" else "We are here to help you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                ContactInfoItem(
                    icon = Icons.Default.Email,
                    label = if (isArabic) "البريد الإلكتروني" else "Email",
                    value = "support@smartreader.com",
                    isArabic = isArabic
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                ContactInfoItem(
                    icon = Icons.Default.Phone,
                    label = if (isArabic) "الهاتف" else "Phone",
                    value = "+966 55 123 4567",
                    isArabic = isArabic
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                ContactInfoItem(
                    icon = Icons.Default.Language,
                    label = if (isArabic) "الموقع الإلكتروني" else "Website",
                    value = "www.smartreader.com",
                    isArabic = isArabic
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                ContactInfoItem(
                    icon = Icons.Default.Share,
                    label = if (isArabic) "منصات التواصل" else "Social Media",
                    value = "@smartreader_app",
                    isArabic = isArabic
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isArabic) "إغلاق" else "Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isArabic) "من نحن" else "About Us",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isArabic) """
                        تطبيق قارئ الكتب الذكي هو منصة متكاملة تهدف إلى إثراء المحتوى العربي وتوفير تجربة قراءة فريدة ومتميزة.
                        
                        نسعى من خلال هذا التطبيق إلى دمج أحدث تقنيات الذكاء الاصطناعي مع جماليات التصميم العربي الأصيل، لتقديم تجربة لا تضاهى للقارئ العربي.
                        
                        المميزات:
                        - مكتبة ضخمة من الكتب العربية والعالمية
                        - واجهة مستخدم عصرية ومريحة للعين
                        - أدوات ذكية للمساعدة في القراءة والفهم
                        - دعم كامل للوضع الليلي والقراءة الصوتية
                        
                        فريق العمل:
                        نخبة من المطورين والمصممين الشغوفين باللغة العربية والتقنية.
                    """.trimIndent() else """
                        Smart Book Reader is a complete platform aiming to enrich Arabic content and provide a unique reading experience.
                        
                        We blend the latest AI technologies with elegant design to offer an unmatched experience.
                        
                        Features:
                        - Large library of Arabic and international books
                        - Modern and eye-comfortable UI
                        - Smart tools to assist reading and understanding
                        - Full support for dark mode and audio reading
                        
                        Team:
                        A passionate team of developers and designers.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.6
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isArabic) "إغلاق" else "Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ContactInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isArabic: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsButton(
    text: String,
    onClick: () -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
