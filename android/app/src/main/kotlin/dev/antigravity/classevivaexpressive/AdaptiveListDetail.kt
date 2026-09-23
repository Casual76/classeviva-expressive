package dev.antigravity.classevivaexpressive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidDetailContent
import dev.antigravity.fluidengine.ui.fluid.FluidDetailPlaceholder
import dev.antigravity.fluidengine.ui.fluid.FluidListDetailScaffold

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
) {
  var paneId by rememberSaveable { mutableStateOf<String?>(null) }
  var twoPane by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(twoPane) {
    val open = paneId
    if (!twoPane && open != null) {
      paneId = null
      onOpenPage(open)
    }
  }
  FluidListDetailScaffold(
    ambient = ambient,
    list = { isTwoPane ->
      if (twoPane != isTwoPane) twoPane = isTwoPane
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
