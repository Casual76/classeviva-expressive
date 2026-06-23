package dev.antigravity.classevivaexpressive.feature.widgets

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class WidgetStatus {
  CONTENT,
  LOGGED_OUT,
  EMPTY,
}

enum class WidgetUpcomingType {
  HOMEWORK,
  ASSESSMENT,
  EVENT,
}

data class WidgetUpcomingItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val dateLabel: String,
  val sortKey: String,
  val type: WidgetUpcomingType,
  val deepLink: String,
)

data class WidgetGradeItem(
  val id: String,
  val subject: String,
  val valueLabel: String,
  val dateLabel: String,
  val deepLink: String,
)

data class WidgetCommunicationItem(
  val id: String,
  val title: String,
  val sender: String,
  val dateLabel: String,
  val deepLink: String,
)

data class SchoolWidgetModel(
  val status: WidgetStatus,
  val header: String,
  val syncLabel: String,
  val syncWarning: Boolean,
  val refreshing: Boolean,
  val lastRefreshError: String,
  val upcoming: List<WidgetUpcomingItem>,
  val unseenGrades: List<WidgetGradeItem>,
  val unreadCommunications: List<WidgetCommunicationItem>,
  val unseenGradeCount: Int,
  val unreadCommunicationCount: Int,
  val emptyMessage: String,
)

object SchoolWidgetMapper {
  fun map(
    snapshot: DashboardSnapshot,
    preferences: SchoolWidgetPreferences,
    hasSession: Boolean,
    today: LocalDate = LocalDate.now(),
    nowEpochMillis: Long = System.currentTimeMillis(),
  ): SchoolWidgetModel {
    if (!hasSession) {
      return SchoolWidgetModel(
        status = WidgetStatus.LOGGED_OUT,
        header = "Classeviva Oggi",
        syncLabel = "Accesso richiesto",
        syncWarning = false,
        refreshing = preferences.refreshing,
        lastRefreshError = preferences.lastRefreshError,
        upcoming = emptyList(),
        unseenGrades = emptyList(),
        unreadCommunications = emptyList(),
        unseenGradeCount = 0,
        unreadCommunicationCount = 0,
        emptyMessage = "Accedi per vedere compiti, voti e comunicazioni.",
      )
    }

    val privacy = preferences.privacyMode
    val upcoming = snapshot.upcomingItems
      .asSequence()
      .mapNotNull { item -> item.toWidgetUpcomingItem(preferences, privacy, today) }
      .sortedWith(compareBy<WidgetUpcomingItem> { it.sortKey }.thenBy { it.title })
      .toList()

    val unseenGrades = if (preferences.showGrades) {
      snapshot.unseenGrades.map { grade -> grade.toWidgetGradeItem(privacy, today) }
    } else {
      emptyList()
    }
    val unreadCommunications = if (preferences.showCommunications) {
      snapshot.unreadCommunications.map { communication -> communication.toWidgetCommunicationItem(privacy, today) }
    } else {
      emptyList()
    }

    val lastSync = snapshot.syncStatus.lastSuccessfulSyncEpochMillis
    val stale = lastSync == null || nowEpochMillis - lastSync > StaleWarningMillis
    val syncLabel = when {
      preferences.refreshing -> "Aggiornamento..."
      snapshot.syncStatus.state == SyncState.PARTIAL -> "Aggiornamento parziale"
      snapshot.syncStatus.state == SyncState.OFFLINE -> "Offline"
      snapshot.syncStatus.state == SyncState.ERROR -> "Non aggiornato"
      lastSync != null -> "Aggiornato ${formatSyncTime(lastSync)}"
      else -> "Dati non aggiornati"
    }
    val hasContent = upcoming.isNotEmpty() || unseenGrades.isNotEmpty() || unreadCommunications.isNotEmpty()

    return SchoolWidgetModel(
      status = if (hasContent) WidgetStatus.CONTENT else WidgetStatus.EMPTY,
      header = "Classeviva Oggi",
      syncLabel = syncLabel,
      syncWarning = stale || snapshot.syncStatus.state in setOf(SyncState.PARTIAL, SyncState.OFFLINE, SyncState.ERROR),
      refreshing = preferences.refreshing,
      lastRefreshError = preferences.lastRefreshError,
      upcoming = upcoming,
      unseenGrades = unseenGrades,
      unreadCommunications = unreadCommunications,
      unseenGradeCount = snapshot.unseenGrades.size.takeIf { preferences.showGrades } ?: 0,
      unreadCommunicationCount = snapshot.unreadCommunications.size.takeIf { preferences.showCommunications } ?: 0,
      emptyMessage = "Nessun impegno o elemento da leggere nei prossimi giorni.",
    )
  }

