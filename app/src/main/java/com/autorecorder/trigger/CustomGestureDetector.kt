package com.autorecorder.trigger

import android.content.Context
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Matches live accelerometer magnitude against a user-recorded template
 * using Dynamic Time Warping on normalized, resampled series.
 */
class CustomGestureDetector(private val onMatch: () -> Unit) {

    private var template: FloatArray? = null
    private var threshold = 30f

    private val windowSize = 64
    private val buffer = FloatArray(windowSize)
    private var head = 0
    private var count = 0
    private var sampleCount = 0
    private var lastCheck = 0L
    private var lastTrigger = 0L

    fun load(context: Context) {
        val p = context.getSharedPreferences("autorecorder_config", Context.MODE_PRIVATE)
        val raw = p.getString("templateData", null)
        template = raw?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
        threshold = p.getFloat("templateThreshold", 30f)
    }

    fun hasTemplate(): Boolean = template != null && template!!.size >= 5

    fun onAcceleration(x: Float, y: Float, z: Float) {
        val t = template ?: return
        if (t.size < 5) return
        val mag = sqrt(x * x + y * y + z * z)

        buffer[head] = mag
        head = (head + 1) % windowSize
        count = minOf(count + 1, windowSize)
        sampleCount++

        val now = System.currentTimeMillis()
        if (sampleCount >= t.size && count >= t.size && now - lastCheck >= 500) {
            lastCheck = now
            val candidate = FloatArray(count)
            val start = if (count < windowSize) 0 else head
            for (k in 0 until count) {
                candidate[k] = buffer[(start + k) % windowSize]
            }
            val d = dtw(normalize(resample(t, 48)), normalize(resample(candidate, 48)))
            if (d < threshold && now - lastTrigger > 6000) {
                lastTrigger = now
                onMatch()
            }
        }
    }

    private fun normalize(a: FloatArray): FloatArray {
        val mean = a.average().toFloat()
        val variance = a.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance.toFloat()).coerceAtLeast(1e-3f)
        return FloatArray(a.size) { (a[it] - mean) / std }
    }

    private fun resample(a: FloatArray, n: Int): FloatArray {
        if (a.size == n) return a.copyOf()
        if (a.size < 2) return FloatArray(n) { a[0] }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val idx = (i * (a.size - 1).toFloat()) / (n - 1)
            val lo = idx.toInt()
            val hi = minOf(lo + 1, a.size - 1)
            val frac = idx - lo
            out[i] = a[lo] * (1 - frac) + a[hi] * frac
        }
        return out
    }

    private fun dtw(a: FloatArray, b: FloatArray): Float {
        val n = a.size
        val m = b.size
        val d = Array(n + 1) { DoubleArray(m + 1) { Double.POSITIVE_INFINITY } }
        d[0][0] = 0.0
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(a[i - 1] - b[j - 1]).toDouble()
                d[i][j] = cost + minOf(d[i - 1][j], d[i][j - 1], d[i - 1][j - 1])
            }
        }
        return d[n][m].toFloat()
    }
}