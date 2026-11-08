package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.classevivaexpressive.core.database.database.*
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.data.sync.*
import dev.antigravity.classevivaexpressive.core.network.client.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Does not reference SchoolYearStore: historical reads cannot change UI selection. */
@Singleton
class ScopedSchoolDataAccess @Inject constructor(
  private val sessions: SessionStore, private val cache: SnapshotCacheDao,
  private val rest: ClassevivaRestClient, private val json: Json,
) : SchoolDataAccess {
  override suspend fun read(query: SchoolDataQuery): SchoolDataPage = try {
    readScoped(query)
  } catch (e: ClassevivaNetworkException) {
    throw SchoolDataUnavailable("account_changed", true)
  }

  private suspend fun readScoped(query: SchoolDataQuery): SchoolDataPage {
    query.validate()
    val captured = sessions.captureAuthSnapshot()
    val session = captured.snapshot?.session ?: throw SchoolDataUnavailable("auth_missing")
    if ((query.account != null && query.account != session.username) ||
      (query.student != null && query.student != session.studentId)) throw SchoolDataUnavailable("scope_unavailable")
    val work = sessions.captureAccountWork(allowOffline = true)
    if (work.lease != captured.lease) throw SchoolDataUnavailable("account_changed")
    val authReady = captured.snapshot?.authenticationState?.rest == RestAuthenticationState.READY
    val dated = query.section in setOf("agenda", "lessons", "absences")
    val current = SchoolYearSelectionPolicy.current(LocalDate.now())
    val key = "aria:${session.username.length}:${session.username}:${session.studentId}:${query.year.id}:${query.section}"
    var stored = cache.getByKey(key)
    var refreshed = false
    if (query.refresh) {
      if (query.cursor != null) throw SchoolDataUnavailable("refresh_with_cursor")
      if (!authReady) throw SchoolDataUnavailable("auth_required")
      if (!dated && query.year != current) throw SchoolDataUnavailable("historical_refresh_unavailable")
      val start = "${query.year.startYear}-09-01"; val end = "${query.year.endYear}-08-31"
      val payload = try { withAccountWork(work) { withStrictSchoolRead {
        when (query.section) {
          "grades" -> json.encodeToString(rest.getGrades())
          "agenda" -> json.encodeToString(rest.getAgenda(start, end))
          "lessons" -> json.encodeToString(rest.getLessons(start, end))
          "absences" -> json.encodeToString(rest.getAbsences(start, end))
          "communications" -> json.encodeToString(rest.getCommunications())
          "notes" -> json.encodeToString(rest.getNotes())
          "materials" -> json.encodeToString(rest.getMaterials())
          "documents" -> json.encodeToString(rest.getDocuments())
          else -> throw SchoolDataUnavailable("unsupported_section")
        }
      } } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
          if (!sessions.accountGuard.isCurrent(work.lease, false)) throw SchoolDataUnavailable("account_changed")
          throw SchoolDataUnavailable(if (sessions.readAuthSnapshot()?.authenticationState?.rest != RestAuthenticationState.READY) "auth_required" else "refresh_failed", true)
        }
      if ((json.parseToJsonElement(payload) as JsonArray).size > 20_000) throw SchoolDataUnavailable("source_too_large")
      stored = SnapshotCacheEntity(key, payload, System.currentTimeMillis())
      sessions.commitAccountWork(work) { cache.upsert(stored!!) }
      refreshed = true
    }
    // Legacy student-only snapshots cannot prove the requesting account; never relabel them.
    val ownSnapshot = stored != null
    val snapshot = stored ?: throw SchoolDataUnavailable("data_unavailable", true)
    val records = try { (json.parseToJsonElement(snapshot.payload) as JsonArray).map { it as JsonObject } }
      catch (e: Exception) { throw SchoolDataUnavailable("cache_invalid", true) }
    if (records.size > 20_000) throw SchoolDataUnavailable("source_too_large")
    val from = "${query.year.startYear}-09-01"; val to = "${query.year.endYear}-08-31"
    val scoped = if (query.section == "documents") records else records.filter {
      val dateKey = if(query.section == "materials") "sharedAt" else "date"
      val date = (it[dateKey] as? JsonPrimitive)?.contentOrNull?.take(10)
      date != null && date >= from && date <= to
    }
    val bound = query.copy(account = session.username, student = session.studentId)
    val (page, total, next) = SchoolDataPaging.page(bound, scoped, snapshot.updatedAtEpochMillis.toString())
    // Aggregates use every matching row, irrespective of page size.
    val matched = SchoolDataPaging.matching(bound, scoped)
    return sessions.commitAccountWork(work) {
      val now = System.currentTimeMillis()
      SchoolDataPage(page, total, next, SchoolDataPaging.aggregates(matched), session.username, session.studentId,
        query.year.id, query.section, snapshot.updatedAtEpochMillis, now,
        !authReady || now - snapshot.updatedAtEpochMillis > 300_000, refreshed,
        if(ownSnapshot && dated) scoped.size == records.size else null, if(refreshed) true else null, query.from ?: from, query.to ?: to)
    }
  }
}
