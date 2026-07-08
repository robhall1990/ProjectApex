package com.projectapex.intelligence.ingest

import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameNormaliserTest {

    private val normaliser = FrameNormaliser()

    @Test
    fun `sorts cars by position`() {
        val result = normaliser.normalise(
            frame(1, lap = 5, cars = listOf(car("NOR", 2, 5, 1.5), car("VER", 1, 5, 0.0)))
        )
        assertEquals(listOf("VER", "NOR"), result.cars.map { it.driverId })
    }

    @Test
    fun `derives missing intervals from consecutive leader gaps`() {
        val result = normaliser.normalise(
            frame(
                1, lap = 5,
                cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 1.5), car("HAM", 3, 5, 4.0)),
            )
        )
        assertNull(result.cars[0].interval)                     // leader has no car ahead
        assertEquals(1.5, result.cars[1].interval!!.value, 1e-9)
        assertEquals(2.5, result.cars[2].interval!!.value, 1e-9)
    }

    @Test
    fun `keeps feed-provided intervals untouched`() {
        val result = normaliser.normalise(
            frame(
                1, lap = 5,
                cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 1.5, interval = 1.4)),
            )
        )
        assertEquals(1.4, result.cars[1].interval!!.value, 1e-9)
    }

    @Test
    fun `clamps interval at zero when timing noise crosses gaps`() {
        val result = normaliser.normalise(
            frame(
                1, lap = 5,
                // Behind car reports a marginally smaller leader gap than the car ahead.
                cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 2.0), car("HAM", 3, 5, 1.9)),
            )
        )
        assertEquals(0.0, result.cars[2].interval!!.value, 1e-9)
    }
}
