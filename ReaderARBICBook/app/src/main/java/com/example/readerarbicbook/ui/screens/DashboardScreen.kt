package com.example.readerarbicbook.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import coil.compose.AsyncImage
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.ui.theme.*
import kotlinx.coroutines.delay

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.data.LocalBookMeta
import com.example.readerarbicbook.network.BookSummary
import com.example.readerarbicbook.viewmodel.BookViewModel
import com.example.readerarbicbook.viewmodel.BooksListUiState

import com.example.readerarbicbook.BuildConfig

// Data classes for AI recommendations
sealed class RecommendationItem {
    data class AIBookRecommendation(
        val id: String,
        val title: String,
        val author: String,
        val coverUrl: String,
        val rating: Float,
        val reason: String,
        val aiScore: Float,
        val genre: String
    ) : RecommendationItem()
    
    data class ReadingProgress(
        val bookId: String,
        val title: String,
        val author: String,
        val coverUrl: String,
        val progress: Float,
        val pagesRead: Int,
        val totalPages: Int,
        val estimatedTimeLeft: String
    ) : RecommendationItem()
    
    object Loading : RecommendationItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    onBookClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    val context = LocalContext.current
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val localBookMeta by DevicePreferences.getLocalBookMeta(context).collectAsState(initial = emptyMap())
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(deviceId) {
        if (deviceId != "unknown") {
            bookViewModel.getBooks(deviceId, null)
        }
    }

    val booksListState by bookViewModel.booksListState.collectAsState()

    val recentBooks = remember(booksListState, deviceId) {
        if (booksListState is BooksListUiState.Success) {
            (booksListState as BooksListUiState.Success).books.take(2)
        } else {
            emptyList()
        }
    }

    val continueReading = remember(booksListState, deviceId, localBookMeta) {
        if (booksListState is BooksListUiState.Success) {
            val books = (booksListState as BooksListUiState.Success).books
            books.shuffled().take(3).map { book ->
                val localMeta = localBookMeta[book.id]
                val displayTitle = localMeta?.title?.trim().orEmpty().ifBlank { book.title }
                val displayAuthor = localMeta?.author?.trim().orEmpty().ifBlank { book.author }
                val coverUrl = when {
                    !localMeta?.coverUri.isNullOrBlank() -> localMeta?.coverUri.orEmpty()
                    book.cover_image != null -> "${BuildConfig.BASE_URL.removeSuffix("/")}/books/${book.id}/cover?android_id=$deviceId"
                    else -> ""
                }

                RecommendationItem.ReadingProgress(
                    bookId = book.id.toString(),
                    title = displayTitle,
                    author = displayAuthor,
                    coverUrl = coverUrl,
                    progress = 0.1f,
                    pagesRead = 10,
                    totalPages = 100,
                    estimatedTimeLeft = "غير محدد"
                )
            }
        } else {
            emptyList()
        }
    }

