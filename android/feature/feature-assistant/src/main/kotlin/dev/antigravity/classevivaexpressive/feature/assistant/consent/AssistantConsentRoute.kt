package dev.antigravity.classevivaexpressive.feature.assistant.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.classevivaexpressive.feature.assistant.settings.AssistantSettingsViewModel
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidHeroCard

/**
 * La pagina di consenso, alla prima accensione: cosa parte dal telefono, verso chi, cosa resta
 * qui, cosa l'assistente non fa mai da solo. Il tasto che accende e' l'unico modo di dire si';
 * tornare indietro lascia tutto spento.
 */
@Composable
fun AssistantConsentRoute(
  onBack: () -> Unit,
  onAccepted: () -> Unit,
  viewModel: AssistantSettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  FluidScreen(
    title = "Assistente IA",
    subtitle = "Prima di accenderlo, due parole su cosa fa dei tuoi dati.",
    ambient = FeatureIdentity.Settings.ambient(),
    onBack = onBack,
    itemSpacing = 12.dp,
  ) {
    item {
      FluidHeroCard(
        title = "Chiedi al registro",
        subtitle = "Voti, medie, compiti, orario, circolari, assenze: risposte in linguaggio naturale, e qualche azione nell'app.",
      )
    }
    item { FluidSectionHeader(title = "Cosa parte dal telefono") }
    item {
      ConsentCard(
        "Le tue domande (scritte, o trascritte dalla voce) e i dati del registro che servono per rispondere: i voti e le medie, i compiti e l'agenda, l'orario, il testo delle comunicazioni e degli allegati che chiedi di leggere, le assenze. " +
          "Nome, classe e scuola vengono inclusi, cosi' le risposte suonano tue.",
      )
    }
    item { FluidSectionHeader(title = "Verso chi") }
    item {
      ConsentCard(
        "Verso il servizio che hai scelto e verificato con la tua chiave: Groq, Google (Gemini) o OpenRouter. " +
          "Non c'e' nessun server di ClasseViva Expressive in mezzo: i dati vanno dal telefono al servizio e basta, e valgono le regole di quel servizio sulla tua chiave.",
      )
    }
    item { FluidSectionHeader(title = "Cosa resta qui") }
    item {
      ConsentCard(
        "Le conversazioni, salvate su questo telefono: le puoi rileggere, continuare o cancellare quando vuoi, e se ne vanno con il logout. " +
          "Le chiavi, cifrate nel Keystore del dispositivo. Le richieste lunghe continuano in una notifica anche se chiudi l'app.",
      )
    }
    item { FluidSectionHeader(title = "Cosa non fa") }
    item {
      ConsentCard(
        "Non scrive mai alla scuola per conto tuo. Le sole cose che puo' toccare sono nell'app (aprire una pagina, cambiare un'impostazione, segnare lette le comunicazioni, prendere visione, aggiungere un evento), " +
          "solo se attivi le azioni, e quelle che contano chiedono conferma con un tasto. E' spento finche' non lo accendi qui.",
      )
    }
    item {
      Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = if (state.verified.isEmpty()) "Serve prima una chiave verificata" else "Ho capito, accendi l'assistente",
          enabled = state.verified.isNotEmpty(),
          fillWidth = true,
          onClick = {
            viewModel.acceptConsentAndEnable()
            onAccepted()
          },
        )
        FluidButton(text = "Non ora", style = FluidButtonStyle.Plain, fillWidth = true, onClick = onBack)
      }
    }
  }
}

@Composable
private fun ConsentCard(text: String) {
  FluidCard(glass = true) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
  }
}
