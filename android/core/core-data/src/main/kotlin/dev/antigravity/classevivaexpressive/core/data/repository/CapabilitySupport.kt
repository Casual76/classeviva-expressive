package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun yearScopedCacheKey(section: String, schoolYear: SchoolYearRef): String {
  return "${schoolYear.id}::$section"
}

@Singleton
class DefaultCapabilityResolver @Inject constructor(
  private val schoolYearStore: SchoolYearStore,
) : CapabilityResolver {
  override fun observeCapabilityMatrix(): Flow<List<FeatureCapability>> {
    return schoolYearStore.observeSelectedSchoolYear().map { buildCapabilityMatrix() }
  }

  override fun observeCapability(feature: RegistroFeature): Flow<FeatureCapability> {
    return observeCapabilityMatrix().map { matrix ->
      matrix.firstOrNull { it.feature == feature }
        ?: FeatureCapability(
          feature = feature,
          mode = FeatureCapabilityMode.UNSUPPORTED,
          enabled = false,
          label = "Non supportato",
          detail = "La funzione richiesta non e presente nella matrice capability.",
        )
    }
  }

  private fun buildCapabilityMatrix(): List<FeatureCapability> {
    fun direct(feature: RegistroFeature, label: String, detail: String) = FeatureCapability(
      feature = feature,
      mode = FeatureCapabilityMode.DIRECT_REST,
      enabled = true,
      label = label,
      detail = detail,
    )

    fun portal(feature: RegistroFeature, label: String, detail: String) = FeatureCapability(
      feature = feature,
      mode = FeatureCapabilityMode.DIRECT_PORTAL,
      enabled = true,
      label = label,
      detail = detail,
    )

    fun optional(feature: RegistroFeature, label: String, detail: String) = FeatureCapability(
      feature = feature,
      mode = FeatureCapabilityMode.TENANT_OPTIONAL,
      enabled = true,
      label = label,
      detail = detail,
    )

    return buildList {
      add(direct(RegistroFeature.LOGIN_SESSION, "Disponibile", "Login e sessione gestiti direttamente dal client Android."))
      add(direct(RegistroFeature.PROFILE, "Disponibile", "Profilo studente disponibile via REST ufficiali."))
      add(direct(RegistroFeature.GRADES, "Disponibile", "Voti filtrati per anno scolastico via REST ufficiali."))
      add(direct(RegistroFeature.PERIODS, "Disponibile", "Periodi ufficiali letti direttamente dal client."))
      add(direct(RegistroFeature.SUBJECTS, "Disponibile", "Materie ufficiali lette direttamente dal client."))
      add(direct(RegistroFeature.AGENDA, "Disponibile", "Agenda filtrata sull'anno scolastico selezionato."))
      add(direct(RegistroFeature.HOMEWORKS, "Disponibile", "Compiti disponibili come sezione dedicata."))
      add(direct(RegistroFeature.LESSONS, "Disponibile", "Lezioni e argomenti filtrati sull'anno scolastico selezionato."))
      add(direct(RegistroFeature.ABSENCES, "Disponibile", "Assenze, ritardi e uscite lette via REST ufficiali."))
      add(portal(RegistroFeature.ABSENCE_JUSTIFICATIONS, "Disponibile", "Giustificazioni assenze tramite portale on-device (nessun server esterno)."))
      add(direct(RegistroFeature.NOTICEBOARD, "Disponibile", "Bacheca, lettura e allegati diretti via REST ufficiali."))
      add(portal(RegistroFeature.NOTICEBOARD_REPLY, "Disponibile", "Risposte bacheca tramite portale on-device."))
      add(portal(RegistroFeature.NOTICEBOARD_JOIN, "Disponibile", "Adesioni bacheca tramite portale on-device."))
      add(portal(RegistroFeature.NOTICEBOARD_UPLOAD, "Disponibile", "Upload allegati bacheca tramite portale on-device."))
      add(direct(RegistroFeature.NOTES, "Disponibile", "Note e richiami filtrati per anno via REST."))
      add(direct(RegistroFeature.MATERIALS, "Disponibile", "Materiali filtrati per anno via REST ufficiali."))
      add(direct(RegistroFeature.DOCUMENTS, "Disponibile", "Documenti e pagelle via REST ufficiali."))
      add(direct(RegistroFeature.SCHOOLBOOKS, "Disponibile", "Libri di testo disponibili nel client."))
      add(portal(RegistroFeature.MEETINGS, "Best-effort", "Colloqui tramite portale on-device. WebView come fallback se il parsing fallisce."))
      add(direct(RegistroFeature.NOTIFICATIONS, "Disponibile", "Notifiche e deep link gestiti nativamente da Android."))
      add(direct(RegistroFeature.PREVIOUS_SCHOOL_YEAR, "Disponibile", "Anno precedente via REST con filtro data client-side. Nessuna configurazione richiesta."))
      add(optional(RegistroFeature.SPORTELLO, "Opzionale", "Sportello scuola varia per tenant e verra attivato solo se il modulo esiste."))
      add(optional(RegistroFeature.QUESTIONNAIRES, "Opzionale", "Questionari e sondaggi dipendono dai moduli abilitati per la scuola."))
    }
  }
}
