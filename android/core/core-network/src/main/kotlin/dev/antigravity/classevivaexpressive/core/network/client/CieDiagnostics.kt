package dev.antigravity.classevivaexpressive.core.network.client

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Traces of the CIE → portal hand-over, readable with `adb logcat -s CieDiag`. Only cookie
 * names, JSON key shapes, HTTP codes and outcome labels: never a value, token or identity.
 */
const val CieDiagLogTag = "CieDiag"

fun logCieDiag(message: String) {
  runCatching { Log.i(CieDiagLogTag, message) }
}

/** `{a:{b,c},d:[{e}]}`-style outline of a JSON payload, with value types instead of values. */
internal fun jsonShape(element: JsonElement?, depth: Int = 0): String = when (element) {
  null -> "null"
  is JsonObject -> if (depth >= 3) "{…}" else element.entries.joinToString(",", "{", "}") { (key, value) ->
    "${key.filter { it.isLetterOrDigit() || it == '_' }.take(32)}:${jsonShape(value, depth + 1)}"
  }
  is JsonArray -> if (element.isEmpty()) "[]" else "[${element.size}x${jsonShape(element.first(), depth + 1)}]"
  is JsonPrimitive -> when {
    element.isString -> "s${element.content.length}"
    element.content == "null" -> "null"
    else -> "n"
  }
}
