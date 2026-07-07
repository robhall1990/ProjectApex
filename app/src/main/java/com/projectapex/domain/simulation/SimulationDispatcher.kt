package com.projectapex.domain.simulation

import javax.inject.Qualifier

/**
 * Qualifies the [kotlinx.coroutines.CoroutineDispatcher] [RaceSimulator]'s
 * background tick loop runs on, so tests can substitute a test dispatcher
 * bound to a virtual-time scheduler instead of the real one Hilt provides.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SimulationDispatcher
