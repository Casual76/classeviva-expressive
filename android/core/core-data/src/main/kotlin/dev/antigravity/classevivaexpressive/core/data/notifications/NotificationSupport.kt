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
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.data.repository.LessonsSection
import dev.antigravity.classevivaexpressive.core.data.repository.AbsencesSection
import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.HomeworkSection
import dev.antigravity.classevivaexpressive.core.data.repository.NotesSection
import dev.antigravity.classevivaexpressive.core.data.repository.buildLessonsWithFallback
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationChannelStatus
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

const val HomeworkChannelId = "compiti"
const val CommunicationsChannelId = "comunicazioni"
const val AbsencesChannelId = "assenze"
const val GradesChannelId = "voti"
const val AgendaChannelId = "verifiche_agenda"
const val NotesChannelId = "annotazioni"
const val TestChannelId = "test"
const val LiveTimetableChannelId = "orario_live_v2"
private const val LegacyLiveTimetableChannelId = "orario_live"
private const val RequestPromotedOngoingExtra = "android.requestPromotedOngoing"

internal const val HomeworkCacheSection = HomeworkSection
internal const val CommunicationsCacheSection = CommunicationsSection
internal const val AbsencesCacheSection = AbsencesSection
internal const val GradesCacheSection = GradesSection
internal const val AgendaCacheSection = AgendaSection
internal const val NotesCacheSection = NotesSection
internal const val LessonsCacheSection = LessonsSection

data class SyncSnapshotPayloads(
  val homeworks: String? = null,
  val communications: String? = null,
  val absences: String? = null,
  val grades: String? = null,
  val agenda: String? = null,
  val notes: String? = null,
  val lessons: String? = null,
)

data class ChannelDefinition(
  val id: String,
  val label: String,
  val description: String,
  val importance: Int,
)

private val channelDefinitions = listOf(
  ChannelDefinition(
    id = GradesChannelId,
    label = "Voti",
    description = "Nuovi voti e aggiornamenti alle valutazioni.",
    importance = NotificationManager.IMPORTANCE_HIGH,
  ),
  ChannelDefinition(
    id = HomeworkChannelId,
    label = "Compiti",
    description = "Nuovi compiti assegnati dai docenti.",
    importance = NotificationManager.IMPORTANCE_DEFAULT,
  ),
  ChannelDefinition(
    id = AgendaChannelId,
    label = "Verifiche & Agenda",
    description = "Verifiche, interrogazioni e altri eventi aggiunti all'agenda.",
    importance = NotificationManager.IMPORTANCE_HIGH,
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
    id = NotesChannelId,
    label = "Annotazioni",
    description = "Nuove annotazioni e note dei docenti.",
    importance = NotificationManager.IMPORTANCE_DEFAULT,
  ),
  ChannelDefinition(
    id = TestChannelId,
    label = "Test",
    description = "Notifiche di test e diagnostica locale.",
    importance = NotificationManager.IMPORTANCE_DEFAULT,
  ),
  ChannelDefinition(
    id = LiveTimetableChannelId,
    label = "Orario live",
    description = "Stato live delle lezioni della mattinata (Now Bar / Live Updates).",
    importance = NotificationManager.IMPORTANCE_HIGH,
  ),
)

