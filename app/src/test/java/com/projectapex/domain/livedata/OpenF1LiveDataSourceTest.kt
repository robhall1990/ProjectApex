package com.projectapex.domain.livedata

import com.projectapex.data.openf1.DriverDto
import com.projectapex.data.openf1.IntervalDto
import com.projectapex.data.openf1.LapDto
import com.projectapex.data.openf1.OpenF1Api
import com.projectapex.data.openf1.PitDto
import com.projectapex.data.openf1.PositionDto
import com.projectapex.data.openf1.RaceControlDto
import com.projectapex.data.openf1.SessionDto
import com.projectapex.data.openf1.StintDto
import com.projectapex.domain.race.RaceEngine
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenF1LiveDataSourceTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-07-11T14:00:05Z"), ZoneOffset.UTC)

    private class FakeOpenF1Api(
        private val sessionKey: Int = 42,
        var shouldFail: Boolean = false,
    ) : OpenF1Api {
        var pollCount = 0

        override suspend fun getSessions(sessionKey: String): List<SessionDto> =
            listOf(SessionDto(sessionKey = this.sessionKey, sessionName = "Race", sessionType = "Race"))

        override suspend fun getDrivers(sessionKey: Int): List<DriverDto> =
            listOf(DriverDto(driverNumber = 1, fullName = "Max Verstappen", broadcastName = "VER", teamName = "Red Bull Racing"))

        override suspend fun getPositions(sessionKey: Int): List<PositionDto> {
            if (shouldFail) throw IOException("network down")
            pollCount++
            return listOf(PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, position = 1))
        }

        override suspend fun getIntervals(sessionKey: Int): List<IntervalDto> =
            listOf(IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, gapToLeader = JsonPrimitive(0.0)))

        override suspend fun getLaps(sessionKey: Int): List<LapDto> =
            listOf(LapDto(driverNumber = 1, lapNumber = 3))

        override suspend fun getStints(sessionKey: Int): List<StintDto> = emptyList()

        override suspend fun getPitStops(sessionKey: Int): List<PitDto> = emptyList()

        override suspend fun getRaceControl(sessionKey: Int): List<RaceControlDto> = emptyList()
    }

    @Test
    fun `starts, resolves the session, and updates the race engine with live data`() = runTest {
        val engine = RaceEngine()
        val api = FakeOpenF1Api()
        val source = OpenF1LiveDataSource(engine, api, StandardTestDispatcher(testScheduler), fixedClock)

        source.start(totalLaps = 58)
        runCurrent()

        assertTrue(source.isRunning.value)
        assertEquals(ConnectionStatus.Live, source.connectionStatus.value)
        assertEquals(1, engine.state.value.cars.size)
        assertEquals("1", engine.state.value.cars.single().driver.id)
        assertEquals(58, engine.state.value.totalLaps)
        assertEquals(1, api.pollCount)

        source.stop()
    }

    @Test
    fun `a failed poll keeps the last good state and surfaces an error status`() = runTest {
        val engine = RaceEngine()
        val api = FakeOpenF1Api()
        val source = OpenF1LiveDataSource(engine, api, StandardTestDispatcher(testScheduler), fixedClock)

        source.start(totalLaps = 58)
        runCurrent()
        val goodState = engine.state.value
        assertEquals(1, goodState.cars.size)

        api.shouldFail = true
        advanceTimeBy(5_100)
        runCurrent()

        assertEquals(goodState, engine.state.value)
        assertTrue(source.connectionStatus.value is ConnectionStatus.Error)

        source.stop()
    }

    @Test
    fun `stop halts polling and further engine updates`() = runTest {
        val engine = RaceEngine()
        val api = FakeOpenF1Api()
        val source = OpenF1LiveDataSource(engine, api, StandardTestDispatcher(testScheduler), fixedClock)

        source.start(totalLaps = 58)
        runCurrent()
        source.stop()
        val stateAtStop = engine.state.value

        advanceTimeBy(20_000)
        runCurrent()

        assertFalse(source.isRunning.value)
        assertEquals(ConnectionStatus.Idle, source.connectionStatus.value)
        assertEquals(stateAtStop, engine.state.value)
    }
}