    val isLoading = booksListState is BooksListUiState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryGradientBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            GlassSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchClick() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "...ابحث عن كتبك المفضلة" else "...Search your favorite books",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isArabic) "بحث" else "Search",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            WelcomeSection(isArabic = isArabic)

            Spacer(modifier = Modifier.height(24.dp))

            LuxuryTabRow(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                isArabic = isArabic
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                ShimmerAIRecommendations()
            } else {
                when (selectedTab) {
                    0 -> LatestAdditionsSection(
                        books = recentBooks,
                        deviceId = deviceId,
                        onBookClick = onBookClick,
                        onRefresh = {
                            if (deviceId != "unknown") {
                                bookViewModel.getBooks(deviceId, null)
                            }
                        },
                        isArabic = isArabic,
                        localBookMeta = localBookMeta
                    )
                    1 -> ContinueReadingSection(
                        readingProgress = continueReading,
                        onBookClick = onBookClick,
                        isArabic = isArabic
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WelcomeSection(isArabic: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = if (isArabic) "مرحباً بك في قارئك الذكي" else "Welcome to your smart reader",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isArabic) "اكتشف عالماً من المعرفة مع توصيات الذكاء الاصطناعي" else "Discover a world of knowledge with AI recommendations",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun LatestAdditionsSection(
    books: List<BookSummary>,
    deviceId: String,
    onBookClick: (String) -> Unit,
    onRefresh: () -> Unit,
    isArabic: Boolean,
    localBookMeta: Map<Int, LocalBookMeta>
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "توصيات مخصصة لك" else "Personalized for you",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (isArabic) "تحديث" else "Refresh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (books.isEmpty()) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                blurRadius = 6.dp,
                borderWidth = 0.5.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isArabic) "لا توجد كتب مضافة بعد" else "No books added yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                books.chunked(2).forEach { rowBooks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowBooks.forEach { book ->
                            val localMeta = localBookMeta[book.id]
                            val displayTitle = localMeta?.title?.trim().orEmpty().ifBlank { book.title }
                            val displayAuthor = localMeta?.author?.trim().orEmpty().ifBlank { book.author }
                            val coverModel: Any? = when {
                                !localMeta?.coverUri.isNullOrBlank() -> Uri.parse(localMeta?.coverUri.orEmpty())
                                book.cover_image != null -> "${BuildConfig.BASE_URL.removeSuffix("/")}/books/${book.id}/cover?android_id=$deviceId"
                                else -> null
                            }
                            LatestAdditionCard(
                                modifier = Modifier.weight(1f),
                                title = displayTitle,
                                author = displayAuthor,
                                coverModel = coverModel,
                                isArabic = isArabic,
                                onClick = { onBookClick(book.id.toString()) }
                            )
                        }
                        if (rowBooks.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestAdditionCard(
    modifier: Modifier = Modifier,
    title: String,
    author: String,
    coverModel: Any?,
    isArabic: Boolean,
    onClick: () -> Unit
) {
    GlassBookCard(
        modifier = modifier
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .graphicsLayer {
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(12.dp)
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = "Cover $title",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            AIPurpleGlow.copy(alpha = 0.9f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "جديد" else "New",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (isArabic) "تقييم" else "Rating",
                        tint = AIGoldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "4.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = if (isArabic) "آخر إضافة" else "Latest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun LuxuryTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isArabic: Boolean
) {
    val tabs = if (isArabic) listOf("آخر الإضافات", "استكمال القراءة") else listOf("Latest additions", "Continue reading")
    
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
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedTab
                
                AnimatedContent(
                    targetState = isSelected,
                    label = "tab_selection"
                ) { selected ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AIRecommendationsSection(
    recommendations: List<RecommendationItem.AIBookRecommendation>,
    onBookClick: (String) -> Unit,
    isArabic: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "توصيات مخصصة لك" else "Personalized recommendations",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            IconButton(onClick = { /* Refresh recommendations */ }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (isArabic) "تحديث التوصيات" else "Refresh recommendations",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = recommendations,
                key = { it.id }
            ) { recommendation ->
                AnimatedContent(
                    targetState = recommendation,
                    label = "ai_recommendation",
                    transitionSpec = {
                        scaleIn(animationSpec = tween(400)) + fadeIn() togetherWith
                    scaleOut(animationSpec = tween(400)) + fadeOut()
                    }
                ) { item ->
                    AIRecommendationCard(
                        recommendation = item,
                        onClick = { onBookClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AIRecommendationCard(
    recommendation: RecommendationItem.AIBookRecommendation,
    onClick: () -> Unit
) {
    GlassBookCard(
        modifier = Modifier
            .width(280.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Book Cover with 3D effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .graphicsLayer {
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(12.dp)
                        clip = true
                    }
            ) {
                AsyncImage(
                    model = recommendation.coverUrl,
                    contentDescription = "Cover ${recommendation.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // AI Score badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            AIPurpleGlow.copy(alpha = 0.9f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Recommendation",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${recommendation.aiScore.toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Book Title
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Author
            Text(
                text = recommendation.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // AI Reason
            Text(
                text = recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rating and Genre
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "تقييم",
                        tint = AIGoldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${recommendation.rating}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                
                Text(
                    text = recommendation.genre,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ContinueReadingSection(
    readingProgress: List<RecommendationItem.ReadingProgress>,
    onBookClick: (String) -> Unit,
    isArabic: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "استكمل قراءتك" else "Continue reading",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        if (readingProgress.isEmpty()) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                blurRadius = 6.dp,
                borderWidth = 0.5.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isArabic) "لا يوجد تقدم قراءة حالياً" else "No reading progress yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                readingProgress.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowItems.forEach { item ->
                            ContinueReadingCard(
                                modifier = Modifier.weight(1f),
                                progress = item,
                                onClick = { onBookClick(item.bookId) },
                                isArabic = isArabic
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    modifier: Modifier = Modifier,
    progress: RecommendationItem.ReadingProgress,
    onClick: () -> Unit,
    isArabic: Boolean
) {
    GlassBookCard(
        modifier = modifier
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .graphicsLayer {
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(12.dp)
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (progress.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = progress.coverUrl,
                        contentDescription = "Cover ${progress.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            AIPurpleGlow.copy(alpha = 0.9f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(progress.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = progress.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = progress.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (isArabic) "تقييم" else "Rating",
                        tint = AIGoldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "4.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = if (isArabic) "استكمال القراءة" else "Continue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadingProgressCard(
    progress: RecommendationItem.ReadingProgress,
    onClick: () -> Unit,
    isArabic: Boolean
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        blurRadius = 6.dp,
        borderWidth = 0.5.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book Cover
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        shadowElevation = 6.dp.toPx()
                        shape = RoundedCornerShape(8.dp)
                        clip = true
                    }
            ) {
                AsyncImage(
                    model = progress.coverUrl,
                    contentDescription = "Cover ${progress.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title
                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Author
                Text(
                    text = progress.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Progress Bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isArabic) "${(progress.progress * 100).toInt()}% مكتمل" else "${(progress.progress * 100).toInt()}% completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isArabic) "${progress.pagesRead}/${progress.totalPages} صفحة" else "${progress.pagesRead}/${progress.totalPages} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Time Left
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = if (isArabic) "الوقت المتبقي" else "Time left",
                        tint = AICyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isArabic) "متبقي ${progress.estimatedTimeLeft}" else "Time left ${progress.estimatedTimeLeft}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = if (isArabic) "استئناف القراءة" else "Resume reading",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ShimmerAIRecommendations() {
    Column {
        // Shimmer for section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerEffect(width = 150.dp, height = 24.dp)
            ShimmerEffect(width = 24.dp, height = 24.dp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Shimmer for AI recommendations list
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(3) {
                ShimmerAIRecommendation()
            }
        }
    }
}
