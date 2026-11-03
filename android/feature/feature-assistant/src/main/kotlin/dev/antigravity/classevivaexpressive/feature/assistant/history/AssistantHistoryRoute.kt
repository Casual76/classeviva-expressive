package dev.antigravity.classevivaexpressive.feature.assistant.history

import dev.antigravity.classevivaexpressive.core.designsystem.theme.countLabel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaHeroCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmSheet
import java.time.ZoneId
import java.util.Date

/**
 * Le conversazioni salvate, dalla piu' recente, raggruppate per giorno. Un tocco riapre e
 * continua; la pressione lunga elimina. La piu' importante e' la prima riga: ricominciare.
 */
@Composable
fun AssistantHistoryRoute(
  onBack: () -> Unit,
  onOpenConversation: (Long) -> Unit,
  onNewConversation: () -> Unit,
  viewModel: AssistantHistoryViewModel = hiltViewModel(),
) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  val active by viewModel.activeConversationId.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  // Eliminare e' senza ritorno: si chiede, con un foglio, prima di farlo.
  var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
  var conversationToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
  val zone = ZoneId.systemDefault()
  val today = LocalDate.now(zone)
  val grouped = items.groupBy { Instant.ofEpochMilli(it.updatedAtMillis).atZone(zone).toLocalDate() }

  FluidScreen(
    title = "Assistente",
    subtitle = if (items.isEmpty()) "Le conversazioni restano qui, sul telefono." else "${countLabel(items.size, "conversazione", "conversazioni")}, sul telefono.",
    ambient = FeatureIdentity.Settings.ambient(),
    onBack = onBack,
    itemSpacing = 12.dp,
    actions = {
      if (items.isNotEmpty()) {
        FluidBarAction(icon = Icons.Rounded.DeleteSweep, contentDescription = "Elimina tutte", onClick = { confirmDeleteAll = true })
      }
    },
  ) {
    item {
      ClassevivaHeroCard(
        title = "Chiedi al registro",
        subtitle = "Voti, compiti, circolari, orario: a voce dal tasto sopra la pillola, o scrivendo qui.",
      )
    }
    item {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Nuova conversazione",
          subtitle = "Una domanda che parte da zero.",
          tone = FluidTone.Primary,
          leading = { Icon(Icons.Rounded.Add, contentDescription = null) },
          onClick = onNewConversation,
        )
      }
    }
    grouped.forEach { (day, conversations) ->
      item(key = "day-$day") {
        FluidSectionHeader(
          title = when (day) {
            today -> "Oggi"
            today.minusDays(1) -> "Ieri"
            else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(conversations.first().updatedAtMillis))
          },
        )
      }
      item(key = "group-$day") {
        FluidListGroup(glass = true) {
          conversations.forEachIndexed { index, conversation ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = conversation.title,
              subtitle = buildString {
                append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(conversation.updatedAtMillis)))
                conversation.lastProvider?.let { append(" · ").append(it.label) }
                if (conversation.id == active && busy) append(" · in corso")
              },
              tone = if (conversation.id == active) FluidTone.Primary else FluidTone.Neutral,
              onClick = { onOpenConversation(conversation.id) },
              contextActions = {
                listOf(FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) { conversationToDelete = conversation.id })
              },
            )
          }
        }
      }
    }
  }

  FluidConfirmSheet(
    visible = confirmDeleteAll,
    onDismissRequest = { confirmDeleteAll = false },
    title = "Eliminare tutte le conversazioni?",
    message = "Restano solo su questo telefono, e una volta eliminate non si possono recuperare.",
    confirmLabel = "Elimina tutte",
    destructive = true,
    onConfirm = {
      confirmDeleteAll = false
      viewModel.deleteAll()
    },
  )
  FluidConfirmSheet(
    visible = conversationToDelete != null,
    onDismissRequest = { conversationToDelete = null },
    title = "Eliminare questa conversazione?",
    message = "Una volta eliminata non si puo' recuperare.",
    confirmLabel = "Elimina",
    destructive = true,
    onConfirm = {
      conversationToDelete?.let(viewModel::delete)
      conversationToDelete = null
    },
  )
}
