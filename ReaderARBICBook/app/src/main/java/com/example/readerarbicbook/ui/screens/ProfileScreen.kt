package com.example.readerarbicbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.readerarbicbook.ui.components.*

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.readerarbicbook.BuildConfig
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerarbicbook.data.DevicePreferences
import com.example.readerarbicbook.viewmodel.BookViewModel
import com.example.readerarbicbook.viewmodel.BooksListUiState
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    bookViewModel: BookViewModel = viewModel()
) {
    val context = LocalContext.current
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val scope = rememberCoroutineScope()
    
    // Get nickname from preferences
    val nickname by DevicePreferences.getNickname(context).collectAsState(initial = "")
    var isEditingNickname by remember { mutableStateOf(false) }
    var tempNickname by remember { mutableStateOf(nickname) }
    
    // Get books count
    val booksListState by bookViewModel.booksListState.collectAsState()
    val booksCount = when (booksListState) {
        is BooksListUiState.Success -> (booksListState as BooksListUiState.Success).books.size
        else -> 0
    }
    
    LaunchedEffect(deviceId) {
        if (deviceId.isNotBlank() && deviceId != "unknown") {
            bookViewModel.getBooks(deviceId)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, deviceId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                deviceId.isNotBlank() && deviceId != "unknown"
            ) {
                bookViewModel.getBooks(deviceId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Update temp nickname when nickname changes
    LaunchedEffect(nickname) {
        if (nickname.isNotEmpty()) {
            tempNickname = nickname
        } else {
            // Generate default nickname if empty
            tempNickname = if (deviceId != "unknown" && deviceId.length > 6) {
                "guest_${deviceId.substring(0, 6)}"
            } else {
                "guest_user"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryGradientBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = if (isArabic) "الإعدادات" else "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Profile Info
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Nickname
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "الاسم المستعار" else "Nickname",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            IconButton(
                                onClick = { isEditingNickname = !isEditingNickname },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditingNickname) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditingNickname) { if (isArabic) "حفظ" else "Save" } else { if (isArabic) "تعديل" else "Edit" },
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isEditingNickname) {
                            // Edit mode
                            TextField(
                                value = tempNickname,
                                onValueChange = { tempNickname = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(if (isArabic) "أدخل اسمك المستعار" else "Enter your nickname") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            // Save button
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        DevicePreferences.setNickname(context, tempNickname)
                                        isEditingNickname = false
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(if (isArabic) "حفظ" else "Save", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            // Display mode
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                blurRadius = 4.dp
                            ) {
                                Text(
                                    text = tempNickname,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Books Count Section
                        Text(
                            text = if (isArabic) "مجموع الكتب" else "Total Books",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            blurRadius = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isArabic) "$booksCount كتاب" else "$booksCount books",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
