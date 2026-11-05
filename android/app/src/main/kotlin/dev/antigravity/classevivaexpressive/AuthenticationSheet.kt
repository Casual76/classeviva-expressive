package dev.antigravity.classevivaexpressive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidTextField

/** Reauthentication overlays the app; dismissing it keeps the cached account and navigation. */
@Composable
internal fun AuthenticationSheet(
  visible: Boolean,
  initialUsername: String,
  busy: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onClearError: () -> Unit,
  onLogin: (String, String) -> Unit,
  onCieLogin: () -> Unit,
) {
  var username by remember(visible, initialUsername) { mutableStateOf(initialUsername) }
  var password by remember(visible) { mutableStateOf("") } // Secrets never enter saved state.
  fun submit() {
    if (!busy && username.isNotBlank() && password.isNotBlank()) onLogin(username.trim(), password)
  }
  FluidGlassModalPortal(
    visible = visible,
    onDismissRequest = { if (!busy) onDismiss() },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Aggiorna l’accesso",
  ) {
    FluidSheetScaffold(
      title = "Aggiorna l’accesso",
      message = "Rientra nello stesso profilo studente. I dati già scaricati restano disponibili anche offline.",
      actions = {
        FluidButton("Accedi", ::submit, enabled = !busy && username.isNotBlank() && password.isNotBlank(), loading = busy,
          fillWidth = true, modifier = Modifier.testTag("reauth_submit"))
        FluidButton("Entra con la CIE", { password = ""; onCieLogin() }, enabled = !busy, fillWidth = true, style = FluidButtonStyle.Tinted,
          modifier = Modifier.testTag("reauth_cie"))
        FluidButton("Continua offline", onDismiss, enabled = !busy, fillWidth = true, style = FluidButtonStyle.Plain)
      },
    ) {
      FluidTextField(username, { username = it; onClearError() }, label = "Username o codice studente", enabled = !busy,
        modifier = Modifier.fillMaxWidth().testTag("reauth_username"), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
      FluidTextField(password, { password = it; onClearError() }, label = "Password", secret = true, enabled = !busy,
        modifier = Modifier.fillMaxWidth().testTag("reauth_password"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }))
      if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
  }
}