  private fun AgendaItem.toWidgetUpcomingItem(
    preferences: SchoolWidgetPreferences,
    privacy: WidgetPrivacyMode,
    today: LocalDate,
  ): WidgetUpcomingItem? {
    val parsedDate = parseDate(date) ?: return null
    if (parsedDate.isBefore(today)) return null
    val type = when (category) {
      AgendaCategory.HOMEWORK -> {
        if (!preferences.showHomework || parsedDate.isAfter(today.plusDays(preferences.homeworkDays.toLong()))) return null
        WidgetUpcomingType.HOMEWORK
      }
      AgendaCategory.ASSESSMENT -> {
        if (!preferences.showAssessments || parsedDate.isAfter(today.plusDays(preferences.assessmentDays.toLong()))) return null
        WidgetUpcomingType.ASSESSMENT
      }
      AgendaCategory.EVENT,
      AgendaCategory.CUSTOM,
      -> {
        if (!preferences.showOtherEvents || parsedDate.isAfter(today.plusDays(preferences.assessmentDays.toLong()))) return null
        WidgetUpcomingType.EVENT
      }
      AgendaCategory.LESSON -> return null
    }
    val safeTitle = when (privacy) {
      WidgetPrivacyMode.FULL -> title.ifBlank { subject ?: subtitle }
      WidgetPrivacyMode.DISCREET -> when (type) {
        WidgetUpcomingType.HOMEWORK -> "Compito in arrivo"
        WidgetUpcomingType.ASSESSMENT -> "Verifica in arrivo"
        WidgetUpcomingType.EVENT -> "Impegno in arrivo"
      }
    }
    val safeSubtitle = when (privacy) {
      WidgetPrivacyMode.FULL -> subject?.takeIf(String::isNotBlank) ?: subtitle
      WidgetPrivacyMode.DISCREET -> type.label
    }
    return WidgetUpcomingItem(
      id = id,
      title = safeTitle,
      subtitle = safeSubtitle,
      dateLabel = dateLabel(today, parsedDate),
      sortKey = "${parsedDate}-${time.orEmpty()}-$id",
      type = type,
      deepLink = if (category == AgendaCategory.HOMEWORK && id.startsWith(HomeworkIdPrefix)) {
        WidgetDeepLinks.homework(id.removePrefix(HomeworkIdPrefix))
      } else {
        WidgetDeepLinks.agenda(agendaId = id, date = date)
      },
    )
  }

  private fun Grade.toWidgetGradeItem(
    privacy: WidgetPrivacyMode,
    today: LocalDate,
  ): WidgetGradeItem {
    val parsedDate = parseDate(date)
    return WidgetGradeItem(
      id = id,
      subject = if (privacy == WidgetPrivacyMode.FULL) subject else "Voto da aprire",
      valueLabel = if (privacy == WidgetPrivacyMode.FULL) valueLabel else "--",
      dateLabel = parsedDate?.let { dateLabel(today, it) } ?: date,
      deepLink = WidgetDeepLinks.grades(id),
    )
  }

  private fun Communication.toWidgetCommunicationItem(
    privacy: WidgetPrivacyMode,
    today: LocalDate,
  ): WidgetCommunicationItem {
    val parsedDate = parseDate(date)
    return WidgetCommunicationItem(
      id = id,
      title = if (privacy == WidgetPrivacyMode.FULL) title else "Comunicazione da leggere",
      sender = if (privacy == WidgetPrivacyMode.FULL) sender else "Bacheca",
      dateLabel = parsedDate?.let { dateLabel(today, it) } ?: date,
      deepLink = WidgetDeepLinks.communications(pubId = pubId, evtCode = evtCode),
    )
  }

  private val WidgetUpcomingType.label: String
    get() = when (this) {
      WidgetUpcomingType.HOMEWORK -> "Compito"
      WidgetUpcomingType.ASSESSMENT -> "Verifica"
      WidgetUpcomingType.EVENT -> "Agenda"
    }
}

internal object WidgetDeepLinks {
  fun home(): String = "classevivaexpressive://open/home"
  fun grades(gradeId: String? = null): String = open("grades", "gradeId" to gradeId)
  fun agenda(agendaId: String? = null, date: String? = null): String =
    open("agenda", "agendaId" to agendaId, "date" to date)

  fun homework(homeworkId: String? = null): String = open("homework", "homeworkId" to homeworkId)
  fun communications(pubId: String? = null, evtCode: String? = null): String =
    open("communications", "tab" to "board", "pubId" to pubId, "evtCode" to evtCode)

  private fun open(path: String, vararg params: Pair<String, String?>): String {
    val query = params
      .filter { (_, value) -> !value.isNullOrBlank() }
      .joinToString("&") { (key, value) -> "$key=${value.orEmpty().encodeUrlPart()}" }
    val base = "classevivaexpressive://open/$path"
    return if (query.isBlank()) base else "$base?$query"
  }

  private fun String.encodeUrlPart(): String = java.net.URLEncoder
    .encode(this, Charsets.UTF_8.name())
    .replace("+", "%20")
}

private const val HomeworkIdPrefix = "homework-"
private const val StaleWarningMillis = 24L * 60L * 60L * 1000L
private val ShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

private fun parseDate(value: String): LocalDate? {
  return runCatching { LocalDate.parse(value) }.getOrNull()
}

private fun dateLabel(today: LocalDate, date: LocalDate): String {
  return when (date) {
    today -> "Oggi"
    today.plusDays(1) -> "Domani"
    else -> date.format(ShortDateFormatter)
  }
}

private fun formatSyncTime(epochMillis: Long): String {
  val time = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()
  return "%02d:%02d".format(time.hour, time.minute)
}
