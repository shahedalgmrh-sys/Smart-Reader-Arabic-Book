package com.example.readerarbicbook.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.readerarbicbook.ui.components.AudioGenerationOverlay
import com.example.readerarbicbook.ui.components.DuplicateBookSnackbarHost
import androidx.compose.material3.SnackbarHostState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UploadBlockingErrorCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_invalid_file_message_and_retry_button_in_arabic() {
        var retried = false

        composeRule.setContent {
            UploadBlockingErrorCard(
                issue = AddBookBlockingIssue.INVALID_FILE,
                isArabic = true,
                onRetry = { retried = true }
            )
        }

        composeRule.onNodeWithText("تعذر معالجة الملف").assertIsDisplayed()
        composeRule.onNodeWithText("هذا الملف غير صالح أو تالف. اختر ملفًا بديلًا أو أعد تحميله.").assertIsDisplayed()
        composeRule.onNodeWithText("إعادة المحاولة").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun shows_offline_message_and_retry_button_in_arabic() {
        var retried = false

        composeRule.setContent {
            UploadBlockingErrorCard(
                issue = AddBookBlockingIssue.OFFLINE,
                isArabic = true,
                onRetry = { retried = true }
            )
        }

        composeRule.onNodeWithText("انقطاع الاتصال بالإنترنت").assertIsDisplayed()
        composeRule.onNodeWithText("لا يمكن تنفيذ الرفع حاليًا. تحقق من اتصال الشبكة وحاول لاحقًا.").assertIsDisplayed()
        composeRule.onNodeWithText("إعادة المحاولة").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun shows_duplicate_book_snackbar_in_red() {
        composeRule.setContent {
            val host = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                host.showSnackbar(message = "هذا الكتاب موجود مسبقًا في مكتبتك")
            }
            DuplicateBookSnackbarHost(hostState = host)
        }

        composeRule.onNodeWithTag("duplicate_book_toast").assertIsDisplayed()
        composeRule.onNodeWithText("هذا الكتاب موجود مسبقًا في مكتبتك").assertIsDisplayed()
    }

    @Test
    fun shows_audio_generation_overlay_with_countdown_in_arabic() {
        composeRule.setContent {
            AudioGenerationOverlay(
                visible = true,
                isArabic = true,
                remainingSeconds = 65,
                progress = 40
            )
        }

        composeRule.onNodeWithTag("audio_generation_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("جاري إنشاء الملف الصوتي...").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_generation_time").assertIsDisplayed()
    }
}
