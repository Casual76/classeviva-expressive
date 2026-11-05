package dev.antigravity.classevivaexpressive.core.data.sync

import android.content.Context
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AccountSyncSchedulerTest {
  @Test fun staleUiConfigurationCannotScheduleOrCancelReplacementAccountsWork() = runTest {
    val store = testSessionStore()
    val oldEpoch = store.accountGuard.authenticationEpoch()
    val original = store.readAuthSnapshot()!!
    store.clear()
    store.writeAuthSnapshot(original.copy(session = original.session!!.copy(studentId = "student-b")))
    val context = mockk<Context>()
    val scheduler = AccountSyncScheduler(context, store)
    mockkObject(SyncWorkScheduler)
    every { SyncWorkScheduler.schedule(any()) } just Runs
    every { SyncWorkScheduler.cancel(any()) } just Runs
    try {
      scheduler.schedule(oldEpoch, "student-1")
      scheduler.cancel(oldEpoch)
      verify(exactly = 0) { SyncWorkScheduler.schedule(any()) }
      verify(exactly = 0) { SyncWorkScheduler.cancel(any()) }
      scheduler.schedule(store.accountGuard.authenticationEpoch(), "student-b")
      verify(exactly = 1) { SyncWorkScheduler.schedule(context) }
    } finally { unmockkObject(SyncWorkScheduler) }
  }

  @Test fun terminalRestRejectionAllowsCurrentCancellationButBlocksSchedulingUntilReady() = runTest {
    val store = testSessionStore()
    val epoch = store.accountGuard.authenticationEpoch()
    val ready = store.readAuthSnapshot()!!
    store.writeAuthSnapshot(ready.copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
    val context = mockk<Context>()
    val scheduler = AccountSyncScheduler(context, store)
    mockkObject(SyncWorkScheduler)
    every { SyncWorkScheduler.schedule(any()) } just Runs
    every { SyncWorkScheduler.cancel(any()) } just Runs
    try {
      scheduler.schedule(epoch, "student-1")
      scheduler.cancel(epoch)
      verify(exactly = 0) { SyncWorkScheduler.schedule(any()) }
      verify(exactly = 1) { SyncWorkScheduler.cancel(context) }
      store.writeAuthSnapshot(ready)
      scheduler.schedule(epoch, "student-1")
      verify(exactly = 1) { SyncWorkScheduler.schedule(context) }
    } finally { unmockkObject(SyncWorkScheduler) }
  }
}
