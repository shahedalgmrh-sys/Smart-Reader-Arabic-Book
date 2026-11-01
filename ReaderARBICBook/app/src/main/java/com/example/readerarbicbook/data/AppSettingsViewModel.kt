package com.example.readerarbicbook.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()

    val themeMode: StateFlow<String> = DevicePreferences.getThemeMode(appContext)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "DYNAMIC_ADAPTIVE")

    val language: StateFlow<String> = DevicePreferences.getLanguage(appContext)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "English")

    val nickname: StateFlow<String> = DevicePreferences.getNickname(appContext)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "guest")

    val deviceId: StateFlow<String> = DevicePreferences.getDeviceId(appContext)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "unknown")

    fun setThemeMode(value: String) {
        viewModelScope.launch { DevicePreferences.setThemeMode(appContext, value) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { DevicePreferences.setLanguage(appContext, lang) }
    }

    fun setNickname(name: String) {
        viewModelScope.launch { DevicePreferences.setNickname(appContext, name) }
    }

    fun ensureDeviceId() {
        viewModelScope.launch { DevicePreferences.ensureDeviceId(appContext) }
    }

}
