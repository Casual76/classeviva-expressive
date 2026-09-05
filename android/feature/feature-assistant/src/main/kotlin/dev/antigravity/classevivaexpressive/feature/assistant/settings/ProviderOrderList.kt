package dev.antigravity.classevivaexpressive.feature.assistant.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * L'ordine dei provider, con le frecce su e giu'. Solo i provider con una chiave verificata
 * compaiono; gli altri restano in coda all'ordine salvato, e ci tornano quando avranno una chiave.
 */
@Composable
fun ProviderOrderList(
  order: List<ProviderId>,
  available: Set<ProviderId>,
  onReorder: (List<ProviderId>) -> Unit,
) {
  val visible = order.filter { it in available }

  fun move(from: Int, to: Int) {
    if (from == to || to !in visible.indices) return
    val mutable = visible.toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    onReorder(mutable + order.filter { it !in available })
  }

  FluidListGroup {
    visible.forEachIndexed { index, provider ->
      if (index > 0) FluidListDivider()
      FluidListRow(
        title = provider.label,
        subtitle = if (index == 0) "Primo a rispondere" else "Riserva, se il precedente e' al limite o non risponde",
        badge = {
          Row {
            ArrowButton(Icons.Rounded.KeyboardArrowUp, "Sposta su", enabled = index > 0) { move(index, index - 1) }
            ArrowButton(Icons.Rounded.KeyboardArrowDown, "Sposta giu'", enabled = index < visible.lastIndex) { move(index, index + 1) }
          }
        },
      )
    }
  }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(36.dp)
      .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.8f else 0.25f),
      modifier = Modifier.size(20.dp),
    )
  }
}
