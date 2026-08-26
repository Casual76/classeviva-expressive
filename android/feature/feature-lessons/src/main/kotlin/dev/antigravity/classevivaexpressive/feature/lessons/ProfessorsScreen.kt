package dev.antigravity.classevivaexpressive.feature.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHero
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHeroMetric
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.fluidGlassGroups
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
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
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContainerScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidLoadingBlock
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

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
  onOpenProfessor: ((String) -> Unit)? = null,
  viewModel: ProfessorsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Il rettangolo della riga toccata: e' da li' che la finestra parte e li' che torna.
  var professorOrigin by remember { mutableStateOf<Rect?>(null) }

  FluidScreen(
    title = "Professori",
    ambient = FeatureIdentity.People.ambient(),
    subtitle = "Presenza, rigore e valutazioni per i tuoi docenti — solo i docenti ufficiali della classe.",
    onBack = onBack,
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 12.dp,
  ) {
    if (state.professors.isEmpty()) {
      item {
        if (state.isRefreshing) {
          FluidLoadingBlock()
        } else {
          FluidEmptyState(
            title = "Nessun docente rilevato",
            detail = "Le statistiche appariranno dopo che lezioni e voti saranno sincronizzati.",
          )
        }
      }
    } else {
      item {
        val avgPresence = state.professors.map { it.presenceRate }.average()
        val distinctSubjects = state.professors.flatMap { it.subjects }.distinct().size
        FeatureHero(
          identity = FeatureIdentity.People,
          eyebrow = "La classe docente",
          value = state.professors.size.toString(),
          title = if (state.professors.size == 1) "docente rilevato" else "docenti rilevati",
          description = "Presenza, materie e valutazioni diventano un profilo leggibile per ogni docente ufficiale.",
          icon = Icons.Rounded.Groups,
          metrics = listOf(
            FeatureHeroMetric("Presenza media", "${(avgPresence * 100).toInt()}%"),
            FeatureHeroMetric("Materie", distinctSubjects.toString()),
            FeatureHeroMetric("Voti assegnati", state.professors.sumOf { it.gradeCount }.toString()),
          ),
        )
      }
      item { FluidSectionHeader("Docenti") }
      fluidGlassGroups(state.professors) { prof ->
        var rowBounds by remember { mutableStateOf<Rect?>(null) }
        val presenceTone = when {
          prof.presenceRate >= 0.85f -> FluidTone.Success
          prof.presenceRate >= 0.65f -> FluidTone.Warning
          else -> FluidTone.Danger
        }
        val strictnessTone = when (prof.strictnessLabel) {
          "Molto esigente" -> FluidTone.Danger
          "Esigente" -> FluidTone.Warning
          "Equilibrato" -> FluidTone.Info
          else -> FluidTone.Success
        }
        FluidListRow(
          modifier = Modifier.fluidExpandOrigin { rowBounds = it },
          title = prof.teacherName,
          subtitle = prof.subjects.joinToString(", ").ifBlank { "Materia non specificata" },
          eyebrow = "Presenza ${(prof.presenceRate * 100).toInt()}%",
          meta = buildString {
            if (prof.gradeCount > 0) append("${prof.gradeCount} voti")
            prof.averageGrade?.let { append(" · media %.1f".format(it)) }
            if (prof.gradeCount == 0) append("Nessun voto assegnato")
          },
          tone = presenceTone,
          leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
          onClick = {
            professorOrigin = rowBounds
            if (onOpenProfessor != null) onOpenProfessor(prof.teacherName) else viewModel.selectProfessor(prof)
          },
          badge = { FluidStatusBadge(prof.strictnessLabel.uppercase(), tone = strictnessTone) },
          animatePress = true,
        )
      }
    }
  }

  // Portale, non sheet: dichiarato sempre, cosi' l'uscita non si smonta insieme alla selezione.
  FluidGlassModalPortal(
    item = if (onOpenProfessor == null) state.selectedProfessor else null,
    onDismissRequest = viewModel::dismissProfessor,
    origin = { professorOrigin },
    paneTitle = "Dettaglio docente",
  ) { prof ->
    ProfessorDetailContent(prof = prof)
  }
}

