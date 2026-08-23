package dev.antigravity.classevivaexpressive.core.designsystem.theme

import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.fluidengine.foundation.SyncState as EngineSyncState
import dev.antigravity.fluidengine.foundation.SyncStatus as FluidSyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState as AppSyncState
import dev.antigravity.fluidengine.ui.theme.lastSyncLabel as fluidLastSyncLabel
import dev.antigravity.fluidengine.ui.theme.noticeMessage as fluidNoticeMessage

/**
 * Il messaggio per l'unico "non e' un errore" che ClasseViva conosce.
 *
 * Un anno che la scuola non ha ancora aperto e' lo stato ordinario del registro nelle settimane
 * intorno a settembre: niente e' rotto e nessuno puo' aggiustarlo. Vestirlo da guasto e' il modo in
 * cui un colore smette di voler dire qualcosa.
 */
private const val SchoolYearNotStartedNotice = "Anno scolastico non ancora aperto"

/**
 * Da stato di sincronizzazione dell'app a quello che i componenti dell'engine leggono.
 *
 * I due tipi restano separati apposta. Nel dominio `schoolYearNotStarted` e' un **booleano** perche'
 * decide un comportamento — se il coordinatore puo' ricadere sull'anno precedente — e un booleano e'
 * la forma giusta per una decisione. Nell'engine e' una **frase**, perche' li' serve solo da dire, e
 * un design system che sapesse cos'e' un anno scolastico saprebbe troppo.
 *
 * Questa funzione e' il punto in cui la prima diventa la seconda, ed e' l'unico posto in cui la
 * frase e' scritta.
 */
fun SyncStatus.toFluid(): FluidSyncStatus = FluidSyncStatus(
  state = when (state) {
    AppSyncState.IDLE -> EngineSyncState.IDLE
    AppSyncState.SYNCING -> EngineSyncState.SYNCING
    AppSyncState.PARTIAL -> EngineSyncState.PARTIAL
    AppSyncState.OFFLINE -> EngineSyncState.OFFLINE
    AppSyncState.ERROR -> EngineSyncState.ERROR
  },
  lastSuccessfulSyncEpochMillis = lastSuccessfulSyncEpochMillis,
  message = message,
  failedSections = failedSections,
  notice = if (schoolYearNotStarted) SchoolYearNotStartedNotice else null,
)

/** Quanto tempo fa e' andata a buon fine l'ultima sincronizzazione, in parole. */
fun SyncStatus.lastSyncLabel(): String = toFluid().fluidLastSyncLabel()

/**
 * Cosa c'e' da dire su questo aggiornamento, o `null` se non c'e' niente.
 *
 * Esposta perche' una lista possa decidere se riservare uno spazio: una voce che non disegna niente
 * si prende comunque la sua parte di spaziatura, che diventa un buco in cima alla pagina senza
 * nessuna spiegazione attaccata.
 */
fun SyncStatus.noticeMessage(): String? = toFluid().fluidNoticeMessage()
