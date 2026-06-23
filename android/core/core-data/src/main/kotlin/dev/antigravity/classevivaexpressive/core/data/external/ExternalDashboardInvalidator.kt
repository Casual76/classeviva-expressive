package dev.antigravity.classevivaexpressive.core.data.external

import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface ExternalDashboardInvalidator {
  suspend fun invalidateDashboard()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalDashboardInvalidatorModule {
  @Multibinds
  abstract fun bindExternalDashboardInvalidators(): Set<ExternalDashboardInvalidator>
}
