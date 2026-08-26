package dev.antigravity.classevivaexpressive.core.database.database

import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un blocco di scritture che il database applica tutte o nessuna.
 *
 * Esiste come interfaccia e non come `SchoolDatabase` iniettato direttamente perche' un restore a
 * meta' e' peggio di un restore fallito, e la sola cosa che lo dimostra e' un test che verifica che
 * ogni scrittura sia avvenuta dentro il blocco. `withTransaction` su un mock di una classe astratta
 * di Room non fa quello che sembra; qui in test si passa un'implementazione che esegue il blocco e
 * basta, e il test puo' contare.
 */
interface BackupTransactionRunner {
  suspend fun <T> inTransaction(block: suspend () -> T): T
}

@Singleton
class RoomBackupTransactionRunner @Inject constructor(
  private val database: SchoolDatabase,
) : BackupTransactionRunner {
  override suspend fun <T> inTransaction(block: suspend () -> T): T =
    database.withTransaction { block() }
}

@Module
@InstallIn(SingletonComponent::class)
object BackupTransactionModule {
  @Provides
  @Singleton
  fun provideBackupTransactionRunner(
    runner: RoomBackupTransactionRunner,
  ): BackupTransactionRunner = runner
}
