package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape

/**
 * Il riquadro lilla che apre una pagina di testo: login, impostazioni, consenso dell'assistente.
 *
 * Era `FluidHeroCard` dell'engine, deprecato dalla 3.0 (un `Surface` di Material con l'angolo
 * circolare): l'engine indica `FluidHero` per le schermate piene di dati e `FluidCard(highlighted)` per
 * un riquadro che dice una cosa. Qui l'aspetto dell'app resta com'era (tinta `primaryContainer`,
 * titolo e frase), con l'angolo continuo delle regole del design system. E' una scelta di gusto: se
 * un giorno questi riquadri vorranno essere `FluidCard(highlighted = true)` si cambia qui, in un posto.
 */
@Composable
fun ClassevivaHeroCard(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = ContinuousCornerShape(28.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      trailing?.invoke() ?: Icon(
        imageVector = Icons.Rounded.School,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