object AppNotificationChannels {
  fun create(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    runCatching { manager.deleteNotificationChannel(LegacyLiveTimetableChannelId) }
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
): Result<Unit> = sendTestNotificationForChannel(context, TestChannelId, preferences)

fun sendTestNotificationForChannel(
  context: Context,
  channelId: String,
  preferences: NotificationPreferences,
): Result<Unit> = runCatching {
  require(preferences.enabled) { "Attiva prima l'interruttore generale delle notifiche." }
  val runtime = readNotificationRuntimeState(context)
  require(runtime.appNotificationsEnabled) { "Le notifiche sono disattivate nelle impostazioni di sistema." }
  require(runtime.permissionGranted) { "Concedi il permesso notifiche per inviare il test." }
  AppNotificationChannels.create(context)

  val notification = when (channelId) {
    GradesChannelId -> buildGradeTestNotification(context)
    HomeworkChannelId -> buildHomeworkTestNotification(context)
    AgendaChannelId -> buildAgendaTestNotification(context)
    CommunicationsChannelId -> buildCommunicationTestNotification(context)
    AbsencesChannelId -> buildAbsenceTestNotification(context)
    NotesChannelId -> buildNoteTestNotification(context)
    else -> buildGenericTestNotification(context)
  }
  notifyCompat(context = context, notificationId = testNotificationId(channelId), notification = notification).getOrThrow()
}

private fun testNotificationId(channelId: String) = when (channelId) {
  GradesChannelId -> 3100
  HomeworkChannelId -> 3101
  AgendaChannelId -> 3102
  CommunicationsChannelId -> 3103
  AbsencesChannelId -> 3104
  NotesChannelId -> 3105
  else -> 3111
}

private fun buildGradeTestNotification(context: Context): Notification {
  val bigText = "Matematica — Scritto\n${gradeEmoji(7.5)} 7.5 — Buona padronanza degli argomenti trattati.\n\nPhysics — Orale\n${gradeEmoji(5.0)} 5 — Preparazione insufficiente."
  return NotificationCompat.Builder(context, GradesChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("2 nuovi voti")
    .setContentText("${gradeEmoji(7.5)} 7.5 in Matematica · ${gradeEmoji(5.0)} 5 in Fisica")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText(bigText)
        .setSummaryText("Registro Elettronico"),
    )
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setAutoCancel(true)
    .build()
}

private fun buildHomeworkTestNotification(context: Context): Notification {
  val dueFormatted = readableDate(LocalDate.now().plusDays(3).toString())
  return NotificationCompat.Builder(context, HomeworkChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("Nuovo compito — Matematica")
    .setContentText("Esercizi pag. 142: 1→15 · entro $dueFormatted")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText("Matematica\nEsercizi pagina 142, esercizi da 1 a 15.\n\nConsegna entro: $dueFormatted")
        .setSummaryText("Agenda"),
    )
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setAutoCancel(true)
    .build()
}

private fun buildAgendaTestNotification(context: Context): Notification {
  val dateFormatted = readableDate(LocalDate.now().plusDays(5).toString())
  return NotificationCompat.Builder(context, AgendaChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("Verifica — Matematica")
    .setContentText("Funzioni e limiti · $dateFormatted")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText("Matematica\nVerifica scritta su funzioni e limiti.\n\nData: $dateFormatted")
        .setSummaryText("Agenda — Verifica"),
    )
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setAutoCancel(true)
    .build()
}

private fun buildCommunicationTestNotification(context: Context): Notification {
  return NotificationCompat.Builder(context, CommunicationsChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("Nuova comunicazione")
    .setContentText("Riunione genitori — ven 30 apr, ore 17:00 · Segreteria")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText("Mittente: Segreteria Didattica\n\nRiunione genitori programmata per venerdì 30 aprile alle ore 17:00 nell'aula magna.")
        .setSummaryText("Bacheca"),
    )
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setAutoCancel(true)
    .build()
}

private fun buildAbsenceTestNotification(context: Context): Notification {
  return NotificationCompat.Builder(context, AbsencesChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("Assenza registrata")
    .setContentText("Assenza del ${readableDate(LocalDate.now().minusDays(1).toString())} — da giustificare")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText("È stata registrata un'assenza del ${readableDate(LocalDate.now().minusDays(1).toString())}.\n\nRicordati di presentare la giustifica.")
        .setSummaryText("Presenze"),
    )
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setAutoCancel(true)
    .build()
}

private fun buildNoteTestNotification(context: Context): Notification {
  return NotificationCompat.Builder(context, NotesChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("Nuova annotazione")
    .setContentText("Condotta — Prof. Rossi · Nota disciplinare")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText("Categoria: Condotta\nDocente: Prof. Rossi\n\nNota disciplinare: comportamento scorretto durante la lezione.")
        .setSummaryText("Annotazioni"),
    )
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setAutoCancel(true)
    .build()
}

private fun buildGenericTestNotification(context: Context): Notification {
  return NotificationCompat.Builder(context, TestChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle("Classeviva Expressive")
    .setContentText("Notifica di test inviata correttamente.")
    .setStyle(
      NotificationCompat.BigTextStyle()
        .bigText("Notifica di test inviata correttamente dal canale Test."),
    )
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setAutoCancel(true)
    .build()
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
    val launchIntent = launchPendingIntent(context)

    if (preferences.grades) {
      dispatchGrades(previous, current, launchIntent)
    }
    if (preferences.agenda) {
      dispatchAgendaEvents(previous, current, launchIntent)
    }
    if (preferences.homework) {
      dispatchHomeworks(previous, current, launchIntent)
    }
    if (preferences.communications) {
      dispatchCommunications(previous, current, launchIntent)
    }
    if (preferences.absences) {
      dispatchAbsences(previous, current, launchIntent)
    }
    if (preferences.notes) {
      dispatchNotes(previous, current, launchIntent)
    }
    if (preferences.liveTimetable) {
      dispatchLiveTimetable(current, launchIntent)
    } else {
      NotificationManagerCompat.from(context).cancel(LiveTimetableNotificationId)
    }
  }

  private fun dispatchLiveTimetable(
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    if (Build.VERSION.SDK_INT < 36) return

    val lessons = buildLessonsWithFallback(
      lessons = decodeList<Lesson>(current.lessons),
      agenda = decodeList<AgendaItem>(current.agenda),
    )
    val today = LocalDate.now()
    val todayLessons = lessons
      .filter { it.date == today.toString() }
      .filterNot { it.subject.isBlank() && it.teacher.isNullOrBlank() }
      .sortedBy { it.time }

    if (todayLessons.isEmpty() || todayLessons.none { !it.teacher.isNullOrBlank() }) {
      NotificationManagerCompat.from(context).cancel(LiveTimetableNotificationId)
      return
    }

    val snapshot = LiveTimetableSnapshot.from(today, LocalTime.now(), todayLessons)
    if (snapshot == null) {
      NotificationManagerCompat.from(context).cancel(LiveTimetableNotificationId)
      return
    }

    notifyCompat(
      context = context,
      notificationId = LiveTimetableNotificationId,
      notification = buildLiveTimetableNotification(context, snapshot, launchIntent),
    )
  }

  private fun dispatchGrades(
    previous: SyncSnapshotPayloads,
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    val before = decodeList<Grade>(previous.grades)
    val after = decodeList<Grade>(current.grades)
    val newItems = after.filterNot { candidate -> before.any { it.id == candidate.id } }
    if (newItems.isEmpty()) return

    val notification = if (newItems.size == 1) {
      val grade = newItems.first()
      val emoji = gradeEmoji(grade.numericValue)
      val typeLabel = grade.type.ifBlank { null }
      val periodLabel = grade.period?.ifBlank { null }
      val bodyParts = buildList {
        add("${grade.subject}${if (typeLabel != null) " — $typeLabel" else ""}")
        add("$emoji ${grade.valueLabel}")
        if (!grade.description.isNullOrBlank()) add(grade.description)
        if (!grade.notes.isNullOrBlank()) add("Note: ${grade.notes}")
        if (!grade.teacher.isNullOrBlank()) add(grade.teacher)
        if (periodLabel != null) add("Periodo: $periodLabel")
      }
      val subtitle = "$emoji ${grade.valueLabel} · ${grade.subject}"
      NotificationCompat.Builder(context, GradesChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("Nuovo voto — ${grade.subject}")
        .setContentText(subtitle)
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText(bodyParts.joinToString("\n"))
            .setSummaryText("Registro Voti · ${readableDate(grade.date)}"),
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    } else {
      val inboxStyle = NotificationCompat.InboxStyle()
        .setSummaryText("Registro Voti — ${newItems.size} nuovi voti")
      newItems.sortedByDescending { it.date }.forEach { grade ->
        inboxStyle.addLine("${gradeEmoji(grade.numericValue)} ${grade.valueLabel.padEnd(5)} ${grade.subject}")
      }
      val preview = newItems.take(2).joinToString(" · ") { "${gradeEmoji(it.numericValue)} ${it.valueLabel} in ${it.subject}" }
      NotificationCompat.Builder(context, GradesChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("${newItems.size} nuovi voti")
        .setContentText(preview)
        .setStyle(inboxStyle)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    }
    notifyCompat(context, 4400, notification)
  }

  private fun dispatchAgendaEvents(
    previous: SyncSnapshotPayloads,
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    val before = decodeList<AgendaItem>(previous.agenda)
    val after = decodeList<AgendaItem>(current.agenda)
    val newRelevant = after.filter { it.category == AgendaCategory.ASSESSMENT || it.category == AgendaCategory.HOMEWORK }
    val prevRelevant = before.filter { it.category == AgendaCategory.ASSESSMENT || it.category == AgendaCategory.HOMEWORK }
    val newItems = newRelevant.filterNot { candidate -> prevRelevant.any { it.id == candidate.id } }
    if (newItems.isEmpty()) return

    val assessments = newItems.filter { it.category == AgendaCategory.ASSESSMENT }
    val homeworks = newItems.filter { it.category == AgendaCategory.HOMEWORK }

    if (assessments.isNotEmpty()) {
      val notification = if (assessments.size == 1) {
        val item = assessments.first()
        val subject = item.subject ?: item.title
        val detail = item.detail?.ifBlank { null } ?: item.subtitle
        NotificationCompat.Builder(context, AgendaChannelId)
          .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
          .setContentTitle("Verifica — $subject")
          .setContentText("${detail.take(80)} · ${readableDate(item.date)}")
          .setStyle(
            NotificationCompat.BigTextStyle()
              .bigText("$subject\n$detail\n\nData: ${readableDate(item.date)}")
              .setSummaryText("Agenda — Verifica"),
          )
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true)
          .applyLaunchIntent(launchIntent)
          .build()
      } else {
        val inboxStyle = NotificationCompat.InboxStyle()
          .setSummaryText("Agenda — ${assessments.size} verifiche")
        assessments.sortedBy { it.date }.forEach { item ->
          inboxStyle.addLine("${item.subject ?: item.title} · ${readableDate(item.date)}")
        }
        NotificationCompat.Builder(context, AgendaChannelId)
          .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
          .setContentTitle("${assessments.size} verifiche in agenda")
          .setContentText(assessments.take(2).joinToString(" · ") { it.subject ?: it.title })
          .setStyle(inboxStyle)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true)
          .applyLaunchIntent(launchIntent)
          .build()
      }
      notifyCompat(context, 4500, notification)
    }

    if (homeworks.isNotEmpty()) {
      val notification = if (homeworks.size == 1) {
        val item = homeworks.first()
        val subject = item.subject ?: item.title
        val detail = item.detail?.ifBlank { null } ?: item.subtitle
        NotificationCompat.Builder(context, AgendaChannelId)
          .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
          .setContentTitle("Compito da agenda — $subject")
          .setContentText("${detail.take(80)} · ${readableDate(item.date)}")
          .setStyle(
            NotificationCompat.BigTextStyle()
              .bigText("$subject\n$detail\n\nEntro: ${readableDate(item.date)}")
              .setSummaryText("Agenda — Compito"),
          )
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
          .applyLaunchIntent(launchIntent)
          .build()
      } else {
        val inboxStyle = NotificationCompat.InboxStyle()
          .setSummaryText("Agenda — ${homeworks.size} compiti")
        homeworks.sortedBy { it.date }.forEach { item ->
          inboxStyle.addLine("${item.subject ?: item.title} · ${readableDate(item.date)}")
        }
        NotificationCompat.Builder(context, AgendaChannelId)
          .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
          .setContentTitle("${homeworks.size} compiti aggiunti in agenda")
          .setContentText(homeworks.take(2).joinToString(" · ") { it.subject ?: it.title })
          .setStyle(inboxStyle)
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
          .applyLaunchIntent(launchIntent)
          .build()
      }
      notifyCompat(context, 4501, notification)
    }
  }

  private fun dispatchHomeworks(
    previous: SyncSnapshotPayloads,
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    val before = decodeList<Homework>(previous.homeworks)
    val after = decodeList<Homework>(current.homeworks)
    val newItems = after.filterNot { candidate -> before.any { it.id == candidate.id } }
    if (newItems.isEmpty()) return

    val notification = if (newItems.size == 1) {
      val hw = newItems.first()
      val desc = hw.description.ifBlank { "Nessuna descrizione." }
      NotificationCompat.Builder(context, HomeworkChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("Nuovo compito — ${hw.subject}")
        .setContentText("${desc.take(80)} · entro ${readableDate(hw.dueDate)}")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("${hw.subject}\n$desc\n\nConsegna entro: ${readableDate(hw.dueDate)}")
            .setSummaryText("Compiti"),
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    } else {
      val inboxStyle = NotificationCompat.InboxStyle()
        .setSummaryText("Compiti — ${newItems.size} nuovi")
      newItems.sortedBy { it.dueDate }.forEach { hw ->
        inboxStyle.addLine("${hw.subject} · entro ${readableDate(hw.dueDate)}")
      }
      NotificationCompat.Builder(context, HomeworkChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("${newItems.size} nuovi compiti")
        .setContentText(newItems.take(2).joinToString(" · ") { it.subject })
        .setStyle(inboxStyle)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    }
    notifyCompat(context, 4100, notification)
  }

  private fun dispatchCommunications(
    previous: SyncSnapshotPayloads,
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    val before = decodeList<Communication>(previous.communications)
    val after = decodeList<Communication>(current.communications)
    val newItems = after.filterNot { candidate ->
      before.any { it.id == candidate.id && it.pubId == candidate.pubId }
    }
    if (newItems.isEmpty()) return

    val notification = if (newItems.size == 1) {
      val comm = newItems.first()
      val preview = comm.contentPreview.ifBlank { comm.title }
      val senderLabel = comm.sender.ifBlank { null }
      val bodyLines = buildList {
        if (senderLabel != null) add("Mittente: $senderLabel")
        add(preview.take(300))
        if (!comm.category.isNullOrBlank()) add("Categoria: ${comm.category}")
        if (comm.needsAck) add("Richiede presa visione.")
        if (comm.needsReply) add("Richiede risposta.")
      }
      NotificationCompat.Builder(context, CommunicationsChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle(comm.title.ifBlank { "Nuova comunicazione" })
        .setContentText("${senderLabel ?: "Bacheca"} · ${readableDate(comm.date)}")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText(bodyLines.joinToString("\n"))
            .setSummaryText("Comunicazioni · ${readableDate(comm.date)}"),
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    } else {
      val inboxStyle = NotificationCompat.InboxStyle()
        .setSummaryText("Bacheca — ${newItems.size} nuove comunicazioni")
      newItems.sortedByDescending { it.date }.forEach { comm ->
        inboxStyle.addLine("${comm.title.take(50)} · ${readableDate(comm.date)}")
      }
      NotificationCompat.Builder(context, CommunicationsChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("${newItems.size} nuove comunicazioni")
        .setContentText("La bacheca è stata aggiornata.")
        .setStyle(inboxStyle)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    }
    notifyCompat(context, 4200, notification)
  }

  private fun dispatchAbsences(
    previous: SyncSnapshotPayloads,
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    val before = decodeList<AbsenceRecord>(previous.absences)
    val after = decodeList<AbsenceRecord>(current.absences)
    val newItems = after.filterNot { candidate ->
      before.any { existing ->
        existing.id == candidate.id &&
          existing.date == candidate.date &&
          existing.type == candidate.type
      }
    }
    if (newItems.isEmpty()) return

    val notification = if (newItems.size == 1) {
      val record = newItems.first()
      val label = absenceLabel(record)
      val justifyNote = if (!record.justified && record.canJustify) "\nRicordati di presentare la giustifica." else ""
      NotificationCompat.Builder(context, AbsencesChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("$label registrata")
        .setContentText("$label del ${readableDate(record.date)}${if (!record.justified) " — da giustificare" else ""}")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("$label del ${readableDate(record.date)}.\nStato: ${if (record.justified) "Giustificata" else "Da giustificare"}$justifyNote")
            .setSummaryText("Presenze"),
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    } else {
      val inboxStyle = NotificationCompat.InboxStyle()
        .setSummaryText("Presenze — ${newItems.size} nuovi eventi")
      newItems.sortedByDescending { it.date }.forEach { record ->
        val justified = if (record.justified) "✓" else "!"
        inboxStyle.addLine("$justified ${absenceLabel(record)} · ${readableDate(record.date)}")
      }
      NotificationCompat.Builder(context, AbsencesChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("${newItems.size} nuovi eventi presenze")
        .setContentText(newItems.take(2).joinToString(" · ") { "${absenceLabel(it)} del ${readableDate(it.date)}" })
        .setStyle(inboxStyle)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    }
    notifyCompat(context, 4300, notification)
  }

  private fun dispatchNotes(
    previous: SyncSnapshotPayloads,
    current: SyncSnapshotPayloads,
    launchIntent: PendingIntent?,
  ) {
    val before = decodeList<Note>(previous.notes)
    val after = decodeList<Note>(current.notes)
    val newItems = after.filterNot { candidate -> before.any { it.id == candidate.id } }
    if (newItems.isEmpty()) return

    val notification = if (newItems.size == 1) {
      val note = newItems.first()
      val bodyLines = buildList {
        if (note.categoryLabel.isNotBlank()) add("Categoria: ${note.categoryLabel}")
        add("Autore: ${note.author}")
        add(note.contentPreview.ifBlank { note.title })
      }
      NotificationCompat.Builder(context, NotesChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle(note.title.ifBlank { "Nuova annotazione" })
        .setContentText("${note.author} · ${readableDate(note.date)}")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText(bodyLines.joinToString("\n"))
            .setSummaryText("Annotazioni · ${readableDate(note.date)}"),
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    } else {
      val inboxStyle = NotificationCompat.InboxStyle()
        .setSummaryText("Annotazioni — ${newItems.size} nuove")
      newItems.sortedByDescending { it.date }.forEach { note ->
        inboxStyle.addLine("${note.title.take(50)} · ${note.author}")
      }
      NotificationCompat.Builder(context, NotesChannelId)
        .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
        .setContentTitle("${newItems.size} nuove annotazioni")
        .setContentText(newItems.take(2).joinToString(" · ") { it.author })
        .setStyle(inboxStyle)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .applyLaunchIntent(launchIntent)
        .build()
    }
    notifyCompat(context, 4600, notification)
  }

  private inline fun <reified T> decodeList(payload: String?): List<T> {
    val source = payload?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString<List<T>>(source) }.getOrDefault(emptyList())
  }

  private fun absenceLabel(record: AbsenceRecord): String = when (record.type) {
    AbsenceType.ABSENCE -> "Assenza"
    AbsenceType.LATE -> "Ritardo"
    AbsenceType.EXIT -> "Uscita anticipata"
  }
}

private const val LiveTimetableNotificationId = 4700

private data class LiveTimetableSnapshot(
  val current: Lesson,
  val next: Lesson?,
  val progress: Int,
  val progressMax: Int,
  val endAtEpochMillis: Long,
) {
  companion object {
    fun from(date: LocalDate, now: LocalTime, lessons: List<Lesson>): LiveTimetableSnapshot? {
      val parsed = lessons.mapNotNull { lesson ->
        val start = runCatching { LocalTime.parse(lesson.time) }.getOrNull() ?: return@mapNotNull null
        val end = lesson.endTime
          ?.takeIf(String::isNotBlank)
          ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
          ?: start.plusMinutes(lesson.durationMinutes.coerceAtLeast(60).toLong())
        ParsedLiveLesson(lesson, start, end)
      }.filter { it.end.isAfter(it.start) }

      if (parsed.isEmpty()) return null
      val first = parsed.first()
      val last = parsed.last()
      if (now.isBefore(first.start.minusMinutes(30)) || !now.isBefore(last.end)) return null

      val activeIndex = parsed.indexOfFirst { !now.isBefore(it.start) && now.isBefore(it.end) }
      val selectedIndex = if (activeIndex >= 0) activeIndex else parsed.indexOfFirst { now.isBefore(it.start) }
      val selected = parsed.getOrNull(selectedIndex) ?: return null
      val elapsed = java.time.Duration.between(selected.start, now.coerceAtLeast(selected.start)).toMinutes().toInt()
      val total = java.time.Duration.between(selected.start, selected.end).toMinutes().toInt().coerceAtLeast(1)
      val endMillis = LocalDateTime.of(date, selected.end)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

      return LiveTimetableSnapshot(
        current = selected.lesson,
        next = parsed.getOrNull(selectedIndex + 1)?.lesson,
        progress = elapsed.coerceIn(0, total),
        progressMax = total,
        endAtEpochMillis = endMillis,
      )
    }
  }
}

private data class ParsedLiveLesson(
  val lesson: Lesson,
  val start: LocalTime,
  val end: LocalTime,
)

@RequiresApi(36)
private fun buildLiveTimetableNotification(
  context: Context,
  snapshot: LiveTimetableSnapshot,
  launchIntent: PendingIntent?,
): Notification {
  val current = snapshot.current
  val next = snapshot.next
  val currentLine = listOfNotNull(
    current.teacher?.takeIf(String::isNotBlank),
    current.room?.takeIf(String::isNotBlank)?.let { "Aula $it" },
  ).joinToString(" / ").ifBlank { "Docente in corso" }
  val nextLine = next?.let { lesson ->
    buildString {
      append("Dopo: ${lesson.subject}")
      lesson.teacher?.takeIf(String::isNotBlank)?.let { append(" / $it") }
      lesson.room?.takeIf(String::isNotBlank)?.let { append(" / Aula $it") }
    }
  } ?: "Ultima ora della mattinata"
  val style = Notification.ProgressStyle()
    .setStyledByProgress(true)
    .setProgress(snapshot.progress)
    .setProgressSegments(
      listOf(
        Notification.ProgressStyle.Segment(snapshot.progressMax).setColor(Color.rgb(66, 133, 244)),
      ),
    )
    .setProgressTrackerIcon(Icon.createWithResource(context, dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo))

  // Costruzione notifica Live Update / Samsung Now Bar (Android 16+ — API 36).
  // Usiamo SOLO le API ufficiali — niente extras hack — perché il flag legacy
  // creava una seconda card duplicata nella shade su Samsung One UI 7.
  val builder = Notification.Builder(context, LiveTimetableChannelId)
    .setSmallIcon(dev.antigravity.classevivaexpressive.core.data.R.drawable.ic_stat_logo)
    .setContentTitle(current.subject)
    .setContentText(currentLine)
    .setStyle(style)
    .setSubText(nextLine)
    .setShortCriticalText(current.subject.take(10))
    .setWhen(snapshot.endAtEpochMillis)
    .setShowWhen(true)
    .setUsesChronometer(true)
    .setChronometerCountDown(true)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setColor(Color.rgb(66, 133, 244))
    .setCategory(Notification.CATEGORY_PROGRESS)
    .setVisibility(Notification.VISIBILITY_PUBLIC)
    .setContentIntent(launchIntent)

  // API 36+: richiediamo esplicitamente la promozione a Live Update / Now Bar.
  // Niente più extras.putBoolean — il sistema deduplicava male e mostrava due card.
  // Usiamo reflection perché la firma esatta varia tra build SDK e questo modulo
  // non sempre vede l'API stable; il fallback è una notifica ongoing classica.
  // Fallback compatibile: se il metodo non e' esposto dal runtime aggiungiamo l'extra.
  val promotionRequested = runCatching {
    val method = builder.javaClass.methods.firstOrNull {
      (it.name == "setRequestPromotedOngoing" || it.name == "requestPromotedOngoing") &&
        it.parameterTypes.size == 1
    }
    method?.invoke(builder, true) != null
  }.getOrDefault(false)
  if (!promotionRequested) {
    builder.addExtras(Bundle().apply { putBoolean(RequestPromotedOngoingExtra, true) })
  }
  return builder.build()
}

/**
 * Esposto per ricalcolare la notifica live on-demand (es. avvio app, cambio lezione)
 * senza dover passare dal worker periodico.
 */
suspend fun refreshLiveTimetableNotification(
  context: Context,
  settingsStore: SettingsStore,
  snapshotCacheDao: dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao,
  schoolYearStore: dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore,
  json: Json,
) {
  if (Build.VERSION.SDK_INT < 36) return
  val prefs = settingsStore.settings.first().notificationPreferences
  if (!prefs.enabled || !prefs.liveTimetable) {
    NotificationManagerCompat.from(context).cancel(LiveTimetableNotificationId)
    return
  }
  val runtime = readNotificationRuntimeState(context)
  if (!runtime.permissionGranted || !runtime.appNotificationsEnabled) return
  AppNotificationChannels.create(context)

  val currentYear = schoolYearStore.currentSchoolYearRef()
  val lessonsPayload = snapshotCacheDao
    .getByKey(dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey(LessonsCacheSection, currentYear))
    ?.payload
  val agendaPayload = snapshotCacheDao
    .getByKey(dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey(AgendaCacheSection, currentYear))
    ?.payload
  val lessons = dev.antigravity.classevivaexpressive.core.data.repository.buildLessonsWithFallback(
    lessons = runCatching { json.decodeFromString<List<Lesson>>(lessonsPayload ?: "") }.getOrDefault(emptyList()),
    agenda = runCatching { json.decodeFromString<List<AgendaItem>>(agendaPayload ?: "") }.getOrDefault(emptyList()),
  )
  val today = LocalDate.now()
  val todayLessons = lessons
    .filter { it.date == today.toString() }
    .filterNot { it.subject.isBlank() && it.teacher.isNullOrBlank() }
    .sortedBy { it.time }
  if (todayLessons.isEmpty() || todayLessons.none { !it.teacher.isNullOrBlank() }) {
    NotificationManagerCompat.from(context).cancel(LiveTimetableNotificationId)
    return
  }
  val snapshot = LiveTimetableSnapshot.from(today, LocalTime.now(), todayLessons)
  if (snapshot == null) {
    NotificationManagerCompat.from(context).cancel(LiveTimetableNotificationId)
    return
  }
  val launchIntent = launchPendingIntentInternal(context)
  notifyCompat(
    context = context,
    notificationId = LiveTimetableNotificationId,
    notification = buildLiveTimetableNotification(context, snapshot, launchIntent),
  )
}

private fun launchPendingIntentInternal(context: Context): PendingIntent? {
  val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
  return PendingIntent.getActivity(
    context,
    0,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

internal fun gradeEmoji(numericValue: Double?): String = when {
  numericValue == null -> "📝"
  numericValue >= 9.0 -> "⭐"
  numericValue >= 7.5 -> "🟢"
  numericValue >= 6.0 -> "🟡"
  else -> "🔴"
}

internal fun readableDate(value: String): String {
  val parsed = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
  return "%02d/%02d/%04d".format(parsed.dayOfMonth, parsed.monthValue, parsed.year)
}

private fun launchPendingIntent(context: Context): PendingIntent? {
  val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
  return PendingIntent.getActivity(
    context,
    0,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

private fun NotificationCompat.Builder.applyLaunchIntent(intent: PendingIntent?): NotificationCompat.Builder {
  if (intent != null) setContentIntent(intent)
  return this
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
