package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.database.database.*
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ScopedCustomEventTest {
  private val store = testSessionStore()
  private val events = mockk<CustomEventDao>()
  private val persisted = mutableMapOf<String, CustomEventEntity>()
  private val event = CustomEvent(id = "cv-event:operation", title = "Study", description = "", subject = "", date = "2026-11-08")
  init {
    coEvery { events.getById(any()) } answers { persisted[firstArg()] }
    coEvery { events.upsert(any()) } answers { firstArg<CustomEventEntity>().let { persisted[it.id] = it } }
    every { events.observeAll() } answers { flowOf(persisted.values.toList()) }
  }
  private fun repository() = SchoolDataRepository(
    json = Json, snapshotCacheDao = mockk(), syncMetadataDao = mockk(), customEventDao = events,
    studentScoreDao = mockk(), downloadRecordDao = mockk(), seenGradeDao = mockk(), subjectGoalDao = mockk(),
    changeHistoryDao = mockk(), gradeDao = mockk(), agendaDao = mockk(), agendaCategoryOverrideDao = mockk(),
    absenceDao = mockk(), communicationDao = mockk(), attachmentCacheDao = mockk(), materialDao = mockk(),
    documentDao = mockk(), syncCoordinator = mockk(), downloadManager = mockk(), sessionStore = store,
    schoolYearStore = mockk(), timetableTemplateStore = mockk(), capabilityResolver = mockk(),
    predictiveTimetableUseCase = mockk(), externalDashboardInvalidators = emptySet(), context = mockk(),
  )
  @Test fun retryAfterRepositoryRecreationReadsSamePersistedEntity() = runTest {
    val epoch = store.captureAuthSnapshot().authenticationEpoch
    repository().addScopedCustomEvent(event, epoch)
    val original = persisted[event.id]
    val recreated = repository()
    recreated.addScopedCustomEvent(event, epoch)
    assertEquals(original, persisted[event.id])
    assertEquals(event, recreated.observeCustomEvents().first().single().copy(createdAt = null))
    coVerify(exactly = 1) { events.upsert(any()) }
  }
  @Test fun conflictingRetryCannotOverwritePersistedEvent() = runTest {
    val epoch = store.captureAuthSnapshot().authenticationEpoch
    repository().addScopedCustomEvent(event, epoch)
    try {
      repository().addScopedCustomEvent(event.copy(title = "Changed"), epoch)
      fail("conflicting operation accepted")
    } catch (expected: IllegalArgumentException) {
      assertEquals("operation_id_conflict", expected.message)
    }
    assertEquals(event, repository().observeCustomEvents().first().single().copy(createdAt = null))
    coVerify(exactly = 1) { events.upsert(any()) }
  }
  @Test fun revokedAccountEpochCannotPersistEvent() = runTest {
    val epoch = store.captureAuthSnapshot().authenticationEpoch
    store.clear()
    val failure = runCatching { repository().addScopedCustomEvent(event, epoch) }.exceptionOrNull()
    assertNotNull("revoked write succeeded", failure)
    coVerify(exactly = 0) { events.getById(any()) }
    coVerify(exactly = 0) { events.upsert(any()) }
  }
  @Test fun legacyEventRemainsEditable() = runTest {
    val legacy = event.copy(id = "legacy-ui-event")
    repository().addCustomEvent(legacy)
    repository().addCustomEvent(legacy.copy(title = "Updated"))
    assertEquals("Updated", repository().observeCustomEvents().first().single().title)
  }
  @Test fun sameStudentAccountSwitchInvalidatesPendingEventEpoch() = runTest {
    val previous = store.readAuthSnapshot()!!
    val epoch = store.captureAuthSnapshot().authenticationEpoch
    store.writeAuthSnapshot(previous.copy(session = previous.session!!.copy(username = "another-account")))
    assertNotNull(runCatching { repository().addScopedCustomEvent(event, epoch) }.exceptionOrNull())
    coVerify(exactly = 0) { events.upsert(any()) }
  }
  @Test fun originalDataLeaseRejectsSuspendedWriteAcrossPasswordToCieAliasChange() = runTest {
    val original = store.captureAuthSnapshot()
    val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
    val resume = kotlinx.coroutines.CompletableDeferred<Unit>()
    val pending = async {
      entered.complete(Unit); resume.await()
      runCatching { repository().addScopedCustomEvent(event, original.authenticationEpoch, original.lease.epoch) }
    }
    entered.await()
    val previous = store.readAuthSnapshot()!!
    store.writeAuthSnapshot(previous.copy(session = previous.session!!.copy(username = "cie-alias", ident = "fixture-ident", authMethod = dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod.CIE), credentials = null, cie = dev.antigravity.classevivaexpressive.core.datastore.CieCredentials("fixture-pa", "fixture-refresh", "fixture-ident", 999000)))
    assertEquals(original.authenticationEpoch, store.captureAuthSnapshot().authenticationEpoch)
    assertNotEquals(original.lease, store.captureAuthSnapshot().lease)
    resume.complete(Unit)
    assertTrue(pending.await().isFailure)
    coVerify(exactly = 0) { events.upsert(any()) }
  }

}
