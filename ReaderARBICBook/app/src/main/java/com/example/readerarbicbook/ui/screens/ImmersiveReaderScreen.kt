package com.example.readerarbicbook.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.data.LocalBookMeta
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.ui.theme.*
import com.example.readerarbicbook.viewmodel.BookUiState
import com.example.readerarbicbook.viewmodel.BookViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    val context = LocalContext.current
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val localBookMeta by DevicePreferences.getLocalBookMeta(context).collectAsState(initial = emptyMap())
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    
    // Fetch book details
    LaunchedEffect(bookId, deviceId) {
        if (deviceId != "unknown" && bookId.isNotEmpty()) {
            bookViewModel.getBook(bookId.toIntOrNull() ?: 0, deviceId)
        }
    }

    val bookState by bookViewModel.bookState.collectAsState()

    // Reading State
    var currentPage by remember { mutableIntStateOf(1) }
    var hasRestoredProgress by remember { mutableStateOf(false) }
    var isImmersiveMode by remember { mutableStateOf(false) }
    var showAIAssistant by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(18.sp) }
    var lineSpacing by remember { mutableStateOf(1.8f) }
    var isDarkMode by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkMode) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                    )
                }
            )
    ) {
        when (val state = bookState) {
            is BookUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is BookUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("عودة")
                    }
                }
            }
            is BookUiState.Success -> {
                val book = state.book
                val localMeta: LocalBookMeta? = localBookMeta[book.id]
                val displayTitle = localMeta?.title?.trim().orEmpty().ifBlank { book.title }
                val displayAuthor = localMeta?.author?.trim().orEmpty().ifBlank { book.author }
                // Simple pagination logic: ~1000 characters per page
                val pages = remember(book.content) {
                    if (book.content.isEmpty()) listOf("لا يوجد محتوى لعرضه.")
                    else book.content.chunked(1000)
                }
                val totalPages = pages.size
                
                // Restore saved page
                LaunchedEffect(book.id, totalPages, book.last_page) {
                    val restored = (book.last_page ?: 1).coerceIn(1, totalPages)
                    currentPage = restored
                    hasRestoredProgress = true
                }
                
                // Save progress
                LaunchedEffect(currentPage, hasRestoredProgress, deviceId, book.id, book.last_page) {
                    if (!hasRestoredProgress) return@LaunchedEffect
                    if (deviceId == "unknown") return@LaunchedEffect
                    if (currentPage == (book.last_page ?: 1)) return@LaunchedEffect
                    bookViewModel.updateProgress(book.id, currentPage, deviceId)
                }

                // Ensure current page is valid
                val safeCurrentPage = currentPage.coerceIn(1, totalPages)
                val pageContent = pages.getOrElse(safeCurrentPage - 1) { "" }

                // Main Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (isImmersiveMode) 0.dp else 16.dp,
                            bottom = if (isImmersiveMode) 0.dp else 16.dp
                        )
                ) {
                    // Top Bar
                    AnimatedVisibility(
                        visible = !isImmersiveMode,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        ReaderTopBar(
                            title = displayTitle,
                            author = displayAuthor,
                            currentPage = safeCurrentPage,
                            totalPages = totalPages,
                            onNavigateBack = onNavigateBack
                        )
                    }
                    
                    // Reading Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isImmersiveMode) 32.dp else 24.dp,
                                vertical = if (isImmersiveMode) 24.dp else 16.dp
                            )
                    ) {
                        ReadingContent(
                            text = pageContent,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            isDarkMode = isDarkMode,
                            onTextSelected = { selectedText = it }
                        )
                    }
                    
                    // Bottom Controls
                    AnimatedVisibility(
                        visible = !isImmersiveMode,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        ReaderBottomControls(
                            currentPage = safeCurrentPage,
                            totalPages = totalPages,
                            onPageChange = { currentPage = it },
                            onToggleImmersive = { isImmersiveMode = !isImmersiveMode }
                        )
                    }
                }
                
                // AI Assistant Sheet
                AnimatedVisibility(
                    visible = showAIAssistant,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    AIReaderAssistantSheet(
                        selectedText = selectedText.ifEmpty { pageContent.take(200) + "..." }, // Context
                        onDismiss = { showAIAssistant = false },
                        onTextAnalysis = { 
                            // Here we would call AI service. For now, simulating analysis.
                            // In real app: bookViewModel.analyzeText(it)
                        }
                    )
                }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    author: String,
    currentPage: Int,
    totalPages: Int,
    onNavigateBack: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        blurRadius = 8.dp,
        borderWidth = 0.5.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(0.dp))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Reading Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الصفحة $currentPage من $totalPages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                Text(
                    text = "${((currentPage.toFloat() / totalPages) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LinearProgressIndicator(
                progress = { currentPage.toFloat() / totalPages },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ReadingContent(
    text: String,
    fontSize: TextUnit,
    lineSpacing: Float,
    isDarkMode: Boolean,
    onTextSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize,
                    lineHeight = fontSize * lineSpacing,
                    color = if (isDarkMode) {
                        Color(0xFFE2E8F0)
                    } else {
                        Color(0xFF1E293B)
                    }
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun ReaderBottomControls(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    onToggleImmersive: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        blurRadius = 8.dp,
        borderWidth = 0.5.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Page
            IconButton(
                onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
                enabled = currentPage > 1
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "الصفحة السابقة",
                    tint = if (currentPage > 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Page Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$currentPage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "من $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Next Page
            IconButton(
                onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
                enabled = currentPage < totalPages
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "الصفحة التالية",
                    tint = if (currentPage < totalPages) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Immersive Mode Toggle
            IconButton(onClick = onToggleImmersive) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "الوضع المغمور",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingAIReaderAssistant(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ai_button_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            }
    ) {
        GlassFloatingActionButton(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = AIPurpleGlow.copy(alpha = 0.6f),
                    spotColor = AIPurpleGlow.copy(alpha = 0.8f)
                )
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "المساعد الذكي",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIReaderAssistantSheet(
    selectedText: String,
    onDismiss: () -> Unit,
    onTextAnalysis: (String) -> Unit
) {
    GlassBottomSheet(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المساعد الذكي",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Selected Text (if any)
            if (selectedText.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = 4.dp,
                    borderWidth = 1.dp,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // AI Features for Reader
            AIReaderFeatureCard(
                title = "تلخيص النص",
                description = "احصل على ملخص ذكي للنص المحدد",
                icon = Icons.Default.Summarize,
                color = AIPurpleGlow,
                onClick = { onTextAnalysis(selectedText) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            AIReaderFeatureCard(
                title = "شرح المفردات",
                description = "اكتشف معاني الكلمات الصعبة",
                icon = Icons.Default.MenuBook,
                color = AIGoldAccent,
                onClick = { /* Handle vocabulary explanation */ }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            AIReaderFeatureCard(
                title = "تحليل الأسلوب",
                description = "افهم أسلوب الكاتب وتقنياته",
                icon = Icons.Default.Psychology,
                color = AICyanAccent,
                onClick = { /* Handle style analysis */ }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            AIReaderFeatureCard(
                title = "اقتباسات ذكية",
                description = "استخراج أجمل الاقتباسات من النص",
                icon = Icons.Default.FormatQuote,
                color = MaterialTheme.colorScheme.secondary,
                onClick = { /* Handle quote extraction */ }
            )
        }
    }
}

@Composable
private fun AIReaderFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        blurRadius = 6.dp,
        borderWidth = 1.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "انتقل",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ReaderSettingsPanel(
    fontSize: TextUnit,
    lineSpacing: Float,
    isDarkMode: Boolean,
    onFontSizeChange: (TextUnit) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onDarkModeToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        blurRadius = 12.dp,
        borderWidth = 1.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "إعدادات القراءة",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Font Size
            Text(
                text = "حجم الخط",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { 
                    if (fontSize.value > 12) onFontSizeChange((fontSize.value - 2).sp) 
                }) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "تصغير الخط",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "${fontSize.value.toInt()}sp",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.Center
                )
                
                IconButton(onClick = { 
                    if (fontSize.value < 24) onFontSizeChange((fontSize.value + 2).sp) 
                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "تكبير الخط",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Line Spacing
            Text(
                text = "تباعد الأسطر",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Slider(
                value = lineSpacing,
                onValueChange = onLineSpacingChange,
                valueRange = 1.2f..2.0f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )
            
            Text(
                text = "%.1f".format(lineSpacing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Dark Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الوضع الليلي",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onDarkModeToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun TextSelectionToolbar(
    selectedText: String,
    onDismiss: () -> Unit,
    onAIAnalysis: (String) -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        blurRadius = 8.dp,
        borderWidth = 1.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copy
            IconButton(onClick = { /* Copy text */ }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "نسخ",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Highlight
            IconButton(onClick = { /* Highlight text */ }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "تمييز",
                    tint = AIGoldAccent
                )
            }
            
            // AI Analysis
            IconButton(onClick = { 
                onAIAnalysis(selectedText)
                onDismiss()
            }) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "تحليل ذكي",
                    tint = AIPurpleGlow
                )
            }
            
            // Share
            IconButton(onClick = { /* Share text */ }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "مشاركة",
                    tint = AICyanAccent
                )
            }
            
            // Cancel
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إلغاء",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
