package com.projectapex.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps [ProcessLifecycleOwner] — a single lifecycle for the whole app
 * process, driven by activity start/stop rather than any one screen — into
 * a plain [StateFlow] so non-UI code ([com.projectapex.domain.livedata.OpenF1LiveDataSource],
 * via [com.projectapex.domain.AppForegroundState]) can react to
 * foreground/background transitions without depending on Android lifecycle
 * types directly.
 *
 * Starts foregrounded: [ProcessLifecycleOwner.get]'s first `onStart` fires
 * essentially immediately on process launch, so defaulting to `true`
 * avoids a spurious "backgrounded" instant before that callback lands.
 */
@Singleton
class AppForegroundMonitor @Inject constructor() : DefaultLifecycleObserver {

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
    }
}
