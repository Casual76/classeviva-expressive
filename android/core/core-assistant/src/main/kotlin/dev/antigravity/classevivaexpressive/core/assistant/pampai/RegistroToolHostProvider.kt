package dev.antigravity.classevivaexpressive.core.assistant.pampai

import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.assistant.actions.ActionResult
import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantAction
import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantActionSink
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantContextFactory
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantActionExecutor
import dev.antigravity.classevivaexpressive.core.assistant.tools.AssistantToolContext
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.fluidengine.ai.bridge.AiToolHostProvider
import dev.antigravity.fluidengine.ai.bridge.ReadyState
import dev.antigravity.fluidengine.ai.bridge.RemoteCall
import dev.antigravity.fluidengine.ai.provider.ModelCapabilities
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.first

/** Cio' che il provider prende dal grafo: non e' un'Activity, quindi se lo cerca da solo. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RegistroBridgeEntryPoint {
  fun registry(): ToolRegistry<AssistantToolContext>
  fun contextFactory(): AssistantContextFactory
  fun executor(): AssistantActionExecutor
  fun auth(): AuthRepository
  fun grades(): GradesRepository
}

/**
 * Il registro visto da PampAI/Aria: gli stessi strumenti dell'assistente interno, eseguiti qui —
 * dove ci sono la sessione e i dati — e protetti dal permesso `signature` del bridge, cioe' da
 * un'app firmata con la stessa chiave.
 *
 * Le conferme le chiede il chiamante (con `describe`, che qui torna le parole vere: il titolo
 * della circolare, la materia dell'obiettivo); quando la chiamata arriva con `confirmed`, le
 * azioni girano senza ripassare dal cancello dell'app, che non avrebbe una schermata a cui
 * chiedere.
 */
class RegistroToolHostProvider : AiToolHostProvider<AssistantToolContext>() {

  private val entry: RegistroBridgeEntryPoint by lazy {
    EntryPointAccessors.fromApplication(context!!.applicationContext, RegistroBridgeEntryPoint::class.java)
  }

  override fun registry(): ToolRegistry<AssistantToolContext> = entry.registry()

  override suspend fun context(call: RemoteCall): AssistantToolContext = entry.contextFactory().create(
    actionsEnabled = true,
    actions = BridgeActionSink(entry.executor(), context!!),
    // Il chiamante ha un modello profondo suo e sa cosa farsene: gli allegati partono nella forma
    // piena (PDF e immagini), e se il suo modello non li regge ci pensa lui a tradurli.
    deepCapabilities = ModelCapabilities(vision = true, documents = true),
  )

  override fun domain(): String = "cv"

  override fun appLabel(): String = "ClasseViva Expressive"

  override fun routerHint(): String =
    "il registro elettronico della scuola: voti e medie, agenda con verifiche e compiti, orario delle lezioni, comunicazioni e circolari, assenze, note, materiali, la giornata di scuola"

  /** Le materie: sono i nomi che l'utente dice a voce ("com'e' andata in sistemi?"). */
  override fun vocabulary(): List<String> = runCatching {
    kotlinx.coroutines.runBlocking { entry.grades().observeSubjects().first().map { it.description } }
  }.getOrDefault(emptyList())

  override fun ready(): ReadyState {
    val session = entry.auth().session.value
    return if (session?.studentId != null) ReadyState(true) else ReadyState(false, "nessuno studente collegato: apri ClasseViva Expressive e accedi al registro")
  }

  override fun partsAuthority(): String? = "${context?.packageName}.fileprovider"
}

/**
 * Le azioni chieste da fuori: gia' confermate dal chiamante, quindi si eseguono. "Apri una pagina"
 * in piu' porta l'app davanti: da un assistente esterno, aprire qualcosa che resta dietro non e'
 * aprire niente.
 */
private class BridgeActionSink(
  private val executor: AssistantActionExecutor,
  private val context: Context,
) : AssistantActionSink {

  override suspend fun perform(action: AssistantAction): ActionResult {
    val result = executor.performPreConfirmed(action)
    if (action is AssistantAction.Open) {
      val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { intent?.let { context.startActivity(it) } }
    }
    return result
  }
}
