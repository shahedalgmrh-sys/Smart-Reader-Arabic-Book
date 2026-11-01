package com.example.readerarbicbook.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.readerarbicbook.ui.components.BottomNavItem
import com.example.readerarbicbook.ui.components.GlassBottomNavigation
import com.example.readerarbicbook.ui.components.GlassCard
import com.example.readerarbicbook.ui.components.luxuryGradientBackground
import com.example.readerarbicbook.ui.screens.*
import com.example.readerarbicbook.data.DevicePreferences
import androidx.compose.ui.platform.LocalContext
import com.example.readerarbicbook.data.BookRepository
import com.example.readerarbicbook.viewmodel.AudioGenerationUiState
import com.example.readerarbicbook.viewmodel.BookViewModel

@Composable
fun SmartBookReaderNavigation() {
    val navController = rememberNavController()
    val bookViewModel: BookViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"
    val context = LocalContext.current
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    val audioGenerationState by bookViewModel.audioGenerationState.collectAsState()
    val deviceId by DevicePreferences.getDeviceId(context).collectAsState(initial = "unknown")
    val hasAddedAnyBook by produceState<Boolean?>(initialValue = null, key1 = context, key2 = deviceId) {
        value = DevicePreferences.hasAddedAnyBook(context)
        if (value == false && deviceId.isNotBlank() && deviceId != "unknown") {
            try {
                val resp = BookRepository.getBooks(limit = 1, offset = 0, query = null, deviceId = deviceId)
                val hasServerBooks = resp.isSuccessful && !resp.body().isNullOrEmpty()
                if (hasServerBooks) {
                    DevicePreferences.setHasOnboarded(context, true)
                    value = true
                }
            } catch (_: Exception) {
                value = false
            }
        }
    }

    val bottomNavItems = if (isArabic) {
        listOf(
            BottomNavItem("profile", Icons.Default.Person, "الملف الشخصي"),
            BottomNavItem("add_book", Icons.Default.Add, "إضافة"),
            BottomNavItem("library", Icons.Default.LibraryBooks, "المكتبة"),
            BottomNavItem("dashboard", Icons.Default.Home, "الرئيسية")
        )
    } else {
        listOf(
            BottomNavItem("profile", Icons.Default.Person, "Profile"),
            BottomNavItem("add_book", Icons.Default.Add, "Add"),
            BottomNavItem("library", Icons.Default.LibraryBooks, "Library"),
            BottomNavItem("dashboard", Icons.Default.Home, "Home")
        )
    }
    
    val showBottomBar = hasAddedAnyBook != null && currentRoute in listOf("dashboard", "library", "add_book", "profile")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                GlassBottomNavigation(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onItemClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .luxuryGradientBackground()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            val resolvedHasAdded = hasAddedAnyBook
            if (resolvedHasAdded == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                val startDestination = if (resolvedHasAdded) "dashboard" else "add_book"
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = {
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    }
                ) {
                    // Main Screens
                    composable("dashboard") {
                        DashboardScreen(
                            onBookClick = { bookId -> navController.navigate("book_details/$bookId") },
                            onSearchClick = { navController.navigate("library") },
                            bookViewModel = bookViewModel
                        )
                    }

                    composable("library") {
                        LibraryScreen(
                            onBookClick = { book -> navController.navigate("book_details/${book.id}") },
                            onSearchClick = { /* Handle search within library */ },
                            bookViewModel = bookViewModel
                        )
                    }

                    composable("add_book") {
                        AddBookScreen(
                            onNavigateBack = {
                                navController.navigate("dashboard") {
                                    popUpTo("add_book") { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            bookViewModel = bookViewModel
                        )
                    }

                    composable("profile") {
                        ProfileScreen(
                            onNavigateToSettings = { navController.navigate("settings") },
                            bookViewModel = bookViewModel
                        )
                    }

                    // Other Screens
                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToAbout = { navController.navigate("about") }
                        )
                    }

                    composable("about") {
                        AboutScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    // Note: book_details is the main reading/details screen now
                    composable("book_details/{bookId}") { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                        BookDetailsScreen(
                            bookId = bookId,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToReader = { navController.navigate("immersive_reader/$bookId") },
                            bookViewModel = bookViewModel
                        )
                    }

                    composable("immersive_reader/{bookId}") { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                        ImmersiveReaderScreen(
                            bookId = bookId,
                            onNavigateBack = { navController.popBackStack() },
                            bookViewModel = bookViewModel
                        )
                    }
                }
            }

            val generating = audioGenerationState as? AudioGenerationUiState.Generating
            if (generating != null) {
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    blurRadius = 12.dp,
                    borderWidth = 1.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "جاري إنشاء الصوت" else "Generating audio",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { bookViewModel.resetAudioGenerationState() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isArabic) "إغلاق" else "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { generating.progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isArabic) {
                                formatEta(true, generating.remainingSeconds)
                            } else {
                                formatEta(false, generating.remainingSeconds)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatEta(isArabic: Boolean, remainingSeconds: Int): String {
    val total = remainingSeconds.coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return if (isArabic) {
        if (minutes > 0) {
            "الوقت المتبقي: $minutes دقيقة و $seconds ثانية"
        } else {
            "الوقت المتبقي: $seconds ثانية"
        }
    } else {
        if (minutes > 0) {
            "Time left: ${minutes}m ${seconds}s"
        } else {
            "Time left: ${seconds}s"
        }
    }
}
