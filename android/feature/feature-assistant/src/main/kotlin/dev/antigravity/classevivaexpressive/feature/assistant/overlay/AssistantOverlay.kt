package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantActions
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantOverlay
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantOverlayState
import dev.antigravity.fluidengine.aiui.overlay.rememberFluidTtsSpeaker
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState

/** Cosa l'overlay chiede all'app: aprire una pagina, con un dettaglio se c'e'. */
fun interface AssistantNavigator {
  fun open(page: AppPage, itemId: String?)
}

/**
 * L'overlay dell'assistente sopra la pagina: aureola in cima, barra di scrittura, card della risposta
 * che cresce dal tasto sopra la pillola. E' `FluidAssistantOverlay` dell'engine (che nacque da questo
 * file) con quello che resta dell'app: lo stato e i verbi del ViewModel, le frasi dei gruppi del
 * registro, e le pagine da aprire quando un tool o un chip lo chiede.
 */
@Composable
fun BoxScope.AssistantOverlay(
  overlay: FluidAssistantOverlayState,
  viewModel: AssistantOverlayViewModel,
  backdrop: GlassBackdropState,
  navigator: AssistantNavigator,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val mic by viewModel.micLevel.collectAsStateWithLifecycle()
  val pending by viewModel.pending.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val navigation by viewModel.navigation.collectAsStateWithLifecycle()
  val speaker = rememberFluidTtsSpeaker()

  // Le pagine da aprire decise dai tool (`apri`): la card si fa da parte e la pagina cambia.
  LaunchedEffect(navigation) {
    val request = navigation ?: return@LaunchedEffect
    viewModel.consumeNavigation()
    overlay.collapseForNavigation()
    navigator.open(request.page, request.itemId)
  }

  val actions = remember(viewModel, navigator) {
    FluidAssistantActions(
      onAskText = viewModel::askText,
      onAskVoice = viewModel::askVoice,
      onStopListening = viewModel::stopListening,
      onCancel = viewModel::cancel,
      onDismiss = viewModel::dismiss,
      onNewConversation = viewModel::newConversation,
      onConfirm = viewModel::resolve,
      onChip = { chip -> AssistantTexts.chipTarget(chip)?.let { (page, id) -> navigator.open(page, id) } },
    )
  }

  FluidAssistantOverlay(
    state = state,
    micLevel = mic,
    pending = pending,
    overlay = overlay,
    backdrop = backdrop,
    actions = actions,
    strings = AssistantTexts.strings,
    chipLabel = AssistantTexts::chipLabel,
    speaker = speaker,
    speakReplies = settings.speakReplies,
    lastMode = viewModel.lastMode,
    onForegroundChanged = viewModel::setForeground,
    // Un chip che non porta in nessuna pagina lascia la card dov'e'.
    collapseOnChip = { chip -> AssistantTexts.chipTarget(chip) != null },
  )
}
