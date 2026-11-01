package com.example.readerarbicbook.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.readerarbicbook.data.BookRepository
import com.example.readerarbicbook.network.BookSummary
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Refresh
import android.util.Log
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.readerarbicbook.BuildConfig
import androidx.compose.ui.text.font.FontWeight
import okhttp3.ResponseBody
import android.os.Environment
import java.io.File
import java.io.OutputStream
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.painterResource
import com.example.readerarbicbook.R
import com.example.readerarbicbook.data.DevicePreferences
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)

@Composable
@Preview(showBackground = true)

fun LibraryScreen(
    refreshKey: Int = 0,
    initialNewItem: BookSummary? = null,
    onBookClick: (BookSummary) -> Unit = {},
    deviceId: String = "device"
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<BookSummary>>(emptyList()) }
    var offset by remember { mutableStateOf(0) }
    val limit = 10
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hiddenIdsFlow = remember { DevicePreferences.getHiddenIds(context) }
    var hiddenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        hiddenIdsFlow.collect { set -> hiddenIds = set }
    }
    var mounted by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { mounted = false }
    }
    var lastError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableStateOf(0) }
    fun loadPage(reset: Boolean = false) {
        if (loading || endReached) return
        loading = true
        lastError = null
        Log.d("LibraryScreen", "loadPage start q=$query offset=$offset limit=$limit reset=$reset")
        scope.launch {
            try {
                val resp = BookRepository.getBooks(limit = limit, offset = offset, query = query.ifBlank { null }, deviceId = deviceId)
                if (resp.isSuccessful) {
                    val page = resp.body().orEmpty()
                    Log.d("LibraryScreen", "response ok size=${page.size}")
                    if (reset) {
                        var newList = page
                        initialNewItem?.let { newItem ->
                            if (newList.none { it.id == newItem.id }) {
                                newList = listOf(newItem) + newList
                            }
                        }
                        if (mounted) items = newList
                    } else {
                        if (mounted) items = items + page
                    }
                    if (page.size < limit) {
                        endReached = true
                        Log.d("LibraryScreen", "endReached true")
                    } else {
                        offset += page.size
                        Log.d("LibraryScreen", "offset advanced to $offset")
                    }
                } else {
                    lastError = "HTTP ${resp.code()} ${resp.message()}"
                    android.widget.Toast.makeText(context, "خطأ الشبكة: ${resp.message()}", android.widget.Toast.LENGTH_LONG).show()
                    endReached = true
                    Log.e("LibraryScreen", "response error code=${resp.code()} message=${resp.message()}")
                }
            } catch (e: Exception) {
                lastError = e.message
                android.widget.Toast.makeText(context, "فشل الاتصال: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                endReached = true
                Log.e("LibraryScreen", "exception ${e.message}")
                if (retryCount < 1) {
                    retryCount += 1
                    delay(1500)
                    loading = false
                    Log.d("LibraryScreen", "retry attempt $retryCount")
                    loadPage(reset = reset)
                    return@launch
                }
            } finally {
                loading = false
                Log.d("LibraryScreen", "loadPage end loading=false")
            }
        }
    }

    LaunchedEffect(refreshKey) {
        items = emptyList()
        offset = 0
        endReached = false
        loadPage(reset = true)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp, top = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "مكتبتك",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ابحث عن كتابك أو أضف كتاباً جديداً",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("بحث باسم الكتاب") }
                        )
                        IconButton(
                            onClick = {
                                items = emptyList()
                                offset = 0
                                endReached = false
                                loadPage(reset = true)
                            },
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                query = ""
                                items = emptyList()
                                offset = 0
                                endReached = false
                                loadPage(reset = true)
                            },
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                }
            }
        }
        item {
            if (loading && items.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        items(items.filterNot { hiddenIds.contains(it.id.toString()) }) { item ->
            BookItem(
                item,
                onClick = { onBookClick(item) },
                deviceId = deviceId,
                onDelete = {
                    scope.launch {
                        DevicePreferences.addHiddenId(context, item.id)
                        Toast.makeText(context, "تم إخفاء الكتاب محلياً", Toast.LENGTH_SHORT).show()
                    }
                },
                onDownloadAudio = {
                    scope.launch {
                        try {
                            val audioResp = BookRepository.getAudio(item.id, deviceId)
                            if (audioResp.isSuccessful) {
                                val body: ResponseBody? = audioResp.body()
                                if (body != null) {
                                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                                    val outFile = File(dir, "book_${item.id}.mp3")
                                    outFile.outputStream().use { os ->
                                        body.byteStream().copyTo(os)
                                    }
                                    Toast.makeText(context, "تم تنزيل الصوت: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "لا يوجد ملف صوت", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "فشل تنزيل الصوت: ${audioResp.message()}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
        item {
            if (!endReached) {
                    Button(
                        onClick = { loadPage() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (loading) "جارِ التحميل..." else "تحميل المزيد")
                    }
                } else if (items.isEmpty() && !loading) {
                    Text("لا توجد نتائج", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (lastError != null && !loading) {
            Button(
                onClick = {
                    retryCount = 0
                    endReached = false
                    loadPage(reset = items.isEmpty())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إعادة المحاولة")
            }
            Text("خطأ: ${lastError}", style = MaterialTheme.typography.bodySmall)
        }
    }

@Composable
fun BookItem(
    item: BookSummary,
    onClick: () -> Unit,
    deviceId: String,
    onDelete: () -> Unit,
    onDownloadAudio: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // محاولة عرض الغلاف المحلي أولاً
            val ctx = LocalContext.current
            val localCover = remember(item.id) {
                val coversDir = File(ctx.filesDir, "covers")
                val jpg = File(coversDir, "book_${item.id}.jpg")
                val png = File(coversDir, "book_${item.id}.png")
                when {
                    jpg.exists() -> jpg
                    png.exists() -> png
                    else -> null
                }
            }
            if (localCover != null) {
                AsyncImage(
                    model = localCover,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground)
                )
            } else if (item.cover_image != null) {
                AsyncImage(
                    model = BuildConfig.BASE_URL + "books/${item.id}/cover?android_id=$deviceId",
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
                if (!item.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onDownloadAudio, modifier = Modifier.weight(1f)) {
                Text("تنزيل الصوت")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("حذف")
            }
        }
    }
}
