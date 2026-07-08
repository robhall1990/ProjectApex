package com.projectapex.feature.race

import com.projectapex.intelligence.detect.Severity

/**
 * Presentation model for one race intelligence card (APX-012). Built from a
 * [com.projectapex.intelligence.rank.ScoredObservation] by [ObservationPresenter];
 * carries only what the UI draws. `RaceInsight` (the legacy domain type) is
 * gone — this is its presentation-layer successor.
 */
data class RaceInsightUi(
    val id: String,
    /** Emoji icon keyed off the observation type. */
    val icon: String,
    /** Primary line, e.g. "NOR projected to enter DRS in 2 laps". */
    val headline: String,
    /** Secondary line with supporting numbers, e.g. "closing at 0.4s/lap". */
    val detail: String,
    /** Drives the priority dot colour; sourced from the observation's severity. */
    val severity: Severity,
)
