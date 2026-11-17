package dev.antigravity.classevivaexpressive.core.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryEpochTest {
  @Test fun existingFakeAuthRepositoriesHaveASafeDefaultEpochFlow() = runBlocking {
    val fake = object : AuthRepository {
      override val session = MutableStateFlow<UserSession?>(null)
      override suspend fun restore(): UserSession? = session.value
      override suspend fun login(username: String, password: String): Result<UserSession> = Result.failure(UnsupportedOperationException())
      override suspend fun logout() { session.value = null }
    }
    assertEquals(fake.dataEpoch(), fake.observeDataEpoch().first())
    assertEquals(0L, fake.observeDataEpoch().first())
  }
}
