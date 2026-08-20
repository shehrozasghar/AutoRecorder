package com.autorecorder.trigger

import android.content.Context
import androidx.media.session.MediaSessionCompat
import androidx.media.session.PlaybackStateCompat

/**
 * Captures volume key presses in the background via a MediaSession
 * that claims media-button + volume-key handling.
 */
class VolumeKeyReceiver(
    private val context: Context,
    private val onVolumePressed: () -> Unit
) {
    private var session: MediaSessionCompat? = null

    fun start() {
        stop()
        session = MediaSessionCompat(context, "autorecorder_volume").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_VOLUME_KEYS
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0f, 1f)
                    .build()
            )
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
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
        }
    }

    fun stop() {
        session?.release()
        session = null
    }
}