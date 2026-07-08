package com.projectapex.intelligence.features

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.events.EngineEvent
import com.projectapex.intelligence.events.EventLog
import com.projectapex.intelligence.ingest.CarTiming
import com.projectapex.intelligence.ingest.Seconds
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.ingest.TrackStatus
import kotlin.math.abs

/**
 * The single owner of all derived race history
 * (docs/RaceIntelligencePlatform.md §3.2, §5.1): lap book, stint book, gap
 * history, pit log, position log. One writer (the pipeline); everyone else
 * reads through [FeatureView]. All buffers are bounded by race length —
 * a few MB worst case, no eviction needed within a session.
 */
class FeatureStore(private val config: IntelligenceConfig) : FeatureView {

    private var latestFrame: TimingFrame? = null

    private val lapBooks = mutableMapOf<String, MutableList<LapRecord>>()
    private val stintBooks = mutableMapOf<String, MutableList<Stint>>()
    private val gapHistories = mutableMapOf<String, MutableList<GapSample>>()
    private val positionLogs = mutableMapOf<String, MutableList<PositionSample>>()
    private val pitLog = mutableListOf<PitStop>()
    private val eventLog = EventLog()

    /** Track statuses seen since each driver's last lap completion → lap flags. */
    private val statusSinceLap = mutableMapOf<String, MutableSet<TrackStatus>>()
    private val pendingInLap = mutableSetOf<String>()
    private val pendingOutLap = mutableSetOf<String>()
    /** Last tyre age seen per driver — an age going backwards means a fresh set. */
    private val lastSeenTyreAge = mutableMapOf<String, Int>()

    /** Memoised model fits, invalidated by key (driver, data size). */
    private val paceCache = mutableMapOf<Triple<String, Int, Int>, PaceEstimate?>()
    private val degCache = mutableMapOf<Triple<String, Int, Int>, DegFit?>()

    // ── Update (single writer) ──────────────────────────────────────────────

    fun update(frame: TimingFrame, events: List<EngineEvent>) {
        val isFirstFrame = latestFrame == null
        latestFrame = frame
        eventLog.append(events)

        // Drivers whose stint change is owned by a pit event in this batch —
        // the tyre-mismatch fallback below must not double-handle them.
        val pitExitDrivers = events.filterIsInstance<EngineEvent.PitExit>()
            .map { it.driverId }.toSet()

        frame.cars.forEach { car ->
            statusSinceLap.getOrPut(car.driverId) { mutableSetOf() }.add(frame.trackStatus)
            if (isFirstFrame) {
                openStint(car, startLap = car.lapsCompleted)
                positionLogs.getOrPut(car.driverId) { mutableListOf() }
                    .add(PositionSample(frame.lap, car.position))
            } else if (car.driverId !in pitExitDrivers) {
                // Tyre change without pit-status data (today's simulator has no
                // pit lane model): a compound switch or an age going backwards
                // still closes the stint. Runs before lap recording so a
                // boundary lap lands in the stint whose tyres it was driven on.
                val stint = stintBooks[car.driverId]?.lastOrNull()
                val ageWentBackwards = car.tyre.ageLaps < (lastSeenTyreAge[car.driverId] ?: 0)
                if (stint != null && stint.endLap == null &&
                    (stint.compound != car.tyre.compound || ageWentBackwards)
                ) {
                    closeStint(car.driverId, atLap = car.lapsCompleted)
                    openStint(car, startLap = car.lapsCompleted)
                }
            }
            lastSeenTyreAge[car.driverId] = car.tyre.ageLaps
        }

        events.forEach { event ->
            when (event) {
                is EngineEvent.PitEntry -> {
                    pendingInLap.add(event.driverId)
                    pitLog.add(
                        PitStop(event.driverId, lapIn = event.atLap, lapOut = null,
                            tyreAfterAgeLaps = null, compoundAfter = null)
                    )
                }
                is EngineEvent.PitExit -> {
                    pendingOutLap.add(event.driverId)
                    val index = pitLog.indexOfLast { it.driverId == event.driverId && it.lapOut == null }
                    if (index >= 0) {
                        pitLog[index] = pitLog[index].copy(
                            lapOut = event.atLap,
                            tyreAfterAgeLaps = event.tyre.ageLaps,
                            compoundAfter = event.tyre.compound,
                        )
                    }
                    closeStint(event.driverId, atLap = event.atLap)
                    frame.cars.find { it.driverId == event.driverId }
                        ?.let { openStint(it, startLap = it.lapsCompleted) }
                }
                is EngineEvent.LapCompleted -> recordLap(event, frame)
                is EngineEvent.PositionChange ->
                    positionLogs.getOrPut(event.driverId) { mutableListOf() }
                        .add(PositionSample(event.atLap, event.to))
                else -> Unit
            }
        }
    }

