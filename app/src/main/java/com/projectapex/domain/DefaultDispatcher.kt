package com.projectapex.domain

import javax.inject.Qualifier

/**
 * Qualifies the general-purpose background [kotlinx.coroutines.CoroutineDispatcher]
 * used by domain services with a long-running internal coroutine (e.g.
 * [com.projectapex.domain.simulation.RaceSimulator]'s tick loop,
 * [com.projectapex.domain.timeline.RaceTimeline]'s snapshot-recording
 * subscription), so tests can substitute a test dispatcher bound to a
 * virtual-time scheduler instead of the real one Hilt provides.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
