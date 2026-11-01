package com.example.readerarbicbook.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false

    fun startRecording(outputFile: File) {
        if (isRecording) stopRecording()

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000) // High quality
            setAudioSamplingRate(44100) // CD Quality
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                Log.d("AudioRecorderManager", "Recording started: ${outputFile.absolutePath}")
            } catch (e: IOException) {
                Log.e("AudioRecorderManager", "prepare() failed", e)
                release()
            } catch (e: IllegalStateException) {
                Log.e("AudioRecorderManager", "start() failed", e)
                release()
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // RuntimeException is thrown when stop() is called immediately after start().
            // In this case the output file is not written, and the object cannot be reused.
            Log.e("AudioRecorderManager", "stop() failed", e)
        } finally {
            release()
            isRecording = false
            Log.d("AudioRecorderManager", "Recording stopped")
        }
    }

    private fun release() {
        recorder?.release()
        recorder = null
    }
}