@Composable
fun ProfessorDetailRoute(
  teacherName: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ProfessorsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val professor = state.professors.firstOrNull { it.teacherName == teacherName }

  if (professor == null) {
    FluidScreen(title = "Dettaglio professore", modifier = modifier, onBack = onBack) {
      item(key = "professor-detail-missing") {
        FluidEmptyState(
          title = "Professore non disponibile",
          detail = "Il profilo potrebbe non essere ancora stato ricostruito dai dati sincronizzati.",
        )
      }
    }
    return
  }

  val presenceTone = when {
    professor.presenceRate >= 0.85f -> FluidTone.Success
    professor.presenceRate >= 0.65f -> FluidTone.Warning
    else -> FluidTone.Danger
  }
  val strictnessTone = when (professor.strictnessLabel) {
    "Molto esigente" -> FluidTone.Danger
    "Esigente" -> FluidTone.Warning
    "Equilibrato" -> FluidTone.Info
    else -> FluidTone.Success
  }

  FluidContainerScaffold(
    title = "Dettaglio professore",
    modifier = modifier,
    onBack = onBack,
    hero = {
      FluidListRow(
        title = professor.teacherName,
        subtitle = professor.subjects.joinToString(", ").ifBlank { "Materia non specificata" },
        eyebrow = "Presenza ${(professor.presenceRate * 100).toInt()}%",
        meta = if (professor.gradeCount > 0) "${professor.gradeCount} voti" else "Nessun voto assegnato",
        tone = presenceTone,
        leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
        badge = { FluidStatusBadge(professor.strictnessLabel.uppercase(), tone = strictnessTone) },
        animatePress = false,
      )
    },
    secondary = {
      FluidSectionHeader("Presenza")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FluidMetricTile("Presenti", professor.actualDays.toString(), "Lezioni firmate.", Modifier.weight(1f), FluidTone.Success, glass = true)
        FluidMetricTile("Attesi", professor.expectedDays.toString(), "Giornate tipiche.", Modifier.weight(1f), FluidTone.Info, glass = true)
        FluidMetricTile("Tasso", "${(professor.presenceRate * 100).toInt()}%", "Presenze / attesi.", Modifier.weight(1f), presenceTone, glass = true)
      }
      if (professor.absenceDays.isNotEmpty()) {
        FluidSectionHeader("Probabili assenze recenti")
        professor.absenceDays.takeLast(5).forEach { date ->
          FluidListRow(
            title = date,
            subtitle = "Giorno tipico senza lezione registrata.",
            tone = FluidTone.Warning,
            badge = { FluidStatusBadge("ASSENTE", tone = FluidTone.Warning) },
          )
        }
      }
      FluidSectionHeader("Indice di rigore — ${professor.strictnessScore}/100")
      FluidListRow(
        title = professor.strictnessLabel,
        subtitle = "Punteggio basato su densità valutativa, scritti, peso e copertura degli argomenti.",
        tone = strictnessTone,
        badge = { FluidStatusBadge(professor.strictnessScore.toString(), tone = strictnessTone) },
      )
      if (professor.gradeCount > 0) {
        FluidSectionHeader("Voti")
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FluidMetricTile("Totale", professor.gradeCount.toString(), "Valutazioni.", Modifier.weight(1f), glass = true)
          professor.averageGrade?.let { average ->
            FluidMetricTile("Media", "%.1f".format(average), "Sul tuo profilo.", Modifier.weight(1f), presenceTone, glass = true)
          }
        }
      }
      FluidSectionHeader("Dossier")
      FluidListRow(
        title = professor.funNickname,
        subtitle = "${professor.longestPresenceStreakWeeks} settimane consecutive · ${professor.subjects.size} materie monitorate.",
        tone = FluidTone.Success,
        badge = { FluidStatusBadge("PROFILO", tone = FluidTone.Success) },
      )
    },
  )
}

