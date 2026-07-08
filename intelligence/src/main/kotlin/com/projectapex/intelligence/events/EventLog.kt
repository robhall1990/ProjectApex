package com.projectapex.intelligence.events

/**
 * Bounded, append-only record of every derived event. Feeds the FeatureView's
 * `events(sinceLap)` query today and the fact-retrieval layer ("why did HAM
 * pit?") in a later ticket.
 */
class EventLog(private val capacity: Int = 4096) {

    private val entries = ArrayDeque<EngineEvent>()

    fun append(events: List<EngineEvent>) {
        events.forEach { event ->
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(event)
        }
    }

    fun since(lap: Int): List<EngineEvent> = entries.filter { it.atLap >= lap }

    fun all(): List<EngineEvent> = entries.toList()
}
