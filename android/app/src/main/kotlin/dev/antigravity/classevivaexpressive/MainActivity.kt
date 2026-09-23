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
  private val shortcuts = MutableSharedFlow<KeyboardShortcut>(extraBufferCapacity = 1)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MainApp(incomingIntents = incomingIntents, shortcuts = shortcuts)
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
   * Le scorciatoie per chi ha la tastiera attaccata al tablet. Ctrl+R (e F5) aggiornano la pagina
   * che si sta guardando, il gesto di tirarla giu'; Ctrl+1…5 aprono le sezioni della barra, come
   * le schede di un browser. Sul tasto premuto e non su quello rilasciato, come nei browser: la
   * risposta arriva quando il dito scende. Tenuti premuti non si ripetono.
   */
  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
    val shortcut = when {
      keyCode == android.view.KeyEvent.KEYCODE_F5 -> KeyboardShortcut.Refresh
      !event.isCtrlPressed -> null
      keyCode == android.view.KeyEvent.KEYCODE_R -> KeyboardShortcut.Refresh
      keyCode in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_5 ->
        KeyboardShortcut.Section(keyCode - android.view.KeyEvent.KEYCODE_1)
      else -> null
    } ?: return super.onKeyDown(keyCode, event)
    if (event.repeatCount == 0) shortcuts.tryEmit(shortcut)
    return true
  }

  /**
   * Le stesse scorciatoie, nell'elenco che il sistema mostra tenendo premuto Meta (o Meta+/): senza,
   * esistevano solo per chi le indovinava.
   */
  override fun onProvideKeyboardShortcuts(
    data: MutableList<android.view.KeyboardShortcutGroup>,
    menu: android.view.Menu?,
    deviceId: Int,
  ) {
    super.onProvideKeyboardShortcuts(data, menu, deviceId)
    val ctrl = android.view.KeyEvent.META_CTRL_ON
    val sections = listOf("Home", "Voti", "Agenda", "Bacheca", "Altro")
    data += android.view.KeyboardShortcutGroup(
      "ClasseViva Expressive",
      buildList {
        add(android.view.KeyboardShortcutInfo("Aggiorna la pagina", android.view.KeyEvent.KEYCODE_R, ctrl))
        sections.forEachIndexed { index, label ->
          add(android.view.KeyboardShortcutInfo(label, android.view.KeyEvent.KEYCODE_1 + index, ctrl))
        }
        add(android.view.KeyboardShortcutInfo("Indietro", android.view.KeyEvent.KEYCODE_ESCAPE, 0))
      },
    )
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    incomingIntents.tryEmit(intent)
  }
}
