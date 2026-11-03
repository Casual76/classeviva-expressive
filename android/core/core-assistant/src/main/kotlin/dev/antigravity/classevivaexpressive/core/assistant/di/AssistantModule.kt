package dev.antigravity.classevivaexpressive.core.assistant.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.assistant.tools.AllTools
import dev.antigravity.classevivaexpressive.core.assistant.tools.AssistantToolContext
import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import dev.antigravity.fluidengine.ai.keys.AiKeyStore
import dev.antigravity.fluidengine.ai.keys.AiKeyVerifier
import dev.antigravity.fluidengine.ai.keys.AiSettingsStore
import dev.antigravity.fluidengine.ai.keys.ModelCatalogStore
import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.orchestrator.AiConfirmationGate
import dev.antigravity.fluidengine.ai.orchestrator.AiDiagnosticsLog
import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestrator
import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestratorConfig
import dev.antigravity.fluidengine.ai.orchestrator.AiRouter
import dev.antigravity.fluidengine.ai.provider.ProviderFactory
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import javax.inject.Singleton

/** L'assistente cablato in Hilt: l'engine sotto, il registro sopra. Tutto singleton, tutto pigro. */
@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

  @Provides
  @Singleton
  fun provideAiHttp(@ApplicationContext context: Context): AiHttp {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    // Letture larghe: una risposta lunga sul modello profondo puo' tacere per un pezzo.
    return AiHttp(userAgent = "ClassevivaExpressive/$version", readTimeoutMillis = 120_000, streamChunkTimeoutMillis = 45_000)
  }

  @Provides
  @Singleton
  fun provideAiKeyStore(@ApplicationContext context: Context): AiKeyStore = AiKeyStore(context)

  @Provides
  @Singleton
  fun provideAiSettingsStore(@ApplicationContext context: Context): AiSettingsStore = AiSettingsStore(context)

  @Provides
  @Singleton
  fun provideModelCatalogStore(@ApplicationContext context: Context): ModelCatalogStore = ModelCatalogStore(File(context.filesDir, "ai/models"))

  @Provides
  @Singleton
  fun provideProviderFactory(http: AiHttp, keys: AiKeyStore, settings: AiSettingsStore, catalogs: ModelCatalogStore): ProviderFactory =
    ProviderFactory(
      http = http,
      keys = keys,
      settings = settings,
      referer = "https://github.com/Casual76/classeviva-expressive",
      appTitle = "ClasseViva Expressive",
      catalogs = catalogs,
    )

  @Provides
  @Singleton
  fun provideAiKeyVerifier(keys: AiKeyStore, settings: AiSettingsStore, providers: ProviderFactory, catalogs: ModelCatalogStore): AiKeyVerifier =
    AiKeyVerifier(keys, settings, providers, catalogs)

  @Provides
  @Singleton
  fun provideDiagnostics(): AiDiagnosticsLog = AiDiagnosticsLog()

  /** La configurazione dell'orchestratore: sta in un posto solo perche' ne dipende anche il cancello. */
  @Provides
  @Singleton
  fun provideOrchestratorConfig(): AiOrchestratorConfig = assistantOrchestratorConfig()

  /** Il cancello delle conferme prende la finestra dalla stessa configurazione: i due numeri non si scostano. */
  @Provides
  @Singleton
  fun provideConfirmationGate(config: AiOrchestratorConfig): AiConfirmationGate = AiConfirmationGate(config)

  @Provides
  @Singleton
  fun provideToolRegistry(): ToolRegistry<AssistantToolContext> = AllTools.registry()

  @Provides
  @Singleton
  fun provideRouter(): AiRouter = AiRouter(
    groups = RegistroToolGroup.entries,
    actionGroup = RegistroToolGroup.APP,
    domainHint = "il registro elettronico di uno studente: voti, agenda, compiti, orario, comunicazioni della scuola, assenze",
    defaultGroups = listOf(RegistroToolGroup.VOTI, RegistroToolGroup.AGENDA),
  )

  @Provides
  @Singleton
  fun provideOrchestrator(
    registry: ToolRegistry<AssistantToolContext>,
    router: AiRouter,
    diagnostics: AiDiagnosticsLog,
    config: AiOrchestratorConfig,
    gate: AiConfirmationGate,
  ): AiOrchestrator<AssistantToolContext> =
    AiOrchestrator(
      registry = registry,
      router = router,
      diagnostics = diagnostics,
      config = config,
      gate = gate,
    )
}

/**
 * I tempi e i limiti dell'assistente del registro.
 *
 * Il tetto dei tool e' quello di *lavoro* (30 s): la finestra delle conferme (60 s del cancello) la
 * somma l'orchestratore ai tool che la aspettano (`awaitsConfirmation` sul gruppo `APP`), e prima i
 * due numeri erano uno solo scritto a mano (90 s) che andava tenuto sopra il cancello.
 */
internal fun assistantOrchestratorConfig(): AiOrchestratorConfig = AiOrchestratorConfig(
  // Il service ha quattro minuti; una card che aspetta ne avrebbe uno e mezzo.
  // Dodici giri, non otto: qui una domanda vera ne vale piu' d'uno — cerca, non trova,
  // riprova con un'altra parola, apre la circolare, legge l'allegato, risponde.
  maxRounds = 12,
  maxMoreTools = 4,
  toolTimeoutMillis = 30_000L,
  totalBudgetMillis = 300_000L,
  finalReserveMillis = 25_000L,
  // Un elenco di voti dell'anno o il confronto fra tutte le materie non sta in 2400 caratteri.
  toolTextChars = 4_000,
  maxOutputTokens = 2_000,
)
