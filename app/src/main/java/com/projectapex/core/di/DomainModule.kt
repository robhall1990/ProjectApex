package com.projectapex.core.di

import com.projectapex.domain.DefaultDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Hilt bindings for domain-layer types that can't be constructor-injected
 * directly (e.g. [CoroutineDispatcher], which has no injectable constructor
 * of its own). Kept out of `domain/` so the domain layer itself never
 * imports the Hilt/Dagger framework.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
