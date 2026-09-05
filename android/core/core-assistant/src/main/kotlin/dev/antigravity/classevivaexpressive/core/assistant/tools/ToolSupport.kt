package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Le date come le scrive il modello e come le legge l'utente. Il modello riceve sempre `yyyy-MM-dd`
 * piu' il giorno della settimana fra parentesi: la seconda parte e' quella che gli evita di dire
 * "venerdi'" di un sabato.
 */
object Dates {
  private val italian: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val italianShort: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

  private val weekdays: Map<String, DayOfWeek> = mapOf(
    "lunedi" to DayOfWeek.MONDAY,
    "martedi" to DayOfWeek.TUESDAY,
    "mercoledi" to DayOfWeek.WEDNESDAY,
    "giovedi" to DayOfWeek.THURSDAY,
    "venerdi" to DayOfWeek.FRIDAY,
    "sabato" to DayOfWeek.SATURDAY,
    "domenica" to DayOfWeek.SUNDAY,
  )

  /**
   * Una data scritta dal modello: ISO, italiana, o una parola ("oggi", "domani", "ieri", "lunedi'",
   * "lunedi' prossimo"). Un giorno della settimana e' la prossima occorrenza, oggi compreso.
   */
  fun parse(raw: String?, today: LocalDate): LocalDate? {
    val text = Text.normalize(raw ?: return null)
    if (text.isEmpty()) return null
    when (text) {
      "oggi" -> return today
      "domani" -> return today.plusDays(1)
      "dopodomani" -> return today.plusDays(2)
      "ieri" -> return today.minusDays(1)
      "l altro ieri", "l'altro ieri", "altro ieri" -> return today.minusDays(2)
    }
    runCatching { return LocalDate.parse(text) }
    runCatching { return LocalDate.parse(text, italian) }
    runCatching { return LocalDate.parse(text, italianShort) }
    val words = text.split(" ")
    val day = words.firstNotNullOfOrNull { weekdays[it] } ?: return null
    val next = words.any { it.startsWith("prossim") }
    val previous = words.any { it.startsWith("scors") || it.startsWith("passat") }
    return when {
      previous -> today.with(TemporalAdjusters.previous(day))
      next && today.dayOfWeek == day -> today.plusWeeks(1)
      else -> today.with(TemporalAdjusters.nextOrSame(day))
    }
  }

  /** L'intervallo di una richiesta: [from]-[to] se dati, altrimenti [defaultDays] giorni da oggi in avanti. */
  fun range(from: String?, to: String?, today: LocalDate, defaultDays: Long): ClosedRange<LocalDate> {
    val start = parse(from, today)
    val end = parse(to, today)
    return when {
      start != null && end != null -> if (end < start) end..start else start..end
      start != null -> start..start.plusDays(defaultDays)
      end != null -> today..end
      else -> today..today.plusDays(defaultDays)
    }
  }

  fun label(date: LocalDate): String = "$date (${shortDay(date.dayOfWeek)})"

  /** Una data come la scrive l'app (ISO); se non si legge, torna com'e'. */
  fun label(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val parsed = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return raw
    return label(parsed)
  }

  fun parseAppDate(raw: String?): LocalDate? = raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

  fun shortDay(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "lun"
    DayOfWeek.TUESDAY -> "mar"
    DayOfWeek.WEDNESDAY -> "mer"
    DayOfWeek.THURSDAY -> "gio"
    DayOfWeek.FRIDAY -> "ven"
    DayOfWeek.SATURDAY -> "sab"
    DayOfWeek.SUNDAY -> "dom"
  }

  fun longDay(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "lunedi'"
    DayOfWeek.TUESDAY -> "martedi'"
    DayOfWeek.WEDNESDAY -> "mercoledi'"
    DayOfWeek.THURSDAY -> "giovedi'"
    DayOfWeek.FRIDAY -> "venerdi'"
    DayOfWeek.SATURDAY -> "sabato"
    DayOfWeek.SUNDAY -> "domenica"
  }
}

