package dev.antigravity.classevivaexpressive.core.data.repository

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Calendar-derived dashboard data also changes without a database write. This clock exists only
 * while the dashboard is collected; it performs no network or persistent background work.
 * Rechecking at most once a minute also recovers from a manual time or timezone change.
 */
fun observeSchoolDate(
  now: () -> ZonedDateTime = ZonedDateTime::now,
  pause: suspend (Long) -> Unit = { delay(it) },
): Flow<LocalDate> = flow {
  while (currentCoroutineContext().isActive) {
    val current = now()
    emit(current.toLocalDate())
    val nextDay = current.toLocalDate().plusDays(1).atStartOfDay(current.zone)
    pause(Duration.between(current, nextDay).toMillis().coerceIn(1L, 60_000L))
  }
}.distinctUntilChanged()
