package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectPaletteTest {

  private val light = SubjectPalette(emptyMap(), isDark = false, surface = Color(0xFFF7F7FA), onSurface = Color(0xFF121214))
  private val dark = SubjectPalette(emptyMap(), isDark = true, surface = Color(0xFF121214), onSurface = Color(0xFFF2F2F7))

  @Test
  fun requestedDefaults_areWhereAlessioPutThem() {
    assertEquals(Color(0xFFD9C3A0), subjectDefaultColor(SubjectKeys.Storia))
    assertEquals(Color(0xFF8A9A3B), subjectDefaultColor(SubjectKeys.Scienze))
    assertEquals(Color(0xFFFFFFFF), subjectDefaultColor(SubjectKeys.Italiano))
    assertEquals(Color(0xFFD9534F), subjectDefaultColor(SubjectKeys.Motorie))
    assertEquals(light.fill("DISEGNO E STORIA DELL'ARTE"), light.fill("Storia dell'arte"))
  }

  @Test
  fun unknownSubject_alwaysGetsTheSameFallback() {
    val first = subjectDefaultColor("x:diritto ed economia")
    assertEquals(first, subjectDefaultColor("x:diritto ed economia"))
    assertTrue(SubjectSwatches.any { it.color == first })
  }

  @Test
  fun override_winsOverTheDefault() {
    val chosen = Color(0xFF2F6FDE)
    val palette = SubjectPalette(mapOf(SubjectKeys.Storia to chosen.toArgb()), false, Color.White, Color.Black)
    assertEquals(chosen, palette.fill("STORIA"))
    assertTrue(palette.isOverridden(SubjectKeys.Storia))
    assertFalse(palette.isOverridden(SubjectKeys.Fisica))
  }

  @Test
  fun white_isOutlinedOnALightSurface_purpleIsNot() {
    assertTrue(light.needsOutline("ITALIANO"))
    assertFalse(light.needsOutline("INGLESE"))
    // Il segno del bianco si scurisce, altrimenti sparirebbe sulla riga.
    assertTrue(light.mark("ITALIANO").luminance() < 0.8f)
  }

  @Test
  fun darkTheme_dimsWhiteAndKeepsDarkColoursReadable() {
    assertTrue(dark.fill("ITALIANO").luminance() < 0.8f)
    assertTrue(dark.fill("RELIGIONE").luminance() > 0.05f)
  }

  @Test
  fun content_isChosenByContrast() {
    val darkContent = Color(0xFF121214)
    assertEquals(darkContent, light.vivid("ITALIANO").content)
    assertEquals(darkContent, light.vivid("STORIA DELL'ARTE").content)
    assertTrue(light.vivid("RELIGIONE CATTOLICA").content.luminance() > 0.9f)
  }
}
