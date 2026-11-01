package com.example.readerarbicbook.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerarbicbook.data.BookRepository
import com.example.readerarbicbook.network.BookResponse
import com.example.readerarbicbook.network.BookSummary
import com.example.readerarbicbook.network.BookUpdate
import com.example.readerarbicbook.utils.FileUtils
import com.example.readerarbicbook.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

sealed class BookUiState {
    object Loading : BookUiState()
    data class Success(val book: BookResponse) : BookUiState()
    data class Error(val message: String) : BookUiState()
    object Idle : BookUiState()
}

sealed class BooksListUiState {
    object Loading : BooksListUiState()
    data class Success(val books: List<BookSummary>) : BooksListUiState()
    data class Error(val message: String) : BooksListUiState()
    object Idle : BooksListUiState()
}

sealed class UploadUiState {
    object Idle : UploadUiState()
    object Loading : UploadUiState()
    data class Success(val book: BookResponse) : UploadUiState()
    data class Duplicate(val message: String) : UploadUiState()
    data class Error(val message: String, val kind: UploadErrorKind = UploadErrorKind.GENERIC) : UploadUiState()
}

enum class UploadErrorKind {
    INVALID_FILE,
    OFFLINE,
    GENERIC
}

enum class AudioReuseHint {
    UNKNOWN,
    FOUND,
    NOT_FOUND
}

sealed class AudioGenerationUiState {
    object Idle : AudioGenerationUiState()
    data class Generating(
        val bookId: Int,
        val progress: Int,
        val remainingSeconds: Int,
        val status: String
    ) : AudioGenerationUiState()
    object Completed : AudioGenerationUiState()
    data class Error(val message: String) : AudioGenerationUiState()
}

class BookViewModel : ViewModel() {

    private val _bookState = MutableStateFlow<BookUiState>(BookUiState.Idle)
    val bookState: StateFlow<BookUiState> = _bookState.asStateFlow()

    private val _booksListState = MutableStateFlow<BooksListUiState>(BooksListUiState.Idle)
    val booksListState: StateFlow<BooksListUiState> = _booksListState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _audioReuseHint = MutableStateFlow(AudioReuseHint.UNKNOWN)
    val audioReuseHint: StateFlow<AudioReuseHint> = _audioReuseHint.asStateFlow()
    
    private val _ipAddress = MutableStateFlow<String?>(null)
    val ipAddress: StateFlow<String?> = _ipAddress.asStateFlow()

    private val _audioGenerationState = MutableStateFlow<AudioGenerationUiState>(AudioGenerationUiState.Idle)
    val audioGenerationState: StateFlow<AudioGenerationUiState> = _audioGenerationState.asStateFlow()

    private var audioPollJob: Job? = null

