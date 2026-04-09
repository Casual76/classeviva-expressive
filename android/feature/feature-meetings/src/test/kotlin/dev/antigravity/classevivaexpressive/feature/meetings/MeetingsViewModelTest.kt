package dev.antigravity.classevivaexpressive.feature.meetings

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingJoinLink
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val meetingsRepository = mockk<MeetingsRepository>(relaxed = true)
  private val schoolYearRepository = mockk<SchoolYearRepository>(relaxed = true)
  private val capabilityResolver = mockk<CapabilityResolver>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel(): MeetingsViewModel {
    every { schoolYearRepository.observeSelectedSchoolYear() } returns flowOf(SchoolYearRef(2025, 2026))
    every { capabilityResolver.observeCapability(RegistroFeature.MEETINGS) } returns flowOf(
      FeatureCapability(feature = RegistroFeature.MEETINGS, mode = FeatureCapabilityMode.GATEWAY, enabled = true, label = "Disponibile"),
    )
    return MeetingsViewModel(meetingsRepository, schoolYearRepository, capabilityResolver)
  }

  private fun buildTeacher(id: String = "t1") = MeetingTeacher(id = id, name = "Prof. Rossi", subject = "Matematica")

  private fun buildSlot(id: String = "s1", teacherId: String = "t1") = MeetingSlot(
    id = id, teacherId = teacherId, date = "2026-04-15", startTime = "15:00",
    endTime = "15:15", location = "Aula 5", available = true,
  )

  private fun buildBooking(id: String = "b1") = MeetingBooking(
    id = id,
    teacher = buildTeacher(),
    slot = buildSlot(),
    status = "CONFIRMED",
  )

  // ─── Caricamento insegnanti ───────────────────────────────────────────────

  @Test
  fun teachersFromRepository_areExposedInState() = runTest {
    val teacher = buildTeacher()
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(listOf(teacher))
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.teachers.size)
      assertEquals("Prof. Rossi", state.teachers.first().name)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Caricamento slot disponibili ────────────────────────────────────────

  @Test
  fun slotsFromRepository_areExposedInState() = runTest {
    val slot = buildSlot()
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(listOf(slot))
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.slots.size)
      assertEquals("s1", state.slots.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Caricamento prenotazioni ─────────────────────────────────────────────

  @Test
  fun bookingsFromRepository_areExposedInState() = runTest {
    val booking = buildBooking()
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(listOf(booking))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.bookings.size)
      assertEquals("b1", state.bookings.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Prenotazione colloquio ───────────────────────────────────────────────

  @Test
  fun book_setsSuccessMessage() = runTest {
    val slot = buildSlot()
    val booking = buildBooking()
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(listOf(slot))
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(emptyList())
    coEvery { meetingsRepository.bookMeeting(slot) } returns Result.success(booking)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.book(slot)
      val updated = awaitItem()
      assertEquals("Colloquio prenotato.", updated.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun book_setsErrorMessageOnFailure() = runTest {
    val slot = buildSlot()
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(listOf(slot))
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(emptyList())
    coEvery { meetingsRepository.bookMeeting(slot) } returns Result.failure(Exception("Slot non disponibile"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.book(slot)
      val error = awaitItem()
      assertEquals("Slot non disponibile", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Cancellazione prenotazione ───────────────────────────────────────────

  @Test
  fun cancel_callsRepositoryAndSetsSuccessMessage() = runTest {
    val booking = buildBooking()
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(listOf(booking))
    coEvery { meetingsRepository.cancelMeeting(booking) } returns Result.success(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.cancel(booking)
      val updated = awaitItem()
      assertEquals("Prenotazione annullata.", updated.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
    coVerify { meetingsRepository.cancelMeeting(booking) }
  }

  // ─── Join colloquio ───────────────────────────────────────────────────────

  @Test
  fun join_setsJoinLinkInMessage() = runTest {
    val booking = buildBooking()
    val link = MeetingJoinLink(bookingId = "b1", url = "https://meet.example.com/b1")
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(listOf(booking))
    coEvery { meetingsRepository.joinMeeting(booking) } returns Result.success(link)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.join(booking)
      val updated = awaitItem()
      assertTrue(updated.lastMessage?.contains("https://meet.example.com/b1") == true)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(emptyList())
    coEvery { meetingsRepository.refreshMeetings(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { meetingsRepository.refreshMeetings(force = true) }
  }

  @Test
  fun refresh_setsIsRefreshingTrueThenFalse() = runTest {
    every { meetingsRepository.observeMeetingTeachers() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingSlots() } returns flowOf(emptyList())
    every { meetingsRepository.observeMeetingBookings() } returns flowOf(emptyList())
    coEvery { meetingsRepository.refreshMeetings(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.refresh()
      val refreshing = awaitItem()
      assertTrue(refreshing.isRefreshing)
      val done = awaitItem()
      assertEquals(false, done.isRefreshing)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
