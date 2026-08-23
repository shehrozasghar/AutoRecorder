package com.autorecorder.recorder

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore

/**
 * Audio-only fallback recorder (used when the camera is unavailable).
 * Writes AAC audio into Music/AutoRecorder via MediaStore.
 */
class AudioOnlyRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null

    fun start(): Boolean {
        return try {
            val cv = ContentValues().apply {
                put(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    "AR_AUDIO_${System.currentTimeMillis()}.m4a"
                )
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + "/AutoRecorder"
                )
            }
            val u = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv
            ) ?: return false
            uri = u
            val fd = context.contentResolver.openFileDescriptor(u, "rw") ?: return false
            pfd = fd

            val mr = MediaRecorder()
            mediaRecorder = mr
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(fd.fileDescriptor)
            mr.prepare()
            mr.start()
            true
        } catch (_: Exception) {
            stopAndCleanup()
            false
        }
    }

    fun stop() {
        stopAndCleanup()
    }

    private fun stopAndCleanup() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        try {
            pfd?.close()
        } catch (_: Exception) {
        }
        pfd = null
        deleteIfEmpty()
    }

    /** Removes the MediaStore entry if nothing meaningful was recorded. */
    private fun deleteIfEmpty() {
        val u = uri ?: return
        uri = null
        try {
            var size = -1L
            val cursor: Cursor? = context.contentResolver.query(
                u, arrayOf(MediaStore.Audio.Media.SIZE), null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) size = c.getLong(0)
            }
            if (size < MIN_VALID_BYTES) {
                context.contentResolver.delete(u, null, null)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MIN_VALID_BYTES = 4096L
    }
}