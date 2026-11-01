package com.example.readerarbicbook.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
 

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    language: String,
    nickname: String,
    deviceId: String,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit
) {
    var localNickname by remember { mutableStateOf(nickname) }
 

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(language=="Arabic") "الاسم" else "Nickname")
                OutlinedTextField(
                    value = localNickname,
                    onValueChange = {
                        localNickname = it
                        onNicknameChange(it)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Theme Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if(language=="Arabic") "الوضع الليلي" else "Dark Mode")
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }
        }

        // Language Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if(language=="Arabic") "اللغة" else "Language")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RadioButton(selected = language=="English", onClick = { onLanguageChange("English") })
                    Text("English")
                    RadioButton(selected = language=="Arabic", onClick = { onLanguageChange("Arabic") })
                    Text("العربية")
                }
            }
        }

        // About & Contact Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
 
                Button(onClick = { /* navigate to About */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(if(language=="Arabic") "حول التطبيق" else "About Us")
                }
                Button(onClick = { /* navigate to Contact */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(if(language=="Arabic") "تواصل معنا" else "Contact Us")
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(
        darkMode = false,
        language = "English",
        nickname = "guest",
        deviceId = "device_123",
        onDarkModeChange = {},
        onLanguageChange = {},
        onNicknameChange = {}
    )
}
