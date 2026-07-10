package com.projectapex.core.di

import com.projectapex.core.AppForegroundMonitor
import com.projectapex.domain.AppForegroundState
import com.projectapex.domain.DefaultDispatcher
import com.projectapex.intelligence.api.IntelligenceConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import javax.inject.Singleton

/**
 * Hilt bindings for domain- and intelligence-layer types that can't be
 * constructor-injected directly (e.g. [CoroutineDispatcher] and [Clock],
 * neither of which has an injectable constructor, and the pure-Kotlin
 * [IntelligenceConfig], which knows nothing of Hilt). Kept out of `domain/`
 * and `:intelligence` so neither imports the Hilt/Dagger framework.
 *
 * Also where [AppForegroundMonitor] (Android-only, `core/`) is narrowed to
 * the plain [StateFlow] domain code actually depends on (APX-016).
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    /** Default platform tuning (APX-012). A future per-track config swaps in here. */
    @Provides
    @Singleton
    fun provideIntelligenceConfig(): IntelligenceConfig = IntelligenceConfig()

    @Provides
    @AppForegroundState
    fun provideAppForegroundState(monitor: AppForegroundMonitor): StateFlow<Boolean> = monitor.isForeground
}
