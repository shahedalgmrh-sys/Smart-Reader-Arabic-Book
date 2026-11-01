package com.example.readerarbicbook.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.provider.Settings
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

private val Context.dataStore by preferencesDataStore("settings")

data class LocalBookMeta(
    val title: String,
    val author: String,
    val coverUri: String?
)

object DevicePreferences {

    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    private val KEY_NICKNAME = stringPreferencesKey("nickname")
    private val KEY_HIDDEN_IDS = stringSetPreferencesKey("hidden_ids")
    private val KEY_DEVICE_IP = stringPreferencesKey("device_ip")
    private val KEY_ADDED_BOOK_KEYS = stringSetPreferencesKey("added_book_keys")
    private val KEY_ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    private val KEY_LOCAL_BOOK_META = stringSetPreferencesKey("local_book_meta_v1")
    private val KEY_HAS_ONBOARDED = booleanPreferencesKey("has_onboarded_v1")

    // Dark Mode
    fun getDarkMode(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DARK_MODE] ?: false }

    suspend fun setDarkMode(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = value }
    }

    fun getThemeMode(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_THEME_MODE] ?: "DYNAMIC_ADAPTIVE" }

    suspend fun setThemeMode(context: Context, value: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = value }
    }

    fun getAccentPalette(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_ACCENT_PALETTE] ?: "DEFAULT" }

    suspend fun setAccentPalette(context: Context, value: String) {
        context.dataStore.edit { it[KEY_ACCENT_PALETTE] = value }
    }

    // Language
    fun getLanguage(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_LANGUAGE] ?: "English" }

    suspend fun setLanguage(context: Context, value: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = value }
    }

    // Device ID
    suspend fun ensureDeviceId(context: Context) {
        val prefs = context.dataStore.data.first()
        val stored = prefs[KEY_DEVICE_ID]

        if (!stored.isNullOrBlank() && stored != "unknown") {
            return
        }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val value = when {
            androidId.isNotBlank() && androidId != "unknown" -> androidId
            else -> UUID.randomUUID().toString()
        }
        setDeviceId(context, value)
    }

    fun getDeviceId(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_DEVICE_ID] ?: (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown") }

    private suspend fun setDeviceId(context: Context, value: String) {
        context.dataStore.edit { it[KEY_DEVICE_ID] = value }
    }

    // Format device ID for better readability
    fun formatDeviceId(deviceId: String): String {
        return if (deviceId.length > 12) {
            "${deviceId.take(4)}-${deviceId.drop(4).take(4)}-${deviceId.drop(8).take(4)}"
        } else if (deviceId.startsWith("device_")) {
            "Device-${deviceId.drop(7).take(6)}"
        } else {
            deviceId
        }
    }

    // Nickname
    fun getNickname(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_NICKNAME] ?: "guest" }

    suspend fun setNickname(context: Context, value: String) {
        context.dataStore.edit { it[KEY_NICKNAME] = value }
    }

    // Device IP Address
    fun getDeviceIp(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_DEVICE_IP] ?: getLocalIpAddress() ?: "unknown" }

    suspend fun ensureDeviceIp(context: Context) {
        val current = getDeviceIp(context).first()
        if (current.isEmpty() || current == "unknown") {
            val ipAddress = getLocalIpAddress() ?: "unknown"
            setDeviceIp(context, ipAddress)
        }
    }

    suspend fun setDeviceIp(context: Context, value: String) {
        context.dataStore.edit { it[KEY_DEVICE_IP] = value }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        if (sAddr.indexOf(':') < 0) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            // ignore
        }
        return null
    }

    // Hidden Books (local-only delete)
    fun getHiddenIds(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_HIDDEN_IDS] ?: emptySet() }

    suspend fun addHiddenId(context: Context, id: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_IDS] ?: emptySet()
            prefs[KEY_HIDDEN_IDS] = current + id.toString()
        }
    }

    suspend fun removeHiddenId(context: Context, id: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_IDS] ?: emptySet()
            prefs[KEY_HIDDEN_IDS] = current - id.toString()
        }
    }

    fun getAddedBookKeys(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_ADDED_BOOK_KEYS] ?: emptySet() }

    suspend fun addAddedBookKey(context: Context, value: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_ADDED_BOOK_KEYS] ?: emptySet()
            prefs[KEY_ADDED_BOOK_KEYS] = current + value
        }
    }

    suspend fun setHasOnboarded(context: Context, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_ONBOARDED] = value
        }
    }

    suspend fun hasOnboarded(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_HAS_ONBOARDED] ?: false
    }

    suspend fun hasAddedAnyBook(context: Context): Boolean {
        if (hasOnboarded(context)) {
            return true
        }
        return getAddedBookKeys(context).first().isNotEmpty()
    }

    fun getLocalBookMeta(context: Context): Flow<Map<Int, LocalBookMeta>> =
        context.dataStore.data.map { prefs ->
            val entries = prefs[KEY_LOCAL_BOOK_META] ?: emptySet()
            decodeLocalBookMeta(entries)
        }

    suspend fun upsertLocalBookMeta(
        context: Context,
        bookId: Int,
        title: String,
        author: String,
        coverUri: String?
    ) {
        val entry = encodeLocalBookMeta(bookId = bookId, title = title, author = author, coverUri = coverUri)
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LOCAL_BOOK_META] ?: emptySet()
            val without = current.filterNot { it.startsWith("$bookId\t") }.toSet()
            prefs[KEY_LOCAL_BOOK_META] = without + entry
        }
    }

    private fun encodeLocalBookMeta(bookId: Int, title: String, author: String, coverUri: String?): String {
        return buildString {
            append(bookId)
            append('\t')
            append(URLEncoder.encode(title, "UTF-8"))
            append('\t')
            append(URLEncoder.encode(author, "UTF-8"))
            append('\t')
            append(URLEncoder.encode(coverUri.orEmpty(), "UTF-8"))
        }
    }

    private fun decodeLocalBookMeta(encoded: Set<String>): Map<Int, LocalBookMeta> {
        val map = LinkedHashMap<Int, LocalBookMeta>(encoded.size)
        for (entry in encoded) {
            val parts = entry.split('\t')
            if (parts.size < 4) continue
            val id = parts[0].toIntOrNull() ?: continue
            val title = URLDecoder.decode(parts[1], "UTF-8")
            val author = URLDecoder.decode(parts[2], "UTF-8")
            val coverRaw = URLDecoder.decode(parts[3], "UTF-8").trim()
            val coverUri = coverRaw.ifBlank { null }
            map[id] = LocalBookMeta(title = title, author = author, coverUri = coverUri)
        }
        return map
    }

}
