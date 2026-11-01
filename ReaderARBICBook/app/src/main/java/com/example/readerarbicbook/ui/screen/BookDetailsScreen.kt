package com.example.readerarbicbook.ui.screen

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.readerarbicbook.BuildConfig
import androidx.compose.ui.res.painterResource
import com.example.readerarbicbook.R
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.readerarbicbook.network.BookResponse
import com.example.readerarbicbook.data.BookRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    bookId: Int,
    onBack: () -> Unit,
    language: String,
    deviceId: String
) {
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<BookDetailsUiState>(BookDetailsUiState.Loading) }

    // Audio Player State
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isAudioGenerating by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var audioDuration by remember { mutableIntStateOf(0) }

    // Helper to format milliseconds to MM:SS
    fun formatTime(millis: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Update progress bar
    LaunchedEffect(mediaPlayer, isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            delay(500) // Update every 500ms
        }
    }

    LaunchedEffect(bookId) {
        scope.launch {
            uiState = BookDetailsUiState.Loading
            try {
                val response = BookRepository.getBook(bookId, deviceId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        uiState = BookDetailsUiState.Success(body)
                    } else {
                        uiState = BookDetailsUiState.Error("Empty body")
                    }
                } else {
                    uiState = BookDetailsUiState.Error("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                uiState = BookDetailsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (language == "Arabic") "تفاصيل الكتاب" else "Book Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is BookDetailsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is BookDetailsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message ?: "Error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is BookDetailsUiState.Success -> {
                    val b = state.book
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Modern Cover Design
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            run {
                                val coversDir = File(LocalContext.current.filesDir, "covers")
                                val jpg = File(coversDir, "book_${bookId}.jpg")
                                val png = File(coversDir, "book_${bookId}.png")
                                val local = when {
                                    jpg.exists() -> jpg
                                    png.exists() -> png
                                    else -> null
                                }
                                val model = local ?: if (b.cover_image != null) BuildConfig.BASE_URL + "books/$bookId/cover?android_id=$deviceId" else null
                                
                                AsyncImage(
                                    model = model,
                                    contentDescription = "Book Cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                                    error = painterResource(R.drawable.ic_launcher_foreground)
                                )
                            }
                        }

                        // Title and Author
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = b.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = b.author,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Modern Audio Player
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (b.audio_path == null) {
                                    Text(
                                        text = if (language == "Arabic") "الصوت غير متوفر" else "Audio not available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            isAudioGenerating = true
                                            scope.launch {
                                                try {
                                                    val res = BookRepository.generateAudio(bookId, deviceId)
                                                    if (res.isSuccessful) {
                                                        val audioPath = res.body()?.audio_path
                                                        uiState = if (audioPath != null) {
                                                            BookDetailsUiState.Success(b.copy(audio_path = audioPath))
                                                        } else {
                                                            BookDetailsUiState.Success(b)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                } finally {
                                                    isAudioGenerating = false
                                                }
                                            }
                                        },
                                        enabled = !isAudioGenerating,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isAudioGenerating) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(if (language == "Arabic") "توليد الصوت الآن" else "Generate Audio Now")
                                    }
                                } else {
                                    // Progress Bar
                                    Slider(
                                        value = currentPosition.toFloat(),
                                        onValueChange = { 
                                            currentPosition = it.toInt()
                                            mediaPlayer?.seekTo(currentPosition)
                                        },
                                        valueRange = 0f..audioDuration.toFloat().coerceAtLeast(1f),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                        )
                                    )
                                    
                                    // Time Labels
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = formatTime(currentPosition), style = MaterialTheme.typography.bodySmall)
                                        Text(text = formatTime(audioDuration), style = MaterialTheme.typography.bodySmall)
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Controls
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Replay 10s
                                        IconButton(onClick = { 
                                            mediaPlayer?.let { mp ->
                                                val newPos = (mp.currentPosition - 10000).coerceAtLeast(0)
                                                mp.seekTo(newPos)
                                                currentPosition = newPos
                                            }
                                        }) {
                                            Icon(Icons.Filled.Refresh, contentDescription = "Rewind 10s", modifier = Modifier.size(28.dp))
                                        }

                                        // Play/Pause
                                        FilledIconButton(
                                            onClick = {
                                                if (isPlaying) {
                                                    mediaPlayer?.pause()
                                                    isPlaying = false
                                                } else {
                                                    try {
                                                        if (mediaPlayer == null) {
                                                            mediaPlayer = MediaPlayer().apply {
                                                                setDataSource(BuildConfig.BASE_URL + "books/$bookId/audio?android_id=$deviceId")
                                                                prepareAsync()
                                                                setOnPreparedListener { mp ->
                                                                    audioDuration = mp.duration
                                                                    start()
                                                                    isPlaying = true
                                                                }
                                                                setOnCompletionListener {
                                                                    isPlaying = false
                                                                    currentPosition = 0
                                                                }
                                                                setOnErrorListener { _, _, _ ->
                                                                    isPlaying = false
                                                                    false
                                                                }
                                                            }
                                                        } else {
                                                            mediaPlayer?.start()
                                                            isPlaying = true
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(64.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        // Forward 10s
                                        IconButton(onClick = { 
                                            mediaPlayer?.let { mp ->
                                                val newPos = (mp.currentPosition + 10000).coerceAtMost(mp.duration)
                                                mp.seekTo(newPos)
                                                currentPosition = newPos
                                            }
                                        }) {
                                            Icon(Icons.Filled.FastForward, contentDescription = "Forward 10s", modifier = Modifier.size(28.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (!b.description.isNullOrBlank()) {
                            Text(
                                text = b.description,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(16.dp)
                            )
                        }
                        
                        // Text Content
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        var contentScale by remember { mutableStateOf(1f) }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(8.dp)
                        ) {
                            Text(
                                text = if (language == "Arabic") "حجم الخط" else "Text size",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            Slider(
                                value = contentScale,
                                onValueChange = { contentScale = it },
                                valueRange = 0.8f..1.6f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Text(
                            text = b.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * contentScale,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * contentScale
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

sealed class BookDetailsUiState {
    data object Loading : BookDetailsUiState()
    data class Success(val book: BookResponse) : BookDetailsUiState()
    data class Error(val message: String?) : BookDetailsUiState()
}
