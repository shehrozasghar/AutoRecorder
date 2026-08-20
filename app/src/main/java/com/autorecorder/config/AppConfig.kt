package com.autorecorder.config

import android.content.Context

data class AppConfig(
    val shakeEnabled: Boolean = true,
    val shakeCount: Int = 3,
    val shakeThreshold: Float = 18f,
    val shakeCooldownMs: Long = 150L,
    val volumeEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val voiceKeywords: List<String> = listOf("start recording", "record", "capture"),
    val voiceWindowSeconds: Int = 10,
    val customEnabled: Boolean = true,
    val useBackCamera: Boolean = false,
    val maxRecordingMinutes: Int = 5,
    val keepScreenAwake: Boolean = false
) {
    fun save(context: Context) {
        val p = context.getSharedPreferences("autorecorder_config", Context.MODE_PRIVATE)
        p.edit()
            .putBoolean("shakeEnabled", shakeEnabled)
            .putInt("shakeCount", shakeCount)
            .putFloat("shakeThreshold", shakeThreshold)
            .putLong("shakeCooldownMs", shakeCooldownMs)
            .putBoolean("volumeEnabled", volumeEnabled)
            .putBoolean("voiceEnabled", voiceEnabled)
            .putString("voiceKeywords", voiceKeywords.joinToString("|"))
            .putInt("voiceWindowSeconds", voiceWindowSeconds)
            .putBoolean("customEnabled", customEnabled)
            .putBoolean("useBackCamera", useBackCamera)
            .putInt("maxRecordingMinutes", maxRecordingMinutes)
            .putBoolean("keepScreenAwake", keepScreenAwake)
            .apply()
    }

    companion object {
        fun load(context: Context): AppConfig {
            val p = context.getSharedPreferences("autorecorder_config", Context.MODE_PRIVATE)
            return AppConfig(
                shakeEnabled = p.getBoolean("shakeEnabled", true),
                shakeCount = p.getInt("shakeCount", 3),
                shakeThreshold = p.getFloat("shakeThreshold", 18f),
                shakeCooldownMs = p.getLong("shakeCooldownMs", 150L),
                volumeEnabled = p.getBoolean("volumeEnabled", true),
                voiceEnabled = p.getBoolean("voiceEnabled", true),
                voiceKeywords = p.getString("voiceKeywords", "start recording|record|capture")
                    ?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                voiceWindowSeconds = p.getInt("voiceWindowSeconds", 10),
                customEnabled = p.getBoolean("customEnabled", true),
                useBackCamera = p.getBoolean("useBackCamera", false),
                maxRecordingMinutes = p.getInt("maxRecordingMinutes", 5),
                keepScreenAwake = p.getBoolean("keepScreenAwake", false)
            )
        }
    }
}