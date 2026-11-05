package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.domain.model.PortalCookieDto
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthenticationRedactionTest {
  @Test fun authenticationDtosNeverPrintTokensCredentialsOrPersonalData() {
    val secret = "private-marker-do-not-print"
    val values = listOf(
      LoginRequestDto(secret, secret, secret),
      LoginResponseDto(secret, secret, secret, secret, secret, secret, secret),
      LoginResult(secret, secret, StudentProfile(secret, secret, secret), secret),
      PortalCookieDto(secret, secret),
    )
    values.forEach { assertFalse(it.toString().contains(secret)) }
  }
}
