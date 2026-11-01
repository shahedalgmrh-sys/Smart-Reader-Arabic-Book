package com.example.readerarbicbook.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavRecorder {
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var out: FileOutputStream? = null
    private var totalAudioLen: Long = 0
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun start(file: File) {
        stop()
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBuf)
        out = FileOutputStream(file)
        writeWavHeader(out!!, sampleRate, 1, 16)
        totalAudioLen = 0
        recorder?.startRecording()
        running = true
        thread = Thread {
            val buffer = ByteArray(minBuf)
            while (running) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    out?.write(buffer, 0, read)
                    totalAudioLen += read.toLong()
                }
            }
        }
        thread?.start()
    }

    fun stop() {
        running = false
        try {
            thread?.join()
        } catch (_: Exception) { }
        thread = null
        recorder?.stop()
        recorder?.release()
        recorder = null
        val stream = out
        if (stream != null) {
            try {
                val totalDataLen = totalAudioLen + 36
                val channels = 1
                val byteRate = 16 * sampleRate * channels / 8
                stream.flush()
                stream.channel.position(0)
                writeWavHeader(stream, sampleRate, channels, 16, totalDataLen, totalAudioLen, byteRate)
                stream.close()
            } catch (_: Exception) { }
        }
        out = null
    }

    private fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, bitsPerSample: Int, totalDataLen: Long = 0, totalAudioLen: Long = 0, byteRate: Int = 0) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(if (byteRate != 0) byteRate else bitsPerSample * sampleRate * channels / 8)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(totalAudioLen.toInt())
        out.write(header.array(), 0, 44)
    }
}

