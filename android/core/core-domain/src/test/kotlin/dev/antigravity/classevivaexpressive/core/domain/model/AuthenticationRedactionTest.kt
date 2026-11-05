package dev.antigravity.classevivaexpressive.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Test

class AuthenticationRedactionTest {
  @Test fun portalCookieDiagnosticsNeverIncludeNameOrValue() {
    val diagnostics = PortalCookieDto("identity-cookie-marker", "secret-cookie-marker").toString()
    assertFalse(diagnostics.contains("identity-cookie-marker"))
    assertFalse(diagnostics.contains("secret-cookie-marker"))
  }
}
