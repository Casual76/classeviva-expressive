package dev.antigravity.classevivaexpressive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidDetailContent
import dev.antigravity.fluidengine.ui.fluid.FluidDetailPlaceholder
import dev.antigravity.fluidengine.ui.fluid.FluidListDetailScaffold
import dev.antigravity.fluidengine.ui.fluid.fluidListDetailLayout

/**
 * Una pagina "elenco che apre una pagina" che su uno schermo largo diventa elenco+dettaglio.
 *
 * Sul telefono niente cambia: toccare una riga chiama [onOpenPage], che spinge la rotta di dettaglio
 * come prima, con la sua apertura dal punto toccato. Su uno schermo largo la stessa riga sceglie cosa
 * mostrare nel pannello accanto, e il dettaglio e' lo stesso composable della rotta — una pagina
 * sola, scritta una volta.
 *
 * Quando la finestra si stringe con qualcosa aperto accanto (rotazione, finestra divisa), quella
 * cosa non sparisce: diventa la pagina di dettaglio, cioe' quello che si vedrebbe sul telefono
 * dopo averla aperta.
 */
@Composable
internal fun AdaptiveListDetail(
  ambient: FluidAmbient,
  emptyTitle: String,
  emptyMessage: String,
  emptyIcon: ImageVector,
  onOpenPage: (String) -> Unit,
  list: @Composable (inPane: Boolean, selectedId: String?, onOpen: (String) -> Unit) -> Unit,
  detail: @Composable (id: String, onClose: () -> Unit) -> Unit,
  /**
   * Una cosa da aprire appena la pagina si vede — un deep link, una notifica. Su uno schermo largo
   * va nel pannello accanto con l'elenco visibile, su uno stretto diventa la pagina di dettaglio.
   */
  openRequest: String? = null,
  onOpenRequestConsumed: (String) -> Unit = {},
) {
  var paneId by rememberSaveable { mutableStateOf<String?>(null) }
  var twoPane by rememberSaveable { mutableStateOf(false) }
  // La richiesta si serve solo dopo la prima misura: prima non si sa ancora se c'e' un pannello.
  var measured by remember { mutableStateOf(false) }
  LaunchedEffect(openRequest, measured) {
    val request = openRequest ?: return@LaunchedEffect
    if (!measured) return@LaunchedEffect
    onOpenRequestConsumed(request)
    if (twoPane) paneId = request else onOpenPage(request)
  }
  LaunchedEffect(twoPane) {
    val open = paneId
    if (!twoPane && open != null) {
      paneId = null
      onOpenPage(open)
    }
  }
  // Indietro chiude prima la cosa aperta accanto, come sul telefono chiude la pagina di dettaglio:
  // il gesto significa "torna all'elenco" su tutti e due i formati, e solo dall'elenco esce.
  BackHandler(enabled = twoPane && paneId != null) { paneId = null }
  FluidListDetailScaffold(
    ambient = ambient,
    list = { isTwoPane ->
      if (twoPane != isTwoPane) twoPane = isTwoPane
      if (!measured) measured = true
      list(isTwoPane, paneId.takeIf { isTwoPane }) { id ->
        if (isTwoPane) paneId = id else onOpenPage(id)
      }
    },
    detail = {
      FluidDetailContent(item = paneId) { id ->
        if (id == null) {
          FluidDetailPlaceholder(title = emptyTitle, message = emptyMessage, icon = emptyIcon)
        } else {
          detail(id) { paneId = null }
        }
      }
    },
  )
}

/** Dove una pagina di dettaglio lascia detto all'elenco cosa riaprire accanto. */
internal const val PaneOpenRequestKey = "adaptive:pane-open"

/**
 * Una pagina di dettaglio che, se la finestra diventa abbastanza larga per i due pannelli, torna
 * all'elenco e chiede di essere riaperta accanto.
 *
 * Succede ruotando il tablet, o allargando la finestra divisa, con un compito aperto: senza, restava
 * una pagina sola stirata su tutta la larghezza, proprio dove la pagina elenco+dettaglio avrebbe
 * mostrato la stessa cosa con l'elenco accanto. E' l'altra meta' di cio' che [AdaptiveListDetail]
 * fa quando la finestra si stringe.
 */
@Composable
internal fun PaneAwareDetail(
  id: String,
  onWide: (String) -> Unit,
  content: @Composable () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    content()
    if (fluidListDetailLayout(maxWidth).twoPane) {
      LaunchedEffect(id) { onWide(id) }
    }
  }
}
