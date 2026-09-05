package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassSurface

/**
 * La barra di scrittura (pressione lunga sul tasto): una capsula di vetro con il campo, il
 * microfono per passare alla voce, l'invio, e la freccia circolare per ricominciare la
 * conversazione. Il focus arriva da solo: la tastiera si apre senza un secondo tocco.
 */
@Composable
fun AssistantTextBar(
  backdrop: GlassBackdropState,
  busy: Boolean,
  micAvailable: Boolean,
  onSend: (String) -> Unit,
  onVoice: () -> Unit,
  onNewConversation: () -> Unit,
  modifier: Modifier = Modifier,
  autoFocus: Boolean = true,
) {
  var text by rememberSaveable { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
  fun submit() {
    val query = text.trim()
    if (query.isEmpty() || busy) return
    onSend(query)
    text = ""
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .glassSurface(state = backdrop, tint = GlassDefaults.modalTint(), shape = FluidCapsuleShape, role = GlassRole.Modal)
      .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BarIcon(icon = Icons.Rounded.Refresh, description = "Nuova conversazione", onClick = onNewConversation)
    FluidTextField(
      value = text,
      onValueChange = { text = it },
      placeholder = "Chiedi al registro…",
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { submit() }),
      modifier = Modifier
        .weight(1f)
        .focusRequester(focus),
    )
    Spacer(Modifier.width(4.dp))
    if (text.isBlank()) {
      if (micAvailable) BarIcon(icon = Icons.Rounded.Mic, description = "Chiedi a voce", onClick = onVoice)
    } else {
      BarIcon(icon = Icons.Rounded.ArrowUpward, description = "Invia", onClick = { submit() }, primary = true)
    }
  }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit, primary: Boolean = false) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .fluidPressable(onClick = onClick, role = Role.Button),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      modifier = Modifier.size(22.dp),
    )
  }
}
