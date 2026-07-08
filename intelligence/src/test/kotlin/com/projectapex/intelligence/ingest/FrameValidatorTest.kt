package com.projectapex.intelligence.ingest

import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameValidatorTest {

    private val validator = FrameValidator()

    @Test
    fun `accepts a well-formed frame`() {
        val result = validator.validate(
            frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 1.5)))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rejects positions that are not a permutation`() {
        val result = validator.validate(
            frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 3, 5, 1.5)))
        )
        assertTrue(result.any { it is FrameValidator.Violation.PositionsNotPermutation })
    }

    @Test
    fun `rejects negative gaps`() {
        val result = validator.validate(
            frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, -0.3)))
        )
        assertTrue(result.any { it is FrameValidator.Violation.NegativeGap })
    }

    @Test
    fun `rejects duplicate drivers`() {
        val result = validator.validate(
            frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("VER", 2, 5, 1.0)))
        )
        assertTrue(result.any { it is FrameValidator.Violation.DuplicateDriver })
    }

    @Test
    fun `rejects stale and duplicate sequences but not the first valid frame`() {
        assertTrue(validator.validate(frame(5, lap = 1, cars = listOf(car("VER", 1, 1, 0.0)))).isEmpty())

        val duplicate = validator.validate(frame(5, lap = 1, cars = listOf(car("VER", 1, 1, 0.0))))
        assertEquals(1, duplicate.size)
        assertTrue(duplicate.single() is FrameValidator.Violation.StaleSequence)

        val stale = validator.validate(frame(3, lap = 1, cars = listOf(car("VER", 1, 1, 0.0))))
        assertTrue(stale.any { it is FrameValidator.Violation.StaleSequence })
    }

    @Test
    fun `a rejected frame does not advance the accepted sequence`() {
        // Rejected for empty grid at sequence 10...
        assertTrue(validator.validate(frame(10, lap = 1, cars = emptyList())).isNotEmpty())
        // ...so sequence 9 is still acceptable afterwards.
        assertTrue(validator.validate(frame(9, lap = 1, cars = listOf(car("VER", 1, 1, 0.0)))).isEmpty())
    }
}