/** Confronti fra testi scritti da persone diverse: senza accenti, senza maiuscole, senza doppi spazi. */
object Text {
  fun normalize(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9/'\\-]+"), " ").trim().replace(Regex(" +"), " ")
  }

  /** Vero se tutte le parole di [query] compaiono in [candidate] (come prefissi di parola). */
  fun matches(query: String, candidate: String): Boolean {
    val words = normalize(query).split(" ").filter { it.length > 1 }
    if (words.isEmpty()) return false
    val target = normalize(candidate)
    val targetWords = target.split(" ")
    return words.all { w -> targetWords.any { it.startsWith(w) } || target.contains(w) }
  }

  fun clip(text: String?, max: Int): String {
    val clean = text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    return if (clean.length <= max) clean else clean.take(max - 1) + "…"
  }
}

/**
 * Le materie come le dice lo studente ("mate", "fisica", "inglese") contro come le scrive il registro
 * ("MATEMATICA", "LINGUA E CULTURA INGLESE"). Prima l'uguaglianza, poi l'inizio, poi la parola dentro.
 */
object Subjects {
  private val aliases = mapOf(
    "mate" to "matematica",
    "ita" to "italiano",
    "ing" to "inglese",
    "sto" to "storia",
    "geo" to "geografia",
    "fis" to "fisica",
    "chim" to "chimica",
    "bio" to "biologia",
    "ed fisica" to "scienze motorie",
    "ginnastica" to "scienze motorie",
    "motoria" to "scienze motorie",
    "info" to "informatica",
    "arte" to "arte",
    "religione" to "religione",
    "filo" to "filosofia",
    "lat" to "latino",
    "gre" to "greco",
  )

  fun match(query: String?, subjects: Collection<String>): String? {
    val raw = query?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val distinct = subjects.distinct()
    val normalized = Text.normalize(raw)
    val wanted = aliases[normalized] ?: normalized
    distinct.firstOrNull { Text.normalize(it) == wanted }?.let { return it }
    distinct.firstOrNull { Text.normalize(it).startsWith(wanted) }?.let { return it }
    distinct.firstOrNull { Text.normalize(it).split(" ").any { word -> word.startsWith(wanted) } }?.let { return it }
    distinct.firstOrNull { Text.normalize(it).contains(wanted) }?.let { return it }
    // "scienze motorie" contro "SCIENZE MOTORIE E SPORTIVE": tutte le parole della richiesta dentro.
    return distinct.firstOrNull { Text.matches(wanted, it) }
  }
}

/** Una riga di voto per il modello: data, materia, valore, tipo, peso se diverso da uno, descrizione. */
fun Grade.toolLine(withSubject: Boolean = true): String = buildString {
  append(Dates.label(date))
  if (withSubject) append(" · ").append(subject)
  append(" · ").append(valueLabel)
  if (type.isNotBlank()) append(" (").append(type).append(')')
  val w = GradeWeights.effective(this@toolLine)
  if (w != 1.0) append(" peso ").append(GradeWeights.format(w))
  description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(Text.clip(it, 80)) }
  notes?.takeIf { it.isNotBlank() }?.let { append(" · nota: ").append(Text.clip(it, 60)) }
  append(" · id ").append(id)
}

/** Il peso di un voto come lo intende il registro: assente = 1, zero = non conta, percentuale = frazione. */
object GradeWeights {
  fun effective(grade: Grade): Double {
    val w = grade.weight ?: return 1.0
    return when {
      w <= 0.0 -> 0.0
      w > 5.0 -> w / 100.0
      else -> w
    }
  }

  fun format(w: Double): String = if (w == w.toLong().toDouble()) w.toLong().toString() else String.format(Locale.ROOT, "%.2f", w).trimEnd('0').trimEnd('.')
}
