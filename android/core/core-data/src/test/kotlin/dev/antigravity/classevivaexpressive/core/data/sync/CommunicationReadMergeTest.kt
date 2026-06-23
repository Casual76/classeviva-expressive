package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationReadMergeTest {
  @Test
  fun preserveLocallyReadCommunications_keepsLocalReadWhenRemoteIsUnread() {
    val merged = preserveLocallyReadCommunications(
      communications = listOf(communication(id = "c1", read = false)),
      locallyReadIds = setOf("c1"),
    )

    assertTrue(merged.single().read)
  }

  @Test
  fun preserveLocallyReadCommunications_doesNotMarkOtherRemoteUnreadItems() {
    val merged = preserveLocallyReadCommunications(
      communications = listOf(
        communication(id = "c1", read = false),
        communication(id = "c2", read = false),
      ),
      locallyReadIds = setOf("c1"),
    )

    assertTrue(merged.first { it.id == "c1" }.read)
    assertFalse(merged.first { it.id == "c2" }.read)
  }

  private fun communication(id: String, read: Boolean): Communication {
    return Communication(
      id = id,
      pubId = id,
      evtCode = "CIR",
      title = "Circolare",
      contentPreview = "Preview",
      sender = "Segreteria",
      date = "2026-06-23",
      read = read,
    )
  }
}
