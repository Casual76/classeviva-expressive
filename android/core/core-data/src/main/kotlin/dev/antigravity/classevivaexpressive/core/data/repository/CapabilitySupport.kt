package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaGatewayClient
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
  private val gatewayClient: ClassevivaGatewayClient,
) : CapabilityResolver {
  override fun observeCapabilityMatrix(): Flow<List<FeatureCapability>> {
    return schoolYearStore.observeSelectedSchoolYear().map(::buildCapabilityMatrix)
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

  private fun buildCapabilityMatrix(selectedYear: SchoolYearRef): List<FeatureCapability> {
    val currentYear = schoolYearStore.currentSchoolYearRef()
    val previousYearSelected = selectedYear.id != currentYear.id
    val gatewayConfigured = gatewayClient.isConfigured()

    fun direct(feature: RegistroFeature, label: String, detail: String): FeatureCapability {
      return FeatureCapability(
        feature = feature,
        mode = FeatureCapabilityMode.DIRECT_REST,
        enabled = true,
        label = label,
        detail = detail,
      )
    }

    fun gateway(
      feature: RegistroFeature,
      label: String,
      detail: String,
      enabled: Boolean = gatewayConfigured,
    ): FeatureCapability {
      return FeatureCapability(
        feature = feature,
        mode = FeatureCapabilityMode.GATEWAY,
        enabled = enabled,
        label = label,
        detail = detail,
      )
    }

    fun optional(feature: RegistroFeature, label: String, detail: String): FeatureCapability {
      return FeatureCapability(
        feature = feature,
        mode = FeatureCapabilityMode.TENANT_OPTIONAL,
        enabled = gatewayConfigured,
        label = label,
        detail = detail,
      )
    }

    return buildList {
      add(direct(RegistroFeature.LOGIN_SESSION, "Disponibile", "Login e sessione gestiti direttamente dal client Android."))
      add(direct(RegistroFeature.PROFILE, "Disponibile", "Profilo studente disponibile via REST ufficiali."))
      add(if (previousYearSelected) gateway(RegistroFeature.GRADES, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.GRADES, "Disponibile", "Voti e media letti via REST ufficiali."))
      add(if (previousYearSelected) gateway(RegistroFeature.PERIODS, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.PERIODS, "Disponibile", "Periodi ufficiali letti direttamente dal client."))
      add(if (previousYearSelected) gateway(RegistroFeature.SUBJECTS, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.SUBJECTS, "Disponibile", "Materie ufficiali lette direttamente dal client."))
      add(direct(RegistroFeature.AGENDA, "Disponibile", "Agenda filtrata sull'anno scolastico selezionato."))
      add(direct(RegistroFeature.HOMEWORKS, "Disponibile", "Compiti disponibili come sezione dedicata, con submit via gateway quando necessario."))
      add(direct(RegistroFeature.LESSONS, "Disponibile", "Lezioni e argomenti filtrati sull'anno scolastico selezionato."))
      add(if (previousYearSelected) gateway(RegistroFeature.ABSENCES, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.ABSENCES, "Disponibile", "Assenze, ritardi e uscite lette via REST ufficiali."))
      add(gateway(RegistroFeature.ABSENCE_JUSTIFICATIONS, gatewayLabel(gatewayConfigured), "Le giustificazioni assenze passano dal gateway controllato."))
      add(if (previousYearSelected) gateway(RegistroFeature.NOTICEBOARD, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.NOTICEBOARD, "Disponibile", "Bacheca, lettura e allegati diretti via REST ufficiali."))
      add(gateway(RegistroFeature.NOTICEBOARD_REPLY, gatewayLabel(gatewayConfigured), "Risposte bacheca tramite gateway."))
      add(gateway(RegistroFeature.NOTICEBOARD_JOIN, gatewayLabel(gatewayConfigured), "Adesioni bacheca tramite gateway."))
      add(gateway(RegistroFeature.NOTICEBOARD_UPLOAD, gatewayLabel(gatewayConfigured), "Upload allegati bacheca tramite gateway."))
      add(if (previousYearSelected) gateway(RegistroFeature.NOTES, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.NOTES, "Disponibile", "Note e richiami disponibili nel client."))
      add(if (previousYearSelected) gateway(RegistroFeature.MATERIALS, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.MATERIALS, "Disponibile", "Materiali e allegati disponibili via REST ufficiali."))
      add(if (previousYearSelected) gateway(RegistroFeature.DOCUMENTS, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.DOCUMENTS, "Disponibile", "Documenti e pagelle disponibili via REST ufficiali."))
      add(if (previousYearSelected) gateway(RegistroFeature.SCHOOLBOOKS, "Richiede gateway", gatewayYearDetail()) else direct(RegistroFeature.SCHOOLBOOKS, "Disponibile", "Libri di testo disponibili nel client."))
      add(gateway(RegistroFeature.MEETINGS, gatewayLabel(gatewayConfigured), "I colloqui richiedono il gateway controllato e possono dipendere dal tenant."))
      add(direct(RegistroFeature.NOTIFICATIONS, "Disponibile", "Notifiche e deep link gestiti nativamente da Android."))
      add(gateway(RegistroFeature.PREVIOUS_SCHOOL_YEAR, gatewayLabel(gatewayConfigured), "L'anno scolastico precedente usa gateway quando i dati non sono disponibili via REST diretta.", enabled = gatewayConfigured || !previousYearSelected))
      add(optional(RegistroFeature.SPORTELLO, "Opzionale", "Sportello scuola varia per tenant e verra attivato solo se il modulo esiste."))
      add(optional(RegistroFeature.QUESTIONNAIRES, "Opzionale", "Questionari e sondaggi dipendono dai moduli abilitati per la scuola."))
    }
  }

  private fun gatewayLabel(gatewayConfigured: Boolean): String {
    return if (gatewayConfigured) "Richiede gateway" else "Gateway non configurato"
  }

  private fun gatewayYearDetail(): String {
    return if (gatewayClient.isConfigured()) {
      "L'anno precedente usa il gateway controllato per evitare incoerenze tra cache e REST ufficiali."
    } else {
      "Configura il gateway per consultare correttamente l'anno scolastico precedente."
    }
  }
}
