package dev.antigravity.classevivaexpressive.core.assistant.prompt

import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import dev.antigravity.classevivaexpressive.core.assistant.tools.Text

/**
 * Lo stadio zero: una tabella di parole che decide i gruppi senza chiamare nessuno. Se la domanda
 * nomina un gruppo solo, e lo nomina chiaramente, il router remoto si salta e si risparmiano una
 * chiamata e un secondo; se ne sfiora piu' d'uno, i candidati vanno al router come suggerimento.
 * Sbagliare qui costa poco: il modello ha comunque `altri_tool` per chiedere cio' che manca.
 */
object PreRouter {

  data class Verdict(
    /** I gruppi decisi (se [confident]) o suggeriti. */
    val groups: Set<RegistroToolGroup>,
    val confident: Boolean,
    /** La domanda chiede di leggere un allegato o un testo lungo: si parte dal livello profondo. */
    val deep: Boolean,
  )

  private class Rule(val group: RegistroToolGroup, pattern: String, val weight: Int = 2) {
    val regex = Regex(pattern)
  }

  private val rules = listOf(
    Rule(RegistroToolGroup.VOTI, "\\b(vot[oi]|medi[ae]|obiettiv\\w*|insufficienz\\w*|sufficienz\\w*|ho preso|prender[oe]|valutazion[ei])\\b"),
    Rule(RegistroToolGroup.VOTI, "\\b(pagell\\w*)\\b", 1),
    Rule(RegistroToolGroup.AGENDA, "\\b(compit[oi]|verific[ah]e?|interrogazion[ei]|impegn[oi]|scadenz[ae]|agenda|event[oi]|da fare|da studiare|consegn\\w*)\\b"),
    Rule(RegistroToolGroup.AGENDA, "\\b(domani|dopodomani|settimana|prossim[oaie]|lunedi|martedi|mercoledi|giovedi|venerdi|sabato)\\b", 1),
    Rule(RegistroToolGroup.ORARIO, "\\b(orario|lezion[ei]|argoment[oi]|spiegat[oaie]|fatto in classe|che ore|a che ora|prima ora|ultima ora|quante ore)\\b"),
    Rule(RegistroToolGroup.BACHECA, "\\b(circolar[ei]|comunicazion[ei]|bacheca|avvis[oi]|allegat[oi]|pdf|not[ae] disciplinar\\w*|annotazion[ei]|richiam[oi])\\b"),
    Rule(RegistroToolGroup.ASSENZE, "\\b(assenz[ae]|assente|ritard[oi]|uscit[ae] anticipat[ae]|giustific\\w*|presenz[ae]|mancat[oai])\\b"),
    Rule(RegistroToolGroup.STATISTICHE, "\\b(statistic\\w*|andamento|distribuzion\\w*|professor[ei]|prof|docent[ei]|punteggio|rigor\\w*|severit\\w*|trend)\\b"),
    Rule(RegistroToolGroup.DIDATTICA, "\\b(material[ei]|didattic[ao]|document[oi]|libr[oi]|testo adottat\\w*|dispens[ae]|cartell[ae])\\b"),
    Rule(RegistroToolGroup.APP, "\\b(apri|aprimi|vai (a|su|alla|ai|al|in)|mostra(mi)?|porta(mi)?|imposta|cambia|metti|attiva|disattiva|spegni|accendi|segna|aggiungi|salva|aggiorna|sincronizza|ricarica|tema|scuro|chiaro|notific\\w*|colore|accento)\\b"),
    Rule(RegistroToolGroup.APP, "\\b(presa visione|presa d'atto|conferma la lettura|segna(le)? (come )?lett[ae])\\b", 3),
  )

  private val deepSignals = Regex("\\b(allegat[oi]|pdf|cosa dice|cosa c'e' scritto|cosa c e scritto|contenuto|riassum\\w*|leggi(mi)?|leggere|testo (della|del))\\b")

  fun decide(question: String, actionsEnabled: Boolean): Verdict {
    val text = Text.normalize(question)
    val scores = linkedMapOf<RegistroToolGroup, Int>()
    rules.forEach { rule ->
      if (rule.group == RegistroToolGroup.APP && !actionsEnabled) return@forEach
      val hits = rule.regex.findAll(text).count()
      if (hits > 0) scores[rule.group] = (scores[rule.group] ?: 0) + rule.weight * hits
    }
    val deep = deepSignals.containsMatchIn(text) && (scores.containsKey(RegistroToolGroup.BACHECA) || scores.containsKey(RegistroToolGroup.DIDATTICA)) ||
      question.length > 400
    if (scores.isEmpty()) return Verdict(emptySet(), confident = false, deep = deep)
    val ranked = scores.entries.sortedByDescending { it.value }
    val top = ranked.first()
    val second = ranked.getOrNull(1)
    val confident = top.value >= 2 && (second == null || second.value == 0) && top.key != RegistroToolGroup.APP
    val groups = if (confident) setOf(top.key) else ranked.map { it.key }.take(4).toSet()
    return Verdict(groups, confident, deep)
  }
}
