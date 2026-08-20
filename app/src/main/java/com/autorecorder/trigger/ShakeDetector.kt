package com.autorecorder.trigger

import kotlin.math.sqrt

class ShakeDetector(
    var threshold: Float = 18f,
    var countRequired: Int = 3,
    var cooldownMs: Long = 150L
) {
    var onShake: (() -> Unit)? = null

    private var lastPeak = 0L
    private var peaks = 0
    private var lastTrigger = 0L

    fun onAcceleration(x: Float, y: Float, z: Float) {
        val onShake = onShake ?: return
        val mag = sqrt(x * x + y * y + z * z)
        if (mag < threshold) return

        val now = System.currentTimeMillis()
        if (now - lastTrigger < 3000) return
        if (now - lastPeak >= cooldownMs) {
            lastPeak = now
            peaks++
            if (peaks >= countRequired) {
                peaks = 0
                lastTrigger = now
                onShake()
            }
        }
    }

    fun reset() {
        peaks = 0
    }
}