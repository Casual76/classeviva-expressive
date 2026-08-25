package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup

/**
 * Una sezione di righe, dentro una lista pigra, come pannelli di vetro.
 *
 * Sostituisce il `items()` riga per riga, e il motivo e' la regola del design system: **il vetro va
 * sul contenitore, mai sulla riga.** Venti righe erano venti superfici, cioe' venti registrazioni di
 * layer e venti catene di `RenderEffect`, per un effetto che nessuno distingue da una sola.
 *
 * ### Perche' spezza
 *
 * Un pannello di vetro registra un `GraphicsLayer` grande quanto se stesso, e un layer e' una
 * texture della GPU. Un gruppo e' alto quanto le righe che uno si trova ad avere: la pagina Voti di
 * questo account ne ha ottantaquattro, cioe' sedicimila pixel, e oltre il tetto il pannello arriva
 * **nero**. Non e' un caso limite teorico, e' stata la prima cosa che si e' vista accendendo il
 * vetro sulle liste.
 *
 * Quindi una sezione lunga diventa piu' pannelli, e non e' solo una difesa: un pannello di cui non
 * si vedono mai i bordi non si legge come un pannello. Dodici righe sono piu' o meno una schermata,
 * che e' la scala a cui il materiale si vede.
 *
 * Il prezzo e' `animateItem()`, che funziona solo su un item diretto della lista: le righe non si
 * riordinano piu' con un'animazione propria. Nessuna di queste liste riordina niente mentre la
 * guardi, arrivano gia' ordinate dal database.
 */
fun <T> LazyListScope.fluidGlassGroups(
  items: List<T>,
  key: String? = null,
  row: @Composable (T) -> Unit,
) {
  if (items.isEmpty()) return
  items.chunked(FluidGlassGroupMaxRows).forEachIndexed { chunk, rows ->
    item(
      key = key?.let { "$it:$chunk" },
      contentType = "fluid-glass-group",
    ) {
      FluidListGroup(glass = true) {
        rows.forEachIndexed { index, value ->
          if (index > 0) FluidListDivider()
          row(value)
        }
      }
    }
  }
}

/**
 * Quante righe stanno in un pannello prima che ne cominci un altro. Vedi [fluidGlassGroups].
 *
 * Anche un numero di *composizione*, non solo di texture: un pannello e' un item solo della lista
 * pigra, e tutto il suo contenuto viene composto e misurato nel frame in cui entra. A dodici righe
 * di comunicazioni multi-riga quel frame costava 50-70 ms — il singhiozzo che si vedeva a ogni
 * pannello nuovo anche su un telefono veloce. Otto tiene il pannello alla scala a cui il materiale
 * si legge e il frame d'ingresso dentro il bilancio.
 */
private const val FluidGlassGroupMaxRows = 8
