package com.projectapex.domain

import javax.inject.Qualifier

/**
 * Qualifies the process-foreground [kotlinx.coroutines.flow.StateFlow]
 * ([com.projectapex.core.AppForegroundMonitor.isForeground]) so domain
 * services that idle while backgrounded (e.g.
 * [com.projectapex.domain.livedata.OpenF1LiveDataSource]) depend only on
 * this flow, not on the Android-only class that produces it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppForegroundState
