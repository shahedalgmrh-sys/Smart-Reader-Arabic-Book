package com.example.readerarbicbook.data

import com.example.readerarbicbook.network.AudioResponse
import com.example.readerarbicbook.network.AudioProgressResponse
import com.example.readerarbicbook.network.AudioPositionResponse
import com.example.readerarbicbook.network.BookApi
import com.example.readerarbicbook.network.BookCreate
import com.example.readerarbicbook.network.BookResponse
import com.example.readerarbicbook.network.BookSummary
import com.example.readerarbicbook.network.BookUpdate
import com.example.readerarbicbook.network.IpResponse
import com.example.readerarbicbook.network.RetrofitInstance
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response

object BookRepository {

    private val api: BookApi = RetrofitInstance.api

    suspend fun addBookJson(req: BookCreate): Response<BookResponse> {
        return api.addBookJson(req)
    }

    suspend fun addBook(
        title: RequestBody,
        author: RequestBody,
        description: RequestBody?,
        file: MultipartBody.Part,
        coverImage: MultipartBody.Part?,
        deviceId: RequestBody
    ): Response<BookResponse> {
        return api.addBook(title, author, description, file, coverImage, deviceId)
    }
    
    suspend fun updateBook(
        id: Int,
        update: BookUpdate,
        deviceId: String
    ): Response<BookResponse> {
        return api.updateBook(id, update, deviceId)
    }

    suspend fun getBooks(
        limit: Int,
        offset: Int,
        query: String?,
        deviceId: String
    ): Response<List<BookSummary>> {
        return api.getBooks(limit = limit, offset = offset, q = query, deviceId = deviceId)
    }

    suspend fun getBook(id: Int, deviceId: String): Response<BookResponse> {
        return api.getBook(id, deviceId)
    }

    suspend fun generateAudio(id: Int, deviceId: String): Response<AudioResponse> {
        return api.generateAudio(id, deviceId)
    }

    suspend fun getAudioProgress(id: Int, deviceId: String): Response<AudioProgressResponse> {
        return api.getAudioProgress(id, deviceId)
    }

    suspend fun getAudio(id: Int, deviceId: String): Response<ResponseBody> {
        return api.getAudio(id, deviceId)
    }
    
    suspend fun deleteBook(id: Int, deviceId: String): Response<ResponseBody> {
        return api.deleteBook(id, deviceId)
    }
    
    suspend fun getIp(): Response<IpResponse> {
        return api.getIp()
    }
    
    suspend fun updateProgress(id: Int, lastPage: Int, deviceId: String): Response<ResponseBody> {
        return api.updateProgress(id, lastPage, deviceId)
    }

    suspend fun getAudioPosition(id: Int, deviceId: String): Response<AudioPositionResponse> {
        return api.getAudioPosition(id, deviceId)
    }

    suspend fun updateAudioPosition(id: Int, positionMs: Int, deviceId: String): Response<ResponseBody> {
        return api.updateAudioPosition(id, positionMs, deviceId)
    }

    suspend fun getAudioByChecksum(
        checksum: RequestBody,
        deviceId: String,
        range: String?
    ): Response<ResponseBody> {
        return api.getAudioByChecksum(checksum = checksum, deviceId = deviceId, range = range)
    }
}
