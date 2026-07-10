package com.projectapex.data.openf1

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit client for the public OpenF1 API (https://openf1.org). No API key
 * required. Unauthenticated/undocumented rate limits — callers should poll
 * conservatively (see [com.projectapex.domain.livedata.OpenF1LiveDataSource]).
 *
 * This environment's egress policy blocks api.openf1.org, so this interface
 * and [OpenF1Dtos] were written from documented/training knowledge of
 * OpenF1's schema rather than a live-verified response. Spot-check field
 * names on-device against a real session before race day.
 *
 * `/position`, `/intervals` and `/laps` grow monotonically through a race,
 * so callers pass [after] (a `date>` cursor, see below) on every poll but
 * the first. `/stints`, `/pit` and `/race_control` stay bounded and are
 * always fetched in full.
 */
interface OpenF1Api {

    @GET("sessions")
    suspend fun getSessions(@Query("session_key") sessionKey: String = "latest"): List<SessionDto>

    @GET("drivers")
    suspend fun getDrivers(@Query("session_key") sessionKey: Int): List<DriverDto>

    /**
     * [after], when non-null, is sent as `date>` — OpenF1 encodes the
     * comparison operator in the query *name* itself rather than as an
     * operator value, so `@Query(value = "date>", encoded = true)` is used
     * instead of a conventional `@Query("date")`. Unverified against a live
     * response from this sandbox (network-restricted); confirm on-device
     * before relying on it (see [com.projectapex.domain.livedata.OpenF1LiveDataSource]
     * doc).
     */
    @GET("position")
    suspend fun getPositions(
        @Query("session_key") sessionKey: Int,
        @Query(value = "date>", encoded = true) after: String? = null,
    ): List<PositionDto>

    @GET("intervals")
    suspend fun getIntervals(
        @Query("session_key") sessionKey: Int,
        @Query(value = "date>", encoded = true) after: String? = null,
    ): List<IntervalDto>

    @GET("laps")
    suspend fun getLaps(
        @Query("session_key") sessionKey: Int,
        @Query(value = "date>", encoded = true) after: String? = null,
    ): List<LapDto>

    @GET("stints")
    suspend fun getStints(@Query("session_key") sessionKey: Int): List<StintDto>

    @GET("pit")
    suspend fun getPitStops(@Query("session_key") sessionKey: Int): List<PitDto>

    @GET("race_control")
    suspend fun getRaceControl(@Query("session_key") sessionKey: Int): List<RaceControlDto>
}