    private fun recordLap(event: EngineEvent.LapCompleted, frame: TimingFrame) {
        val car = frame.cars.find { it.driverId == event.driverId } ?: return
        val flags = mutableSetOf<LapFlag>()

        val statuses = statusSinceLap.getOrPut(event.driverId) { mutableSetOf() }
        if (TrackStatus.SC in statuses) flags += LapFlag.SC_LAP
        if (TrackStatus.VSC in statuses) flags += LapFlag.VSC_LAP
        if (TrackStatus.YELLOW in statuses) flags += LapFlag.YELLOW
        statuses.clear()
        statuses.add(frame.trackStatus)

        if (pendingInLap.remove(event.driverId)) flags += LapFlag.IN_LAP
        if (pendingOutLap.remove(event.driverId)) flags += LapFlag.OUT_LAP

        val raw = event.lapTime?.value
        val corrected = raw?.let { FuelModel.corrected(it, event.lapNumber, frame.totalLaps, config) }

        // Robust outlier flag (§9.1): compare against the median of the recent
        // clean laps; MAD-scaled threshold with a floor for near-noiseless data.
        if (raw != null && flags.isEmpty()) {
            val recentClean = lapBooks[event.driverId].orEmpty()
                .filter { it.isClean }
                .takeLast(config.paceWindow)
                .mapNotNull { it.fuelCorrectedSeconds }
            if (recentClean.size >= 3) {
                val threshold = maxOf(
                    config.outlierMadMultiplier * mad(recentClean),
                    config.outlierFloorSeconds,
                )
                if (abs(corrected!! - median(recentClean)) > threshold) flags += LapFlag.OUTLIER
            }
        }

        val record = LapRecord(
            lap = event.lapNumber,
            timeSeconds = raw,
            fuelCorrectedSeconds = corrected,
            tyre = car.tyre,
            flags = flags,
        )
        lapBooks.getOrPut(event.driverId) { mutableListOf() }.add(record)

        val stints = stintBooks.getOrPut(event.driverId) { mutableListOf() }
        val current = stints.lastOrNull()
        if (current != null && current.endLap == null) {
            stints[stints.lastIndex] = current.copy(laps = current.laps + record)
        }

        car.gapToLeader?.let { gap ->
            gapHistories.getOrPut(event.driverId) { mutableListOf() }
                .add(GapSample(event.lapNumber, gap))
        }
    }

    private fun openStint(car: CarTiming, startLap: Int) {
        stintBooks.getOrPut(car.driverId) { mutableListOf() }.add(
            Stint(
                compound = car.tyre.compound,
                startLap = startLap,
                startAgeLaps = car.tyre.ageLaps,
                laps = emptyList(),
            )
        )
    }

    private fun closeStint(driverId: String, atLap: Int) {
        val stints = stintBooks[driverId] ?: return
        val current = stints.lastOrNull() ?: return
        if (current.endLap == null) stints[stints.lastIndex] = current.copy(endLap = atLap)
    }

    // ── FeatureView ─────────────────────────────────────────────────────────

    override val lap: Int get() = latestFrame?.lap ?: 0
    override val totalLaps: Int get() = latestFrame?.totalLaps ?: 0
    override val trackStatus: TrackStatus get() = latestFrame?.trackStatus ?: TrackStatus.GREEN
    override val runningOrder: List<CarTiming> get() = latestFrame?.cars.orEmpty()

    override fun laps(driverId: String): List<LapRecord> = lapBooks[driverId].orEmpty()

    override fun currentStint(driverId: String): Stint? =
        stintBooks[driverId]?.lastOrNull()?.takeIf { it.endLap == null }

    override fun stints(driverId: String): List<Stint> = stintBooks[driverId].orEmpty()

    override fun interval(aheadId: String, behindId: String): Seconds? {
        val cars = latestFrame?.cars ?: return null
        val ahead = cars.find { it.driverId == aheadId }?.gapToLeader ?: return null
        val behind = cars.find { it.driverId == behindId }?.gapToLeader ?: return null
        return Seconds(behind.value - ahead.value)
    }

    override fun intervalHistory(aheadId: String, behindId: String, lastNLaps: Int): List<GapPoint> {
        val aheadByLap = gapHistories[aheadId].orEmpty().associateBy { it.lap }
        return gapHistories[behindId].orEmpty()
            .mapNotNull { behind ->
                aheadByLap[behind.lap]?.let { ahead ->
                    GapPoint(behind.lap, Seconds(behind.gapToLeader.value - ahead.gapToLeader.value))
                }
            }
            .takeLast(lastNLaps)
    }

    override fun pace(driverId: String, window: Int): PaceEstimate? {
        val effectiveWindow = if (window > 0) window else config.paceWindow
        val book = lapBooks[driverId].orEmpty()
        return paceCache.getOrPut(Triple(driverId, book.size, effectiveWindow)) {
            val clean = book.filter { it.isClean }.takeLast(effectiveWindow)
            if (clean.size < 2) return@getOrPut null
            val points = clean.mapIndexed { i, r -> i.toDouble() to r.fuelCorrectedSeconds!! }
            val ols = olsFit(points) ?: return@getOrPut null
            PaceEstimate(
                meanSeconds = clean.sumOf { it.fuelCorrectedSeconds!! } / clean.size,
                slopePerLap = ols.slope,
                sigma = ols.sigma,
                n = clean.size,
            )
        }
    }

    override fun degFit(driverId: String): DegFit? {
        val stint = currentStint(driverId) ?: return null
        return degCache.getOrPut(Triple(driverId, stintBooks[driverId]!!.size, stint.laps.size)) {
            DegModel.fit(stint, config)
        }
    }

    override fun pitLoss(status: TrackStatus): Seconds = PitLossModel.pitLoss(status, config)

    override fun cumulativeTime(driverId: String): Seconds? =
        latestFrame?.cars?.find { it.driverId == driverId }?.gapToLeader

    override fun pitStops(driverId: String): List<PitStop> = pitLog.filter { it.driverId == driverId }

    override fun positionHistory(driverId: String): List<PositionSample> =
        positionLogs[driverId].orEmpty()

    override fun events(sinceLap: Int): List<EngineEvent> = eventLog.since(sinceLap)
}