@Composable
private fun ProfessorDetailContent(
  prof: ProfessorStats,
) {
  val strictnessTone = when (prof.strictnessLabel) {
    "Molto esigente" -> FluidTone.Danger
    "Esigente" -> FluidTone.Warning
    "Equilibrato" -> FluidTone.Info
    else -> FluidTone.Success
  }

  Box {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(prof.teacherName, style = MaterialTheme.typography.headlineSmall)
      }
      item { FluidSectionHeader("Materie") }
      item {
        Text(
          text = prof.subjects.joinToString(", ").ifBlank { "Non specificato" },
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      // ── Presenza ──────────────────────────────────────────────────────
      item { FluidSectionHeader("Presenza") }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FluidMetricTile(
            label = "Giorni presenti",
            value = "${prof.actualDays}",
            detail = "Lezioni firmate.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Success,
            glass = true,
          )
          FluidMetricTile(
            label = "Giorni attesi",
            value = "${prof.expectedDays}",
            detail = "Dalle sue giornate tipiche.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Info,
            glass = true,
          )
          FluidMetricTile(
            label = "Tasso",
            value = "${(prof.presenceRate * 100).toInt()}%",
            detail = "Presenze / attesi.",
            modifier = Modifier.weight(1f),
            tone = when {
              prof.presenceRate >= 0.85f -> FluidTone.Success
              prof.presenceRate >= 0.65f -> FluidTone.Warning
              else -> FluidTone.Danger
            },
            glass = true,
          )
        }
      }
      if (prof.absenceDays.isNotEmpty()) {
        item { FluidSectionHeader("Probabili assenze recenti") }
        fluidGlassGroups(prof.absenceDays.takeLast(5)) { date ->
          FluidListRow(
            title = date,
            subtitle = "Giorno tipico senza lezione registrata.",
            tone = FluidTone.Warning,
            badge = { FluidStatusBadge("ASSENTE", tone = FluidTone.Warning) },
          )
        }
      }

      // ── Indice di rigore ──────────────────────────────────────────────
      item { FluidSectionHeader("Indice di rigore — ${prof.strictnessScore}/100") }
      item {
        FluidListRow(
          title = prof.strictnessLabel,
          subtitle = "Punteggio calcolato su 4 indicatori oggettivi di classe.",
          tone = strictnessTone,
          badge = { FluidStatusBadge("${prof.strictnessScore}", tone = strictnessTone) },
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FluidMetricTile(
            label = "Voti/lezione",
            value = "%.2f".format(prof.evaluationDensity),
            detail = "Densita valutativa.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Info,
            glass = true,
          )
          FluidMetricTile(
            label = "Scritti",
            value = "${(prof.writtenExamRatio * 100).toInt()}%",
            detail = "Esami scritti.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Info,
            glass = true,
          )
          FluidMetricTile(
            label = "Peso medio",
            value = "%.1f".format(prof.avgGradeWeight),
            detail = "Importanza voti.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Info,
            glass = true,
          )
          FluidMetricTile(
            label = "Argomenti",
            value = "${(prof.topicCoverageRate * 100).toInt()}%",
            detail = "Lezioni firmate.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Info,
            glass = true,
          )
        }
      }

      // ── Voti ──────────────────────────────────────────────────────────
      if (prof.gradeCount > 0) {
        item { FluidSectionHeader("Voti") }
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            FluidMetricTile(
              label = "Totale voti",
              value = "${prof.gradeCount}",
              detail = "Valutazioni assegnate.",
              modifier = Modifier.weight(1f),
              glass = true,
            )
            prof.averageGrade?.let { avg ->
              FluidMetricTile(
                label = "La tua media",
                value = "%.1f".format(avg),
                detail = "Dipende dallo studente.",
                modifier = Modifier.weight(1f),
                tone = when {
                  avg >= 7.5 -> FluidTone.Success
                  avg >= 6.0 -> FluidTone.Warning
                  else -> FluidTone.Danger
                },
                glass = true,
              )
            }
            prof.mostFrequentGradeType?.let { type ->
              FluidMetricTile(
                label = "Tipo prevalente",
                value = type,
                detail = "Tipologia più comune.",
                modifier = Modifier.weight(1f),
                tone = FluidTone.Info,
                glass = true,
              )
            }
          }
        }
      }

      // ── Dossier Segreto 🔥 ─────────────────────────────────────────────
      item { FluidSectionHeader("Dossier Segreto 🕵️") }
      item {
        FluidListRow(
          title = prof.funNickname,
          subtitle = buildString {
            append("Classificazione segreta basata su ")
            append("${prof.actualDays} giorni di osservazione, ")
            append("${prof.gradeCount} valutazioni e ")
            append("${prof.subjects.size} materie monitorate.")
          },
          tone = FluidTone.Success,
          badge = { FluidStatusBadge("TOP SECRET", tone = FluidTone.Success) },
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
        FluidListRow(
          title = "Profilo Psicologico",
          subtitle = roast,
          tone = FluidTone.Warning,
          badge = { FluidStatusBadge("ROAST", tone = FluidTone.Warning) },
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FluidMetricTile(
            label = "Streak 🔥",
            value = "${prof.longestPresenceStreakWeeks}",
            detail = "Settimane consecutive.",
            modifier = Modifier.weight(1f),
            tone = if (prof.longestPresenceStreakWeeks >= 8) FluidTone.Success else FluidTone.Info,
            glass = true,
          )
          FluidMetricTile(
            label = "Giorno top",
            value = prof.favoriteDayOfWeek?.let { DayOfWeek.of(it).shortLabel() } ?: "N/D",
            detail = "Giorno con più firme.",
            modifier = Modifier.weight(1f),
            tone = FluidTone.Info,
            glass = true,
          )
          FluidMetricTile(
            label = "Materie",
            value = "${prof.subjects.size}",
            detail = prof.subjects.joinToString(", ").take(30),
            modifier = Modifier.weight(1f),
            tone = if (prof.subjects.size > 1) FluidTone.Warning else FluidTone.Info,
            glass = true,
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
          prof.strictnessScore > 70 -> FluidTone.Danger
          prof.strictnessScore > 50 -> FluidTone.Warning
          else -> FluidTone.Success
        }
        FluidListRow(
          title = "Livello di Pericolo",
          subtitle = "Indice di probabilità di essere interrogati/verificati a sorpresa.",
          eyebrow = dangerLevel,
          tone = dangerTone,
          badge = { FluidStatusBadge(dangerLevel.takeLast(dangerLevel.length - 2).trim(), tone = dangerTone) },
        )
      }
    }
  }
}
