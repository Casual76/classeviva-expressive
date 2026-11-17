package dev.antigravity.classevivaexpressive

import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/** Keep the latest tap through login/restoration, then retire it as soon as it has been handled. */
internal class IncomingIntentInbox(savedState: Bundle?, launchIntent: Intent) {
  private val pending = MutableStateFlow(
    if (savedState == null) launchIntent.takeIf { it.data != null }
    else @Suppress("DEPRECATION") savedState.getParcelable<Intent>(PendingIntentKey),
  )
  val intents = pending.filterNotNull()

  fun offer(intent: Intent) {
    if (intent.data != null) pending.value = intent
  }

  fun consume(intent: Intent) {
    // A second tap can arrive while the first one is being processed.
    if (pending.value === intent) pending.value = null
  }

  fun save(outState: Bundle) {
    outState.putParcelable(PendingIntentKey, pending.value)
  }

  private companion object {
    const val PendingIntentKey = "navigation:pending-intent"
  }
}
