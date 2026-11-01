package com.example.readerarbicbook.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.readerarbicbook.BuildConfig
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.viewmodel.BookUiState
import com.example.readerarbicbook.viewmodel.AudioGenerationUiState
import com.example.readerarbicbook.viewmodel.BookViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.data.LocalBookMeta
import com.example.readerarbicbook.data.BookRepository
import com.example.readerarbicbook.network.BookResponse

@Composable
fun BookDetailsScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReader: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    val context = LocalContext.current
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val localBookMeta by DevicePreferences.getLocalBookMeta(context).collectAsState(initial = emptyMap())
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bookId, deviceId) {
        if (deviceId != "unknown" && bookId != "0" && bookId.isNotEmpty()) {
            bookViewModel.getBook(bookId.toIntOrNull() ?: 0, deviceId)
        }
    }

    val bookState by bookViewModel.bookState.collectAsState()
    val audioGenerationState by bookViewModel.audioGenerationState.collectAsState()
    
    // Audio Player State
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0f) }
    var savedAudioPositionMs by remember { mutableIntStateOf(0) }
    
    DisposableEffect(bookId, deviceId) {
        onDispose {
            val id = bookId.toIntOrNull() ?: 0
            if (id > 0 && deviceId != "unknown") {
                val posMs = (mediaPlayer?.currentPosition ?: 0).coerceAtLeast(0)
                if (posMs > 0) {
                    scope.launch {
                        try {
                            BookRepository.updateAudioPosition(id = id, positionMs = posMs, deviceId = deviceId)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            mediaPlayer?.release()
        }
    }
    
    // Update Slider
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            currentPosition = mediaPlayer?.currentPosition?.toFloat() ?: 0f
            delay(500)
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (isArabic) "تأكيد الحذف" else "Confirm Delete") },
            text = { Text(if (isArabic) "هل أنت متأكد من أنك تريد حذف هذا الكتاب؟ لا يمكن التراجع عن هذا الإجراء." else "Are you sure you want to delete this book? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        bookViewModel.deleteBook(bookId.toIntOrNull() ?: 0, deviceId) {
                            Toast.makeText(context, if (isArabic) "تم حذف الكتاب" else "Book deleted", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isArabic) "حذف" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryGradientBackground()
    ) {
        when (val state = bookState) {
            is BookUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is BookUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is BookUiState.Success -> {
                val book = state.book
                val localMeta: LocalBookMeta? = localBookMeta[book.id]
                val displayTitle = localMeta?.title?.trim().orEmpty().ifBlank { book.title }
                val displayAuthor = localMeta?.author?.trim().orEmpty().ifBlank { book.author }
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
                val isGeneratingThisBook = remember(audioGenerationState, book.id, bookId) {
                    val id = bookId.toIntOrNull() ?: -1
                    audioGenerationState is AudioGenerationUiState.Generating &&
                        (audioGenerationState as AudioGenerationUiState.Generating).bookId == id
                }

                LaunchedEffect(book.id, deviceId) {
                    if (deviceId == "unknown") return@LaunchedEffect
                    try {
                        val resp = BookRepository.getAudioPosition(id = book.id, deviceId = deviceId)
                        if (resp.isSuccessful) {
                            savedAudioPositionMs = (resp.body()?.position_ms ?: 0).coerceAtLeast(0)
                        }
                    } catch (_: Exception) {
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = if (isArabic) "رجوع" else "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = if (isArabic) "خيارات" else "Options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isArabic) "تعديل الكتاب" else "Edit Book") },
                                    onClick = { 
                                        showMenu = false
                                        showEditDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isArabic) "حذف الكتاب" else "Delete Book") },
                                    onClick = { 
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                )
                            }
                        }
                    }
                    
                    if (showEditDialog) {
                        EditBookDialog(
                            book = book.copy(title = displayTitle, author = displayAuthor),
                            onDismiss = { showEditDialog = false },
                            onConfirm = { title, author, description ->
                                bookViewModel.updateBook(book.id, title, author, description, deviceId) {
                                    Toast.makeText(context, if (isArabic) "تم تحديث الكتاب" else "Book updated", Toast.LENGTH_SHORT).show()
                                    val preservedCover = localMeta?.coverUri
                                    scope.launch {
                                        DevicePreferences.upsertLocalBookMeta(
                                            context = context,
                                            bookId = book.id,
                                            title = title,
                                            author = author,
                                            coverUri = preservedCover
                                        )
                                    }
                                    showEditDialog = false
                                    // Refresh book details
                                    bookViewModel.getBook(book.id, deviceId)
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Start Reading Button
                    GlassButton(
                        onClick = onNavigateToReader,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "بدء القراءة (الوضع الغامر)" else "Start Reading (Immersive)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Book Cover + Meta
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (coverModel != null) {
                                AsyncImage(
                                    model = coverModel,
                                    contentDescription = if (isArabic) "غلاف الكتاب" else "Book cover",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (displayAuthor.isNotBlank()) {
                                Text(
                                    text = displayAuthor,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            val description = book.description?.trim().orEmpty()
                            if (description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Enhanced Audio Interface
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val hasPlayableAudio = book.audio_path != null || mediaPlayer != null
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isArabic) "التسجيلات الصوتية" else "Audio",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        enqueueAudioDownload(
                                            context = context,
                                            bookId = book.id,
                                            deviceId = deviceId,
                                            title = displayTitle,
                                            isArabic = isArabic
                                        )
                                    },
                                    enabled = hasPlayableAudio
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = if (isArabic) "تنزيل" else "Download",
                                        tint = if (hasPlayableAudio) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            if (book.audio_path == null) {
                                Text(
                                    text = if (isGeneratingThisBook) {
                                        if (isArabic) "جاري إنشاء الصوت في الخلفية..." else "Generating audio in background..."
                                    } else {
                                        if (isArabic) "اضغط زر التشغيل لتوليد الصوت" else "Tap play to generate audio"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Progress Bar
                            if (duration > 0) {
                                Slider(
                                    value = currentPosition,
                                    onValueChange = { 
                                        currentPosition = it
                                        mediaPlayer?.seekTo(it.toInt())
                                    },
                                    valueRange = 0f..duration,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(currentPosition.toLong()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = formatTime(duration.toLong()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rewind 10s
                                IconButton(onClick = { 
                                    val newPos = (currentPosition - 10000).coerceAtLeast(0f)
                                    mediaPlayer?.seekTo(newPos.toInt())
                                    currentPosition = newPos
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = if (isArabic) "رجوع 10 ثواني" else "Rewind 10s",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Play/Pause Button (Center, Big)
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary
                                                )
                                            )
                                        )
                                        .clickable {
                                            if (isPlaying) {
                                                mediaPlayer?.pause()
                                                isPlaying = false
                                                bookViewModel.updateAudioPosition(
                                                    id = book.id,
                                                    positionMs = (mediaPlayer?.currentPosition ?: 0).coerceAtLeast(0),
                                                    deviceId = deviceId
                                                )
                                            } else {
                                                if (mediaPlayer == null) {
                                                    try {
                                                        val url = "${BuildConfig.BASE_URL.removeSuffix("/")}/books/${book.id}/audio?android_id=$deviceId"
                                                        
                                                        mediaPlayer = MediaPlayer().apply {
                                                            setDataSource(url)
                                                            setOnErrorListener { _, _, _ ->
                                                                mediaPlayer?.release()
                                                                mediaPlayer = null
                                                                isPlaying = false
                                                                bookViewModel.startAudioGeneration(book.id, deviceId)
                                                                true
                                                            }
                                                            prepareAsync()
                                                            setOnPreparedListener { mp ->
                                                                duration = mp.duration.toFloat()
                                                                val resumeMs = savedAudioPositionMs.coerceIn(0, mp.duration)
                                                                if (resumeMs > 0) {
                                                                    mp.seekTo(resumeMs)
                                                                    currentPosition = resumeMs.toFloat()
                                                                }
                                                                start()
                                                                isPlaying = true
                                                            }
                                                            setOnCompletionListener { 
                                                                isPlaying = false 
                                                                currentPosition = 0f
                                                                savedAudioPositionMs = 0
                                                                bookViewModel.updateAudioPosition(
                                                                    id = book.id,
                                                                    positionMs = 0,
                                                                    deviceId = deviceId
                                                                )
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        bookViewModel.startAudioGeneration(book.id, deviceId)
                                                        Toast.makeText(context, if (isArabic) "جاري إنشاء الصوت..." else "Generating audio...", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    mediaPlayer?.start()
                                                    isPlaying = true
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (book.audio_path == null && mediaPlayer == null && !isPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = if (isArabic) "توليد صوت" else "Generate audio",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isArabic) "تشغيل" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                
                                // Forward 10s
                                IconButton(onClick = { 
                                    val newPos = (currentPosition + 10000).coerceAtMost(duration)
                                    mediaPlayer?.seekTo(newPos.toInt())
                                    currentPosition = newPos
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = if (isArabic) "تقديم 10 ثواني" else "Forward 10s",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun enqueueAudioDownload(
    context: Context,
    bookId: Int,
    deviceId: String,
    title: String,
    isArabic: Boolean
) {
    val baseUrl = BuildConfig.BASE_URL.removeSuffix("/")
    val url = "$baseUrl/books/$bookId/audio?android_id=$deviceId"
    val safeTitle = sanitizeFileName(title).ifBlank { "book_$bookId" }.take(48)
    val fileName = "audio_${bookId}_$safeTitle.mp3"

    val request = DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setTitle(if (isArabic) "تنزيل الصوت" else "Download audio")
        .setDescription(title)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
    Toast.makeText(context, if (isArabic) "بدأ تنزيل الصوت" else "Audio download started", Toast.LENGTH_SHORT).show()
}

private fun sanitizeFileName(input: String): String {
    return input
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
}

@Composable
fun EditBookDialog(
    book: BookResponse,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var description by remember { mutableStateOf(book.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الكتاب") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الكتاب") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("اسم المؤلف") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف الكتاب") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, author, description) }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
