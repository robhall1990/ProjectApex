package com.projectapex.intelligence.features

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.ingest.TyreCompound
import com.projectapex.intelligence.ingest.TyreFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DegModelTest {

    private val config = IntelligenceConfig()

    private fun stintWith(lapTimes: List<Double>, compound: TyreCompound = TyreCompound.MEDIUM): Stint {
        val laps = lapTimes.mapIndexed { index, corrected ->
            LapRecord(
                lap = index + 1,
                timeSeconds = corrected, // raw irrelevant here; fits read fuelCorrected
                fuelCorrectedSeconds = corrected,
                tyre = TyreFit(compound, ageLaps = index + 1),
                flags = emptySet(),
            )
        }
        return Stint(compound = compound, startLap = 1, startAgeLaps = 0, laps = laps)
    }

    @Test
    fun `recovers an exact linear degradation rate`() {
        // t(age) = 90.0 + 0.08 * age, noise-free.
        val stint = stintWith((1..8).map { 90.0 + 0.08 * it })

        val fit = DegModel.fit(stint, config)!!
        assertEquals(90.0, fit.baseSeconds, 1e-9)
        assertEquals(0.08, fit.ratePerLap, 1e-9)
        assertEquals(0.0, fit.sigma, 1e-9)
        assertEquals(1.0, fit.r2, 1e-9)
        assertFalse(fit.cliffDetected)
    }

    @Test
    fun `returns null until the stint has enough clean laps`() {
        val stint = stintWith((1..3).map { 90.0 + 0.08 * it })
        assertNull(DegModel.fit(stint, config))
    }

    @Test
    fun `flagged laps are excluded from the fit`() {
        val clean = (1..6).map { 90.0 + 0.05 * it }
        val laps = stintWith(clean).laps + LapRecord(
            lap = 7, timeSeconds = 130.0, fuelCorrectedSeconds = 130.0,
            tyre = TyreFit(TyreCompound.MEDIUM, 7), flags = setOf(LapFlag.SC_LAP),
        )
        val fit = DegModel.fit(Stint(TyreCompound.MEDIUM, 1, 0, laps), config)!!

        assertEquals(0.05, fit.ratePerLap, 1e-9) // the SC lap didn't poison the slope
    }

    @Test
    fun `three escalating residuals above threshold detect the cliff`() {
        // Linear phase then a sharp break upward — each cliff lap worse than the last.
        val linear = (1..10).map { 90.0 + 0.05 * it }
        val cliff = listOf(92.0, 93.0, 94.5)
        val all = linear + cliff
        val stint = stintWith(all)

        val fit = DegModel.fit(stint, config)!!
        assertTrue(fit.cliffDetected)
        // The reported fit describes the linear phase, not a line tilted by the cliff laps.
        assertEquals(0.05, fit.ratePerLap, 1e-9)
    }

    @Test
    fun `cliff prediction scales the prior life by observed vs prior rate`() {
        // MEDIUM prior: rate 0.05, life 28. Observed rate 0.10 (double) → life ≈ 14.
        val stint = stintWith((1..8).map { 90.0 + 0.10 * it })
        val fit = DegModel.fit(stint, config)!!

        val predicted = DegModel.predictCliffAge(fit, TyreCompound.MEDIUM, currentAgeLaps = 8, config = config)
        assertEquals(14.0, predicted, 0.01)
    }

    @Test
    fun `cliff prediction clamps to now and to 150 percent of prior life`() {
        // Near-zero observed rate would predict an absurd life; clamp at 1.5 × 28 = 42.
        val gentle = stintWith((1..8).map { 90.0 + 0.001 * it })
        val gentleFit = DegModel.fit(gentle, config)!!
        assertEquals(
            42.0,
            DegModel.predictCliffAge(gentleFit, TyreCompound.MEDIUM, 8, config),
            1e-9,
        )

        // Brutal observed rate can't predict a cliff in the past: clamps to current age.
        val brutal = stintWith((1..8).map { 90.0 + 0.50 * it })
        val brutalFit = DegModel.fit(brutal, config)!!
        assertEquals(
            8.0,
            DegModel.predictCliffAge(brutalFit, TyreCompound.MEDIUM, 8, config),
            1e-9,
        )
    }
}
