package dev.antigravity.classevivaexpressive.core.designsystem.theme

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")
private val gradeDateFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale)
private val gradeDateTimeFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", italianLocale)

/**
 * La data di un voto, come la scriverebbe una persona.
 *
 * Vive qui e non in una schermata perche' la stessa data compare in tre posti — la lista, la home e
 * il pop-up — e finche' ognuno se la formattava per conto suo, la home mostrava "2026-05-30" in
 * mezzo a card che altrove dicevano "30 mag 2026". Se il formato non si riconosce si restituisce la
 * stringa com'e': meglio una data grezza che una riga vuota.
 */
fun gradeDateLabel(isoDate: String): String =
  runCatching { LocalDate.parse(isoDate).format(gradeDateFormatter) }.getOrDefault(isoDate)

/** Quando una versione precedente e' stata rilevata. */
fun gradeDateTimeLabel(epochMillis: Long): String = runCatching {
  Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(gradeDateTimeFormatter)
}.getOrDefault("")
