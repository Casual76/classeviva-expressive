package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cosa l'overlay chiede all'app: aprire una pagina, con un dettaglio se c'e'. */
fun interface AssistantNavigator {
  fun open(page: AppPage, itemId: String?)
}

/**
 * L'overlay dell'assistente sopra la pagina: aureola in cima, barra di scrittura (in modalita'
 * testo), card della risposta che **cresce dal tasto** sopra la pillola. Non e' modale: nessuno
 * scrim, i tocchi fuori passano alla pagina, e la card si riduce a una pillola quando l'utente
 * torna a usare l'app; trascinarla in alto la chiude (e interrompe, se sta lavorando), il tasto
 * stop interrompe e basta. La domanda vive nel service: chiudere l'overlay non la ferma.
 */
@Composable
fun BoxScope.AssistantOverlay(
  overlay: AssistantOverlayState,
  viewModel: AssistantOverlayViewModel,
  backdrop: GlassBackdropState,
  navigator: AssistantNavigator,
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
  val mic by viewModel.micLevel.collectAsState()
  val pending by viewModel.pending.collectAsState()
  val settings by viewModel.settings.collectAsState()
  val navigation by viewModel.navigation.collectAsState()
  val scope = rememberCoroutineScope()
  val speaker = remember { TtsSpeaker(context) }
  DisposableEffect(speaker) { onDispose { speaker.release() } }
  val haptics = rememberFluidHaptics()

  // L'app davanti o no: decide se la risposta va anche in notifica. Si legge dal ciclo di vita,
  // e il permesso del microfono si rilegge allo stesso momento (concesso dalle impostazioni di
  // sistema, prima restava "negato" finche' la schermata non veniva ricreata).
  var permissionEpoch by remember { mutableIntStateOf(0) }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> viewModel.setForeground(true)
        Lifecycle.Event.ON_STOP -> viewModel.setForeground(false)
        Lifecycle.Event.ON_RESUME -> permissionEpoch++
        else -> Unit
      }
    }
    viewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      viewModel.setForeground(false)
    }
  }
  val micGranted = remember(permissionEpoch) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    permissionEpoch++
    if (granted) overlay.openVoice()
  }

  // L'assistente e' la parte dell'app che si usa senza guardarla: l'ascolto che parte, il parlato
  // riconosciuto, l'ascolto che finisce e la risposta pronta si sentono sotto il dito.
  var listening by remember { mutableStateOf(false) }
  var spoke by remember { mutableStateOf(false) }
  var answered by remember { mutableStateOf(false) }
  var waitSecond by remember { mutableIntStateOf(-1) }
  LaunchedEffect(mic.speaking) {
    if (listening && mic.speaking && !spoke) {
      spoke = true
      haptics.play(FluidHapticEvent.SpeechDetected)
    }
  }
  LaunchedEffect(state) {
    val current = state
    if (current is AssistantState.Listening) {
      if (!listening) {
        listening = true
        spoke = false
        haptics.play(FluidHapticEvent.ListenStart)
      }
    } else if (listening) {
      listening = false
      haptics.play(FluidHapticEvent.ListenEnd)
    }
    when (current) {
      is AssistantState.Done -> if (!answered) {
        answered = true
        haptics.play(FluidHapticEvent.ReplyReady)
      }
      is AssistantState.Failed -> haptics.play(FluidHapticEvent.Error)
      is AssistantState.Cancelled -> haptics.play(FluidHapticEvent.Stop)
      is AssistantState.SwitchingProvider -> haptics.play(FluidHapticEvent.ProviderSwitched)
      is AssistantState.WaitingRateLimit -> if (current.secondsLeft != waitSecond) {
        waitSecond = current.secondsLeft
        haptics.play(FluidHapticEvent.WaitTick)
      }
      else -> Unit
    }
    if (current !is AssistantState.Done) answered = false
  }

  // Le pagine da aprire decise dai tool (`apri`): la card si fa da parte e la pagina cambia.
  LaunchedEffect(navigation) {
    val request = navigation ?: return@LaunchedEffect
    viewModel.consumeNavigation()
    overlay.collapsed = true
    navigator.open(request.page, request.itemId)
  }

  // Lettura ad alta voce: solo per le domande fatte a voce, solo se l'utente l'ha accesa.
  val speakerReset = state is AssistantState.Listening || state is AssistantState.Classifying || state == AssistantState.Transcribing
  LaunchedEffect(speakerReset) {
    if (speakerReset) speaker.restart()
  }
  LaunchedEffect(state, settings.speakReplies) {
    val speak = settings.speakReplies && viewModel.lastMode == AskMode.VOICE
    when (val s = state) {
      is AssistantState.Answering -> if (speak) speaker.speakNewSentences(s.partial, final = false)
      is AssistantState.Done -> if (speak) speaker.speakNewSentences(s.answer, final = true)
      is AssistantState.Cancelled, is AssistantState.Failed, AssistantState.Idle -> speaker.stop()
      else -> Unit
    }
  }

  // A risposta finita la barra torna: la conversazione continua, e chi ha scritto una volta
  // scrivera' ancora. Non durante il lavoro, che e' il momento in cui doveva sparire.
  LaunchedEffect(state is AssistantState.Done) {
    if (state is AssistantState.Done && !overlay.collapsed) overlay.resumeComposing()
  }

  // Lo stato che nasce altrove (una domanda dalla notifica, una risposta arrivata ad app chiusa)
  // apre l'overlay da solo.
  LaunchedEffect(overlay.suppressed) {
    if (overlay.suppressed) overlay.hide()
  }
  LaunchedEffect(state, overlay.suppressed) {
    if (overlay.suppressed) return@LaunchedEffect
    if (state is AssistantState.Listening && overlay.mode == OverlayMode.HIDDEN) overlay.openVoice()
    if (state.isBusy && overlay.mode == OverlayMode.HIDDEN) overlay.mode = OverlayMode.TEXT
    if (AssistantTexts.wantsExpanded(state) && overlay.mode != OverlayMode.HIDDEN) overlay.collapsed = false
    if (state == AssistantState.HeardNothing) {
      delay(1_600)
      if (viewModel.state.value == AssistantState.HeardNothing) {
        viewModel.dismiss()
        if (overlay.mode == OverlayMode.VOICE) overlay.hide()
      }
    }
  }

  val visible = overlay.mode != OverlayMode.HIDDEN && !overlay.suppressed
  val mood = when {
    !visible -> HaloMood.HIDDEN
    state is AssistantState.Listening -> HaloMood.LISTENING
    state is AssistantState.Answering -> HaloMood.WRITING
    state is AssistantState.Failed -> HaloMood.ERROR
    state is AssistantState.Done -> HaloMood.DONE
    state.isBusy -> HaloMood.WORKING
    else -> HaloMood.DONE
  }
  val level = if (state is AssistantState.Listening) mic.level else 0f

  AssistantHalo(
    mood = if (overlay.collapsed) HaloMood.HIDDEN else mood,
    level = level,
    accent = MaterialTheme.colorScheme.primary,
    secondary = MaterialTheme.colorScheme.secondary,
    tertiary = MaterialTheme.colorScheme.tertiary,
    modifier = Modifier.align(Alignment.TopCenter),
    height = if (overlay.mode == OverlayMode.VOICE) 150.dp else 96.dp,
  )

  // La trasformazione, invece di una comparsa: il pannello **cresce dal tasto** sopra la pillola e
  // ci rientra chiudendosi. E' la stessa idea del tasto che diventa il proprio pop-up, che nel resto
  // dell'app fa il modale di vetro; qui non passa dal modale dell'engine perche' l'assistente per
  // scelta non e' modale (la pagina resta viva e toccabile sotto).
  val morph = remember { Animatable(0f) }
  LaunchedEffect(visible) {
    morph.animateTo(
      targetValue = if (visible) 1f else 0f,
      animationSpec = spring(
        dampingRatio = if (visible) FluidMotion.DampingFluid else FluidMotion.DampingChrome,
        stiffness = if (visible) FluidMotion.ResponseStandard else FluidMotion.ResponseSnappy,
      ),
    )
  }
  var panelBounds by remember { mutableStateOf<Rect?>(null) }

  if (visible || morph.value > 0.001f) {
    val drag = remember { Animatable(0f) }
    val dismissDragPx = with(LocalDensity.current) { DismissDrag.toPx() }
    val origin = overlay.originBounds
    val panel = panelBounds
    Column(
      Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .imePadding()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .fillMaxWidth()
        .onGloballyPositioned { panelBounds = it.boundsInRoot() }
        .graphicsLayer {
          translationY = drag.value
          val progress = morph.value
          if (origin == null || panel == null || panel.width <= 0f || panel.height <= 0f) {
            alpha = progress
            translationY += (1f - progress) * -(panel?.height ?: 0f) * 0.25f
            return@graphicsLayer
          }
          val scaleTo = (origin.width / panel.width).coerceIn(0.05f, 1f)
          val scaleToY = (origin.height / panel.height).coerceIn(0.05f, 1f)
          scaleX = scaleTo + (1f - scaleTo) * progress
          scaleY = scaleToY + (1f - scaleToY) * progress
          translationX = (origin.center.x - panel.center.x) * (1f - progress)
          translationY += (origin.center.y - panel.center.y) * (1f - progress)
          alpha = ((progress - 0.25f) / 0.55f).coerceIn(0f, 1f)
        }
        .pointerInput(overlay.mode, state.isBusy) {
          detectVerticalDragGestures(
            onVerticalDrag = { change, delta ->
              change.consume()
              scope.launch { drag.snapTo((drag.value + delta).coerceAtMost(0f)) }
            },
            onDragEnd = {
              if (drag.value < -dismissDragPx) {
                if (state.isBusy) viewModel.cancel()
                viewModel.dismiss()
                overlay.hide()
              }
              scope.launch { drag.animateTo(0f, spring(FluidMotion.DampingStandard, FluidMotion.ResponseSnappy)) }
            },
            onDragCancel = { scope.launch { drag.animateTo(0f) } },
          )
        },
    ) {
      if (overlay.mode == OverlayMode.TEXT && overlay.composing && !overlay.collapsed) {
        AssistantTextBar(
          backdrop = backdrop,
          autoFocus = overlay.autoFocus,
          busy = state.isBusy,
          micAvailable = micGranted,
          onSend = {
            viewModel.askText(it)
            overlay.sent()
          },
          onVoice = { overlay.openVoice() },
          onNewConversation = { viewModel.newConversation() },
        )
        Spacer(Modifier.padding(4.dp))
      }
      if (overlay.mode == OverlayMode.VOICE && !overlay.collapsed && state is AssistantState.Listening) {
        ListeningHint(
          backdrop = backdrop,
          elapsedMillis = (state as AssistantState.Listening).elapsedMillis,
          onStop = { viewModel.stopListening() },
        )
      }
      val showCard = state != AssistantState.Idle && !(overlay.mode == OverlayMode.VOICE && state is AssistantState.Listening)
      if (showCard) {
        if (overlay.collapsed) {
          CollapsedPill(state = state, backdrop = backdrop, onExpand = { overlay.collapsed = false })
        } else {
          AssistantCard(
            state = state,
            pending = pending,
            backdrop = backdrop,
            onStop = { viewModel.cancel() },
            onChip = { chip ->
              AssistantTexts.chipTarget(chip)?.let { (page, id) ->
                overlay.collapsed = true
                navigator.open(page, id)
              }
            },
            onConfirm = { id, yes ->
              haptics.play(if (yes) FluidHapticEvent.ActionConfirmed else FluidHapticEvent.Reject)
              viewModel.resolve(id, yes)
            },
            onTapBody = { speaker.stop() },
          )
        }
      }
    }
  }

  // Ogni tocco del tasto lascia un gettone, e questo effetto e' l'unico che lo consuma: cosi' una
  // ricomposizione non fa ripartire un ascolto che nessuno ha chiesto.
  LaunchedEffect(overlay.voiceRequest) {
    if (overlay.mode != OverlayMode.VOICE) return@LaunchedEffect
    if (viewModel.isBusy) return@LaunchedEffect
    if (!overlay.consumeVoiceRequest()) return@LaunchedEffect
    if (micGranted) {
      viewModel.askVoice()
    } else {
      overlay.openText()
      micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }
}

