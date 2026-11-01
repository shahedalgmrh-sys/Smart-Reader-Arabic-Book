package com.example.readerarbicbook.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import coil.compose.AsyncImage
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.viewmodel.BooksListUiState
import com.example.readerarbicbook.viewmodel.BookViewModel
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.data.LocalBookMeta
import com.example.readerarbicbook.network.BookSummary
import com.example.readerarbicbook.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (BookSummary) -> Unit,
    onSearchClick: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val booksState by bookViewModel.booksListState.collectAsState()
    val localBookMeta by DevicePreferences.getLocalBookMeta(context).collectAsState(initial = emptyMap())

    LaunchedEffect(deviceId) {
        if (deviceId != "unknown") {
            bookViewModel.getBooks(deviceId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryGradientBackground()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Luxury Header with Glass Search Bar
            GlassSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isArabic) "بحث" else "Search",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            if (deviceId != "unknown") {
                                bookViewModel.getBooks(deviceId, it.ifEmpty { null })
                            }
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    if (isArabic) "ابحث عن كتبك المفضلة..." else "Search your favorite books...",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                    
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = "" 
                            if (deviceId != "unknown") {
                                bookViewModel.getBooks(deviceId)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isArabic) "مسح" else "Clear",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (val state = booksState) {
                    is BooksListUiState.Loading -> {
                        ShimmerBookGrid()
                    }
                    is BooksListUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is BooksListUiState.Success -> {
                        if (state.books.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlassCard(
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .wrapContentSize(),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LibraryBooks,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "لا توجد كتب مضافة",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "أضف كتاباً جديداً من القائمة",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        } else {
                            BooksGrid(
                                books = state.books,
                                onBookClick = onBookClick,
                                deviceId = deviceId,
                                localBookMeta = localBookMeta,
                                isArabic = isArabic
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun BooksGrid(
    books: List<BookSummary>,
    onBookClick: (BookSummary) -> Unit,
    deviceId: String,
    localBookMeta: Map<Int, LocalBookMeta>,
    isArabic: Boolean
) {
    val gridState = rememberLazyGridState()
    
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = books,
            key = { it.id }
        ) { book ->
            BookCard(
                book = book,
                localMeta = localBookMeta[book.id],
                deviceId = deviceId,
                isArabic = isArabic,
                onClick = { onBookClick(book) }
            )
        }
    }
}

@Composable
private fun BookCard(
    book: BookSummary,
    localMeta: LocalBookMeta?,
    deviceId: String,
    isArabic: Boolean,
    onClick: () -> Unit
) {
    GlassBookCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable { onClick() }
    ) {
        val displayTitle = localMeta?.title?.trim().orEmpty().ifBlank { book.title }
        val displayAuthor = localMeta?.author?.trim().orEmpty().ifBlank { book.author }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Book Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val coverModel: Any? = remember(book.id, book.cover_image, deviceId, localMeta?.coverUri) {
                    if (localMeta?.coverUri != null) {
                        Uri.parse(localMeta.coverUri)
                    } else if (localMeta != null) {
                        null
                    } else if (book.cover_image != null) {
                        "${BuildConfig.BASE_URL.removeSuffix("/")}/books/${book.id}/cover?android_id=$deviceId"
                    } else {
                        null
                    }
                }

                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = "غلاف الكتاب $displayTitle",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Book Title
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Author
            if (displayAuthor.isNotBlank()) {
                Text(
                    text = displayAuthor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ShimmerBookGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) {
            ShimmerBookCard()
        }
    }
}
