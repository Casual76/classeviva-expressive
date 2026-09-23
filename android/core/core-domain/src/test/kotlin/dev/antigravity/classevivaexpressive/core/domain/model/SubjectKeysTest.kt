package dev.antigravity.classevivaexpressive.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectKeysTest {

  @Test
  fun classevivaNames_fallIntoTheirFamily() {
    val cases = mapOf(
      "STORIA" to SubjectKeys.Storia,
      "SCIENZE NATURALI (BIOLOGIA, CHIMICA, SCIENZE DELLA TERRA)" to SubjectKeys.Scienze,
      "LINGUA E CULTURA STRANIERA INGLESE" to SubjectKeys.Inglese,
      "LINGUA E CULTURA STRANIERA (INGLESE)" to SubjectKeys.Inglese,
      "FILOSOFIA" to SubjectKeys.Filosofia,
      "FISICA" to SubjectKeys.Fisica,
      "LINGUA E LETTERATURA ITALIANA" to SubjectKeys.Italiano,
      "INFORMATICA" to SubjectKeys.Informatica,
      "DISEGNO E STORIA DELL'ARTE" to SubjectKeys.Arte,
      "SCIENZE MOTORIE E SPORTIVE" to SubjectKeys.Motorie,
      "RELIGIONE CATTOLICA" to SubjectKeys.Religione,
      "IRC" to SubjectKeys.Religione,
      "MATEMATICA" to SubjectKeys.Matematica,
      "LINGUA E CULTURA LATINA" to SubjectKeys.Latino,
      "EDUCAZIONE CIVICA" to SubjectKeys.Civica,
    )
    cases.forEach { (name, key) -> assertEquals(name, key, SubjectKeys.keyOf(name)) }
  }

  @Test
  fun artHistory_isArtBeforeItIsHistory() {
    assertEquals(SubjectKeys.Arte, SubjectKeys.keyOf("Storia dell’arte"))
    assertEquals(SubjectKeys.Arte, SubjectKeys.keyOf("STORIA DELL'ARTE"))
  }

  @Test
  fun physicalEducation_isSportBeforeItIsPhysics() {
    assertEquals(SubjectKeys.Motorie, SubjectKeys.keyOf("EDUCAZIONE FISICA"))
    assertEquals(SubjectKeys.Fisica, SubjectKeys.keyOf("Fisica"))
  }

  @Test
  fun caseAndAccents_doNotChangeTheKey() {
    assertEquals(SubjectKeys.keyOf("SCIENZE NATURALI"), SubjectKeys.keyOf("Scienze naturali"))
    assertEquals(SubjectKeys.Religione, SubjectKeys.keyOf("Attività alternativa"))
  }

  @Test
  fun joinedBlock_takesItsFirstSubject() {
    assertEquals(SubjectKeys.Filosofia, SubjectKeys.keyOf("FILOSOFIA / STORIA"))
  }

  @Test
  fun unknownSubject_keepsItsOwnName() {
    assertEquals("x:scienze umane", SubjectKeys.keyOf("SCIENZE UMANE"))
    assertEquals("x:diritto ed economia", SubjectKeys.keyOf("DIRITTO ED ECONOMIA"))
  }

  @Test
  fun blank_hasNoKey() {
    assertNull(SubjectKeys.keyOf(null))
    assertNull(SubjectKeys.keyOf("  "))
  }

  @Test
  fun familyLabel_onlyForKnownFamilies() {
    assertEquals("Storia dell'arte", SubjectKeys.familyLabel(SubjectKeys.Arte))
    assertNull(SubjectKeys.familyLabel("x:scienze umane"))
  }
}
