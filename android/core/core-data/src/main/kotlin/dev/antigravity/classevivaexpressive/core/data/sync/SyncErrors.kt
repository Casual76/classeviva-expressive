package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaSchoolYearNotStartedException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Which sections failed to refresh, and why.
 *
 * The coordinator used to collect only the section names and then write a fixed
 * "Aggiornamento non riuscito" into the sync metadata for every one of them. A school that simply
 * does not publish a feature, an expired session and a dropped connection all reached the user as
 * the same four words, which left nothing to act on and nothing to report.
 *
 * The network layer already produces a specific, user-facing reason for each failure; this type
 * exists so that reason survives the trip to the screen.
 */
internal class SyncErrors {

  private val recorded = LinkedHashMap<String, String>()

  /** True once any section failed because the school year is not open yet. */
  var schoolYearNotStarted: Boolean = false
    private set

  /** Sections that failed, in the order they failed. */
  val sections: List<String> get() = recorded.keys.toList()

  /** Each failed section with the reason it gave, in the order they failed. */
  val reasons: Map<String, String> get() = LinkedHashMap(recorded)

  fun isEmpty(): Boolean = recorded.isEmpty()

  operator fun contains(section: String): Boolean = recorded.containsKey(section)

  fun reasonFor(section: String): String? = recorded[section]

  fun record(section: String, cause: Throwable?) {
    recorded[section] = describeSyncFailure(cause)
    if (cause.isSchoolYearNotStarted()) schoolYearNotStarted = true
  }

  /** Records a failure whose cause is not available at the call site. */
  operator fun plusAssign(section: String) {
    record(section, null)
  }
}

internal const val GenericSyncFailure = "Aggiornamento non riuscito"

private fun Throwable?.isSchoolYearNotStarted(): Boolean {
  var current: Throwable? = this
  var depth = 0
  while (current != null && depth < MaxCauseDepth) {
    if (current is ClassevivaSchoolYearNotStartedException) return true
    current = current.cause
    depth++
  }
  return false
}

/**
 * Turns a failure into something worth showing.
 *
 * Only messages the network layer authored itself are passed through — those are written for the
 * user and contain no request details. Anything else is reduced to a category, so an unexpected
 * exception can never carry a URL, a token or a response body onto the screen.
 */
internal fun describeSyncFailure(cause: Throwable?): String = when (cause) {
  null -> GenericSyncFailure
  is UnknownHostException -> "Nessuna connessione a Internet."
  is SocketTimeoutException -> "Classeviva non ha risposto in tempo."
  else -> cause.userFacingMessage() ?: when (cause) {
    is IOException -> "Errore di rete durante l'aggiornamento."
    else -> GenericSyncFailure
  }
}

/**
 * The deepest message in the chain that was written for a person rather than for a stack trace.
 *
 * Network failures arrive wrapped, and it is the innermost [ClassevivaNetworkException] that carries
 * the useful sentence ("Sessione scaduta", "Risorsa Classeviva non trovata"); the outer wrapper only
 * says that something went wrong somewhere.
 */
private fun Throwable.userFacingMessage(): String? {
  var current: Throwable? = this
  var best: String? = null
  var depth = 0
  while (current != null && depth < MaxCauseDepth) {
    if (current is ClassevivaNetworkException) {
      current.message?.takeIf { it.isNotBlank() }?.let { best = it }
    }
    current = current.cause
    depth++
  }
  return best
}

/** Guards against a cycle in a `cause` chain, which would otherwise spin here forever. */
private const val MaxCauseDepth = 8
