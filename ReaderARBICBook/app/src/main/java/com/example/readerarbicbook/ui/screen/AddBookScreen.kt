package com.example.readerarbicbook.ui.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.readerarbicbook.data.BookRepository
import com.example.readerarbicbook.network.BookSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(onBookAdded: (BookSummary?) -> Unit = {}, language: String = "English", deviceId: String = "device") {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }
    var bookDescription by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var mounted by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { mounted = false }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedFileUri = uri
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedCoverUri = uri
    }

    Scaffold { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (language == "Arabic") "إضافة كتاب جديد" else "Add New Book",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == "Arabic") "املأ بيانات الكتاب ثم اختر ملفاً أو أدخل النص مباشرة" else "Fill book details then choose a file or enter text",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bookTitle,
                        onValueChange = { bookTitle = it },
                        label = { Text(if (language == "Arabic") "عنوان الكتاب" else "Book Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bookAuthor,
                        onValueChange = { bookAuthor = it },
                        label = { Text(if (language == "Arabic") "اسم المؤلف" else "Book Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bookDescription,
                        onValueChange = { bookDescription = it },
                        label = { Text(if (language == "Arabic") "وصف الكتاب" else "Book Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    Button(
                        onClick = { coverPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(
                            if (selectedCoverUri == null)
                                (if (language == "Arabic") "اختر صورة الغلاف (اختياري)" else "Choose Cover Image (Optional)")
                            else
                                (if (language == "Arabic") "تم اختيار الغلاف ✓" else "Cover Selected ✓")
                        )
                    }
                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/epub+zip",
                                    "text/plain"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedFileUri == null)
                                (if (language == "Arabic") "اختر ملف الكتاب (PDF/DOCX)" else "Choose Book File (PDF/DOCX)")
                            else
                                (if (language == "Arabic") "تم اختيار الملف ✓" else "File Selected ✓")
                        )
                    }
                    var useDirectText by remember { mutableStateOf(false) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { useDirectText = !useDirectText },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (language == "Arabic")
                                    (if (useDirectText) "إدخال ملف" else "نص مباشر")
                                else
                                    (if (useDirectText) "File Input" else "Direct Text")
                            )
                        }
                    }
                    if (useDirectText) {
                        OutlinedTextField(
                            value = bookDescription,
                            onValueChange = { bookDescription = it },
                            label = { Text(if (language == "Arabic") "نص الكتاب" else "Book Text") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 20
                        )
                    }
                    Button(
                onClick = {
                    if (selectedFileUri == null) {
                        if (!useDirectText || bookDescription.isBlank()) {
                            Toast.makeText(context, if (language == "Arabic") "يرجى اختيار ملف أو إدخال نص" else "Choose a file or enter text", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                    } else if (bookTitle.isBlank()) {
                        Toast.makeText(context, if (language == "Arabic") "عنوان الكتاب مطلوب" else "Book title is required", Toast.LENGTH_SHORT).show()
                    } else if (bookAuthor.isBlank()) {
                        Toast.makeText(context, if (language == "Arabic") "اسم المؤلف مطلوب" else "Book author is required", Toast.LENGTH_SHORT).show()
                    } else {
                        uploading = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val response = if (useDirectText && selectedFileUri == null) {
                                    val req = com.example.readerarbicbook.network.BookCreate(
                                        title = bookTitle,
                                        author = bookAuthor,
                                        content = bookDescription,
                                        description = if (bookDescription.isNotBlank()) bookDescription else null,
                                        android_id = deviceId
                                    )
                                    BookRepository.addBookJson(req)
                                } else {
                                    val titleBody = createTextPart(bookTitle)
                                    val authorBody = createTextPart(bookAuthor)
                                    val descBody = if (bookDescription.isNotBlank()) createTextPart(bookDescription) else null
                                    val file = uriToFile(context, selectedFileUri!!)
                                    if (file.length() > MAX_FILE_SIZE_BYTES) {
                                        withContext(Dispatchers.Main) {
                                            if (mounted) {
                                                Toast.makeText(context, if (language == "Arabic") "حجم الملف كبير جداً" else "File is too large", Toast.LENGTH_LONG).show()
                                                uploading = false
                                            }
                                        }
                                        return@launch
                                    }
                                    val filePart = createFilePart(file)
                                    val coverPart = if (selectedCoverUri != null) {
                                        val coverFile = uriToCompressedImageFile(context, selectedCoverUri!!)
                                        if (coverFile.length() > MAX_COVER_SIZE_BYTES) {
                                            withContext(Dispatchers.Main) {
                                                if (mounted) {
                                                    Toast.makeText(context, if (language == "Arabic") "حجم صورة الغلاف كبير جداً" else "Cover image is too large", Toast.LENGTH_LONG).show()
                                                    uploading = false
                                                }
                                            }
                                            return@launch
                                        }
                                        createFilePart(coverFile, "cover_image", isImage = true)
                                    } else null
                                    val deviceBody = createTextPart(deviceId)
                                    BookRepository.addBook(titleBody, authorBody, descBody, filePart, coverPart, deviceBody)
                                }

                                withContext(Dispatchers.Main) {
                                    if (!mounted) return@withContext
                                    if (response.isSuccessful) {
                                        Toast.makeText(context, if (language == "Arabic") "تم إرسال الكتاب بنجاح" else "Book sent successfully", Toast.LENGTH_SHORT).show()
                                        bookTitle = ""
                                        bookAuthor = ""
                                        bookDescription = ""
                                        selectedFileUri = null
                                        selectedCoverUri = null
                                        val body = response.body()
                                        onBookAdded(
                                            body?.let { 
                                                if (selectedCoverUri != null) {
                                                    try {
                                                        val coversDir = File(context.filesDir, "covers")
                                                        coversDir.mkdirs()
                                                        val ext = if (selectedCoverUri!!.toString().contains("png")) "png" else "jpg"
                                                        val outFile = File(coversDir, "book_${it.id}.$ext")
                                                        val srcFile = uriToFile(context, selectedCoverUri!!)
                                                        srcFile.copyTo(outFile, overwrite = true)
                                                    } catch (_: Exception) {}
                                                }
                                                BookSummary(id = it.id, title = it.title, author = it.author, description = it.description, cover_image = it.cover_image) 
                                            }
                                        )
                                    } else {
                                        Toast.makeText(context, (if (language == "Arabic") "خطأ: " else "Error: ") + response.message(), Toast.LENGTH_LONG).show()
                                    }
                                    uploading = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    if (!mounted) return@withContext
                                    Toast.makeText(context, (if (language == "Arabic") "خطأ: " else "Error: ") + (e.message ?: ""), Toast.LENGTH_LONG).show()
                                    uploading = false
                                }
                            }
                        }
                    }
                },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uploading
                    ) {
                        if (uploading) {
                            CircularProgressIndicator()
                        } else {
                            Text(if (language == "Arabic") "إرسال" else "Send")
                        }
                    }
                }
            }
        }
    }
}

