package com.autorecorder.config

import android.content.Context

/**
 * Runtime-configurable settings. All fields are read live when a recording
 * starts, so an in-app change takes effect on the next gesture.
 *
 * recordMode: 0 = audio only, 1 = video front camera, 2 = video back camera
 */
data class AppConfig(
    val shakeEnabled: Boolean = true,
    val shakeCount: Int = 3,
    val shakeThreshold: Float = 18f,
    val shakeCooldownMs: Long = 150L,
    val volumeEnabled: Boolean = true,
    val customEnabled: Boolean = true,
    val recordMode: Int = MODE_AUDIO,
    val maxRecordingMinutes: Int = 5,
    val keepScreenAwake: Boolean = false
) {
    val isVideo: Boolean get() = recordMode == MODE_VIDEO_FRONT || recordMode == MODE_VIDEO_BACK
    val useBackCamera: Boolean get() = recordMode == MODE_VIDEO_BACK

    fun save(context: Context) {
        val p = context.getSharedPreferences("autorecorder_config", Context.MODE_PRIVATE)
        p.edit()
            .putBoolean("shakeEnabled", shakeEnabled)
            .putInt("shakeCount", shakeCount)
            .putFloat("shakeThreshold", shakeThreshold)
            .putLong("shakeCooldownMs", shakeCooldownMs)
            .putBoolean("volumeEnabled", volumeEnabled)
            .putBoolean("customEnabled", customEnabled)
            .putInt("recordMode", recordMode)
            .putInt("maxRecordingMinutes", maxRecordingMinutes)
            .putBoolean("keepScreenAwake", keepScreenAwake)
            .apply()
    }

    companion object {
        const val MODE_AUDIO = 0
        const val MODE_VIDEO_FRONT = 1
        const val MODE_VIDEO_BACK = 2

        fun modeLabel(mode: Int): String = when (mode) {
            MODE_VIDEO_FRONT -> "Video (front)"
            MODE_VIDEO_BACK -> "Video (back)"
            else -> "Audio only"
        }

        fun load(context: Context): AppConfig {
            val p = context.getSharedPreferences("autorecorder_config", Context.MODE_PRIVATE)
            // Migrate legacy "useBackCamera" boolean into recordMode.
            val legacy = if (p.contains("recordMode")) null else p.contains("useBackCamera")
            val recordMode = if (p.contains("recordMode")) {
                p.getInt("recordMode", MODE_VIDEO_FRONT)
            } else if (legacy == true) {
                MODE_VIDEO_BACK
            } else {
                // Fresh install or explicit default -> audio only.
                MODE_AUDIO
            }
            return AppConfig(
                shakeEnabled = p.getBoolean("shakeEnabled", true),
                shakeCount = p.getInt("shakeCount", 3),
                shakeThreshold = p.getFloat("shakeThreshold", 18f),
                shakeCooldownMs = p.getLong("shakeCooldownMs", 150L),
                volumeEnabled = p.getBoolean("volumeEnabled", true),
                customEnabled = p.getBoolean("customEnabled", true),
                recordMode = recordMode,
                maxRecordingMinutes = p.getInt("maxRecordingMinutes", 5),
                keepScreenAwake = p.getBoolean("keepScreenAwake", false)
            )
        }
    }
}