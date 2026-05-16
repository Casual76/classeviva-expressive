package dev.antigravity.classevivaexpressive.feature.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.ProfessorStats
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfessorsUiState(
  val professors: List<ProfessorStats> = emptyList(),
  val isRefreshing: Boolean = false,
  val selectedProfessor: ProfessorStats? = null,
)

@HiltViewModel
class ProfessorsViewModel @Inject constructor(
  private val lessonsRepository: LessonsRepository,
  private val gradesRepository: GradesRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)
  private val selectedProfessor = MutableStateFlow<ProfessorStats?>(null)

  private val professorStats: Flow<List<ProfessorStats>> = combine(
    lessonsRepository.observeLessons(),
    gradesRepository.observeGrades(),
    gradesRepository.observeSubjects(),
  ) { lessons, grades, subjects ->
    computeProfessorStats(lessons, grades, subjects)
  }

  val state = combine(
    professorStats,
    isRefreshing,
    selectedProfessor,
  ) { professors, refreshing, selected ->
    ProfessorsUiState(
      professors = professors,
      isRefreshing = refreshing,
      selectedProfessor = selected,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfessorsUiState())

  fun selectProfessor(prof: ProfessorStats) {
    selectedProfessor.value = prof
  }

  fun dismissProfessor() {
    selectedProfessor.value = null
  }

  fun refresh() {
    viewModelScope.launch {
      isRefreshing.value = true
      lessonsRepository.refreshLessons(force = true)
      gradesRepository.refreshGrades(force = true)
      isRefreshing.value = false
    }
  }
}

// ─── Algoritmo principale ────────────────────────────────────────────────────

private fun computeProfessorStats(
  lessons: List<Lesson>,
  grades: List<Grade>,
  subjects: List<Subject>,
): List<ProfessorStats> {

  // Giorni scolastici effettivi: se nessuna lezione ha un docente, quel giorno
  // viene trattato come chiusura e non pesa sulle presenze attese.
  val openSchoolDates = lessons
    .groupBy { it.date }
    .filterValues { dayLessons -> dayLessons.any { it.isSigned && !it.teacher.isNullOrBlank() } }
    .keys
    .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    .distinct()

  // Professori "ufficiali" dalla lista materie Classeviva (esclude supplenti)
  val officialTeachers: Set<String> = subjects
    .flatMap { it.teachers }
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .toSet()
  val officialNormalized: Set<String> = officialTeachers.map { it.lowercase() }.toSet()

  // Tutti i teacher presenti nelle lezioni e voti
  val fromLessons = lessons.mapNotNull { lesson ->
    lesson.teacher?.trim()?.takeIf { lesson.isSigned && it.isNotBlank() }
  }.distinct()
  val fromGrades = grades.mapNotNull { it.teacher?.trim()?.takeIf { t -> t.isNotBlank() } }.distinct()
  val candidates = (fromLessons + fromGrades).distinct()

  // Filtro: mantieni solo i prof ufficiali (o, se la lista materie non è ancora caricata,
  // solo chi ha insegnato in ≥ 4 settimane distinte — esclude quasi tutti i supplenti)
  val ourTeachers = candidates.filter { teacher ->
    if (officialNormalized.isNotEmpty()) {
      teacher.lowercase() in officialNormalized
    } else {
      val teacherDates = lessons
        .filter { it.isSigned && it.teacher?.trim() == teacher }
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
      val distinctWeeks = teacherDates
        .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .distinct().size
      distinctWeeks >= 4
    }
  }.sorted()

  return ourTeachers.map { teacher ->
    buildProfessorStats(teacher, lessons, grades, openSchoolDates)
  }
}