// دوال مساعدة
private fun createTextPart(text: String): RequestBody {
    return text.toRequestBody("text/plain".toMediaType())
}

private fun createFilePart(file: File, partName: String = "file", isImage: Boolean = false): MultipartBody.Part {
    val mimeType = if (isImage) {
        if (file.extension.equals("png", true)) "image/png" else "image/jpeg"
    } else {
        if (file.extension.equals("pdf", ignoreCase = true)) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
    val rb = file.asRequestBody(mimeType.toMediaType())
    return MultipartBody.Part.createFormData(partName, file.name, rb)
}

fun uriToFile(context: Context, uri: Uri): File {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri)
    val extension = when (mimeType) {
        "application/pdf" -> "pdf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        else -> if (mimeType?.startsWith("image/") == true) "jpg" else "pdf"
    }

    val inputStream = contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("لا يمكن قراءة الملف المختار")
    val file = File(context.cacheDir, "temp_file_${System.currentTimeMillis()}.$extension")
    file.outputStream().use { output ->
        inputStream.copyTo(output)
    }
    return file
}

private fun uriToCompressedImageFile(context: Context, uri: Uri): File {
    val contentResolver = context.contentResolver
    val inputStream = contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("لا يمكن قراءة صورة الغلاف")
    val bitmap = BitmapFactory.decodeStream(inputStream)
    val outputStream = ByteArrayOutputStream()
    var quality = 90
    do {
        outputStream.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        quality -= 10
    } while (outputStream.size() > MAX_COVER_SIZE_BYTES && quality > 40)
    val file = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out ->
        out.write(outputStream.toByteArray())
    }
    return file
}

private const val MAX_FILE_SIZE_BYTES: Long = 15L * 1024L * 1024L
private const val MAX_COVER_SIZE_BYTES: Long = 1L * 1024L * 1024L