/** Il tasto per fermare l'ascolto: una capsula di vetro sotto l'aureola, alta abbastanza da essere un bersaglio vero. */
@Composable
private fun ListeningHint(backdrop: GlassBackdropState, elapsedMillis: Long, onStop: () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
    Row(
      Modifier
        .heightIn(min = 48.dp)
        .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
        .fluidPressable(onClick = onStop, pressedScale = 1f, role = Role.Button)
        .padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, FluidCapsuleShape))
      Spacer(Modifier.width(10.dp))
      Text("Tocca per finire", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
      if (elapsedMillis >= 1_000) {
        Spacer(Modifier.width(8.dp))
        Text("${elapsedMillis / 1000}s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
      }
    }
  }
}

/** La card ridotta: un puntino e una riga, perche' l'utente sta guardando altro. */
@Composable
private fun CollapsedPill(state: AssistantState, backdrop: GlassBackdropState, onExpand: () -> Unit) {
  Row(
    Modifier
      .glassSurface(state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape, role = GlassRole.Floating)
      .fluidPressable(onClick = onExpand, pressedScale = 1f, role = Role.Button)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(8.dp)
        .background(if (state is AssistantState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, FluidCapsuleShape),
    )
    Spacer(Modifier.width(8.dp))
    Text(AssistantTexts.collapsed(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** Quanto va trascinata in alto la card per chiuderla: una misura, non un numero di pixel. */
private val DismissDrag = 56.dp
