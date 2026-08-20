package com.autorecorder.trigger

import android.content.Context
import android.media.VolumeProvider
import android.media.session.MediaSession

/**
 * Captures volume key presses in the background by routing them through a
 * remote VolumeProvider on a MediaSession. Works from a service and with the
 * screen off. Each physical press fires onAdjustVolume(+1/-1) then (0) on
 * release; the engine's debounce logic folds them into a single trigger.
 */
class VolumeKeyReceiver(
    private val context: Context,
    private val onVolumePressed: () -> Unit
) {
    private var session: MediaSession? = null

    fun start() {
        stop()
        val s = MediaSession(context, "autorecorder_volume")
        session = s
        val provider = object : VolumeProvider(
            VolumeProvider.VOLUME_CONTROL_RELATIVE,
            100,
            50
        ) {
            override fun onAdjustVolume(direction: Int) {
                onVolumePressed()
            }
        }
        s.setPlaybackToRemote(provider)
        s.isActive = true
    }

    fun stop() {
        session?.release()
        session = null
    }
}