package dev.antigravity.classevivaexpressive.core.assistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.assistant.runtime.ExecutionResult
import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.provider.ModelTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le due notifiche dell'assistente: quella di avanzamento, che tiene vivo il service e dice cosa
 * sta facendo, e quella della risposta, che compare solo se l'app non e' davanti e riapre la
 * conversazione. Stesso canale, importanza bassa: e' lavoro chiesto dall'utente, non un allarme.
 */
@Singleton
class AssistantNotifications @Inject constructor(@ApplicationContext private val context: Context) {

  private val manager: NotificationManager? get() = context.getSystemService(NotificationManager::class.java)

  fun ensureChannel() {
    val manager = manager ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Assistente", NotificationManager.IMPORTANCE_LOW).apply {
          description = "Avanzamento e risposte dell'assistente."
        },
      )
    }
  }

  fun progress(question: String, status: String): Notification {
    ensureChannel()
    val stop = PendingIntent.getService(
      context, 1,
      Intent(context, AssistantForegroundService::class.java).setAction(AssistantForegroundService.ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
      .setContentTitle(question.take(60))
      .setContentText(status)
      .setStyle(NotificationCompat.BigTextStyle().bigText(status))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setProgress(0, 0, true)
      .addAction(0, "Ferma", stop)
      .setContentIntent(openConversation(null))
      .build()
  }

  /** Dal 13 le notifiche sono un permesso a runtime: senza, niente `notify` — l'avanzamento resta nella card. */
  private fun canPost(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  /** L'unico punto che chiama `notify`: il controllo del permesso sta qui, e lint lo vede qui. */
  @SuppressLint("MissingPermission")
  private fun post(id: Int, notification: Notification) {
    if (!canPost()) return
    runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
  }

  fun updateProgress(question: String, status: String) = post(PROGRESS_ID, progress(question, status))

  fun showResult(result: ExecutionResult) {
    if (result.cancelled) return
    ensureChannel()
    val text = result.answer?.let { firstLines(it) } ?: failureText(result.failure ?: FailureKind.UNKNOWN)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
      .setContentTitle(if (result.answer != null) "Risposta pronta" else "L'assistente si e' fermato")
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(result.question.take(80)))
      .setAutoCancel(true)
      .setContentIntent(openConversation(result.conversationId))
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .build()
    post(RESULT_ID, notification)
  }

  fun cancelProgress() {
    runCatching { NotificationManagerCompat.from(context).cancel(PROGRESS_ID) }
  }

  /** La riga di stato dal `statusKey` dello stato: la stessa lingua della card, senza risorse. */
  fun statusLine(state: AssistantState): String = when (state) {
    is AssistantState.Classifying -> "Capisco cosa serve…"
    is AssistantState.Working -> {
      val base = when (state.statusKey) {
        RegistroToolGroup.VOTI.statusKey -> "Guardo i voti…"
        RegistroToolGroup.AGENDA.statusKey -> "Guardo l'agenda…"
        RegistroToolGroup.ORARIO.statusKey -> "Guardo l'orario…"
        RegistroToolGroup.BACHECA.statusKey -> "Leggo la bacheca…"
        RegistroToolGroup.ASSENZE.statusKey -> "Controllo le assenze…"
        RegistroToolGroup.STATISTICHE.statusKey -> "Faccio i conti…"
        RegistroToolGroup.DIDATTICA.statusKey -> "Cerco fra i materiali…"
        RegistroToolGroup.APP.statusKey -> "Agisco nell'app…"
        "more_tools" -> "Mi serve dell'altro…"
        else -> if (state.tier == ModelTier.DEEP) "Analizzo con calma: puo' volerci un po'…" else "Penso…"
      }
      if (state.tier == ModelTier.DEEP && state.statusKey == "thinking") base else if (state.tier == ModelTier.DEEP) "$base (modello profondo)" else base
    }
    is AssistantState.WaitingRateLimit -> "Il servizio e' al limite: riprovo fra ${state.secondsLeft} s"
    is AssistantState.SwitchingProvider -> "Cambio servizio…"
    is AssistantState.Answering -> "Rispondo…"
    is AssistantState.AwaitingConfirmation -> "Aspetto la tua conferma nell'app"
    is AssistantState.Done -> "Fatto"
    is AssistantState.Failed -> failureText(state.kind)
    is AssistantState.Cancelled -> "Fermato"
    else -> "Un momento…"
  }

  fun failureText(kind: FailureKind): String = when (kind) {
    FailureKind.NO_KEYS -> "Nessuna chiave verificata: apri le impostazioni dell'assistente."
    FailureKind.UNAUTHORIZED -> "La chiave non e' piu' valida: controllala nelle impostazioni."
    FailureKind.RATE_LIMITED -> "Il servizio e' al limite di richieste: riprova fra poco."
    FailureKind.NETWORK -> "Niente rete."
    FailureKind.TIMEOUT -> "Ci ha messo troppo: riprova con una domanda piu' semplice."
    FailureKind.BLOCKED -> "Il servizio ha rifiutato la richiesta."
    FailureKind.PROVIDER -> "Il servizio ha risposto con un errore."
    FailureKind.MICROPHONE -> "Il microfono non e' disponibile."
    FailureKind.TRANSCRIPTION -> "Non sono riuscito a trascrivere."
    FailureKind.UNKNOWN -> "Qualcosa e' andato storto."
  }

  private fun firstLines(answer: String): String = answer.lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n").take(400)

  private fun openConversation(conversationId: Long?): PendingIntent? {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    val uri = Uri.parse(if (conversationId != null) "classevivaexpressive://open/assistant?conversationId=$conversationId" else "classevivaexpressive://open/assistant")
    launch.action = Intent.ACTION_VIEW
    launch.data = uri
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return PendingIntent.getActivity(context, (conversationId ?: 0L).toInt() + 100, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  companion object {
    const val CHANNEL_ID = "assistente"
    const val PROGRESS_ID = 4101
    const val RESULT_ID = 4102
  }
}
