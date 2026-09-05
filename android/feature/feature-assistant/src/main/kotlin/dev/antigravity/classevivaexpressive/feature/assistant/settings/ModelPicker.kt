package dev.antigravity.classevivaexpressive.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.OpenRouterCatalog
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.util.Locale

/** Cosa si sta scegliendo: un livello di chat di un provider, o il modello di trascrizione. */
data class ModelPickRequest(val provider: ProviderId, val tier: ModelTier?, val stt: Boolean = false)

fun ModelTier.label(): String = when (this) {
  ModelTier.ROUTER -> "Router"
  ModelTier.CHAT -> "Chat"
  ModelTier.DEEP -> "Profondo"
}

fun ModelTier.hint(): String = when (this) {
  ModelTier.ROUTER -> "Piccolo e veloce: sceglie gli strumenti"
  ModelTier.CHAT -> "Risponde e usa gli strumenti"
  ModelTier.DEEP -> "Documenti, allegati, testi lunghi"
}

private fun contextLabel(tokens: Int?): String? = tokens?.let {
  when {
    it >= 1_000_000 -> "${it / 1_000_000}M"
    it >= 1_000 -> "${it / 1_000}k"
    else -> it.toString()
  }
}

private fun priceLabel(perM: Double?): String = when {
  perM == null -> "—"
  perM == 0.0 -> "0 $"
  perM < 0.01 -> "<0,01 $"
  else -> String.format(Locale.getDefault(), "%.2f $", perM)
}

/** Un modello in una riga: nome, id, contesto, prezzo se esiste, cosa sa fare. */
@Composable
private fun ModelRow(model: ModelInfo, selected: Boolean, extra: String?, onClick: () -> Unit, badge: (@Composable () -> Unit)? = null) {
  val meta = buildList {
    contextLabel(model.contextWindow)?.let { add("contesto $it") }
    if (model.pricePromptPerM != null) add("${priceLabel(model.pricePromptPerM)} / ${priceLabel(model.priceCompletionPerM)} per M token")
    if (model.free) add("gratuito")
    if (model.supportsReasoning) add("ragiona")
    if (model.supportsVision) add("vede")
    if (model.supportsDocuments) add("legge PDF")
    extra?.let { add(it) }
  }.joinToString(" · ")
  FluidListRow(
    title = model.displayName,
    subtitle = if (model.id == model.displayName) meta else "${model.id}${if (meta.isNotEmpty()) " · $meta" else ""}",
    onClick = onClick,
    leading = if (selected) {
      { Icon(Icons.Rounded.Check, contentDescription = "Scelto", tint = MaterialTheme.colorScheme.primary) }
    } else {
      null
    },
    badge = badge,
  )
}

/**
 * Il picker dei modelli, nel vetro: per Groq e Gemini un elenco col preferito in testa; per
 * OpenRouter ricerca, filtri (gli strumenti sono obbligatori), sezioni Consigliati / Gratuiti /
 * Tutti, e fino a due riserve per la chat.
 */
