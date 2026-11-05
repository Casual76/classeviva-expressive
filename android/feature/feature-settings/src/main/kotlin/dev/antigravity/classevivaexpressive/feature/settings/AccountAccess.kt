package dev.antigravity.classevivaexpressive.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.PortalAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
internal fun AccountAccessActions(
  method: AuthMethod,
  authentication: AuthenticationState,
  busy: Boolean,
  onCredentials: () -> Unit,
  onCie: () -> Unit,
) {
  val restExpired = authentication.rest == RestAuthenticationState.REAUTHENTICATION_REQUIRED
  val portalExpired = authentication.portal == PortalAuthenticationState.REAUTHENTICATION_REQUIRED
  val storageUnavailable = authentication.rest == RestAuthenticationState.STORAGE_UNAVAILABLE
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FluidListGroup(glass = true) {
      FluidListRow(
        title = if (method == AuthMethod.CIE) "Accesso con la CIE" else "Accesso con credenziali",
        subtitle = when {
          storageUnavailable -> "Impossibile leggere o salvare l’accesso sul dispositivo. I dati già scaricati restano disponibili offline."
          restExpired -> "Accedi di nuovo per riprendere gli aggiornamenti. I dati salvati restano disponibili offline."
          portalExpired -> "Il registro è attivo. Per le funzioni del portale serve un nuovo accesso con la CIE."
          else -> "Puoi aggiornare l’accesso senza disconnettere il profilo studente."
        },
        tone = if (restExpired || portalExpired || storageUnavailable) FluidTone.Warning else FluidTone.Neutral,
        disclosure = false,
      )
    }
    FluidButton("Accedi con le credenziali", onCredentials, style = FluidButtonStyle.Tinted, fillWidth = true,
      enabled = !busy, modifier = Modifier.testTag("account_credentials"))
    FluidButton("Entra con la CIE", onCie, style = FluidButtonStyle.Tinted, fillWidth = true,
      enabled = !busy, modifier = Modifier.testTag("account_cie"))
    if (busy) Text("Accesso in corso…", style = MaterialTheme.typography.bodySmall)
  }
}

/** Public so the actual contribution row can also be rendered in app instrumentation tests. */
@Composable
fun CieContributionCredit() {
  FluidListGroup(glass = true) {
    FluidListRow(
      title = "Leone",
      subtitle = "Metodo [omissis] nella fossa dei leoni: è entrato fra le API di Spaggiari ed è uscito con il token.",
      eyebrow = "Accesso CIE",
      disclosure = false,
    )
  }
}
