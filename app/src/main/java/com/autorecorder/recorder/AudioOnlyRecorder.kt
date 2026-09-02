package com.autorecorder.recorder

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Audio-only recorder. Writes AAC audio via MediaRecorder to a temp file,
 * then copies the result into MediaStore (Music/AutoRecorder/) after
 * recording completes. Direct-to-MediaStore FileDescriptor writes are
 * unreliable on Android 10+.
 */
class AudioOnlyRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var uri: Uri? = null

    fun start(): Boolean {
        return try {
            val cacheDir = File(context.cacheDir, "audio_tmp").apply { mkdirs() }
            val file = File(cacheDir, "AR_AUDIO_${System.currentTimeMillis()}.m4a")
            tempFile = file

            val mr = MediaRecorder()
            mediaRecorder = mr
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            true
        } catch (_: Exception) {
            stopAndCleanup()
            false
        }
    }

    fun stop(): Boolean {
        var copied = false
        try {
            mediaRecorder?.stop()
            copied = copyToMediaStore()
        } catch (_: Exception) {
        }
        releaseRecorder()
        deleteTemp()
        return copied
    }

    private fun releaseRecorder() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun deleteTemp() {
        try { tempFile?.delete() } catch (_: Exception) {}
        tempFile = null
    }

    private fun copyToMediaStore(): Boolean {
        val src = tempFile ?: return false
        if (!src.exists() || src.length() < MIN_VALID_BYTES) return false
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, src.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + "/AutoRecorder")
            }
            val u = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv
            ) ?: return false
            uri = u
            context.contentResolver.openOutputStream(u)?.use { out ->
                FileInputStream(src).use { input ->
                    input.copyTo(out)
                }
            }
            return true
        } catch (_: Exception) {
            deleteFromMediaStore()
            return false
        }
    }

    private fun stopAndCleanup() {
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        releaseRecorder()
        deleteTemp()
        deleteFromMediaStore()
    }

    private fun deleteFromMediaStore() {
        val u = uri ?: return
        uri = null
        try { context.contentResolver.delete(u, null, null) } catch (_: Exception) {}
    }

    companion object {
        private const val MIN_VALID_BYTES = 4096L
    }
}
