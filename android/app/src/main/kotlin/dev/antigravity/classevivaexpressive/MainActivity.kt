package dev.antigravity.classevivaexpressive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private val incomingIntents = MutableSharedFlow<android.content.Intent>(replay = 1, extraBufferCapacity = 1)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MainApp(incomingIntents = incomingIntents)
    }
    incomingIntents.tryEmit(intent)
  }

  /**
   * Esc da una tastiera fisica e' "indietro", come su ogni tablet con la tastiera attaccata: chiude
   * il pop-up aperto, torna alla pagina di prima. Android non lo fa da se', e senza questo il tasto
   * che tutti cercano per chiudere qualcosa non faceva niente. Passa dal dispatcher, quindi segue
   * la stessa strada del gesto — pop-up, pannelli, rotte — senza saperne niente.
   */
  override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
    if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && !event.hasModifiers(android.view.KeyEvent.META_CTRL_ON) &&
      !event.isCanceled
    ) {
      onBackPressedDispatcher.onBackPressed()
      return true
    }
    return super.onKeyUp(keyCode, event)
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    incomingIntents.tryEmit(intent)
  }
}
