package dev.antigravity.classevivaexpressive.feature.assistant.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.keys.VerifyResult
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** I link e i passi della guida di ciascun provider. */
object ProviderGuides {
  fun consoleUrl(provider: ProviderId): String = when (provider) {
    ProviderId.GROQ -> "https://console.groq.com/keys"
    ProviderId.GEMINI -> "https://aistudio.google.com/apikey"
    ProviderId.OPENROUTER -> "https://openrouter.ai/settings/keys"
  }

  fun consoleHost(provider: ProviderId): String = when (provider) {
    ProviderId.GROQ -> "console.groq.com"
    ProviderId.GEMINI -> "aistudio.google.com"
    ProviderId.OPENROUTER -> "openrouter.ai"
  }

  const val OPENROUTER_CREDITS_URL = "https://openrouter.ai/settings/credits"

  fun placeholder(provider: ProviderId): String = when (provider) {
    ProviderId.GROQ -> "gsk_…"
    ProviderId.GEMINI -> "AIza…"
    ProviderId.OPENROUTER -> "sk-or-…"
  }

  fun tagline(provider: ProviderId): String = when (provider) {
    ProviderId.GROQ -> "Gratis, veloce, con limiti al minuto. Il primo da provare."
    ProviderId.GEMINI -> "Gratis con limiti giornalieri; legge i PDF da solo."
    ProviderId.OPENROUTER -> "Tanti modelli, alcuni gratuiti; a pagamento i migliori."
  }

  fun steps(provider: ProviderId): List<String> = when (provider) {
    ProviderId.GROQ -> listOf(
      "Apri console.groq.com e accedi (o crea un account gratuito).",
      "Nel menu' scegli API Keys e premi Create API Key.",
      "Dai un nome alla chiave e copiala: comincia con gsk_.",
      "Incollala qui sotto e premi Verifica.",
    )
    ProviderId.GEMINI -> listOf(
      "Apri aistudio.google.com con il tuo account Google.",
      "Premi Get API key, poi Create API key.",
      "Copia la chiave: comincia con AIza.",
      "Incollala qui sotto e premi Verifica.",
    )
    ProviderId.OPENROUTER -> listOf(
      "Apri openrouter.ai e accedi.",
      "Vai in Settings > Keys e premi Create Key.",
      "Copia la chiave: comincia con sk-or-.",
      "Incollala qui sotto e premi Verifica. I modelli gratuiti hanno un tetto giornaliero; per gli altri servono crediti.",
    )
  }
}

/**
 * La scheda di una chiave: stato, guida a quattro passi con il tasto che apre la console, campo
 * (la chiave salvata non si ri-mostra mai: vuoto = "lascia quella salvata"), Verifica con l'esito
 * inline, Rimuovi.
 */
@Composable
fun AiKeySetup(
  viewModel: AssistantSettingsViewModel,
  provider: ProviderId,
  state: KeyState,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
  onVerified: (VerifyResult.Ok) -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptics = rememberFluidHaptics()
  var expanded by remember { mutableStateOf(initiallyExpanded) }
  var input by remember { mutableStateOf("") }
  var verifying by remember { mutableStateOf(false) }
  var outcome by remember { mutableStateOf<String?>(null) }
  var outcomeError by remember { mutableStateOf(false) }
  val onSurface = MaterialTheme.colorScheme.onSurface
  val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(provider.label, style = MaterialTheme.typography.titleSmall, color = onSurface)
        Text(
          text = when {
            state.verified -> "Chiave verificata il ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(state.verifiedAtMillis!!))}"
            state.present -> "Chiave salvata, ancora da verificare"
            else -> ProviderGuides.tagline(provider)
          },
          style = MaterialTheme.typography.bodySmall,
          color = onSurfaceVariant,
        )
      }
      FluidButton(
        text = if (expanded) "Chiudi" else "Come si fa",
        style = FluidButtonStyle.Plain,
        size = FluidButtonSize.Small,
        onClick = { expanded = !expanded },
      )
    }
    AnimatedVisibility(visible = expanded) {
      Column(Modifier.padding(top = 8.dp)) {
        ProviderGuides.steps(provider).forEachIndexed { index, step ->
          Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
            Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant, modifier = Modifier.width(20.dp))
            Text(step, style = MaterialTheme.typography.bodySmall, color = onSurface)
          }
        }
        Spacer(Modifier.height(8.dp))
        Row {
          FluidButton(
            text = "Apri ${ProviderGuides.consoleHost(provider)}",
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProviderGuides.consoleUrl(provider)))) } },
          )
          if (provider == ProviderId.OPENROUTER) {
            Spacer(Modifier.width(8.dp))
            FluidButton(
              text = "Crediti",
              style = FluidButtonStyle.Plain,
              size = FluidButtonSize.Small,
              onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProviderGuides.OPENROUTER_CREDITS_URL))) } },
            )
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      FluidTextField(
        value = input,
        onValueChange = { input = it.trim(); outcome = null },
        placeholder = if (state.present) "Nuova chiave (vuoto = tieni quella salvata)" else "Incolla la chiave ${ProviderGuides.placeholder(provider)}",
        visualTransformation = PasswordVisualTransformation(),
        isError = outcomeError,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      if (input.isNotBlank() || state.present) {
        FluidButton(
          text = if (verifying) "Verifico…" else "Verifica",
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          loading = verifying,
          enabled = !verifying,
          onClick = {
            verifying = true
            outcome = null
            scope.launch {
              // Tutto dentro un solo try: qui si tocca il Keystore, il disco e la rete, e un errore
              // qualsiasi deve dire cos'e' andato storto, non far cadere l'app.
              try {
                when (val result = viewModel.saveAndVerify(provider, input.takeIf { it.isNotBlank() })) {
                  is VerifyResult.Ok -> {
                    outcome = "Chiave valida: ${result.catalogue.chat.size} modelli di chat, ${result.catalogue.stt.size} di trascrizione" +
                      (result.chosenDefault?.let { chosen -> if (result.chosenIsFree) " · scelto $chosen (gratuito)" else " · scelto $chosen (a pagamento: nessun gratuito con strumenti)" } ?: "")
                    outcomeError = false
                    input = ""
                    haptics.play(FluidHapticEvent.Confirm)
                    onVerified(result)
                  }
                  VerifyResult.Invalid -> {
                    outcome = "Chiave non valida: il servizio l'ha rifiutata."
                    outcomeError = true
                    haptics.play(FluidHapticEvent.Error)
                  }
                  is VerifyResult.Failed -> {
                    outcome = "Verifica non riuscita: ${result.error?.message ?: "nessuna risposta"}"
                    outcomeError = true
                    haptics.play(FluidHapticEvent.Error)
                  }
                }
              } catch (e: CancellationException) {
                throw e
              } catch (e: Throwable) {
                outcome = "Verifica non riuscita: ${e.message ?: e::class.java.simpleName}"
                outcomeError = true
                haptics.play(FluidHapticEvent.Error)
              }
              verifying = false
            }
          },
        )
      }
      if (state.present && input.isBlank()) {
        Spacer(Modifier.width(4.dp))
        FluidButton(
          text = "Rimuovi",
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Small,
          onClick = { viewModel.removeKey(provider); outcome = null },
        )
      }
    }
    outcome?.let {
      Spacer(Modifier.height(4.dp))
      Text(it, style = MaterialTheme.typography.bodySmall, color = if (outcomeError) MaterialTheme.colorScheme.error else onSurfaceVariant)
    }
  }
}
