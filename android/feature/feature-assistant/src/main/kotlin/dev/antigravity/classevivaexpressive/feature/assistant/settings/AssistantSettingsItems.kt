package dev.antigravity.classevivaexpressive.feature.assistant.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.keys.ThinkingLevel
import dev.antigravity.fluidengine.ai.orchestrator.AiRequestLog
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun ThinkingLevel.label(): String = when (this) {
  ThinkingLevel.LOW -> "Basso"
  ThinkingLevel.MEDIUM -> "Medio"
  ThinkingLevel.HIGH -> "Alto"
}

/**
 * Impostazioni -> Assistente IA: l'interruttore (che la prima volta passa dal consenso), le chiavi
 * con la guida, l'ordine dei provider, i modelli per livello, le preferenze, la privacy, i totali e
 * le ultime richieste. Sono voci di una lista: le ospita `SettingsScreen`, che tiene la pagina.
 */
fun LazyListScope.assistantSettingsItems(
  viewModel: AssistantSettingsViewModel,
  state: AssistantSettingsUiState,
  onOpenConsent: () -> Unit,
  onOpenHistory: (() -> Unit)?,
) {
  val verified = state.verified

  item {
    FluidListGroup(glass = true) {
      FluidListRow(
        title = "Assistente",
        subtitle = when {
          verified.isEmpty() -> "Serve almeno una chiave verificata qui sotto."
          state.enabled -> "Acceso: il tasto \"Chiedi all'AI\" compare sopra la barra."
          else -> "Spento: nessun tasto, nessuna richiesta parte."
        },
        badge = {
          FluidSwitch(
            checked = state.enabled,
            enabled = verified.isNotEmpty(),
            onCheckedChange = { on ->
              if (on && !state.settings.consentAccepted) onOpenConsent() else viewModel.setEnabled(on)
            },
          )
        },
      )
    }
  }

  item { FluidSectionHeader(title = "Chiavi", detail = "Restano sul telefono, cifrate. Viaggiano solo verso il servizio a cui appartengono.") }
  item {
    FluidListGroup(glass = true) {
      ProviderId.entries.forEachIndexed { index, provider ->
        if (index > 0) FluidListDivider()
        AiKeySetup(
          viewModel = viewModel,
          provider = provider,
          state = state.keys[provider] ?: KeyState(false, null),
          initiallyExpanded = provider == ProviderId.GROQ && verified.isEmpty(),
        )
        if (provider == ProviderId.OPENROUTER && state.keys[provider]?.present == true) {
          OpenRouterKeyDetails(state)
        }
      }
    }
  }

  if (verified.size > 1) {
    item { FluidSectionHeader(title = "Ordine dei servizi", detail = "Il primo risponde; gli altri sono la riserva quando e' al limite o non risponde.") }
    item {
      Text("Chat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
      ProviderOrderList(order = state.settings.chatOrder, available = verified, onReorder = viewModel::setChatOrder)
    }
    item {
      Text("Trascrizione della voce", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
      ProviderOrderList(order = state.settings.sttOrder, available = verified, onReorder = viewModel::setSttOrder)
    }
  }

  if (verified.isNotEmpty()) {
    item { FluidSectionHeader(title = "Modelli", detail = "Tre livelli per servizio: il router sceglie gli strumenti, la chat risponde, il profondo legge documenti e testi lunghi.") }
    item { AssistantModelsSection(viewModel, state) }
  }

  item { FluidSectionHeader(title = "Preferenze") }
  item { AssistantPreferences(viewModel, state) }

  item {
    Text(
      "Le tue domande e i dati del registro che servono per rispondere (voti, compiti, comunicazioni, allegati che chiedi di leggere, con nome, classe e scuola) partono verso il servizio scelto, con la tua chiave. Le conversazioni restano su questo telefono.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
  }

  item { FluidSectionHeader(title = "Uso") }
  item { AssistantUsage(state, onOpenHistory) }
}

@Composable
private fun OpenRouterKeyDetails(state: AssistantSettingsUiState) {
  val info = state.keyInfo[ProviderId.OPENROUTER]
  val chosen = state.settings.chatModel(ProviderId.OPENROUTER)
  val model = state.catalogues[ProviderId.OPENROUTER]?.chat(chosen ?: "")
  Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
    val credits = info?.let {
      val left = it.limitRemainingUsd ?: it.limitUsd
      val today = it.usageDailyUsd ?: 0.0
      "Crediti: ${left?.let { v -> String.format(Locale.ITALIAN, "%.2f $", v) } ?: "illimitati"} · oggi ${String.format(Locale.ITALIAN, "%.3f $", today)}" +
        if (it.isFreeTier) " · account gratuito" else " · account con crediti"
    } ?: "Crediti: verifica la chiave per leggerli"
    Text(credits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (chosen != null) {
      Text(
        if (model?.free == true || model == null) "Modello scelto: $chosen (gratuito)" else "Modello scelto: $chosen (a pagamento: nessun gratuito con strumenti nel catalogo)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AssistantModelsSection(viewModel: AssistantSettingsViewModel, state: AssistantSettingsUiState) {
  var picker by remember { mutableStateOf<ModelPickRequest?>(null) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    state.verified.sortedBy { it.ordinal }.forEach { provider ->
      val catalogue = state.catalogues[provider]
      val refreshedAt = state.settings.modelsRefreshedAt[provider]
      FluidListGroup(glass = true) {
        ModelTier.entries.forEachIndexed { index, tier ->
          if (index > 0) FluidListDivider()
          val chosen = state.settings.model(provider, tier)
          FluidListRow(
            title = "${provider.label} · ${tier.label()}",
            subtitle = "${tier.hint()} · ${catalogue.summary(chosen)}",
            onClick = { picker = ModelPickRequest(provider, tier) },
          )
        }
        FluidListDivider()
        FluidListRow(
          title = "${provider.label} · Trascrizione",
          subtitle = catalogue.summary(state.settings.sttModel(provider)),
          onClick = { picker = ModelPickRequest(provider, null, stt = true) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Aggiorna il catalogo",
          subtitle = if (refreshedAt != null) "Letto il ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(refreshedAt))}" else "Mai letto dalla rete",
          onClick = { viewModel.refreshCatalogue(provider) },
        )
      }
    }
  }
  // Dichiarato sempre, mai dentro un `let`: smontarlo alla chiusura toglie l'animazione di uscita.
  ModelPickerPortal(
    request = picker,
    catalogues = state.catalogues,
    selectedModel = { request -> if (request.stt) state.settings.sttModel(request.provider) else request.tier?.let { state.settings.model(request.provider, it) } },
    fallbacks = state.settings.openRouterFallbacks,
    onSelect = { request, id -> if (request.stt) viewModel.setSttModel(request.provider, id) else request.tier?.let { viewModel.setModel(request.provider, it, id) } },
    onFallbacks = viewModel::setOpenRouterFallbacks,
    onDismiss = { picker = null },
  )
}

@Composable
private fun AssistantPreferences(viewModel: AssistantSettingsViewModel, state: AssistantSettingsUiState) {
  val context = LocalContext.current
  var permissionEpoch by remember { mutableIntStateOf(0) }
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionEpoch++ }
  val micGranted = remember(permissionEpoch) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
  FluidListGroup(glass = true) {
    FluidListRow(
      title = "Ragionamento",
      subtitle = "Quanto il modello pensa prima di rispondere: piu' alto, piu' lento e piu' preciso.",
      badge = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ThinkingLevel.entries.forEach { level ->
            FluidChip(label = level.label(), selected = state.settings.thinking == level, onClick = { viewModel.setThinking(level) })
          }
        }
      },
    )
    FluidListDivider()
    FluidListRow(
      title = "Leggi le risposte",
      subtitle = "Dopo una domanda a voce, la risposta viene letta ad alta voce.",
      badge = { FluidSwitch(checked = state.settings.speakReplies, onCheckedChange = viewModel::setSpeakReplies) },
    )
    FluidListDivider()
    FluidListRow(
      title = "Azioni nell'app",
      subtitle = "Aprire pagine, cambiare impostazioni, segnare lette le comunicazioni, aggiungere eventi. Le azioni che contano chiedono conferma con un tasto.",
      badge = { FluidSwitch(checked = state.settings.actionsEnabled, onCheckedChange = viewModel::setActionsEnabled) },
    )
    FluidListDivider()
    FluidListRow(
      title = "Microfono",
      subtitle = if (micGranted) "Permesso concesso: il tocco sul tasto ascolta." else "Serve per fare domande a voce.",
      badge = if (micGranted) {
        null
      } else {
        {
          FluidButton(
            text = "Consenti",
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            onClick = {
              micLauncher.launch(Manifest.permission.RECORD_AUDIO)
              // Negato per sempre: l'unica strada sono le impostazioni di sistema.
              if (permissionEpoch > 0) {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
              }
            },
          )
        }
      },
    )
  }
}

@Composable
private fun AssistantUsage(state: AssistantSettingsUiState, onOpenHistory: (() -> Unit)?) {
  FluidListGroup(glass = true) {
    val totals = state.totals
    FluidListRow(
      title = "Conversazioni",
      subtitle = if (totals == null || totals.conversations == 0) {
        "Nessuna conversazione ancora."
      } else {
        "${totals.conversations} conversazioni · ${totals.runs} risposte · ${formatTokens(totals.tokens)} token" +
          (if (totals.costUsd > 0.0) " · ${String.format(Locale.ITALIAN, "%.3f $", totals.costUsd)}" else "")
      },
      onClick = onOpenHistory,
    )
    state.recent.forEach { log ->
      FluidListDivider()
      RecentRequestRow(log)
    }
  }
}

@Composable
private fun RecentRequestRow(log: AiRequestLog) {
  val details = buildList {
    add("${log.provider.label}${if (log.switchedTo.isNotEmpty()) " → ${log.switchedTo.joinToString(", ") { it.label }}" else ""} · ${log.models.values.distinct().joinToString(", ")} · ${log.steps} passi · ${log.durationMillis / 1000} s")
    if (log.groups.isNotEmpty()) add("gruppi: ${log.groups.joinToString(", ")}")
    if (log.tools.isNotEmpty()) add("strumenti: ${log.tools.joinToString(", ") { "${it.name} ${it.millis} ms${if (it.ok) "" else " ✕"}" }}")
    log.usage?.let { usage ->
      add("token: ${usage.promptTokens} in, ${usage.completionTokens} out" + (usage.costUsd?.let { " · ${String.format(Locale.getDefault(), "%.4f $", it)}" } ?: ""))
    }
    if (log.waitedSeconds > 0) add("attesa: ${log.waitedSeconds} s")
    log.error?.let { add(it) }
  }.joinToString("\n")
  FluidListRow(
    title = log.question.take(80),
    subtitle = details,
    meta = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(log.startedAtMillis)),
  )
}

private fun formatTokens(tokens: Long): String = when {
  tokens >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", tokens / 1_000_000.0)
  tokens >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", tokens / 1_000.0)
  else -> tokens.toString()
}
