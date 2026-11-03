package dev.antigravity.classevivaexpressive

/**
 * Il nome della rotta senza argomenti e senza parti dopo la barra: `communications?tab=board` e
 * `grade/42` diventano `communications` e `grade`. E' la chiave con cui si riconoscono le sezioni
 * della barra, qualunque cosa le abbia aperte.
 */
internal fun normalizeRoute(route: String?): String? {
  return route
    ?.substringBefore("?")
    ?.substringBefore("/")
    ?.takeIf { it.isNotBlank() }
}

/**
 * L'ordine fra due sezioni della barra, per il passo laterale (`FluidPeerMove`): positivo se [toRoute]
 * sta dopo [fromRoute], negativo se sta prima, **zero se le due non sono pari** (una pagina di
 * dettaglio, la stessa sezione): allora valgono le regole delle pagine, non quelle delle schede.
 * La direzione si legge dall'ordine della barra e non si registra, cosi' l'indietro che disfa un passo
 * e' lo specchio dell'andata.
 */
internal fun peerDirection(fromRoute: String?, toRoute: String?): Int {
  val fromIndex = topLevelRouteOrder.indexOf(fromRoute)
  val toIndex = topLevelRouteOrder.indexOf(toRoute)
  if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return 0
  return if (toIndex > fromIndex) 1 else -1
}
