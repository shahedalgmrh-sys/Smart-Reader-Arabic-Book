package com.example.readerarbicbook.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.viewmodel.BookViewModel
import com.example.readerarbicbook.viewmodel.AudioReuseHint
import com.example.readerarbicbook.viewmodel.UploadErrorKind
import com.example.readerarbicbook.viewmodel.UploadUiState
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.utils.FileUtils
import com.example.readerarbicbook.utils.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AddBookBlockingIssue {
    INVALID_FILE,
    OFFLINE
}

@Composable
fun AddBookScreen(
    onNavigateBack: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }
    var bookDescription by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var blockingIssue by remember { mutableStateOf<AddBookBlockingIssue?>(null) }
    var selectedFileChecksum by remember { mutableStateOf<String?>(null) }
    var isComputingChecksum by remember { mutableStateOf(false) }
    var lastSubmittedChecksum by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val uploadState by bookViewModel.uploadState.collectAsState()
    val audioReuseHint by bookViewModel.audioReuseHint.collectAsState()
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val networkAvailable by produceState(initialValue = NetworkUtils.isInternetAvailable(context), key1 = context) {
        NetworkUtils.observeNetworkStatus(context).collectLatest { isConnected ->
            value = isConnected
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        if (FileUtils.isLikelyValidPdf(context, uri)) {
            selectedFileUri = uri
            selectedFileChecksum = null
            bookViewModel.resetAudioReuseHint()
            isComputingChecksum = true
            scope.launch {
                val checksum = FileUtils.sha256HexOfUri(context, uri)
                selectedFileChecksum = checksum
                isComputingChecksum = false
                if (!checksum.isNullOrBlank() && networkAvailable) {
                    bookViewModel.checkAudioByChecksum(checksum, deviceId)
                }
            }
            if (blockingIssue == AddBookBlockingIssue.INVALID_FILE) {
                blockingIssue = null
            }
        } else {
            selectedFileUri = null
            selectedFileChecksum = null
            blockingIssue = AddBookBlockingIssue.INVALID_FILE
            bookViewModel.resetUploadState()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    LaunchedEffect(uploadState) {
        if (uploadState is UploadUiState.Success) {
            val book = (uploadState as UploadUiState.Success).book
            val toastMessage = when (audioReuseHint) {
                AudioReuseHint.FOUND -> if (isArabic) "تمت إضافة الكتاب بنجاح (تم العثور على صوت جاهز)" else "Book added successfully (audio found)"
                AudioReuseHint.NOT_FOUND -> if (isArabic) "تمت إضافة الكتاب بنجاح" else "Book added successfully"
                AudioReuseHint.UNKNOWN -> if (isArabic) "تمت إضافة الكتاب بنجاح" else "Book added successfully"
            }
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            DevicePreferences.setHasOnboarded(context, true)
            val checksum = lastSubmittedChecksum
            if (!checksum.isNullOrBlank()) {
                val key = makeLocalBookKey(deviceId, checksum)
                DevicePreferences.addAddedBookKey(context, key)
            }
            DevicePreferences.upsertLocalBookMeta(
                context = context,
                bookId = book.id,
                title = bookTitle,
                author = bookAuthor,
                coverUri = selectedImageUri?.toString()
            )
            lastSubmittedChecksum = null
            bookViewModel.resetUploadState()
            bookViewModel.resetAudioReuseHint()
            onNavigateBack()
        } else if (uploadState is UploadUiState.Duplicate) {
            val msg = (uploadState as UploadUiState.Duplicate).message
            scope.launch {
                snackbarHostState.showSnackbar(message = msg, withDismissAction = false, duration = SnackbarDuration.Short)
            }
            DevicePreferences.setHasOnboarded(context, true)
            bookViewModel.resetUploadState()
        } else if (uploadState is UploadUiState.Error) {
            val currentError = uploadState as UploadUiState.Error
            when (currentError.kind) {
                UploadErrorKind.INVALID_FILE -> blockingIssue = AddBookBlockingIssue.INVALID_FILE
                UploadErrorKind.OFFLINE -> blockingIssue = AddBookBlockingIssue.OFFLINE
                UploadErrorKind.GENERIC -> {
                    Toast.makeText(context, currentError.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(networkAvailable) {
        if (!networkAvailable) {
            blockingIssue = AddBookBlockingIssue.OFFLINE
        } else if (blockingIssue == AddBookBlockingIssue.OFFLINE) {
            blockingIssue = null
            bookViewModel.resetUploadState()
        }
    }

    val canSend = selectedFileUri != null &&
        bookTitle.isNotBlank() &&
        bookAuthor.isNotBlank() &&
        uploadState !is UploadUiState.Loading &&
        blockingIssue == null &&
        networkAvailable &&
        !isComputingChecksum

    Scaffold(
        snackbarHost = { DuplicateBookSnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .luxuryGradientBackground()
                .padding(padding)
        ) {
            if (uploadState is UploadUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center, // Centered
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "إضافة كتاب جديد" else "Add New Book",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            if (blockingIssue != null) {
                Spacer(modifier = Modifier.height(16.dp))
                UploadBlockingErrorCard(
                    issue = blockingIssue!!,
                    isArabic = isArabic,
                    onRetry = {
                        when (blockingIssue) {
                            AddBookBlockingIssue.INVALID_FILE -> filePickerLauncher.launch("application/pdf")
                            AddBookBlockingIssue.OFFLINE -> {
                                if (NetworkUtils.isInternetAvailable(context)) {
                                    blockingIssue = null
                                    bookViewModel.resetUploadState()
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (isArabic) "ما زال الاتصال غير متاح" else "Still offline",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            null -> {}
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Form
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Choose File Button
                    GlassButton(
                        onClick = { filePickerLauncher.launch("application/pdf") }, // Allow PDF selection
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedFileUri != null) {
                            if (isArabic) "تم اختيار الملف" else "File selected"
                        } else {
                            if (isArabic) "اختر ملف الكتاب (PDF)" else "Choose book file (PDF)"
                        })
                    }
                    
                    if (selectedFileUri != null) {
                        Text(
                            text = if (isArabic) "تم اختيار الملف بنجاح" else "File selected successfully",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Choose Cover Image Button
                    GlassButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedImageUri != null) {
                            if (isArabic) "تم اختيار الغلاف" else "Cover selected"
                        } else {
                            if (isArabic) "اختر غلاف الكتاب (صورة)" else "Choose cover image"
                        })
                    }

                    if (selectedImageUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = if (isArabic) "معاينة الغلاف" else "Cover preview",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Title Input
                    Text(
                        text = if (isArabic) "عنوان الكتاب" else "Book Title",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    GlassTextField(
                        value = bookTitle,
                        onValueChange = { bookTitle = it },
                        placeholder = if (isArabic) "أدخل عنوان الكتاب" else "Enter book title",
                        singleLine = false
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Author Input
                    Text(
                        text = if (isArabic) "اسم المؤلف" else "Author Name",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    GlassTextField(
                        value = bookAuthor,
                        onValueChange = { bookAuthor = it },
                        placeholder = if (isArabic) "أدخل اسم المؤلف" else "Enter author name",
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description Input
                    Text(
                        text = if (isArabic) "وصف الكتاب" else "Book Description",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    GlassTextField(
                        value = bookDescription,
                        onValueChange = { bookDescription = it },
                        placeholder = if (isArabic) "أدخل وصف الكتاب (اختياري)" else "Enter description (optional)",
                        singleLine = false,
                        modifier = Modifier.height(100.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Send Button
                    GlassButton(
                        onClick = {
                            if (canSend) {
                                scope.launch {
                                    val fileUri = selectedFileUri ?: return@launch
                                    val checksum = selectedFileChecksum ?: FileUtils.sha256HexOfUri(context, fileUri)
                                    selectedFileChecksum = checksum

                                    if (!checksum.isNullOrBlank()) {
                                        lastSubmittedChecksum = checksum
                                    } else {
                                        lastSubmittedChecksum = null
                                    }

                                    bookViewModel.addBook(
                                        context,
                                        bookTitle,
                                        bookAuthor,
                                        bookDescription.ifEmpty { if (isArabic) "لا يوجد وصف" else "No description" },
                                        fileUri,
                                        selectedImageUri,
                                        deviceId,
                                        fileChecksumSha256Hex = checksum
                                    )
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    if (!networkAvailable) {
                                        if (isArabic) "تحقق من اتصال الإنترنت ثم حاول مرة أخرى" else "Check internet connection then retry"
                                    } else {
                                        if (isArabic) "يرجى ملء جميع الحقول واختيار ملف" else "Please fill all fields and choose a file"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .align(Alignment.End)
                            .width(120.dp) // Fixed width for better look
                    ) {
                        Text(
                            text = if (isArabic) "إرسال" else "Send",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Send, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
    }
        }
    }
}

private fun makeLocalBookKey(deviceId: String, checksumSha256Hex: String): String {
    return "${deviceId.trim()}|${checksumSha256Hex.trim().lowercase()}"
}

@Composable
fun UploadBlockingErrorCard(
    issue: AddBookBlockingIssue,
    isArabic: Boolean,
    onRetry: () -> Unit
) {
    val title = when (issue) {
        AddBookBlockingIssue.INVALID_FILE -> if (isArabic) "تعذر معالجة الملف" else "Invalid or corrupted file"
        AddBookBlockingIssue.OFFLINE -> if (isArabic) "انقطاع الاتصال بالإنترنت" else "Internet connection lost"
    }
    val message = when (issue) {
        AddBookBlockingIssue.INVALID_FILE ->
            if (isArabic) "هذا الملف غير صالح أو تالف. اختر ملفًا بديلًا أو أعد تحميله."
            else "This file cannot be processed. Please choose another file or re-upload it."
        AddBookBlockingIssue.OFFLINE ->
            if (isArabic) "لا يمكن تنفيذ الرفع حاليًا. تحقق من اتصال الشبكة وحاول لاحقًا."
            else "Network actions are temporarily disabled. Check your internet and try again."
    }
    val retryText = if (isArabic) "إعادة المحاولة" else "Retry"
    val icon = when (issue) {
        AddBookBlockingIssue.INVALID_FILE -> Icons.Default.ErrorOutline
        AddBookBlockingIssue.OFFLINE -> Icons.Default.WifiOff
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(retryText)
            }
        }
    }
}
