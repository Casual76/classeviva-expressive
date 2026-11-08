package dev.antigravity.classevivaexpressive.core.network.client

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ScopedSchoolReadTest {
  @Test fun notesRequireCategoryArraysAndRejectErrorOrPartlyMalformedPayloads() = runBlocking {
    val malformed = listOf("{}", "{\"error\":[]}", "{\"NTTE\":[],\"NTCL\":{}}", "{\"NTTE\":[false]}")
    withStrictSchoolRead {
      validateSchoolNotes(Json.parseToJsonElement("{\"NTTE\":[],\"NTCL\":[]}").jsonObject)
      for (raw in malformed) {
        try {
          validateSchoolNotes(Json.parseToJsonElement(raw).jsonObject)
          fail("invalid notes payload accepted: $raw")
        } catch (_: ClassevivaNetworkException) { }
      }
    }
    // The strict path must not alter parsing for existing callers.
    validateSchoolNotes(Json.parseToJsonElement("{\"error\":[]}").jsonObject)
  }
  @Test fun explicitEmptyArrayIsValidButUnknownPayloadIsNotEmpty() = runBlocking {
    withStrictSchoolRead {
      validateSchoolCollection(buildJsonObject { put("grades", JsonArray(emptyList())) }, "grades")
      try { validateSchoolCollection(buildJsonObject { put("error", "expired") }, "grades"); fail("invalid payload accepted") }
      catch(_: ClassevivaNetworkException) { }
    }
    // Legacy caller semantics remain opt-in.
    validateSchoolCollection(JsonObject(emptyMap()), "grades")
  }
}
