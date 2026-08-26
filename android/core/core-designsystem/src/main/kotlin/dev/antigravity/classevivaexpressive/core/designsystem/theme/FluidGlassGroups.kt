package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.ui.fluid.LocalFluidItemSpacing
import dev.antigravity.fluidengine.ui.theme.FluidGroupOpenEdge
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
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
 * Quindi una sezione lunga diventa piu' pannelli. **Ma il taglio non si deve vedere**: cadrebbe ogni
 * otto righe, cioe' a un indice che nei dati non significa niente, e nella bacheca e' finito fra due
 * comunicazioni dello stesso giorno. Un mese resta un pannello solo per l'occhio e diventa piu'
 * pannelli per la GPU. Servono due cose insieme:
 *
 * - i pezzi arrotondano solo gli angoli che sono davvero l'inizio e la fine ([FluidGroupSegment]);
 * - il vuoto fra un pezzo e il successivo si richiude, e dentro una `LazyColumn` c'e' un modo solo:
 *   lo spazio lo mette `Arrangement.spacedBy`, che e' una misura sola per tutta la lista e non si
 *   puo' togliere per un item soltanto, quindi il pezzo **dichiara** un'altezza minore di quella che
 *   disegna, esattamente di quello spazio. Il successivo viene posato a filo. Disegnare oltre i
 *   propri limiti e' legittimo qui: una `LazyColumn` ritaglia il viewport, non i singoli item, ed e'
 *   lo stesso motivo per cui l'ombra di una card puo' uscire da lei.
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
  val chunks = items.chunked(FluidGlassGroupMaxRows)
  val last = chunks.lastIndex
  chunks.forEachIndexed { chunk, rows ->
    val segment = when {
      last == 0 -> FluidGroupSegment.Whole
      chunk == 0 -> FluidGroupSegment.First
      chunk == last -> FluidGroupSegment.Last
      else -> FluidGroupSegment.Middle
    }
    item(
      key = key?.let { "$it:$chunk" },
      contentType = "fluid-glass-group",
    ) {
      // L'ultimo pezzo e' l'unico che tiene lo spazio dopo di se': li' e' la fine della lista e
      // l'aria ci vuole. Gli altri la riassorbono, cosi' il pezzo dopo atterra a filo.
      // Lo spazio da riassorbire piu' due volte il ritaglio dei lati aperti: il pezzo che segue
      // deve risalire abbastanza da coprire il proprio ritaglio e quello del pezzo precedente,
      // altrimenti al posto della banda chiara resta una fessura chiara, che non e' un progresso.
      val density = LocalDensity.current
      val gapPx = with(density) {
        LocalFluidItemSpacing.current.roundToPx() + FluidGroupOpenEdge.roundToPx() * 2
      }
      val joinToNext = chunk != last
      FluidListGroup(
        glass = true,
        segment = segment,
        modifier = if (!joinToNext) {
          Modifier
        } else {
          Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height - gapPx).coerceAtLeast(0)) {
              placeable.place(0, 0)
            }
          }
        },
      ) {
        rows.forEachIndexed { index, value ->
          if (index > 0) FluidListDivider()
          row(value)
        }
        if (joinToNext) {
          // Il separatore della giuntura, che senza sarebbe l'unico confine fra due righe a non
          // averlo. Sta dentro il pannello e **sopra** la fascia ritagliata: appoggiato in fondo
          // verrebbe tagliato via insieme al bordo.
          FluidListDivider()
          Spacer(modifier = Modifier.height(FluidGroupOpenEdge))
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
