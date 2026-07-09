package com.projectapex.desktop

import com.projectapex.data.openf1.OpenF1Api
import com.projectapex.domain.livedata.OpenF1LiveDataSource
import com.projectapex.domain.race.RaceEngine
import com.projectapex.domain.simulation.RaceSimulator
import com.projectapex.feature.race.ObservationPresenter
import com.projectapex.intelligence.adapter.RacePulseEngine
import com.projectapex.intelligence.api.IntelligenceConfig
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val OPEN_F1_BASE_URL = "https://api.openf1.org/v1/"
private const val HTTP_TIMEOUT_SECONDS = 10L

/**
 * Manual composition root for the desktop target - no Hilt (Android-only).
 * Wires exactly the same [RaceEngine] -> [RacePulseEngine] pipeline
 * [com.projectapex.core.di.DomainModule]/[com.projectapex.core.di.NetworkModule]
 * wire on Android, by hand, since the graph is small enough that a DI
 * framework isn't earning its keep here (see docs/Roadmap.md).
 *
 * Desktop has no foreground/background distinction the way a phone does, so
 * [OpenF1LiveDataSource] gets an always-true flow — it never idles.
 */
class AppContainer {

    private val dispatcher = Dispatchers.Default
    private val clock: Clock = Clock.systemUTC()
    private val intelligenceConfig = IntelligenceConfig()
    private val alwaysForeground = MutableStateFlow(true)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(OPEN_F1_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val openF1Api: OpenF1Api = retrofit.create(OpenF1Api::class.java)

    val raceEngine = RaceEngine()
    val raceSimulator = RaceSimulator(raceEngine, dispatcher)
    val liveDataSource = OpenF1LiveDataSource(raceEngine, openF1Api, dispatcher, clock, alwaysForeground)
    val racePulseEngine = RacePulseEngine(raceEngine, dispatcher, intelligenceConfig, clock)
    val observationPresenter = ObservationPresenter()

    /** [RaceSimulator] and [OpenF1LiveDataSource] both drive [raceEngine] - only one at a time. */
    fun startSimulator() {
        liveDataSource.stop()
        raceSimulator.start()
    }

    fun startLiveSession(totalLaps: Int) {
        raceSimulator.stop()
        liveDataSource.start(totalLaps)
    }

    fun stopAll() {
        raceSimulator.stop()
        liveDataSource.stop()
    }
}
