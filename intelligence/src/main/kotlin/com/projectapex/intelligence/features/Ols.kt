package com.projectapex.intelligence.features

import kotlin.math.sqrt

/**
 * Ordinary least squares over (x, y) points — the one regression primitive
 * behind both the pace model (§9.1) and the deg model (§9.2), so every
 * consumer agrees on σ and R² semantics.
 */
internal data class OlsFit(
    val intercept: Double,
    val slope: Double,
    /** Residual std dev, √(SSE/(n−2)); 0.0 when n ≤ 2. */
    val sigma: Double,
    /** 1 − SSE/SST; defined as 1.0 when the ys are constant (SST = 0). */
    val r2: Double,
    val n: Int,
)

internal fun olsFit(points: List<Pair<Double, Double>>): OlsFit? {
    val n = points.size
    if (n < 2) return null

    val meanX = points.sumOf { it.first } / n
    val meanY = points.sumOf { it.second } / n
    val sxx = points.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
    if (sxx == 0.0) return null // vertical line — no fit

    val sxy = points.sumOf { (x, y) -> (x - meanX) * (y - meanY) }
    val slope = sxy / sxx
    val intercept = meanY - slope * meanX

    val sse = points.sumOf { (x, y) -> (y - (intercept + slope * x)).let { it * it } }
    val sst = points.sumOf { (_, y) -> (y - meanY) * (y - meanY) }
    val sigma = if (n > 2) sqrt(sse / (n - 2)) else 0.0
    val r2 = if (sst == 0.0) 1.0 else 1.0 - sse / sst

    return OlsFit(intercept, slope, sigma, r2, n)
}

/** Median of a non-empty list. */
internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/** Median absolute deviation around the median. */
internal fun mad(values: List<Double>): Double {
    val m = median(values)
    return median(values.map { kotlin.math.abs(it - m) })
}
