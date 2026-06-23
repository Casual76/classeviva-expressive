package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.domain.model.Communication

internal fun preserveLocallyReadCommunications(
  communications: List<Communication>,
  locallyReadIds: Set<String>,
): List<Communication> {
  if (locallyReadIds.isEmpty()) return communications
  return communications.map { communication ->
    if (communication.id in locallyReadIds && !communication.read) {
      communication.copy(read = true)
    } else {
      communication
    }
  }
}
