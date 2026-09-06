package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.ToolRegistry

/** Il catalogo del registro: quarantasei strumenti in nove gruppi, ognuno tracciato. L'ordine e' quello del prompt. */
object AllTools {
  fun registry(): ToolRegistry<AssistantToolContext> = ToolRegistry(
    tools = traced(
      // voti
      VotiElencoTool(), VotiMediaTool(), VotiAndamentoTool(), VotiServeTool(), VotiDettaglioTool(), MaterieTool(), ObiettiviTool(),
      // agenda
      ImpegniTool(), VerificheProssimeTool(), CompitiTool(), CompitoDettaglioTool(), EventiPersonaliTool(),
      // orario
      OrarioGiornoTool(), OrarioSettimanaTool(), LezioniSvolteTool(),
      // bacheca
      ComunicazioniCercaTool(), ComunicazioneTool(), AllegatoLeggiTool(), NoteDisciplinariTool(), NotaDettaglioTool(),
      // assenze
      AssenzeElencoTool(), AssenzeRiepilogoTool(),
      // statistiche
      StatisticheTool(), ProfessoreTool(), PunteggioStudenteTool(),
      // didattica
      MaterialiCercaTool(), DocumentiTool(), LibriDiTestoTool(),
      // giornata
      GiornataScuolaTool(), SettimanaScuolaTool(), MediaSimulaTool(),
      // app
      ApriTool(), ImpostazioneTool(), BachecaSegnaLetteTool(), BachecaPresaVisioneTool(), ComunicazioneAdesioneTool(), AgendaAggiungiEventoTool(), ObiettivoSalvaTool(),
      AggiornaDatiTool(), StatoSyncTool(), AnnoScolasticoTool(), VotoSegnaVistoTool(), EventoRimuoviTool(), ObiettivoRimuoviTool(), OrarioModificaTool(), FunzioniDisponibiliTool(),
    ),
    groups = RegistroToolGroup.entries,
    actionGroup = RegistroToolGroup.APP,
  )

  private fun traced(vararg tools: AiTool<AssistantToolContext>): List<AiTool<AssistantToolContext>> = tools.map { TracedTool(it) }
}
