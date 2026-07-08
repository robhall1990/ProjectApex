package com.projectapex.intelligence.detect.analysis

/**
 * Shared confidence primitives (APX-012) so every detector composes its
 * confidence the same way (docs/RaceIntelligencePlatform.md §11.2). All
 * results are in [0, 1].
 */
object Confidence {

    /** Sample-size factor n/(n+n0): 0 at no data, 0.5 at n0 samples, →1 with more. */
    fun sampleSize(n: Int, n0: Int = 3): Double = n.toDouble() / (n + n0)

    /**
     * How decisively [value] clears [threshold] on the way to [saturation]:
     * 0 at/below threshold, 1 at/above saturation, linear between.
     */
    fun margin(value: Double, threshold: Double, saturation: Double): Double {
        if (saturation == threshold) return if (value >= threshold) 1.0 else 0.0
        return ((value - threshold) / (saturation - threshold)).coerceIn(0.0, 1.0)
    }

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)
}
