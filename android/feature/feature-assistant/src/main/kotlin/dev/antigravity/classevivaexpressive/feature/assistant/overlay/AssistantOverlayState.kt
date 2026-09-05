package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Come l'assistente e' in scena: nascosto, in ascolto (aureola grande), in modalita' testo
 * (barra + card), con la card ridotta a pillola perche' l'utente ha ripreso a usare la pagina.
 */
enum class OverlayMode { HIDDEN, VOICE, TEXT }

/**
 * Lo stato dell'overlay tenuto fuori dalla composizione della pagina, cosi' sopravvive a ogni
 * ricomposizione e la pagina lo tocca solo con due callback (tocco e pressione lunga sul tasto).
 */
class AssistantOverlayState {
  var mode by mutableStateOf(OverlayMode.HIDDEN)
  var collapsed by mutableStateOf(false)

  /**
   * Vero mentre la barra di scrittura e' aperta. Dopo l'invio si chiude e resta il pensiero
   * dell'assistente: chi ha appena scritto la domanda non ha piu' niente da scrivere, e la
   * tastiera aperta sopra la risposta era solo un ingombro.
   */
  var composing by mutableStateOf(false)
    private set

  /**
   * Cresce a ogni tocco del tasto ed e' un **gettone**: chi lo consuma fa partire l'ascolto, e
   * nessun altro lo rifa'. Un contatore letto da un effetto bastava a far partire un secondo
   * `AudioRecord` sullo stesso microfono a ogni ricomposizione.
   */
  var voiceRequest by mutableIntStateOf(0)
    private set

  private var consumedRequest by mutableIntStateOf(0)

  /** Vero se aprendo la barra si vuole anche la tastiera: si' quando l'ha chiesta l'utente. */
  var autoFocus by mutableStateOf(true)
    private set

  /**
   * Il rettangolo del tasto dell'assistente sopra la pillola. E' l'origine della trasformazione:
   * la card non compare, **cresce da li'** — e tornando indietro ci rientra.
   */
  var originBounds by mutableStateOf<Rect?>(null)

  /** Vero mentre una schermata mostra la conversazione per intero: l'overlay sopra sarebbe un doppione. */
  var suppressed by mutableStateOf(false)

  fun openVoice() {
    mode = OverlayMode.VOICE
    collapsed = false
    composing = false
    voiceRequest++
  }

  /** Consuma il gettone: vero solo per il primo che chiama, e una volta sola per tocco. */
  fun consumeVoiceRequest(): Boolean {
    if (voiceRequest == 0 || voiceRequest == consumedRequest) return false
    consumedRequest = voiceRequest
    return true
  }

  fun openText() {
    mode = OverlayMode.TEXT
    collapsed = false
    composing = true
    autoFocus = true
  }

  /** La domanda e' partita: via la barra, resta la risposta che si sta formando. */
  fun sent() {
    composing = false
  }

  /**
   * La risposta e' arrivata: la barra torna, per la domanda dopo. Senza tastiera in faccia, pero':
   * la si apre solo se la si tocca. Riaprire con il fuoco significava coprire con la tastiera la
   * risposta che si e' appena aspettata.
   */
  fun resumeComposing() {
    if (mode != OverlayMode.TEXT) return
    composing = true
    autoFocus = false
  }

  fun hide() {
    mode = OverlayMode.HIDDEN
    collapsed = false
  }

  /** La pagina e' stata toccata o scorsa: la card si fa da parte senza fermare niente. */
  fun onPageInteraction() {
    if (mode != OverlayMode.HIDDEN) collapsed = true
  }
}
