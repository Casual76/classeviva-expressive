package dev.antigravity.classevivaexpressive.cie

/** Only a nominal outcome and cleanup acknowledgement may cross ActivityResult/saved state. */
data class CieLoginResult(
  val outcome: CieLoginOutcome,
  val browserCleanupComplete: Boolean,
)
