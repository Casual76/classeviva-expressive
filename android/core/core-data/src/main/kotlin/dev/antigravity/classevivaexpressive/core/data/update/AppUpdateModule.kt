package dev.antigravity.classevivaexpressive.core.data.update

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.net.EngineHttp
import dev.antigravity.fluidengine.update.AndroidAppUpdateInstaller
import dev.antigravity.fluidengine.update.EngineAppUpdater
import dev.antigravity.fluidengine.update.UpdateSource
import javax.inject.Singleton

/** Il manifest delle release: lo stesso file che pubblica lo store, nel repository dell'app. */
internal const val ClassevivaManifestUrl =
  "https://raw.githubusercontent.com/Casual76/classeviva-expressive/main/manifest.json"

/**
 * L'aggiornamento dall'app: controllo del manifest, download e installazione sono dell'engine
 * (`engine-update`), che e' la copia da cui nacque il codice che stava qui. Il manifest non pubblica
 * lo sha256 dell'APK: l'installatore controlla comunque che il pacchetto sia questo e che la versione
 * sia quella annunciata.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {

  @Provides
  @Singleton
  fun provideAppUpdater(@ApplicationContext context: Context): AppUpdater {
    val http = EngineHttp(userAgent = "ClassevivaExpressiveUpdater")
    return EngineAppUpdater(
      http = http,
      source = UpdateSource(manifestUrl = ClassevivaManifestUrl, applicationId = context.packageName),
      installer = AndroidAppUpdateInstaller(context, http),
    )
  }
}
