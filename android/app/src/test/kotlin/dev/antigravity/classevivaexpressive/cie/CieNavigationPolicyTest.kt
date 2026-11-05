package dev.antigravity.classevivaexpressive.cie

import org.junit.Assert.*
import org.junit.Test

class CieNavigationPolicyTest {
  private val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJl"
  private val callback = "eu.spaggiari.classevivastudente://spid-callback?spidToken=$token"
  private val portal = "https://web.spaggiari.eu/home/app/default/loginsp.php"

  @Test fun callbackRequiresTrustedMainDocumentAndExactAuthority() {
    assertEquals(token, CieNavigationPolicy.callbackPa(callback, portal, true))
    for (source in listOf(null, "https://web.spaggiari.eu.evil.example/", "https://evil.example/?web.spaggiari.eu", "http://web.spaggiari.eu/", "https://eid.istruzione.it/")) {
      assertNull(CieNavigationPolicy.callbackPa(callback, source, true))
    }
    assertNull(CieNavigationPolicy.callbackPa(callback, portal, false))
    for (value in listOf(callback.replace("://spid-callback", "://spid-callback.evil"), callback.replace("eu.spaggiari.classevivastudente", "evil"), callback.replace("://spid-callback", "://user@spid-callback"), "$callback#fragment", callback.replace("spid-callback?", "spid-callback/extra?"))) {
      assertNull(CieNavigationPolicy.callbackPa(value, portal, true))
    }
  }

  @Test fun duplicateOrUnexpectedParametersAndMalformedTokensAreRejected() {
    for (query in listOf("spidToken=$token&spidToken=$token", "spidToken=$token&pa=$token", "pa=$token", "spidToken=", "spidToken=not-a-jwt", "spidToken=a%0Ab.c", "spidToken=${"a".repeat(16_385)}.b.c")) {
      assertNull(CieNavigationPolicy.callbackPa("eu.spaggiari.classevivastudente://spid-callback?$query", portal, true))
    }
  }

  @Test fun documentsAndPostTargetsCannotEscapeTheFederationOrigins() {
    assertTrue(CieNavigationPolicy.allowsDocument(CieNavigationPolicy.StartUrl))
    assertTrue(CieNavigationPolicy.allowsDocument("https://eid.istruzione.it/login"))
    assertTrue(CieNavigationPolicy.allowsDocument("https://idserver.servizicie.interno.gov.it/idp/login/livello2"))
    for (value in listOf("https://web.spaggiari.eu.attacker.test/", "https://web.spaggiari.eu:444/", "https://user:pass@web.spaggiari.eu/", "http://web.spaggiari.eu/", "file:///etc/passwd", "content://provider/file", "javascript:alert(1)", "https://example.org/form")) {
      assertFalse(value, CieNavigationPolicy.allowsDocument(value))
      assertFalse(value, CieNavigationPolicy.allowsResource(value))
    }
  }

  @Test fun cieIdHandoffAndReturnUseDifferentValidatedContracts() {
    assertTrue(CieNavigationPolicy.isCieIdHandoff("https://idserver.servizicie.interno.gov.it/idp/OpenApp?request=opaque"))
    assertTrue(CieNavigationPolicy.isCieIdHandoff("https://idserver.servizicie.interno.gov.it/idp/login/livello2"))
    assertFalse(CieNavigationPolicy.isCieIdHandoff("https://web.spaggiari.eu/idp/OpenApp"))
    assertFalse(CieNavigationPolicy.isCieIdHandoff("https://idserver.servizicie.interno.gov.it.evil.example/idp/OpenApp"))
    assertTrue(CieNavigationPolicy.allowsCieIdResult("https://idserver.servizicie.interno.gov.it/idp/redirect?response=opaque"))
    assertFalse(CieNavigationPolicy.allowsCieIdResult(portal))
    assertFalse(CieNavigationPolicy.allowsCieIdResult("https://eid.istruzione.it/redirect"))
    assertFalse(CieNavigationPolicy.allowsCieIdResult("https://idserver.servizicie.interno.gov.it.evil.example/idp/redirect"))
    assertFalse(CieNavigationPolicy.allowsCieIdResult("https://idserver.servizicie.interno.gov.it/idp/OpenApp"))
    assertFalse(CieNavigationPolicy.allowsCieIdResult("intent://arbitrary"))
    assertFalse(CieNavigationPolicy.allowsCieIdResult(callback))
  }
}
