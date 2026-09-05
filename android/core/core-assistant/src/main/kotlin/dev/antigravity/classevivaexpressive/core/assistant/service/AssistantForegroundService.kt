package dev.antigravity.classevivaexpressive.core.assistant.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantEngine
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantRuntime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Il service in primo piano che porta a termine una domanda anche se l'app viene chiusa: prende la
 * richiesta dal runtime, la esegue con l'engine, aggiorna la notifica di avanzamento e, se l'app
 * non e' davanti, lascia la risposta in una notifica. Si ferma da solo, sempre — anche su errore,
 * anche allo scadere del tempo che Android concede a un service di questo tipo.
 */
@AndroidEntryPoint
class AssistantForegroundService : Service() {

  @Inject lateinit var runtime: AssistantRuntime
  @Inject lateinit var engine: AssistantEngine
  @Inject lateinit var notifications: AssistantNotifications

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var work: Job? = null

  /** Cresce a ogni richiesta: solo il lavoro piu' recente ha il diritto di fermare il service. */
  private var generation = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      runtime.cancel()
      return START_NOT_STICKY
    }
    val request = runtime.takePendingRequest()
    if (request == null) {
      if (work?.isActive != true) stopSelf(startId)
      return START_NOT_STICKY
    }
    val notification = notifications.progress(request.question, "Un momento…")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(AssistantNotifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(AssistantNotifications.PROGRESS_ID, notification)
    }
    val mine = ++generation
    work?.cancel()
    work = scope.launch {
      val updater = launch {
        runtime.state.map { notifications.statusLine(it) }.distinctUntilChanged().collect { notifications.updateProgress(request.question, it) }
      }
      val result = try {
        engine.execute(request)
      } catch (e: CancellationException) {
        null
      } finally {
        updater.cancel()
      }
      if (result != null && !runtime.appInForeground) notifications.showResult(result)
      if (mine == generation) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.cancelProgress()
        stopSelf(startId)
      }
    }
    return START_NOT_STICKY
  }

  /** Android 15: il tempo per questo tipo di service e' finito. Si ferma, e la domanda risulta fermata. */
  override fun onTimeout(startId: Int, fgsType: Int) {
    runtime.cancel()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_STOP = "dev.antigravity.classevivaexpressive.assistant.STOP"
  }
}
