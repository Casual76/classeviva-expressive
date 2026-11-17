package dev.antigravity.classevivaexpressive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class IncomingIntentInboxTest {
  @Test fun pendingTapSurvivesLoginAndProcessStateRestoration() = runBlocking {
    val inbox = IncomingIntentInbox(null, tap("grades?gradeId=42"))
    val saved = Bundle().also(inbox::save)
    val parcel = Parcel.obtain()
    try {
      parcel.writeBundle(saved)
      parcel.setDataPosition(0)
      val restored = IncomingIntentInbox(parcel.readBundle(Intent::class.java.classLoader), Intent())
      assertEquals("42", restored.intents.first().data?.getQueryParameter("gradeId"))
    } finally { parcel.recycle() }
  }

  @Test fun consumedTapDoesNotReplayAfterRecreationOrASecondSubscription() = runBlocking {
    val originalTap = tap("notes?noteId=42&categoryCode=NTTE")
    val inbox = IncomingIntentInbox(null, originalTap)
    assertSame(originalTap, inbox.intents.first())
    inbox.consume(originalTap)
    assertNull(withTimeoutOrNull(100) { inbox.intents.first() })
    val saved = Bundle().also(inbox::save)
    val restored = IncomingIntentInbox(saved, originalTap)
    assertNull(withTimeoutOrNull(100) { restored.intents.first() })
  }

  @Test fun consumingAnOlderTapCannotEraseTheLatestTap() = runBlocking {
    val first = tap("grades?gradeId=1")
    val second = tap("grades?gradeId=2")
    val inbox = IncomingIntentInbox(null, first)
    inbox.offer(second)
    inbox.consume(first)
    assertSame(second, inbox.intents.first())
  }

  @Test fun normalLauncherIntentsDoNotCreateNavigationCommands() = runBlocking {
    val inbox = IncomingIntentInbox(null, Intent(Intent.ACTION_MAIN))
    assertNull(withTimeoutOrNull(100) { inbox.intents.first() })
  }

  private fun tap(path: String) = Intent(Intent.ACTION_VIEW, Uri.parse("classevivaexpressive://open/$path"))
}
