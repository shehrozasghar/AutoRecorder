package com.autorecorder.trigger

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState

/**
 * Captures volume key presses in the background via a platform MediaSession
 * that claims media-button + volume-key handling.
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
        s.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_VOLUME_KEYS
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE
                )
                .setState(PlaybackState.STATE_PLAYING, 0f, 1f)
                .build()
        )
        s.setCallback(object : MediaSession.Callback() {
            override fun onAdjustVolume(direction: Int, flags: Int) {
                onVolumePressed()
            }

            override fun onPlay() {
                onVolumePressed()
            }

            override fun onPause() {
                onVolumePressed()
            }
        })
        s.isActive = true
    }

    fun stop() {
        session?.release()
        session = null
    }
}