package dev.antigravity.classevivaexpressive.feature.assistant.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantTotals
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantRuntime
import dev.antigravity.fluidengine.ai.keys.AiKeyStore
import dev.antigravity.fluidengine.ai.keys.AiKeyVerifier
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.AiSettingsStore
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.keys.ModelCatalogStore
import dev.antigravity.fluidengine.ai.keys.ThinkingLevel
import dev.antigravity.fluidengine.ai.keys.VerifyResult
import dev.antigravity.fluidengine.ai.orchestrator.AiDiagnosticsLog
import dev.antigravity.fluidengine.ai.orchestrator.AiRequestLog
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.OpenRouterKeyInfo
import dev.antigravity.fluidengine.ai.provider.ProviderId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssistantSettingsUiState(
  val settings: AiSettings = AiSettings(),
  val keys: Map<ProviderId, KeyState> = emptyMap(),
  val catalogues: Map<ProviderId, ModelCatalogue> = emptyMap(),
  val keyInfo: Map<ProviderId, OpenRouterKeyInfo> = emptyMap(),
  val totals: AssistantTotals? = null,
  val recent: List<AiRequestLog> = emptyList(),
) {
  val verified: Set<ProviderId> get() = keys.filterValues { it.verified }.keys
  val enabled: Boolean get() = settings.enabled && verified.isNotEmpty()
}

/**
 * Le impostazioni dell'assistente: l'interruttore, le chiavi, l'ordine dei provider, i modelli per
 * livello, le preferenze, i totali. Tutto passa dagli store dell'engine; il ViewModel li unisce in
 * uno stato solo e traduce i tocchi in scritture.
 */
@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
  private val settingsStore: AiSettingsStore,
  private val keyStore: AiKeyStore,
  private val verifier: AiKeyVerifier,
  private val catalogs: ModelCatalogStore,
  private val diagnostics: AiDiagnosticsLog,
  private val runtime: AssistantRuntime,
) : ViewModel() {

  private val core = combine(settingsStore.settings, keyStore.states, catalogs.catalogues, verifier.keyInfo) { settings, keys, catalogues, info ->
    AssistantSettingsUiState(settings = settings, keys = keys, catalogues = catalogues, keyInfo = info)
  }

  val state: StateFlow<AssistantSettingsUiState> = combine(core, runtime.totals(), diagnostics.entries) { base, totals, recent ->
    base.copy(totals = totals, recent = recent)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettingsUiState())

  init {
    // I cataloghi si leggono dal disco all'apertura e si rinfrescano se vecchi di un giorno.
    viewModelScope.launch {
      keyStore.currentStates().filterValues { it.verified }.keys.forEach { provider ->
        launch { runCatching { verifier.refreshIfStale(provider) } }
      }
    }
  }

  fun setEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setEnabled(enabled) }

  /** Il consenso e l'accensione insieme: e' la pagina di consenso a chiamarlo. */
  fun acceptConsentAndEnable() = viewModelScope.launch {
    settingsStore.setConsentAccepted(System.currentTimeMillis())
    settingsStore.setEnabled(true)
  }

  suspend fun saveAndVerify(provider: ProviderId, key: String?): VerifyResult {
    if (!key.isNullOrBlank()) keyStore.set(provider, key)
    return verifier.verify(provider)
  }

  fun removeKey(provider: ProviderId) = viewModelScope.launch { keyStore.set(provider, null) }

  fun setChatOrder(order: List<ProviderId>) = viewModelScope.launch { settingsStore.setChatOrder(order) }
  fun setSttOrder(order: List<ProviderId>) = viewModelScope.launch { settingsStore.setSttOrder(order) }

  fun setModel(provider: ProviderId, tier: ModelTier, model: String?) = viewModelScope.launch { settingsStore.setModel(provider, tier, model) }
  fun setSttModel(provider: ProviderId, model: String?) = viewModelScope.launch { settingsStore.setSttModel(provider, model) }
  fun setOpenRouterFallbacks(models: List<String>) = viewModelScope.launch { settingsStore.setOpenRouterFallbacks(models) }
  fun setOpenRouterDataCollection(allow: Boolean) = viewModelScope.launch { settingsStore.setOpenRouterAllowDataCollection(allow) }

  fun setThinking(level: ThinkingLevel) = viewModelScope.launch { settingsStore.setThinking(level) }
  fun setSpeakReplies(speak: Boolean) = viewModelScope.launch { settingsStore.setSpeakReplies(speak) }
  fun setActionsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setActionsEnabled(enabled) }

  fun refreshCatalogue(provider: ProviderId) = viewModelScope.launch { runCatching { verifier.refreshIfStale(provider, force = true) } }
}
