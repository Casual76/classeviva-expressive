package dev.antigravity.classevivaexpressive.core.data.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.data.repository.AbsencesSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.HomeworkSection
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationChannelStatus
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val HomeworkChannelId = "compiti"
const val CommunicationsChannelId = "comunicazioni"
const val AbsencesChannelId = "assenze"
const val GradesChannelId = "voti"
const val TestChannelId = "test"

internal const val HomeworkCacheSection = HomeworkSection
internal const val CommunicationsCacheSection = CommunicationsSection
internal const val AbsencesCacheSection = AbsencesSection
internal const val GradesCacheSection = GradesSection

data class SyncSnapshotPayloads(
  val homeworks: String? = null,
  val communications: String? = null,
  val absences: String? = null,
  val grades: String? = null,
)

data class ChannelDefinition(
  val id: String,
  val label: String,
  val description: String,
  val importance: Int,
)

private val channelDefinitions = listOf(
  ChannelDefinition(
    id = HomeworkChannelId,
    label = "Compiti",
    description = "Aggiornamenti relativi a compiti, verifiche e agenda.",
    importance = NotificationManager.IMPORTANCE_DEFAULT,
  ),
  ChannelDefinition(
    id = CommunicationsChannelId,
    label = "Comunicazioni",
    description = "Bacheca, circolari e comunicazioni da leggere.",
    importance = NotificationManager.IMPORTANCE_DEFAULT,
  ),
  ChannelDefinition(
    id = AbsencesChannelId,
    label = "Assenze",
    description = "Assenze, ritardi e uscite da controllare.",
    importance = NotificationManager.IMPORTANCE_HIGH,
  ),
  ChannelDefinition(
    id = GradesChannelId,
    label = "Voti",
    description = "Nuovi voti e aggiornamenti alle valutazioni.",
    importance = NotificationManager.IMPORTANCE_HIGH,
  ),
  ChannelDefinition(
    id = TestChannelId,
    label = "Test",
    description = "Notifiche di test e diagnostica locale.",
    importance = NotificationManager.IMPORTANCE_DEFAULT,
  ),
)

object AppNotificationChannels {
  fun create(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    channelDefinitions.forEach { definition ->
      val existing = manager.getNotificationChannel(definition.id)
      if (existing == null) {
        manager.createNotificationChannel(
          NotificationChannel(definition.id, definition.label, definition.importance).apply {
            description = definition.description
          },
        )
      }
    }
  }

  fun labels(): Map<String, String> = channelDefinitions.associate { it.id to it.label }
}

fun readNotificationRuntimeState(context: Context): NotificationRuntimeState {
  AppNotificationChannels.create(context)
  val managerCompat = NotificationManagerCompat.from(context)
  val manager = context.getSystemService(NotificationManager::class.java)
  val permissionGranted = hasNotificationPermission(context)
  val channels = channelDefinitions.map { definition ->
    val enabled = manager?.getNotificationChannel(definition.id)?.importance != NotificationManager.IMPORTANCE_NONE
    NotificationChannelStatus(
      id = definition.id,
      label = definition.label,
      enabled = enabled,
    )
  }
  return NotificationRuntimeState(
    permissionGranted = permissionGranted,
    appNotificationsEnabled = managerCompat.areNotificationsEnabled(),
    channels = channels,
  )
}

fun sendTestNotification(
  context: Context,
  preferences: NotificationPreferences,
): Result<Unit> = runCatching {
  require(preferences.enabled) { "Attiva prima l'interruttore generale delle notifiche." }
  require(preferences.test) { "Il canale di test e disattivato nelle preferenze locali." }
  val runtime = readNotificationRuntimeState(context)
  require(runtime.appNotificationsEnabled) { "Le notifiche sono disattivate nelle impostazioni di sistema." }
  require(runtime.permissionGranted) { "Concedi il permesso notifiche per inviare il test." }

  AppNotificationChannels.create(context)
  notifyCompat(
    context = context,
    notificationId = 3111,
    notification = NotificationCompat.Builder(context, TestChannelId)
      .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
      .setContentTitle("Classeviva Expressive")
      .setContentText("Notifica di test inviata correttamente.")
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText("Notifica di test inviata correttamente dal canale Test."),
      )
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)
      .build(),
  ).getOrThrow()
}