private fun buildProfessorStats(
  teacher: String,
  allLessons: List<Lesson>,
  allGrades: List<Grade>,
  allDatasetDates: List<LocalDate>,
): ProfessorStats {
  val teacherLessons = allLessons.filter { it.isSigned && it.teacher?.trim() == teacher }
  val teacherGrades = allGrades.filter { it.teacher?.trim() == teacher }

  val subjectsFromLessons = teacherLessons.map { it.subject.trim() }.distinct()
  val subjectsFromGrades = teacherGrades.map { it.subject.trim() }.distinct()
  val allSubjects = (subjectsFromLessons + subjectsFromGrades).distinct()

  // ── Presenza (algoritmo smart multi-materia) ────────────────────────────
  // Per professori con più materie (es. Mucci: Matematica+Fisica,
  // Monti: Storia+Filosofia+Fisica), consideriamo presente anche quando
  // le materie vengono invertite di ordine rispetto al solito.
  val teacherSubjectsLower = allSubjects.map { it.lowercase() }.toSet()

  // Tutte le date in cui il prof ha firmato qualsiasi materia
  val lessonDates = teacherLessons
    .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }

  // Se il prof ha più materie, cerchiamo anche le date dove un'altra lezione
  // nella stessa materia è firmata da un nome simile (varianti del nome)
  val teacherNameLower = teacher.lowercase()
  val teacherSurname = teacher.split(" ").firstOrNull()?.lowercase().orEmpty()

  // Date in cui QUALSIASI delle materie del prof è stata tenuta
  // da qualcuno con lo stesso cognome (gestisce varianti di nome)
  val subjectDates = if (allSubjects.size > 1) {
    allLessons
      .filter { lesson ->
        val subj = lesson.subject.trim().lowercase()
        val lessonTeacher = lesson.teacher?.trim()?.lowercase().orEmpty()
        lesson.isSigned && subj in teacherSubjectsLower && (
          lessonTeacher == teacherNameLower ||
          lessonTeacher.startsWith(teacherSurname)
        )
      }
      .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
  } else {
    lessonDates
  }

  val allTeacherDates = (lessonDates + subjectDates).distinct()

  val lessonDatesByWeekday = allTeacherDates.groupBy { it.dayOfWeek }
  val datasetDatesByWeekday = allDatasetDates.groupBy { it.dayOfWeek }

  // Giorni tipici: giorni della settimana in cui il prof ha insegnato
  // in almeno il 25% delle volte che quel giorno era presente nel dataset.
  val typicalDays: Set<DayOfWeek> = lessonDatesByWeekday.filter { (day, dates) ->
    val totalOccurrences = datasetDatesByWeekday[day]?.size ?: 0
    totalOccurrences > 0 && (dates.size.toFloat() / totalOccurrences) >= 0.25f
  }.keys

  val expectedDays: Int
  val actualDays: Int
  val absenceDays: List<String>

  val distinctLessonDates = allTeacherDates.distinct()

  if (typicalDays.isEmpty()) {
    val teacherWeeks = allTeacherDates
      .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
      .distinct().size
    val totalWeeks = allDatasetDates
      .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
      .distinct().size
    expectedDays = totalWeeks
    actualDays = teacherWeeks
    absenceDays = emptyList()
  } else {
    expectedDays = allDatasetDates.count { it.dayOfWeek in typicalDays }
    actualDays = distinctLessonDates.count { it.dayOfWeek in typicalDays }
    absenceDays = allDatasetDates
      .filter { date -> date.dayOfWeek in typicalDays && distinctLessonDates.none { it == date } }
      .map { it.toString() }
      .sorted()
  }

  val presenceRate = if (expectedDays == 0) 0f
  else (actualDays.toFloat() / expectedDays).coerceIn(0f, 1f)

  // ── Voti & Indice di rigore ─────────────────────────────────────────────
  val gradeCount = teacherGrades.size
  val averageGrade = teacherGrades
    .mapNotNull { it.numericValue }
    .takeIf { it.isNotEmpty() }
    ?.average()
  val mostFrequentType = teacherGrades
    .map { it.type }
    .takeIf { it.isNotEmpty() }
    ?.groupingBy { it }
    ?.eachCount()
    ?.maxByOrNull { it.value }
    ?.key

  val lessonCount = teacherLessons.size.coerceAtLeast(1)
  val evaluationDensity = gradeCount.toFloat() / lessonCount.toFloat()
  val writtenTypes = setOf("scritto", "compito in classe", "verifica", "test", "written")
  val writtenCount = teacherGrades.count { it.type.lowercase() in writtenTypes }
  val writtenExamRatio = if (gradeCount > 0) writtenCount.toFloat() / gradeCount.toFloat() else 0f
  val avgGradeWeight = teacherGrades
    .mapNotNull { it.weight }
    .takeIf { it.isNotEmpty() }
    ?.average()?.toFloat() ?: 1f
  val topicCoverageRate = teacherLessons
    .count { !it.topic.isNullOrBlank() }
    .toFloat() / lessonCount.toFloat()

  // Punteggio di rigore multi-fattore
  val densityScore = (evaluationDensity * 30f).coerceIn(0f, 30f)
  val writtenScore = (writtenExamRatio * 25f).coerceIn(0f, 25f)
  val weightScore = ((avgGradeWeight - 0.5f) * 20f).coerceIn(0f, 20f)
  val topicScore = (topicCoverageRate * 25f).coerceIn(0f, 25f)

  val strictnessScore = (densityScore + writtenScore + weightScore + topicScore).toInt().coerceIn(0, 100)
  val strictnessLabel = when {
    strictnessScore >= 75 -> "Molto esigente"
    strictnessScore >= 55 -> "Esigente"
    strictnessScore >= 35 -> "Equilibrato"
    else -> "Morbido"
  }

  // ── Dossier Segreto (fun stats) ─────────────────────────────────────────
  val teacherWeeks = distinctLessonDates
    .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    .distinct()
    .sorted()
  
  var maxStreak = 0
  var currentStreak = 0
  var lastWeek: LocalDate? = null
  for (week in teacherWeeks) {
    if (lastWeek == null || week == lastWeek.plusWeeks(1)) {
      currentStreak++
    } else {
      currentStreak = 1
    }
    maxStreak = maxOf(maxStreak, currentStreak)
    lastWeek = week
  }

  val favoriteDay = lessonDates
    .groupBy { it.dayOfWeek }
    .maxByOrNull { it.value.size }
    ?.key?.value

  // Ratio di materie diverse insegnate nello stesso giorno (subject swap)
  val subjectSwapDays = distinctLessonDates.count { date ->
    val daySubjects = teacherLessons
      .filter { it.date == date.toString() }
      .map { it.subject.trim().lowercase() }
      .distinct()
    daySubjects.size > 1
  }
  val swapRate = if (distinctLessonDates.isNotEmpty())
    subjectSwapDays.toFloat() / distinctLessonDates.size.toFloat() else 0f

  // Prima ora più frequente
  val favoriteHour = teacherLessons
    .mapNotNull { lesson ->
      runCatching { java.time.LocalTime.parse(lesson.time) }.getOrNull()?.hour
    }
    .takeIf { it.isNotEmpty() }
    ?.groupingBy { it }
    ?.eachCount()
    ?.maxByOrNull { it.value }
    ?.key

  // Frequenza argomenti ripetuti (pigrizia didattica?)
  val topicRepeatRate = teacherLessons
    .mapNotNull { it.topic?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
    .takeIf { it.isNotEmpty() }
    ?.let { topics ->
      val unique = topics.distinct().size
      1f - (unique.toFloat() / topics.size.toFloat())
    } ?: 0f

  val funNickname = when {
    presenceRate < 0.5f -> "Il Fantasma 👻"
    presenceRate < 0.65f -> "L'Ombra 🌑"
    strictnessScore > 85 -> "Il Giustiziere ⚖️"
    strictnessScore > 75 -> "Il Terrore 💀"
    evaluationDensity > 1.0f -> "Pioggia di Voti 🌧️"
    evaluationDensity > 0.6f -> "Il Maratoneta dei Voti 🏃"
    topicCoverageRate > 0.95f && topicRepeatRate < 0.1f -> "Il Perfezionista 🎯"
    topicCoverageRate > 0.90f -> "Il Metodico 📋"
    swapRate > 0.5f -> "Il Giocoliere delle Materie 🤹"
    allSubjects.size > 2 -> "Il Tuttofare 🛠️"
    allSubjects.size > 1 -> "Master of Swapping 🔄"
    maxStreak > 12 -> "Presenza Eterna ♾️"
    maxStreak > 8 -> "Presenza Inamovibile 🗿"
    topicRepeatRate > 0.4f -> "Il DJ del Replay 🔁"
    favoriteHour != null && favoriteHour >= 11 -> "Il Dormiglione 😴"
    favoriteHour != null && favoriteHour <= 8 -> "L'Allodola Mattiniera 🐦"
    presenceRate > 0.95f -> "Il Soldatino 🎖️"
    else -> "Il Pilastro 🏛️"
  }

  return ProfessorStats(
    teacherName = teacher,
    subjects = allSubjects,
    expectedDays = expectedDays,
    actualDays = actualDays,
    presenceRate = presenceRate,
    absenceDays = absenceDays.takeLast(10),
    gradeCount = gradeCount,
    averageGrade = averageGrade,
    mostFrequentGradeType = mostFrequentType,
    strictnessScore = strictnessScore,
    strictnessLabel = strictnessLabel,
    evaluationDensity = evaluationDensity,
    writtenExamRatio = writtenExamRatio,
    avgGradeWeight = avgGradeWeight,
    topicCoverageRate = topicCoverageRate,
    longestPresenceStreakWeeks = maxStreak,
    favoriteDayOfWeek = favoriteDay,
    funNickname = funNickname,
  )
}

