package com.example.readerarbicbook

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.example.readerarbicbook.data.DevicePreferences
import android.provider.Settings
import kotlinx.coroutines.flow.first

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.readerarbicbook", appContext.packageName)
    }

    @Test
    fun ensureAndroidIdStored() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        DevicePreferences.ensureDeviceId(appContext)
        val stored = DevicePreferences.getDeviceId(appContext).first()
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        assertTrue(stored.isNotEmpty())
        assertEquals(androidId, stored)
    }
}
