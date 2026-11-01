package com.example.readerarbicbook.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Header
import java.util.concurrent.TimeUnit
import com.example.readerarbicbook.BuildConfig

data class BookResponse(
    val id: Int,
    val title: String,
    val author: String,
    val content: String,
    val description: String?,
    val cover_image: String?,
    val audio_path: String?,
    val last_page: Int? = 1
)

data class AudioResponse(
    val message: String,
    val audio_path: String?,
    val segments_count: Int? = null,
    val status: String? = null,
    val progress: Int? = null,
    val estimated_time_seconds: Double? = null
)

data class AudioProgressResponse(
    val progress: Int,
    val status: String? = null,
    val eta: Double? = null
)

data class AudioPositionResponse(
    val book_id: Int,
    val position_ms: Int
)

data class BookSummary(
    val id: Int,
    val title: String,
    val author: String,
    val description: String?,
    val cover_image: String?
)

data class BookCreate(
    val title: String,
    val author: String,
    val content: String,
    val description: String?,
    val android_id: String
)

data class BookUpdate(
    val title: String?,
    val author: String?,
    val description: String?
)

data class IpResponse(
    val ip: String
)

interface BookApi {
    @Multipart
    @POST("books")
    suspend fun addBook(
        @Part("title") title: RequestBody,
        @Part("author") author: RequestBody,
        @Part("description") description: RequestBody?,
        @Part file: MultipartBody.Part,
        @Part cover_image: MultipartBody.Part?,
        @Part("android_id") deviceId: RequestBody
    ): Response<BookResponse>

    @POST("books/json")
    suspend fun addBookJson(@Body req: BookCreate): Response<BookResponse>

    @PUT("books/{id}")
    suspend fun updateBook(
        @Path("id") id: Int,
        @Body req: BookUpdate,
        @Query("android_id") deviceId: String
    ): Response<BookResponse>

    @GET("books")
    suspend fun getBooks(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("q") q: String? = null,
        @Query("android_id") deviceId: String
    ): Response<List<BookSummary>>

    @GET("books/{id}")
    suspend fun getBook(@Path("id") id: Int, @Query("android_id") deviceId: String): Response<BookResponse>

    @POST("books/{id}/generate_audio")
    suspend fun generateAudio(@Path("id") id: Int, @Query("android_id") deviceId: String): Response<AudioResponse>

    @GET("books/{id}/progress")
    suspend fun getAudioProgress(@Path("id") id: Int, @Query("android_id") deviceId: String): Response<AudioProgressResponse>

    @GET("books/{id}/audio")
    @Streaming
    suspend fun getAudio(@Path("id") id: Int, @Query("android_id") deviceId: String): Response<ResponseBody>

    @DELETE("books/{id}")
    suspend fun deleteBook(
        @Path("id") id: Int,
        @Query("android_id") deviceId: String
    ): Response<ResponseBody>
    
    @GET("my-ip")
    suspend fun getIp(): Response<IpResponse>
    
    @PUT("books/{id}/progress")
    suspend fun updateProgress(
        @Path("id") id: Int,
        @Query("last_page") lastPage: Int,
        @Query("android_id") deviceId: String
    ): Response<ResponseBody>

    @GET("books/{id}/audio_position")
    suspend fun getAudioPosition(
        @Path("id") id: Int,
        @Query("android_id") deviceId: String
    ): Response<AudioPositionResponse>

    @PUT("books/{id}/audio_position")
    suspend fun updateAudioPosition(
        @Path("id") id: Int,
        @Query("position_ms") positionMs: Int,
        @Query("android_id") deviceId: String
    ): Response<ResponseBody>

    @Multipart
    @POST("upload")
    @Streaming
    suspend fun getAudioByChecksum(
        @Part("checksum") checksum: RequestBody,
        @Query("android_id") deviceId: String,
        @Header("Range") range: String?
    ): Response<ResponseBody>
}


object RetrofitInstance {
    private val retrofit: Retrofit = run {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: BookApi = retrofit.create(BookApi::class.java)
}
