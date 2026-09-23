package dev.antigravity.classevivaexpressive.core.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * La famiglia di una materia: la chiave con cui l'app le da' un colore.
 *
 * Classeviva chiama la stessa materia in modi diversi da scuola a scuola ("DISEGNO E STORIA
 * DELL'ARTE", "Storia dell'arte", "LINGUA E CULTURA STRANIERA INGLESE"…), e a volte in modi diversi
 * nello stesso registro (l'agenda scrive in minuscolo quello che i voti scrivono in maiuscolo). La
 * chiave e' quello che resta uguale.
 *
 * Le chiavi sono salvate nelle impostazioni come indice dei colori scelti: **non si rinominano
 * mai**, o il colore scelto da chi usa l'app torna quello di default senza dire niente.
 */
object SubjectKeys {
  const val Storia = "storia"
  const val Arte = "arte"
  const val Motorie = "motorie"
  const val Scienze = "scienze"
  const val Religione = "religione"
  const val Informatica = "informatica"
  const val Inglese = "inglese"
  const val Italiano = "italiano"
  const val Latino = "latino"
  const val Greco = "greco"
  const val Filosofia = "filosofia"
  const val Matematica = "matematica"
  const val Fisica = "fisica"
  const val Civica = "civica"
  const val Geografia = "geografia"

  /** Una materia che non appartiene a nessuna famiglia nota tiene il proprio nome, con questo prefisso. */
  const val FallbackPrefix = "x:"

  private val diacritics = Regex("\\p{M}+")
  private val separators = Regex("[^a-z0-9]+")

  /** Minuscolo, senza accenti ne' apostrofi (anche quelli tipografici), parole separate da uno spazio. */
  fun normalize(raw: String): String {
    val decomposed = Normalizer.normalize(raw.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
    return decomposed.replace(diacritics, "").replace(separators, " ").trim()
  }

  /**
   * La chiave della materia, o null se il nome e' vuoto. Di un orario che unisce due materie
   * ("FISICA / MATEMATICA") conta la prima, come conta la prima nel titolo del blocco.
   */
  fun keyOf(subject: String?): String? {
    val first = subject?.substringBefore(" / ")?.let(::normalize)
    if (first.isNullOrEmpty()) return null
    val words = first.split(' ')
    // Una supplenza non e' una materia: e' un'ora di qualcun altro, e un colore le darebbe
    // un'identita' che non ha.
    if (words.any { it in NotASubject }) return null
    return familyOf(words) ?: (FallbackPrefix + first)
  }

  /**
   * La famiglia nominata in un testo libero, o null: per un compito che il registro manda senza
   * materia ma col titolo "Chimica: studiare pag. 4". Solo le famiglie note: il titolo di un compito
   * non e' il nome di una materia sconosciuta.
   */
  fun familyIn(text: String?): String? {
    val normalized = text?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return null
    return familyOf(normalized.split(' '))
  }

  private fun familyOf(words: List<String>): String? {
    fun has(word: String) = word in words
    fun starts(prefix: String) = words.any { it.startsWith(prefix) }

    // L'ordine e' la regola: "storia dell'arte" e' arte prima di essere storia, "educazione fisica"
    // e' motoria prima di essere fisica, "scienze motorie" prima di essere scienze.
    return when {
      has("arte") || has("disegno") -> Arte
      starts("motori") || starts("sportiv") || has("ginnastica") || (has("educazione") && has("fisica")) -> Motorie
      has("naturali") || has("biologia") || has("chimica") ||
        (has("scienze") && words.none { it in NonNaturalSciences }) -> Scienze
      has("religione") || has("irc") || starts("cattolic") || has("alternativa") -> Religione
      starts("informatic") -> Informatica
      has("inglese") || has("english") -> Inglese
      starts("italian") -> Italiano
      starts("latin") -> Latino
      starts("grec") -> Greco
      starts("filosof") -> Filosofia
      starts("matemat") || has("mate") -> Matematica
      has("fisica") -> Fisica
      words.any { "storia" in it } -> Storia
      has("civica") || has("cittadinanza") -> Civica
      starts("geograf") -> Geografia
      else -> null
    }
  }

  /** Il nome da mostrare di una famiglia, o null per una materia fuori famiglia. */
  fun familyLabel(key: String): String? = when (key) {
    Storia -> "Storia"
    Arte -> "Storia dell'arte"
    Motorie -> "Scienze motorie"
    Scienze -> "Scienze naturali"
    Religione -> "Religione"
    Informatica -> "Informatica"
    Inglese -> "Inglese"
    Italiano -> "Italiano"
    Latino -> "Latino"
    Greco -> "Greco"
    Filosofia -> "Filosofia"
    Matematica -> "Matematica"
    Fisica -> "Fisica"
    Civica -> "Educazione civica"
    Geografia -> "Geografia"
    else -> null
  }

  private val NotASubject = setOf("supplenza", "sostituzione", "supplente")

  private val NonNaturalSciences = setOf("umane", "sociali", "giuridiche", "economiche", "motorie")
}