private fun DayOfWeek.shortLabel(): String = when (this) {
  DayOfWeek.MONDAY -> "Lun"
  DayOfWeek.TUESDAY -> "Mar"
  DayOfWeek.WEDNESDAY -> "Mer"
  DayOfWeek.THURSDAY -> "Gio"
  DayOfWeek.FRIDAY -> "Ven"
  DayOfWeek.SATURDAY -> "Sab"
  DayOfWeek.SUNDAY -> "Dom"
}

// ─── UI ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessorsRoute(
  onBack: (() -> Unit)? = null,
  viewModel: ProfessorsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Professori",
        subtitle = "Presenza, rigore e valutazioni per i tuoi docenti — solo i docenti ufficiali della classe.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      if (state.professors.isEmpty() && !state.isRefreshing) {
        EmptyState(
          title = "Nessun docente rilevato",
          detail = "Le statistiche appariranno dopo che lezioni e voti saranno sincronizzati.",
          modifier = Modifier.padding(20.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              val avgPresence = state.professors
                .map { it.presenceRate }
                .takeIf { it.isNotEmpty() }
                ?.average()
              MetricTile(
                label = "Docenti",
                value = state.professors.size.toString(),
                detail = "Docenti ufficiali rilevati.",
                modifier = Modifier.weight(1f),
                tone = ExpressiveTone.Info,
              )
              MetricTile(
                label = "Presenza media",
                value = avgPresence?.let { "${(it * 100).toInt()}%" } ?: "N/D",
                detail = "Media classe.",
                modifier = Modifier.weight(1f),
                tone = if ((avgPresence ?: 0.0) >= 0.85) ExpressiveTone.Success else ExpressiveTone.Warning,
              )
            }
          }
          item { ExpressiveAccentLabel("Docenti") }
          items(state.professors, key = { it.teacherName }) { prof ->
            val presenceTone = when {
              prof.presenceRate >= 0.85f -> ExpressiveTone.Success
              prof.presenceRate >= 0.65f -> ExpressiveTone.Warning
              else -> ExpressiveTone.Danger
            }
            val strictnessTone = when (prof.strictnessLabel) {
              "Molto esigente" -> ExpressiveTone.Danger
              "Esigente" -> ExpressiveTone.Warning
              "Equilibrato" -> ExpressiveTone.Info
              else -> ExpressiveTone.Success
            }
            RegisterListRow(
              title = prof.teacherName,
              subtitle = prof.subjects.joinToString(", ").ifBlank { "Materia non specificata" },
              eyebrow = "Presenza ${(prof.presenceRate * 100).toInt()}%",
              meta = buildString {
                if (prof.gradeCount > 0) append("${prof.gradeCount} voti")
                prof.averageGrade?.let { append(" · media %.1f".format(it)) }
                if (prof.gradeCount == 0) append("Nessun voto assegnato")
              },
              tone = presenceTone,
              onClick = { viewModel.selectProfessor(prof) },
              badge = { StatusBadge(prof.strictnessLabel.uppercase(), tone = strictnessTone) },
              animatePress = false,
            )
          }
        }
      }
    }
  }

  state.selectedProfessor?.let { prof ->
    ProfessorDetailSheet(prof = prof, onDismiss = viewModel::dismissProfessor)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfessorDetailSheet(
  prof: ProfessorStats,
  onDismiss: () -> Unit,
) {
  val strictnessTone = when (prof.strictnessLabel) {
    "Molto esigente" -> ExpressiveTone.Danger
    "Esigente" -> ExpressiveTone.Warning
    "Equilibrato" -> ExpressiveTone.Info
    else -> ExpressiveTone.Success
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(prof.teacherName, style = MaterialTheme.typography.headlineSmall)
      }
      item { ExpressiveAccentLabel("Materie") }
      item {
        Text(
          text = prof.subjects.joinToString(", ").ifBlank { "Non specificato" },
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      // ── Presenza ──────────────────────────────────────────────────────
      item { ExpressiveAccentLabel("Presenza") }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricTile(
            label = "Giorni presenti",
            value = "${prof.actualDays}",
            detail = "Lezioni firmate.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Success,
          )
          MetricTile(
            label = "Giorni attesi",
            value = "${prof.expectedDays}",
            detail = "Dalle sue giornate tipiche.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Tasso",
            value = "${(prof.presenceRate * 100).toInt()}%",
            detail = "Presenze / attesi.",
            modifier = Modifier.weight(1f),
            tone = when {
              prof.presenceRate >= 0.85f -> ExpressiveTone.Success
              prof.presenceRate >= 0.65f -> ExpressiveTone.Warning
              else -> ExpressiveTone.Danger
            },
          )
        }
      }
      if (prof.absenceDays.isNotEmpty()) {
        item { ExpressiveAccentLabel("Probabili assenze recenti") }
        items(prof.absenceDays.takeLast(5), key = { "abs_$it" }) { date ->
          RegisterListRow(
            title = date,
            subtitle = "Giorno tipico senza lezione registrata.",
            tone = ExpressiveTone.Warning,
            badge = { StatusBadge("ASSENTE", tone = ExpressiveTone.Warning) },
          )
        }
      }

      // ── Indice di rigore ──────────────────────────────────────────────
      item { ExpressiveAccentLabel("Indice di rigore — ${prof.strictnessScore}/100") }
      item {
        RegisterListRow(
          title = prof.strictnessLabel,
          subtitle = "Punteggio calcolato su 4 indicatori oggettivi di classe.",
          tone = strictnessTone,
          badge = { StatusBadge("${prof.strictnessScore}", tone = strictnessTone) },
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MetricTile(
            label = "Voti/lezione",
            value = "%.2f".format(prof.evaluationDensity),
            detail = "Densita valutativa.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Scritti",
            value = "${(prof.writtenExamRatio * 100).toInt()}%",
            detail = "Esami scritti.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Peso medio",
            value = "%.1f".format(prof.avgGradeWeight),
            detail = "Importanza voti.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Argomenti",
            value = "${(prof.topicCoverageRate * 100).toInt()}%",
            detail = "Lezioni firmate.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Info,
          )
        }
      }

      // ── Voti ──────────────────────────────────────────────────────────
      if (prof.gradeCount > 0) {
        item { ExpressiveAccentLabel("Voti") }
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            MetricTile(
              label = "Totale voti",
              value = "${prof.gradeCount}",
              detail = "Valutazioni assegnate.",
              modifier = Modifier.weight(1f),
            )
            prof.averageGrade?.let { avg ->
              MetricTile(
                label = "La tua media",
                value = "%.1f".format(avg),
                detail = "Dipende dallo studente.",
                modifier = Modifier.weight(1f),
                tone = when {
                  avg >= 7.5 -> ExpressiveTone.Success
                  avg >= 6.0 -> ExpressiveTone.Warning
                  else -> ExpressiveTone.Danger
                },
              )
            }
            prof.mostFrequentGradeType?.let { type ->
              MetricTile(
                label = "Tipo prevalente",
                value = type,
                detail = "Tipologia piu comune.",
                modifier = Modifier.weight(1f),
                tone = ExpressiveTone.Info,
              )
            }
          }
        }
      }

      // ── Dossier Segreto 🔥 ─────────────────────────────────────────────
      item { ExpressiveAccentLabel("Dossier Segreto 🕵️") }
      item {
        RegisterListRow(
          title = prof.funNickname,
          subtitle = buildString {
            append("Classificazione segreta basata su ")
            append("${prof.actualDays} giorni di osservazione, ")
            append("${prof.gradeCount} valutazioni e ")
            append("${prof.subjects.size} materie monitorate.")
          },
          tone = ExpressiveTone.Success,
          badge = { StatusBadge("TOP SECRET", tone = ExpressiveTone.Success) },
        )
      }
      item {
        val roast = when {
          prof.presenceRate < 0.5f -> "Avvistamento raro come un unicorno. Leggenda narra che esista davvero."
          prof.presenceRate < 0.65f -> "Presenza a singhiozzo. Forse ha un lavoro part-time come esploratore?"
          prof.presenceRate > 0.95f && prof.strictnessScore > 70 -> "Sempre presente e sempre pronto a interrogare. Nessuna via di fuga."
          prof.presenceRate > 0.95f -> "Sempre al suo posto. La campanella suona per lui."
          prof.strictnessScore > 85 -> "Terrorizza i corridoi. I voti tremano al suo passaggio."
          prof.strictnessScore > 70 -> "Non si scherza in classe. Porta sempre il registro carico."
          prof.strictnessScore < 20 -> "Relax totale. Se i voti fossero cuscini, sarebbe un materasso."
          prof.evaluationDensity > 0.8f -> "Voti come se piovesse. Ogni lezione è una roulette russa."
          prof.subjects.size > 2 -> "Multi-classe, multi-materia. Probabilmente clonato."
          prof.subjects.size > 1 -> "Cambia materia come cambia umore. Oggi Fisica, domani Filosofia."
          prof.topicCoverageRate > 0.95f -> "Firma sempre l'argomento. Il registro è la sua seconda casa."
          prof.topicCoverageRate < 0.3f -> "L'argomento? Misterioso come il triangolo delle Bermuda."
          else -> "Un prof nella media, ma in fondo, chi vuole essere nella media?"
        }
        RegisterListRow(
          title = "Profilo Psicologico",
          subtitle = roast,
          tone = ExpressiveTone.Warning,
          badge = { StatusBadge("ROAST", tone = ExpressiveTone.Warning) },
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricTile(
            label = "Streak 🔥",
            value = "${prof.longestPresenceStreakWeeks}",
            detail = "Settimane consecutive.",
            modifier = Modifier.weight(1f),
            tone = if (prof.longestPresenceStreakWeeks >= 8) ExpressiveTone.Success else ExpressiveTone.Info,
          )
          MetricTile(
            label = "Giorno top",
            value = prof.favoriteDayOfWeek?.let { DayOfWeek.of(it).shortLabel() } ?: "N/D",
            detail = "Giorno con piu firme.",
            modifier = Modifier.weight(1f),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Materie",
            value = "${prof.subjects.size}",
            detail = prof.subjects.joinToString(", ").take(30),
            modifier = Modifier.weight(1f),
            tone = if (prof.subjects.size > 1) ExpressiveTone.Warning else ExpressiveTone.Info,
          )
        }
      }
      // ── Livello di Pericolo ────────────────────────────────────────────
      item {
        val dangerLevel = when {
          prof.strictnessScore > 80 && prof.presenceRate > 0.9f -> "☠️ MASSIMO"
          prof.strictnessScore > 70 -> "🔴 ALTO"
          prof.strictnessScore > 50 -> "🟡 MEDIO"
          prof.strictnessScore > 30 -> "🟢 BASSO"
          else -> "💤 INESISTENTE"
        }
        val dangerTone = when {
          prof.strictnessScore > 70 -> ExpressiveTone.Danger
          prof.strictnessScore > 50 -> ExpressiveTone.Warning
          else -> ExpressiveTone.Success
        }
        RegisterListRow(
          title = "Livello di Pericolo",
          subtitle = "Indice di probabilità di essere interrogati/verificati a sorpresa.",
          eyebrow = dangerLevel,
          tone = dangerTone,
          badge = { StatusBadge(dangerLevel.takeLast(dangerLevel.length - 2).trim(), tone = dangerTone) },
        )
      }
    }
  }
}
