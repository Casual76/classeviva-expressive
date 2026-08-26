package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles

/**
 * Il dettaglio di un voto, come contenuto di un pop-up.
 *
 * Vive nel design system e non in una schermata perche' lo aprono in due: la lista dei voti e la
 * home. Erano due strade diverse per la stessa cosa — e una delle due era una schermata intera, che
 * per un voto e' un viaggio sproporzionato: si va, si guarda un numero, si torna indietro.
 *
 * Prende il modello di dominio invece di dodici parametri sciolti: questo modulo sa gia' cos'e' un
 * voto (lo sanno [GradeCard], [GradePill] e [gradeBand]), e spacchettarlo al call site sarebbe solo
 * un modo di far scrivere la stessa riga a due schermate.
 */
/** Il colore con cui tingere la finestra che mostra questo voto, o niente se non ne ha uno. */
@Composable
fun gradePaneTint(grade: Grade): Color? =
  gradeBand(grade.numericValue)?.let { gradeVividColors(it).start }

@Composable
fun GradeDetailContent(
  grade: Grade,
  modifier: Modifier = Modifier,
  dateLabel: String = gradeDateLabel(grade.date),
  historyDateLabel: (Long) -> String = ::gradeDateTimeLabel,
) {
  var showHistory by rememberSaveable(grade.id) { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 20.dp)
      .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Nessuna card qui dentro: il pannello **e'** il voto, tinto del suo colore da chi lo apre
    // (vedi `paneTint`). Rimetterci una superficie satura significherebbe una card dentro una card.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = grade.subject.asReadableSubject(),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = listOfNotNull(grade.type.ifBlank { null }, dateLabel).joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium,
          color = LocalContentColor.current.copy(alpha = 0.82f),
        )
      }
      Text(text = grade.valueLabel, style = FluidTextStyles.largeNumeric, maxLines = 1)
    }
    val judgement = listOfNotNull(grade.description, grade.notes, grade.teacher)
      .filter { it.isNotBlank() }
      .joinToString(" · ")
    if (judgement.isNotBlank()) {
      Text(
        text = judgement,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalContentColor.current.copy(alpha = 0.78f),
      )
    }

    if (grade.history.isNotEmpty()) {
      FluidButton(
        text = if (showHistory) {
          "Nascondi cronologia"
        } else {
          "Cronologia versioni (${grade.history.size})"
        },
        onClick = { showHistory = !showHistory },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
      if (showHistory) {
        FluidSectionHeader(title = "Com'era prima")
        grade.history.forEach { version ->
          GradeCard(
            valueLabel = version.valueLabel,
            numericValue = version.numericValue,
            title = version.subject,
            subtitle = listOfNotNull(
              version.type.ifBlank { null },
              historyDateLabel(version.recordedAtEpochMillis),
            ).joinToString(" · "),
            meta = listOfNotNull(version.description, version.notes, version.teacher)
              .filter { it.isNotBlank() }
              .joinToString(" · ")
              .ifBlank { null },
          )
        }
      }
    }

    if (grade.history.isEmpty() && grade.description.isNullOrBlank() && grade.notes.isNullOrBlank()) {
      Text(
        text = "Il registro non ha aggiunto un giudizio a questa valutazione.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
