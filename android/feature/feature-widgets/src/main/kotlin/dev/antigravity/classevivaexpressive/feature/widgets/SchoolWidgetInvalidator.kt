package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.antigravity.classevivaexpressive.core.data.external.ExternalDashboardInvalidator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolWidgetInvalidator @Inject constructor(
  @ApplicationContext private val context: Context,
) : ExternalDashboardInvalidator {
  override suspend fun invalidateDashboard() {
    SchoolOverviewWidget().updateAll(context)
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SchoolWidgetInvalidatorModule {
  @Binds
  @IntoSet
  abstract fun bindSchoolWidgetInvalidator(impl: SchoolWidgetInvalidator): ExternalDashboardInvalidator
}