    fun getBooks(deviceId: String, query: String? = null) {
        viewModelScope.launch {
            _booksListState.value = BooksListUiState.Loading
            try {
                val response = BookRepository.getBooks(limit = 100, offset = 0, query = query, deviceId = deviceId)
                if (response.isSuccessful && response.body() != null) {
                    _booksListState.value = BooksListUiState.Success(response.body()!!)
                } else {
                    _booksListState.value = BooksListUiState.Error("Failed to load books: ${response.message()}")
                }
            } catch (e: Exception) {
                _booksListState.value = BooksListUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun getBook(id: Int, deviceId: String) {
        viewModelScope.launch {
            _bookState.value = BookUiState.Loading
            try {
                val response = BookRepository.getBook(id, deviceId)
                if (response.isSuccessful && response.body() != null) {
                    _bookState.value = BookUiState.Success(response.body()!!)
                } else {
                    _bookState.value = BookUiState.Error("Failed to load book: ${response.message()}")
                }
            } catch (e: Exception) {
                _bookState.value = BookUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun addBook(
        context: Context,
        title: String,
        author: String,
        description: String,
        fileUri: Uri,
        imageUri: Uri? = null,
        deviceId: String,
        fileChecksumSha256Hex: String? = null
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadUiState.Loading
            _audioReuseHint.value = AudioReuseHint.UNKNOWN
            try {
                val file = FileUtils.getFileFromUri(context, fileUri)
                if (file == null) {
                    _uploadState.value = UploadUiState.Error(
                        message = localizedMessageFor(UploadErrorKind.INVALID_FILE),
                        kind = UploadErrorKind.INVALID_FILE
                    )
                    return@launch
                }

                if (!NetworkUtils.isInternetAvailable(context)) {
                    _uploadState.value = UploadUiState.Error(
                        message = localizedMessageFor(UploadErrorKind.OFFLINE),
                        kind = UploadErrorKind.OFFLINE
                    )
                    return@launch
                }

                val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                // Handle Image
                var imagePart: MultipartBody.Part? = null
                if (imageUri != null) {
                    val imageFile = FileUtils.getFileFromUri(context, imageUri)
                    if (imageFile != null) {
                        val requestImageFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                        imagePart = MultipartBody.Part.createFormData("cover_image", imageFile.name, requestImageFile)
                    }
                }
                
                val textType = "text/plain".toMediaTypeOrNull()
                val titlePart = RequestBody.create(textType, title)
                val authorPart = RequestBody.create(textType, author)
                val descPart = RequestBody.create(textType, description)
                val deviceIdPart = RequestBody.create(textType, deviceId)

                val checksum = fileChecksumSha256Hex?.takeIf { it.isNotBlank() }
                    ?: FileUtils.sha256HexOfUri(context, fileUri)
                if (!checksum.isNullOrBlank()) {
                    try {
                        val checksumPart = RequestBody.create(textType, checksum)
                        val resp = BookRepository.getAudioByChecksum(
                            checksum = checksumPart,
                            deviceId = deviceId,
                            range = "bytes=0-0"
                        )
                        resp.body()?.close()
                        _audioReuseHint.value = when {
                            resp.isSuccessful -> AudioReuseHint.FOUND
                            resp.code() == 404 -> AudioReuseHint.NOT_FOUND
                            else -> AudioReuseHint.UNKNOWN
                        }
                    } catch (_: Exception) {
                        _audioReuseHint.value = AudioReuseHint.UNKNOWN
                    }
                }

                val response = BookRepository.addBook(
                    title = titlePart,
                    author = authorPart,
                    description = descPart,
                    file = filePart,
                    coverImage = imagePart,
                    deviceId = deviceIdPart
                )

                if (response.isSuccessful && response.body() != null) {
                    val alreadyExistsHeader = response.headers()["X-Book-Already-Exists"]
                    if (alreadyExistsHeader == "1" || alreadyExistsHeader.equals("true", ignoreCase = true)) {
                        _uploadState.value = UploadUiState.Duplicate("هذا الكتاب موجود مسبقًا في مكتبتك")
                        return@launch
                    }
                    _uploadState.value = UploadUiState.Success(response.body()!!)
                    getBooks(deviceId)
                } else {
                    val serverDetail = response.errorBody()?.string()
                    val errorKind = detectUploadErrorKind(code = response.code(), body = serverDetail, throwable = null)
                    _uploadState.value = UploadUiState.Error(
                        message = localizedMessageFor(errorKind),
                        kind = errorKind
                    )
                }
            } catch (e: Exception) {
                val errorKind = detectUploadErrorKind(code = null, body = null, throwable = e)
                _uploadState.value = UploadUiState.Error(
                    message = localizedMessageFor(errorKind),
                    kind = errorKind
                )
            }
        }
    }

    fun checkAudioByChecksum(checksumSha256Hex: String, deviceId: String) {
        val checksum = checksumSha256Hex.trim().lowercase()
        if (checksum.isBlank()) {
            _audioReuseHint.value = AudioReuseHint.UNKNOWN
            return
        }
        viewModelScope.launch {
            _audioReuseHint.value = AudioReuseHint.UNKNOWN
            try {
                val textType = "text/plain".toMediaTypeOrNull()
                val checksumPart = RequestBody.create(textType, checksum)
                val resp = BookRepository.getAudioByChecksum(
                    checksum = checksumPart,
                    deviceId = deviceId,
                    range = "bytes=0-0"
                )
                resp.body()?.close()
                _audioReuseHint.value = when {
                    resp.isSuccessful -> AudioReuseHint.FOUND
                    resp.code() == 404 -> AudioReuseHint.NOT_FOUND
                    else -> AudioReuseHint.UNKNOWN
                }
            } catch (_: Exception) {
                _audioReuseHint.value = AudioReuseHint.UNKNOWN
            }
        }
    }
    
    fun updateBook(id: Int, title: String?, author: String?, description: String?, deviceId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val update = BookUpdate(title, author, description)
                val response = BookRepository.updateBook(id, update, deviceId)
                if (response.isSuccessful) {
                    onSuccess()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deleteBook(id: Int, deviceId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = BookRepository.deleteBook(id, deviceId)
                if (response.isSuccessful) {
                    onSuccess()
                    getBooks(deviceId)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun resetUploadState() {
        _uploadState.value = UploadUiState.Idle
    }

    fun resetAudioReuseHint() {
        _audioReuseHint.value = AudioReuseHint.UNKNOWN
    }
    
    fun startAudioGeneration(bookId: Int, deviceId: String) {
        viewModelScope.launch {
            try {
                audioPollJob?.cancel()
                val startResp = BookRepository.generateAudio(bookId, deviceId)
                val body = startResp.body()
                val estimatedSeconds = (body?.estimated_time_seconds ?: 60.0).roundToInt().coerceAtLeast(10)
                val startedAt = System.currentTimeMillis()
                val initialProgress = body?.progress ?: 0
                val initialStatus = body?.status ?: "processing"
                _audioGenerationState.value = AudioGenerationUiState.Generating(
                    bookId = bookId,
                    progress = initialProgress,
                    remainingSeconds = estimatedSeconds,
                    status = initialStatus
                )

                audioPollJob = viewModelScope.launch {
                    while (true) {
                        val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000.0).roundToInt()
                        val remaining = max(0, estimatedSeconds - elapsedSeconds)

                        val progressResp = BookRepository.getAudioProgress(bookId, deviceId)
                        val progressBody = progressResp.body()
                        val progress = progressBody?.progress ?: 0
                        val status = progressBody?.status ?: "processing"
                        _audioGenerationState.value = AudioGenerationUiState.Generating(
                            bookId = bookId,
                            progress = progress,
                            remainingSeconds = remaining,
                            status = status
                        )

                        if (progress >= 100 || status.equals("completed", ignoreCase = true)) {
                            break
                        }

                        delay(1000)
                    }
                    _audioGenerationState.value = AudioGenerationUiState.Completed
                    getBook(bookId, deviceId)
                    delay(500)
                    _audioGenerationState.value = AudioGenerationUiState.Idle
                }
            } catch (e: Exception) {
                _audioGenerationState.value = AudioGenerationUiState.Error("تعذر إنشاء الصوت. حاول مرة أخرى.")
            }
        }
    }

    fun resetAudioGenerationState() {
        audioPollJob?.cancel()
        _audioGenerationState.value = AudioGenerationUiState.Idle
    }
    
    fun fetchIp(context: Context) {
        viewModelScope.launch {
            try {
                // Try to get local IP first as requested
                val localIp = NetworkUtils.getDeviceIpAddress(context)
                if (localIp != "Unavailable" && !localIp.startsWith("Error")) {
                    _ipAddress.value = localIp
                } else {
                    // Fallback to server IP if local fails
                    val response = BookRepository.getIp()
                    if (response.isSuccessful && response.body() != null) {
                        _ipAddress.value = response.body()!!.ip
                    } else {
                        _ipAddress.value = "Unavailable"
                    }
                }
            } catch (e: Exception) {
                _ipAddress.value = "Error: ${e.message}"
            }
        }
    }
    
    fun updateProgress(id: Int, lastPage: Int, deviceId: String) {
        viewModelScope.launch {
            try {
                BookRepository.updateProgress(id, lastPage, deviceId)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun updateAudioPosition(id: Int, positionMs: Int, deviceId: String) {
        viewModelScope.launch {
            try {
                BookRepository.updateAudioPosition(id, positionMs, deviceId)
            } catch (_: Exception) {
                // Ignore error
            }
        }
    }

    private fun localizedMessageFor(kind: UploadErrorKind): String {
        return when (kind) {
            UploadErrorKind.INVALID_FILE -> "الملف غير صالح أو تالف. الرجاء اختيار ملف PDF صحيح ثم إعادة المحاولة."
            UploadErrorKind.OFFLINE -> "لا يوجد اتصال بالإنترنت الآن. تحقق من الشبكة ثم حاول مرة أخرى."
            UploadErrorKind.GENERIC -> "حدث خطأ أثناء رفع الملف. الرجاء المحاولة مرة أخرى."
        }
    }

    companion object {
        fun detectUploadErrorKind(code: Int?, body: String?, throwable: Throwable?): UploadErrorKind {
            val detail = extractErrorDetail(body)
            if (looksLikeInvalidFile(detail)) {
                return UploadErrorKind.INVALID_FILE
            }
            if (throwable is java.io.IOException || throwable is HttpException && throwable.code() == 503) {
                return UploadErrorKind.OFFLINE
            }
            if (code == 400 && looksLikeInvalidFile(detail)) {
                return UploadErrorKind.INVALID_FILE
            }
            return UploadErrorKind.GENERIC
        }

        fun extractErrorDetail(body: String?): String {
            if (body.isNullOrBlank()) {
                return ""
            }
            val raw = body.trim()
            val regex = Regex("\"detail\"\\s*:\\s*\"([^\"]*)\"")
            val match = regex.find(raw)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
            return raw
        }

        fun looksLikeInvalidFile(detail: String?): Boolean {
            if (detail.isNullOrBlank()) {
                return false
            }
            val normalized = detail.lowercase()
            return normalized.contains("invalid pdf") ||
                normalized.contains("unsupported file") ||
                normalized.contains("file signature") ||
                normalized.contains("docx extraction failed")
        }
    }
}
