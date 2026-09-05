package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.fluidengine.ai.tools.AiToolGroup

/**
 * I gruppi di strumenti del registro: lo stadio 1 (o il pre-router locale) ne sceglie al massimo
 * quattro, e solo i tool di quei gruppi arrivano al modello. Gli `id` sono le parole che il modello
 * legge; gli `hint` sono le righe con cui il router capisce a cosa serve ciascuno; le `statusKey`
 * le traduce la UI ("Guardo i voti...").
 */
enum class RegistroToolGroup(
  override val id: String,
  override val statusKey: String,
  override val hint: String,
) : AiToolGroup {
  VOTI(
    "voti",
    "grades",
    "voti presi, medie per materia e generali, obiettivi per materia, cosa serve per raggiungere una media",
  ),
  AGENDA(
    "agenda",
    "agenda",
    "impegni in agenda, verifiche e interrogazioni in arrivo, compiti assegnati e il loro testo, eventi personali",
  ),
  ORARIO(
    "orario",
    "lessons",
    "l'orario delle lezioni di un giorno o della settimana, le lezioni svolte e gli argomenti firmati dai docenti",
  ),
  BACHECA(
    "bacheca",
    "board",
    "comunicazioni e circolari della scuola (cercarle, leggerle per intero, leggere gli allegati PDF), note disciplinari",
  ),
  ASSENZE(
    "assenze",
    "absences",
    "assenze, ritardi, uscite anticipate, quali sono da giustificare, il riepilogo",
  ),
  STATISTICHE(
    "statistiche",
    "stats",
    "statistiche generali e andamento dei voti, statistiche sui professori (presenza, rigore), punteggio studente",
  ),
  DIDATTICA(
    "didattica",
    "materials",
    "materiali didattici condivisi dai docenti, documenti della scuola (pagelle, certificati), libri di testo",
  ),
  APP(
    "app",
    "app",
    "azioni nell'app: aprire una pagina o un dettaglio, cambiare impostazioni (tema, colori, notifiche, sincronizzazione), " +
      "segnare lette le comunicazioni, prendere visione di una comunicazione, aggiungere un evento personale, salvare un obiettivo, aggiornare i dati",
  ),
  ;

  companion object {
    fun fromId(id: String?): RegistroToolGroup? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
  }
}