@Singleton
class SyncNotificationDispatcher @Inject constructor(
  @ApplicationContext private val context: Context,
  private val json: Json,
  private val settingsStore: SettingsStore,
) {
  suspend fun dispatch(previous: SyncSnapshotPayloads, current: SyncSnapshotPayloads) {
    val preferences = settingsStore.settings.first().notificationPreferences
    if (!preferences.enabled) return

    val runtime = readNotificationRuntimeState(context)
    if (!runtime.permissionGranted || !runtime.appNotificationsEnabled) return

    AppNotificationChannels.create(context)

    if (preferences.homework) {
      val before = decodeList<Homework>(previous.homeworks)
      val after = decodeList<Homework>(current.homeworks)
      val newItems = after.filterNot { candidate -> before.any { it.id == candidate.id } }
      if (newItems.isNotEmpty()) {
        showNotification(
          channelId = HomeworkChannelId,
          notificationId = 4100 + newItems.hashCode(),
          title = if (newItems.size == 1) "Nuovo compito" else "${newItems.size} nuovi compiti",
          text = if (newItems.size == 1) {
            listOf(newItems.first().subject, newItems.first().description).filter { it.isNotBlank() }.joinToString(" - ")
          } else {
            "Agenda aggiornata con nuovi compiti o verifiche."
          },
        )
      }
    }

    if (preferences.communications) {
      val before = decodeList<Communication>(previous.communications)
      val after = decodeList<Communication>(current.communications)
      val newItems = after.filterNot { candidate -> before.any { it.id == candidate.id && it.pubId == candidate.pubId } }
      if (newItems.isNotEmpty()) {
        showNotification(
          channelId = CommunicationsChannelId,
          notificationId = 4200 + newItems.hashCode(),
          title = if (newItems.size == 1) "Nuova comunicazione" else "${newItems.size} nuove comunicazioni",
          text = if (newItems.size == 1) {
            newItems.first().title
          } else {
            "La bacheca contiene nuovi avvisi da leggere."
          },
        )
      }
    }

    if (preferences.absences) {
      val before = decodeList<AbsenceRecord>(previous.absences)
      val after = decodeList<AbsenceRecord>(current.absences)
      val newItems = after.filterNot { candidate ->
        before.any { existing ->
          existing.id == candidate.id &&
            existing.date == candidate.date &&
            existing.type == candidate.type
        }
      }
      if (newItems.isNotEmpty()) {
        val latest = newItems.maxByOrNull { it.date } ?: newItems.first()
        showNotification(
          channelId = AbsencesChannelId,
          notificationId = 4300 + newItems.hashCode(),
          title = if (newItems.size == 1) "Nuova presenza da controllare" else "${newItems.size} nuovi eventi presenze",
          text = if (newItems.size == 1) {
            "${absenceLabel(latest)} del ${readableDate(latest.date)}"
          } else {
            "Assenze, ritardi o uscite anticipate aggiornati nel registro."
          },
        )
      }
    }

    if (preferences.grades) {
      val before = decodeList<Grade>(previous.grades)
      val after = decodeList<Grade>(current.grades)
      val newItems = after.filterNot { candidate -> before.any { it.id == candidate.id } }
      if (newItems.isNotEmpty()) {
        val latest = newItems.maxByOrNull { it.date } ?: newItems.first()
        showNotification(
          channelId = GradesChannelId,
          notificationId = 4400 + newItems.hashCode(),
          title = if (newItems.size == 1) "Nuovo voto" else "${newItems.size} nuovi voti",
          text = if (newItems.size == 1) {
            "${latest.valueLabel} in ${latest.subject}"
          } else {
            "Nuovi voti o valutazioni aggiornate nel registro."
          },
        )
      }
    }
  }

  private inline fun <reified T> decodeList(payload: String?): List<T> {
    val source = payload?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString<List<T>>(source) }.getOrDefault(emptyList())
  }

  private fun showNotification(
    channelId: String,
    notificationId: Int,
    title: String,
    text: String,
  ) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val contentIntent = launchIntent?.let {
      PendingIntent.getActivity(
        context,
        channelId.hashCode(),
        it,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }

    notifyCompat(
      context = context,
      notificationId = notificationId,
      notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .setPriority(
          if (channelId == AbsencesChannelId) NotificationCompat.PRIORITY_HIGH
          else NotificationCompat.PRIORITY_DEFAULT,
        )
        .apply {
          if (contentIntent != null) {
            setContentIntent(contentIntent)
          }
        }
        .build(),
    )
  }

  private fun absenceLabel(record: AbsenceRecord): String {
    return when (record.type) {
      dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType.ABSENCE -> "Assenza"
      dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType.LATE -> "Ritardo"
      dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType.EXIT -> "Uscita anticipata"
    }
  }

  private fun readableDate(value: String): String {
    val parsed = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
    return "%02d/%02d/%04d".format(parsed.dayOfMonth, parsed.monthValue, parsed.year)
  }
}

private fun hasNotificationPermission(context: Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
  } else {
    true
  }
}

@SuppressLint("MissingPermission")
private fun notifyCompat(
  context: Context,
  notificationId: Int,
  notification: Notification,
): Result<Unit> {
  if (!hasNotificationPermission(context)) {
    return Result.failure(SecurityException("Permesso notifiche non concesso."))
  }
  return runCatching {
    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }
}
