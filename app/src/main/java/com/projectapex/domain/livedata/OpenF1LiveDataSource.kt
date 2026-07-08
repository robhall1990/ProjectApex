package com.projectapex.domain.livedata

import com.projectapex.data.openf1.DriverDto
import com.projectapex.data.openf1.OpenF1Api
import com.projectapex.domain.DefaultDispatcher
import com.projectapex.domain.race.RaceEngine
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Live : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

/**
 * Polls the public OpenF1 API (`:data.openf1`) and drives [RaceEngine] with
 * real race data, mirroring [com.projectapex.domain.simulation.RaceSimulator]'s
 * shape so [com.projectapex.feature.settings.SettingsViewModel] can treat
 * both the same way (mutual exclusion is enforced there, not here).
 *
 * Resolves `session_key=latest` and the driver roster once at [start], then
 * polls the rest of the endpoints on a fixed interval. A poll failure never
 * touches [RaceEngine] — the last good [com.projectapex.domain.model.RaceState]
 * stays on screen — and applies capped exponential backoff before the next
 * attempt, surfaced via [connectionStatus].
 */
@Singleton
class OpenF1LiveDataSource @Inject constructor(
    private val raceEngine: RaceEngine,
    private val api: OpenF1Api,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: Clock,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var tickJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    fun start(totalLaps: Int) {
        if (_isRunning.value) return
        _isRunning.value = true
        _connectionStatus.value = ConnectionStatus.Connecting

        tickJob = scope.launch {
            var sessionKey: Int? = null
            var drivers: List<DriverDto> = emptyList()
            var failures = 0

            while (isActive) {
                try {
                    if (sessionKey == null) {
                        val key = api.getSessions().firstOrNull()?.sessionKey
                            ?: error("No current OpenF1 session")
                        drivers = api.getDrivers(key)
                        sessionKey = key
                    }
                    val key = sessionKey

                    val state = coroutineScope {
                        val positions = async { api.getPositions(key) }
                        val intervals = async { api.getIntervals(key) }
                        val laps = async { api.getLaps(key) }
                        val stints = async { api.getStints(key) }
                        val pitStops = async { api.getPitStops(key) }
                        val raceControl = async { api.getRaceControl(key) }

                        OpenF1RaceStateMapper.map(
                            drivers = drivers,
                            positions = positions.await(),
                            intervals = intervals.await(),
                            laps = laps.await(),
                            stints = stints.await(),
                            pitStops = pitStops.await(),
                            raceControl = raceControl.await(),
                            totalLapsOverride = totalLaps,
                            previousState = raceEngine.state.value,
                            now = Instant.now(clock),
                        )
                    }

                    raceEngine.updateState(state)
                    _connectionStatus.value = ConnectionStatus.Live
                    failures = 0
                    delay(POLL_INTERVAL_MS)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    failures++
                    _connectionStatus.value = ConnectionStatus.Error(t.message ?: t.toString())
                    delay((POLL_INTERVAL_MS * (1L shl failures.coerceAtMost(4))).coerceAtMost(MAX_BACKOFF_MS))
                }
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _isRunning.value = false
        _connectionStatus.value = ConnectionStatus.Idle
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
