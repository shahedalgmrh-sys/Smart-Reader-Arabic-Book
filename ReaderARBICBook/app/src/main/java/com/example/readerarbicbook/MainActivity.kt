package com.example.readerarbicbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.ui.navigation.SmartBookReaderNavigation
import com.example.readerarbicbook.ui.theme.SmartBookReaderTheme
import com.example.readerarbicbook.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        lifecycleScope.launch {
            DevicePreferences.ensureDeviceId(applicationContext)
        }
        
        setContent {
            val themeModePref by DevicePreferences.getThemeMode(applicationContext).collectAsState(initial = "DYNAMIC_ADAPTIVE")
            val themeMode = remember(themeModePref) { ThemeMode.fromPreference(themeModePref) }
            val accentPalettePref by DevicePreferences.getAccentPalette(applicationContext).collectAsState(initial = "DEFAULT")
            val language by DevicePreferences.getLanguage(applicationContext).collectAsState(initial = "Arabic")
            
            val layoutDirection = if (language == "Arabic") LayoutDirection.Rtl else LayoutDirection.Ltr

            SmartBookReaderTheme(themeMode = themeMode, accentPalettePref = accentPalettePref) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        SmartBookReaderNavigation()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    SmartBookReaderTheme {
        MainScreen()
    }
}
