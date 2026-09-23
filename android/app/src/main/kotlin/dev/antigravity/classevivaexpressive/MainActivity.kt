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
  private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MainApp(incomingIntents = incomingIntents, refreshRequests = refreshRequests)
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

  /**
   * Ctrl+R (e F5) aggiornano la pagina che si sta guardando, il gesto di tirarla giu' per chi ha la
   * tastiera attaccata. Sul tasto premuto e non su quello rilasciato, come nei browser: la risposta
   * arriva quando il dito scende. Tenuto premuto non si ripete.
   */
  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
    val refresh = keyCode == android.view.KeyEvent.KEYCODE_F5 ||
      (keyCode == android.view.KeyEvent.KEYCODE_R && event.isCtrlPressed)
    if (refresh) {
      if (event.repeatCount == 0) refreshRequests.tryEmit(Unit)
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    incomingIntents.tryEmit(intent)
  }
}
