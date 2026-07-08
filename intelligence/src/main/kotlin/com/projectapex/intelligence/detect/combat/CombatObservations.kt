package com.projectapex.intelligence.detect.combat

/**
 * The observation type tags and metadata keys the combat family produces
 * (APX-012). Shared here so detectors and the app's presentation mapper agree
 * on the exact strings — no drift across the module boundary. Types are plain
 * strings because [com.projectapex.intelligence.detect.ObservationType] is an
 * open value class (registration-only extensibility).
 */
object CombatTypes {
    const val BATTLE = "battle"
    const val DRS_ACTIVE = "drs_active"
    const val DRS_IMMINENT = "drs_imminent"
    const val GAP_CLOSING = "gap_closing"
    const val GAP_INCREASING = "gap_increasing"
    const val LEADER_PRESSURE = "leader_pressure"
    const val FASTEST_PACE = "fastest_pace"
    const val TYRE_CONCERN = "tyre_concern"
    const val TYRE_CLIFF = "tyre_cliff"
}

/** Metadata keys. All values are `Double` (the observation metadata contract). */
object CombatKeys {
    /** Current interval between the pair, seconds. */
    const val GAP_S = "gap_s"
    /** Projected laps until an event (DRS entry, tyre cliff). */
    const val ETA_LAPS = "eta_laps"
    /** Signed pairwise interval trend, s/lap (negative = closing). */
    const val RATE_S_PER_LAP = "rate_s_per_lap"
    /** How much faster the pace leader is than the race leader, s/lap. */
    const val PACE_MARGIN_S = "pace_margin_s"
    /** A driver's current classified position. */
    const val POSITION = "position"
    /** Tyre age in laps. */
    const val TYRE_AGE_LAPS = "tyre_age_laps"
    /** Fitted degradation rate, s/lap. */
    const val DEG_RATE_S_PER_LAP = "deg_rate_s_per_lap"
    /** 1.0 if a trend is sustained over the full window, else 0.0. */
    const val SUSTAINED = "sustained"
}
