package com.projectapex.intelligence.features

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.ingest.IngestPipeline
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficProjectorTest {

    private val config = IntelligenceConfig()
    private val projector = TrafficProjector(config)

    private fun viewWithGaps(vararg gaps: Pair<String, Double>): FeatureView {
        val pipeline = IngestPipeline(config)
        pipeline.submit(
            frame(
                1, lap = 20,
                cars = gaps.mapIndexed { index, (id, gap) -> car(id, index + 1, 20, gap) },
            )
        )
        return pipeline.view
    }

    @Test
    fun `leader pitting under green rejoins between the right cars`() {
        // No lap data yet → every car advances by the same fallback lap time,
        // so only the gaps and the pit loss decide the rejoin order.
        // VER 0s, NOR +10s, HAM +30s; pit loss 22s → VER rejoins P2, 12s behind NOR.
        val view = viewWithGaps("VER" to 0.0, "NOR" to 10.0, "HAM" to 30.0)

        val rejoin = projector.rejoin("VER", lapsUntilPit = 0, view = view)!!
        assertEquals(2, rejoin.position)
        assertEquals(12.0, rejoin.intervalAhead!!.value, 1e-9)
        assertEquals(8.0, rejoin.intervalBehind!!.value, 1e-9)
        assertFalse(rejoin.dirtyAir)
    }

    @Test
    fun `rejoining just behind another car is dirty air`() {
        // VER pits: 0 + 22 = 22s effective; C sits 21s back → VER rejoins 1s behind C.
        val view = viewWithGaps("VER" to 0.0, "NOR" to 10.0, "C" to 21.0)

        val rejoin = projector.rejoin("VER", lapsUntilPit = 0, view = view)!!
        assertEquals(3, rejoin.position)
        assertEquals(1.0, rejoin.intervalAhead!!.value, 1e-9)
        assertTrue(rejoin.dirtyAir)
    }

    @Test
    fun `staying out keeps a big enough cushion clean`() {
        // NOR pits from P2 with a 40s gap behind: still P2 after the 22s loss.
        val view = viewWithGaps("VER" to 0.0, "NOR" to 10.0, "HAM" to 50.0)

        val rejoin = projector.rejoin("NOR", lapsUntilPit = 0, view = view)!!
        assertEquals(2, rejoin.position)
        assertFalse(rejoin.dirtyAir)
    }
}
