package dev.antigravity.classevivaexpressive.core.assistant.prompt

import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

  private fun context(mode: AskMode = AskMode.TEXT, actions: Boolean = true) = PromptContext(
    profile = StudentProfile(name = "Alessio", surname = "Rossi", schoolClass = "4", section = "F", school = "Liceo Agnoletti"),
    schoolYear = SchoolYearRef(2026, 2027),
    today = LocalDate.of(2026, 9, 5),
    periods = listOf(
      Period("T1", 1, "Trimestre", "Trimestre", false, "2026-09-15", "2026-12-22"),
      Period("P2", 2, "Pentamestre", "Pentamestre", true, "2026-12-23", "2027-06-10"),
    ),
    unseenGrades = 2,
    unreadCommunications = 3,
    todayLessons = 5,
    upcomingItems = 4,
    actionsEnabled = actions,
    mode = mode,
    syncStatus = SyncStatus(state = SyncState.OFFLINE),
  )

  @Test
  fun `il prompt porta chi, quando e cosa c'e' da vedere`() {
    val prompt = PromptBuilder.build(context())
    assertTrue(prompt.contains("Studente: Alessio Rossi"))
    assertTrue(prompt.contains("Classe: 4F"))
    assertTrue(prompt.contains("Anno scolastico: 2026/27"))
    assertTrue(prompt.contains("Oggi: 2026-09-05 (sab), sabato"))
    assertTrue(prompt.contains("2 voti nuovi, 3 comunicazioni non lette"))
    assertTrue(prompt.contains("offline"))
    assertTrue(prompt.contains("Azioni nell'app: abilitate"))
    assertTrue(prompt.contains("voti_media"))
    assertTrue(prompt.contains("[[pagina:voti]]"))
    assertFalse(prompt.contains("Periodo corrente"))
  }

  @Test
  fun `a voce si chiede una risposta breve, con le azioni spente si dice che si legge e basta`() {
    val voice = PromptBuilder.build(context(mode = AskMode.VOICE, actions = false))
    assertTrue(voice.contains("letta ad alta voce"))
    assertTrue(voice.contains("disabilitate (puoi solo leggere)"))
    assertTrue(voice.contains("Modalita': voce"))
  }

  @Test
  fun `i chip ammessi sono le pagine note e i dettagli con un id`() {
    assertTrue(AssistantChips.accepts("pagina", "voti"))
    assertFalse(AssistantChips.accepts("pagina", "radar"))
    assertTrue(AssistantChips.accepts("voto", "123"))
    assertFalse(AssistantChips.accepts("voto", null))
    assertFalse(AssistantChips.accepts("sconosciuto", "x"))
  }
}
