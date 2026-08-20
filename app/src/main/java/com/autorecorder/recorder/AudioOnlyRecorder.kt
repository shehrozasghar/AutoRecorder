package com.autorecorder.recorder

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * Audio-only fallback recorder (used when the camera is unavailable).
 */
class AudioOnlyRecorder(context: Context) {

    private val resolver = context.contentResolver
    private var mediaRecorder: MediaRecorder? = null
    private var uri: Uri? = null

    fun start(maxMinutes: Int) {
        try {
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
            val u = resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv
            ) ?: return
            uri = u
            val fd = resolver.openFileDescriptor(u, "w") ?: return
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
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }
}