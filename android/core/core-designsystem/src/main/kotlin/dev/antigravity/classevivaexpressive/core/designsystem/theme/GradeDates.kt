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

private val nearDayFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("EEE d MMM", italianLocale)

/**
 * Un giorno vicino, detto come lo si dice: «Oggi», «Domani», «Ieri», e altrimenti «mer 24 set».
 *
 * Per le righe che parlano di cose imminenti o appena passate — gli impegni in arrivo, un avviso
 * nuovo — dove l'anno e' ovvio e la distanza da oggi e' l'informazione. Lontano dall'anno in corso
 * torna la data intera. Una stringa che non e' una data resta com'e'.
 */
fun nearDayLabel(isoDate: String, today: LocalDate = LocalDate.now()): String {
  val date = runCatching { LocalDate.parse(isoDate.take(10)) }.getOrNull() ?: return isoDate
  return when (date.toEpochDay() - today.toEpochDay()) {
    0L -> "Oggi"
    1L -> "Domani"
    -1L -> "Ieri"
    else -> if (date.year == today.year) {
      date.format(nearDayFormatter)
    } else {
      date.format(gradeDateFormatter)
    }
  }
}
