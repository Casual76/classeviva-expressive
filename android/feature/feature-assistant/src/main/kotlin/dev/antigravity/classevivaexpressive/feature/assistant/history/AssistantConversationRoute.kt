package dev.antigravity.classevivaexpressive.feature.assistant.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantMessage
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantRun
import dev.antigravity.classevivaexpressive.core.assistant.db.MessageRole
import dev.antigravity.classevivaexpressive.core.assistant.db.MessageStatus
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.classevivaexpressive.feature.assistant.overlay.AssistantTexts
import dev.antigravity.classevivaexpressive.feature.assistant.overlay.MarkdownBody
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.theme.FluidCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Una conversazione per intero: le domande, le risposte con la loro telemetria, e in fondo la
 * barra per continuarla. Mentre una risposta arriva, la si vede formarsi qui come nella card:
 * lo stato vivo viene dal runtime, il testo che resta da Room.
 */
@Composable
fun AssistantConversationRoute(
  onBack: () -> Unit,
  onOpenPage: (AppPage, String?) -> Unit,
  onOverlaySuppressed: (Boolean) -> Unit,
  viewModel: AssistantConversationViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val navigation by viewModel.navigation.collectAsStateWithLifecycle()

  // Qui la risposta si legge nella pagina: la card dell'overlay sopra sarebbe la stessa cosa due volte.
  DisposableEffect(Unit) {
    onOverlaySuppressed(true)
    viewModel.makeActive()
    onDispose { onOverlaySuppressed(false) }
  }
  LaunchedEffect(navigation) {
    val request = navigation ?: return@LaunchedEffect
    viewModel.consumeNavigation()
    onOpenPage(request.page, request.itemId)
  }

  val conversation = state.conversation
  FluidScreen(
    title = conversation?.title?.take(48) ?: "Nuova conversazione",
    subtitle = conversation?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.createdAtMillis)) } ?: "Scrivi qui sotto: la conversazione nasce con la prima domanda.",
    ambient = FeatureIdentity.Settings.ambient(),
    onBack = onBack,
    itemSpacing = 12.dp,
    extraBottomPadding = 96.dp,
    overlay = { backdrop ->
      Box(
        Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        ConversationInputBar(backdrop = backdrop, busy = state.live?.isBusy == true, onSend = viewModel::send, onStop = viewModel::cancel)
      }
    },
  ) {
    if (state.messages.isEmpty() && state.live == null) {
      item {
        FluidCard(glass = true) {
          Text("Prova con: \"che voti ho preso questa settimana?\", \"cosa dice la circolare sulla gita?\", \"quando ho la prossima verifica?\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    state.messages.forEach { message ->
      item(key = message.id) {
        when (message.role) {
          MessageRole.USER -> UserBubble(message)
          MessageRole.ASSISTANT -> AssistantBubble(
            message = message,
            run = state.runs[message.id],
            live = if (message.status == MessageStatus.PENDING || message.status == MessageStatus.STREAMING) state.live else null,
            pending = state.pending,
            onResolve = viewModel::resolve,
            onChip = { page, id -> onOpenPage(page, id) },
          )
        }
      }
    }
    // Una conversazione nuova: la domanda in corso non e' ancora su disco.
    if (state.messages.isEmpty() && state.live != null) {
      item(key = "live") { LiveBubble(state.live!!, state.pending, viewModel::resolve) }
    }
  }
}

@Composable
private fun UserBubble(message: AssistantMessage) {
  FluidCard(glass = true) {
    Text("Tu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(message.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun AssistantBubble(
  message: AssistantMessage,
  run: AssistantRun?,
  live: AssistantState?,
  pending: PendingConfirmation?,
  onResolve: (Long, Boolean) -> Unit,
  onChip: (AppPage, String?) -> Unit,
) {
  FluidCard(highlighted = message.status == MessageStatus.FAILED) {
    Text("Assistente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    val liveText = (live as? AssistantState.Answering)?.partial
    val text = liveText?.takeIf { it.isNotBlank() } ?: message.text
    if (live != null && live.isBusy) {
      AssistantTexts.statusLine(live)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
      if (live is AssistantState.AwaitingConfirmation && pending != null) {
        Spacer(Modifier.height(8.dp))
        Text(pending.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        pending.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidButton(text = "Conferma", onClick = { onResolve(pending.id, true) }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
          FluidButton(text = "Annulla", onClick = { onResolve(pending.id, false) }, style = FluidButtonStyle.Plain, size = FluidButtonSize.Small)
        }
      }
      if (text.isNotBlank()) Spacer(Modifier.height(8.dp))
    }
    when {
      text.isNotBlank() -> MarkdownBody(text)
      message.status == MessageStatus.FAILED -> Text(message.failureKind?.let { AssistantTexts.failure(it) } ?: "Qualcosa e' andato storto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
      message.status == MessageStatus.CANCELLED -> Text("Fermata prima della risposta.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      live == null -> Text("Interrotta: l'app si e' chiusa prima della risposta.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (message.status == MessageStatus.FAILED && text.isNotBlank()) {
      Spacer(Modifier.height(6.dp))
      Text(message.failureKind?.let { AssistantTexts.failure(it) } ?: "Interrotta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (message.chips.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message.chips.forEach { chip ->
          FluidChip(label = AssistantTexts.chipLabel(chip), selected = false, onClick = { AssistantTexts.chipTarget(chip)?.let { (page, id) -> onChip(page, id) } })
        }
      }
    }
    run?.let {
      Spacer(Modifier.height(8.dp))
      Text(telemetry(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun LiveBubble(live: AssistantState, pending: PendingConfirmation?, onResolve: (Long, Boolean) -> Unit) {
  FluidCard {
    Text("Assistente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    AssistantTexts.statusLine(live)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
    (live as? AssistantState.Answering)?.partial?.let {
      Spacer(Modifier.height(8.dp))
      MarkdownBody(it)
    }
    if (live is AssistantState.AwaitingConfirmation && pending != null) {
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(text = "Conferma", onClick = { onResolve(pending.id, true) }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
        FluidButton(text = "Annulla", onClick = { onResolve(pending.id, false) }, style = FluidButtonStyle.Plain, size = FluidButtonSize.Small)
      }
    }
  }
}

/** La telemetria di uno scambio in una riga: passi, token, modelli, costo, tempo. */
fun telemetry(run: AssistantRun): String = buildList {
  add("${run.steps} ${if (run.steps == 1) "passo" else "passi"}")
  run.totalTokens?.let { add(if (it >= 1000) String.format(Locale.getDefault(), "%.1fk token", it / 1000.0) else "$it token") }
  val models = listOfNotNull(run.chatModel, run.deepModel?.takeIf { it != run.chatModel }).map { it.substringAfterLast('/') }
  val provider = run.provider?.label
  if (provider != null || models.isNotEmpty()) add(listOfNotNull(provider, models.joinToString(", ").takeIf { it.isNotEmpty() }).joinToString(" "))
  run.costUsd?.takeIf { it > 0.0 }?.let { add(String.format(Locale.getDefault(), "%.4f $", it)) }
  run.durationMillis?.let { add("${it / 1000} s") }
  if (run.tools.isNotEmpty()) add("${run.tools.size} strumenti")
  if (run.outcome != "ok") add(run.outcome)
}.joinToString(" · ")

@Composable
private fun ConversationInputBar(backdrop: GlassBackdropState, busy: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
  var text by rememberSaveable { mutableStateOf("") }
  fun submit() {
    val query = text.trim()
    if (query.isEmpty() || busy) return
    onSend(query)
    text = ""
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .glassSurface(state = backdrop, tint = GlassDefaults.modalTint(), shape = FluidCapsuleShape, role = GlassRole.Modal)
      .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidTextField(
      value = text,
      onValueChange = { text = it },
      placeholder = if (busy) "Sto rispondendo…" else "Continua la conversazione…",
      singleLine = true,
      enabled = !busy,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { submit() }),
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(4.dp))
    Box(
      modifier = Modifier
        .size(40.dp)
        .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
        .fluidPressable(onClick = { if (busy) onStop() else submit() }, role = Role.Button),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (busy) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
        contentDescription = if (busy) "Ferma" else "Invia",
        tint = if (busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
