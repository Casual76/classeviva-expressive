package dev.antigravity.classevivaexpressive.cie

/** Only a nominal outcome and cleanup acknowledgement may cross ActivityResult/saved state. */
data class CieLoginResult(
  val outcome: CieLoginOutcome,
  val browserCleanupComplete: Boolean,
  /** Host-level reason for a refused navigation (never a path, query, token or cookie). */
  val diagnostic: String? = null,
)
