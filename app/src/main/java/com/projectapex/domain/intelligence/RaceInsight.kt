package com.projectapex.domain.intelligence

/**
 * A single, deterministic observation about a [com.projectapex.domain.model.RaceState],
 * produced by [RaceIntelligenceEngine]. The foundation a future AI
 * explanation layer would read rather than raw race state.
 */
data class RaceInsight(
    val id: String,
    val type: InsightType,
    val priority: InsightPriority,
    val title: String,
    val description: String,
    val timestamp: Long
)
