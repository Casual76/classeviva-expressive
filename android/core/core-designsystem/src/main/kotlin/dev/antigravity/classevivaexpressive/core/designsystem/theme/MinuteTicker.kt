package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/**
 * L'ora corrente, aggiornata allo scoccare del minuto e solo mentre la schermata e' davanti.
 *
 * Restituisce lo `State` e non il valore **di proposito**. Chi lo chiama non deve leggerlo: deve
 * passarlo giu' come lambda a chi ne ha bisogno. Un `val now = rememberMinuteTicker().value` nel
 * corpo di una schermata funziona, sembra a posto, e ricompone l'intera lista una volta al minuto —
 * su un orario da trenta righe si misura.
 *
 * Tre proprieta', e come si ottengono:
 * - **si ferma quando la pagina non e' in primo piano**: `repeatOnLifecycle(RESUMED)` cancella la
 *   coroutine a `onPause`, quindi niente timer dietro uno schermo bloccato o su un'altra rotta;
 * - **batte una volta al minuto e non sessanta al minuto**: il valore e' troncato al minuto, quindi
 *   cambia solo quando cambia il minuto;
 * - **non deriva**: l'attesa e' calcolata sul bordo del minuto reale, non `delay(60_000)`, che
 *   accumulerebbe il ritardo di ogni giro.
 */
@Composable
fun rememberMinuteTicker(clock: () -> LocalDateTime = LocalDateTime::now): State<LocalDateTime> {
  val lifecycleOwner = LocalLifecycleOwner.current
  return produceState(initialValue = clock().truncatedTo(ChronoUnit.MINUTES), lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (true) {
        val now = clock()
        value = now.truncatedTo(ChronoUnit.MINUTES)
        delay(millisUntilNextMinute(now))
      }
    }
  }
}

/** Quanti millisecondi mancano allo scoccare del minuto. Puro, cosi' il bordo si verifica. */
internal fun millisUntilNextMinute(now: LocalDateTime): Long {
  val elapsed = now.second * 1_000L + now.nano / 1_000_000L
  return (60_000L - elapsed).coerceIn(1L, 60_000L)
}
