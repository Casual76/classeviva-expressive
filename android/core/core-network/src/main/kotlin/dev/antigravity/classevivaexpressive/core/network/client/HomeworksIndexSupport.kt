package dev.antigravity.classevivaexpressive.core.network.client

import android.util.Log
import kotlinx.serialization.json.JsonElement

/**
 * La sezione Compiti del registro (`/rest/w1/students/{id}/homeworks/index`).
 *
 * Non e' documentata, e le due strade per arrivarci — il token dell'API REST e la sessione del
 * portale — si scelgono per prova. Questo file tiene quello che hanno in comune: come si riconosce
 * una risposta che e' davvero dati, e il segno che lasciano nel log.
 */
internal const val HomeworksIndexLogTag = "CompitiW1"

internal fun homeworksIndexPath(studentId: String): String = "/rest/w1/students/$studentId/homeworks/index"

internal const val WhoAmIPath = "/rest/w1/misc/whoami"

/**
 * Una riga per chiamata: strada, codice, tipo di contenuto e quanti compiti. Mai il corpo, che
 * contiene i compiti e il nome dello studente.
 */
internal fun logHomeworksIndexAttempt(route: String, code: Int, contentType: String?, items: Int?) {
  runCatching {
    Log.i(HomeworksIndexLogTag, "$route -> $code ${contentType.orEmpty()} items=${items ?: "-"}")
  }
}

/**
 * Una strada che non e' arrivata a una risposta: il tipo d'errore e il messaggio, che e' dell'app
 * (mai credenziali ne' corpi di risposta).
 */
internal fun logHomeworksIndexFailure(route: String, error: Throwable) {
  runCatching {
    Log.i(HomeworksIndexLogTag, "$route -> ${error::class.simpleName}: ${error.message.orEmpty().take(120)}")
  }
}

/**
 * Se il corpo e' JSON e non la pagina di login.
 *
 * Chi non e' autorizzato non riceve sempre un 401: il sito puo' rispondere 200 con l'HTML
 * dell'accesso, e leggerlo come dati darebbe una lista vuota che sembra vera.
 */
internal fun looksLikeJsonPayload(body: String): Boolean {
  val head = body.trimStart()
  return head.startsWith("{") || head.startsWith("[")
}

/**
 * L'id numerico dello studente dalla risposta di `whoami`.
 *
 * La forma non e' documentata: si cerca un id in cima e dentro gli involucri piu' probabili, e se
 * c'e' solo il codice d'identita' (`S1234567X`) se ne tengono le cifre, come fa il login REST.
 */
internal fun parseWhoAmIStudentId(payload: JsonElement): String? {
  val root = payload.obj()
  val scopes = listOf(root, root["data"].obj(), root["user"].obj(), root["student"].obj())
  scopes.forEach { scope ->
    scope.string("id", "studentId", "usrId", "userId")?.let(::normalizeStudentId)?.let { return it }
    scope.string("ident", "identity")?.let(::normalizeStudentId)?.let { return it }
  }
  return null
}
