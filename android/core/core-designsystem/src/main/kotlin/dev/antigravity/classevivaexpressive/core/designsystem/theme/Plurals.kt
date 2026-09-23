package dev.antigravity.classevivaexpressive.core.designsystem.theme

/**
 * Un numero col suo nome, al singolare quando e' uno: «1 voto», «3 voti».
 *
 * Scritta una volta sola perche' ogni schermata che contava qualcosa se la scriveva da se', o non se
 * la scriveva affatto — e «1 voti», «1 settimane», «1 materie» comparivano proprio nel caso piu'
 * frequente all'inizio dell'anno.
 */
fun countLabel(count: Int, one: String, many: String): String =
  if (count == 1) "1 $one" else "$count $many"