@Composable
fun ModelPickerPortal(
  request: ModelPickRequest?,
  catalogues: Map<ProviderId, ModelCatalogue>,
  selectedModel: (ModelPickRequest) -> String?,
  fallbacks: List<String>,
  onSelect: (ModelPickRequest, String) -> Unit,
  onFallbacks: (List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  FluidGlassModalPortal(
    item = request,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = request?.let { "${it.provider.label} · ${if (it.stt) "Trascrizione" else it.tier?.label() ?: "Modello"}" },
  ) { current ->
    val catalogue = catalogues[current.provider] ?: ModelCatalogue(emptyList(), emptyList())
    val models = if (current.stt) catalogue.stt else catalogue.chat
    val selected = selectedModel(current)
    Text(
      text = "${current.provider.label} · ${if (current.stt) "Trascrizione" else current.tier?.label() ?: ""}",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
    current.tier?.let { Text(it.hint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
    Spacer(Modifier.height(8.dp))
    if (current.provider == ProviderId.OPENROUTER && !current.stt) {
      OpenRouterList(
        catalogue = catalogue,
        primary = selected,
        fallbacks = if (current.tier == ModelTier.CHAT) fallbacks else emptyList(),
        withFallbacks = current.tier == ModelTier.CHAT,
        onPrimary = { onSelect(current, it) },
        onFallbacks = onFallbacks,
      )
    } else {
      SimpleList(models = models, selected = selected, onSelect = { onSelect(current, it); onDismiss() })
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun SimpleList(models: List<ModelInfo>, selected: String?, onSelect: (String) -> Unit) {
  if (models.isEmpty()) {
    Text("Nessun modello nel catalogo: verifica la chiave per scaricarlo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    return
  }
  LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
    item {
      FluidListGroup {
        models.forEachIndexed { index, model ->
          if (index > 0) FluidListDivider()
          ModelRow(model = model, selected = model.id == selected, extra = null, onClick = { onSelect(model.id) })
        }
      }
    }
  }
}

@Composable
private fun OpenRouterList(
  catalogue: ModelCatalogue,
  primary: String?,
  fallbacks: List<String>,
  withFallbacks: Boolean,
  onPrimary: (String) -> Unit,
  onFallbacks: (List<String>) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  var onlyReasoning by remember { mutableStateOf(false) }
  var onlyFree by remember { mutableStateOf(false) }
  var onlyVision by remember { mutableStateOf(false) }
  val all = catalogue.chat.filter { it.supportsTools }
  val filtered = all.filter { model ->
    (query.isBlank() || model.id.contains(query, ignoreCase = true) || model.displayName.contains(query, ignoreCase = true)) &&
      (!onlyReasoning || model.supportsReasoning) &&
      (!onlyFree || model.free) &&
      (!onlyVision || model.supportsVision)
  }
  val recommended = OpenRouterCatalog.recommended(catalogue).filter { it in filtered }
  val free = filtered.filter { it.free }
  Column(Modifier.fillMaxWidth()) {
    FluidTextField(value = query, onValueChange = { query = it }, placeholder = "Cerca un modello", modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
      FluidChip(label = "Strumenti", selected = true, onClick = {}, enabled = false)
      FluidChip(label = "Ragiona", selected = onlyReasoning, onClick = { onlyReasoning = !onlyReasoning })
      FluidChip(label = "Gratuiti", selected = onlyFree, onClick = { onlyFree = !onlyFree })
      FluidChip(label = "Vede", selected = onlyVision, onClick = { onlyVision = !onlyVision })
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
      val sections = listOf("Consigliati" to recommended.take(24), "Gratuiti" to free.take(24), "Tutti" to filtered.take(200))
      sections.forEach { (title, models) ->
        if (models.isEmpty()) return@forEach
        item(key = "h-$title") { FluidSectionHeader(title = title) }
        item(key = "g-$title") {
          FluidListGroup {
            models.forEachIndexed { index, model ->
              if (index > 0) FluidListDivider()
              val isPrimary = model.id == primary
              val isFallback = model.id in fallbacks
              ModelRow(
                model = model,
                selected = isPrimary,
                extra = if (isFallback) "riserva" else null,
                onClick = { onPrimary(model.id); if (isFallback) onFallbacks(fallbacks - model.id) },
                badge = if (!withFallbacks || isPrimary) {
                  null
                } else {
                  {
                    FluidButton(
                      text = if (isFallback) "Togli riserva" else "Riserva",
                      style = FluidButtonStyle.Plain,
                      size = FluidButtonSize.Small,
                      enabled = isFallback || fallbacks.size < 2,
                      onClick = { onFallbacks(if (isFallback) fallbacks - model.id else (fallbacks + model.id).take(2)) },
                    )
                  }
                },
              )
            }
          }
          Spacer(Modifier.height(8.dp))
        }
      }
      if (filtered.isEmpty()) item { Text("Nessun modello corrisponde.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
    }
  }
}

/** Il nome di un modello per una riga delle impostazioni: dal catalogo se c'e', l'id altrimenti. */
fun ModelCatalogue?.summary(id: String?): String {
  if (id == null) return "come la chat"
  val model = this?.chat?.firstOrNull { it.id == id } ?: this?.stt?.firstOrNull { it.id == id } ?: return id
  return if (model.free) "${model.displayName} · gratuito" else model.displayName
}
