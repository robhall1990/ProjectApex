package com.projectapex.data.openf1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * DTOs for the public OpenF1 REST API (https://api.openf1.org/v1/,
 * https://openf1.org). Field names/casing follow OpenF1's documented
 * snake_case schema; not verified against a live response from this
 * environment (see [com.projectapex.data.openf1.OpenF1Api] doc comment) —
 * spot-check on-device against a real session before relying on it.
 */
@Serializable
data class SessionDto(
    @SerialName("session_key") val sessionKey: Int,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("session_type") val sessionType: String? = null,
)

@Serializable
data class DriverDto(
    @SerialName("driver_number") val driverNumber: Int,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("broadcast_name") val broadcastName: String? = null,
    @SerialName("team_name") val teamName: String? = null,
)

/** Common shape for the time-series endpoints, so callers can pick the latest record per driver generically. */
interface DatedRecord {
    val date: String
}

@Serializable
data class PositionDto(
    override val date: String,
    @SerialName("driver_number") val driverNumber: Int,
    val position: Int,
) : DatedRecord

/**
 * `gap_to_leader`/`interval` are numeric for a normally-gapped car, a string
 * like `"+1 LAP"` for a lapped car, and null/0 for the leader — hence
 * [JsonElement] rather than a typed number. Use [asGapSeconds] to extract.
 */
@Serializable
data class IntervalDto(
    override val date: String,
    @SerialName("driver_number") val driverNumber: Int,
    @SerialName("gap_to_leader") val gapToLeader: JsonElement? = null,
    val interval: JsonElement? = null,
) : DatedRecord

fun JsonElement?.asGapSeconds(): Double? = (this as? JsonPrimitive)?.doubleOrNull

@Serializable
data class LapDto(
    @SerialName("driver_number") val driverNumber: Int,
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("lap_duration") val lapDuration: Double? = null,
    @SerialName("date_start") val dateStart: String? = null,
)

@Serializable
data class StintDto(
    @SerialName("driver_number") val driverNumber: Int,
    val compound: String,
    @SerialName("stint_number") val stintNumber: Int,
    @SerialName("lap_start") val lapStart: Int,
    @SerialName("lap_end") val lapEnd: Int? = null,
    @SerialName("tyre_age_at_start") val tyreAgeAtStart: Int = 0,
)

@Serializable
data class PitDto(
    @SerialName("driver_number") val driverNumber: Int,
    @SerialName("lap_number") val lapNumber: Int? = null,
    override val date: String,
    @SerialName("pit_duration") val pitDuration: Double? = null,
) : DatedRecord

@Serializable
data class RaceControlDto(
    override val date: String,
    val category: String? = null,
    val flag: String? = null,
    val message: String? = null,
) : DatedRecord
