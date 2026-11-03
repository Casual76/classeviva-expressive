package dev.antigravity.classevivaexpressive.feature.assistant.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsActions
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsOptions
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsSlots
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsState
import dev.antigravity.fluidengine.aiui.settings.fluidAiSettingsSection
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.util.Locale

/** Le parole di questa app dentro la sezione dell'engine: cosa fa l'interruttore e cosa parte dal telefono. */
private val AssistantSettingsStrings = FluidAiSettingsStrings(
  enabledOn = "Acceso: il tasto \"Chiedi all'AI\" compare sopra la barra.",
  enabledOff = "Spento: nessun tasto, nessuna richiesta parte.",
  privacyNote = "Le tue domande e i dati del registro che servono per rispondere (voti, compiti, comunicazioni, " +
    "allegati che chiedi di leggere, con nome, classe e scuola) partono verso il servizio scelto, con la tua " +
    "chiave. Le conversazioni restano su questo telefono.",
)

/**
 * Impostazioni -> Assistente IA: l'interruttore (che la prima volta passa dal consenso), le chiavi
 * con la guida, l'ordine dei servizi, i modelli, le preferenze e la privacy sono la sezione dell'engine
 * (`fluidAiSettingsSection`); resta all'app l'uso, con la via per le conversazioni. Sono voci di
 * una lista: le ospita `SettingsScreen`, che tiene la pagina.
 */
fun LazyListScope.assistantSettingsItems(
  viewModel: AssistantSettingsViewModel,
  state: AssistantSettingsUiState,
  onOpenConsent: () -> Unit,
  onOpenHistory: (() -> Unit)?,
) {
  fluidAiSettingsSection(
    state = FluidAiSettingsState(
      settings = state.settings,
      keys = state.keys,
      catalogues = state.catalogues,
      keyInfo = state.keyInfo,
      recent = state.recent,
    ),
    actions = FluidAiSettingsActions(
      saveAndVerify = viewModel::saveAndVerify,
      removeKey = { viewModel.removeKey(it) },
      setEnabled = { viewModel.setEnabled(it) },
      setChatOrder = { viewModel.setChatOrder(it) },
      setSttOrder = { viewModel.setSttOrder(it) },
      setModel = { provider, tier, model -> viewModel.setModel(provider, tier, model) },
      setSttModel = { provider, model -> viewModel.setSttModel(provider, model) },
      setOpenRouterFallbacks = { viewModel.setOpenRouterFallbacks(it) },
      setOpenRouterDataPolicy = { viewModel.setOpenRouterDataPolicy(it) },
      refreshCatalogue = { viewModel.refreshCatalogue(it) },
      setThinking = { viewModel.setThinking(it) },
      setSpeakReplies = { viewModel.setSpeakReplies(it) },
      setActionsEnabled = { viewModel.setActionsEnabled(it) },
      // La prima accensione passa dalla pagina del consenso.
      onRequestConsent = onOpenConsent,
    ),
    strings = AssistantSettingsStrings,
    options = FluidAiSettingsOptions(requireConsent = true),
    slots = FluidAiSettingsSlots(
      footer = {
        item { FluidSectionHeader(title = "Uso") }
        item { AssistantUsage(state, onOpenHistory) }
      },
    ),
  )
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
  }
}

private fun formatTokens(tokens: Long): String = when {
  tokens >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", tokens / 1_000_000.0)
  tokens >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", tokens / 1_000.0)
  else -> tokens.toString()
}
