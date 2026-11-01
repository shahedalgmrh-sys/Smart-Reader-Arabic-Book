package com.example.readerarbicbook.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BookViewModelErrorDetectionTest {

    @Test
    fun `detects invalid file from backend detail`() {
        val body = """{"detail":"Invalid PDF file"}"""
        val kind = BookViewModel.detectUploadErrorKind(code = 400, body = body, throwable = null)
        assertEquals(UploadErrorKind.INVALID_FILE, kind)
    }

    @Test
    fun `detects offline when io exception happens`() {
        val kind = BookViewModel.detectUploadErrorKind(code = null, body = null, throwable = IOException("timeout"))
        assertEquals(UploadErrorKind.OFFLINE, kind)
    }

    @Test
    fun `extracts detail from json payload`() {
        val detail = BookViewModel.extractErrorDetail("""{"detail":"Unsupported file type"}""")
        assertEquals("Unsupported file type", detail)
    }

    @Test
    fun `invalid file matcher recognizes known text`() {
        assertTrue(BookViewModel.looksLikeInvalidFile("Invalid PDF file signature"))
        assertFalse(BookViewModel.looksLikeInvalidFile("Unauthorized"))
    }
}
