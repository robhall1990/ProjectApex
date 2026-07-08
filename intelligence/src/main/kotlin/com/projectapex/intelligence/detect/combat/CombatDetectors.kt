package com.projectapex.intelligence.detect.combat

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.Detector

/**
 * The combat detector family (APX-012), assembled in one place. The app's
 * orchestrator registers these with a `DetectorEngine`; the engine itself
 * knows none of them by name (registration-only extensibility). Adding a
 * detector to the family is one line here.
 */
object CombatDetectors {
    fun all(config: IntelligenceConfig): List<Detector> = listOf(
        BattleDetector(config),
        GapClosingDetector(config),
        GapIncreasingDetector(config),
        DrsActiveDetector(config),
        DrsImminentDetector(config),
        LeaderPressureDetector(config),
        FastestPaceDetector(config),
        TyreConcernDetector(config),
    )
}
