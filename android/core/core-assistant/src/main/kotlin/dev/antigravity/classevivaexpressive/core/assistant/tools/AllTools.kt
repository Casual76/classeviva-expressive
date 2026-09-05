package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.ToolRegistry

/** Il catalogo del registro: trentadue strumenti in otto gruppi, ognuno tracciato. L'ordine e' quello del prompt. */
object AllTools {
  fun registry(): ToolRegistry<AssistantToolContext> = ToolRegistry(
    tools = traced(
      // voti
      VotiElencoTool(), VotiMediaTool(), VotiServeTool(), VotiDettaglioTool(), MaterieTool(), ObiettiviTool(),
      // agenda
      ImpegniTool(), VerificheProssimeTool(), CompitiTool(), CompitoDettaglioTool(), EventiPersonaliTool(),
      // orario
      OrarioGiornoTool(), OrarioSettimanaTool(), LezioniSvolteTool(),
      // bacheca
      ComunicazioniCercaTool(), ComunicazioneTool(), AllegatoLeggiTool(), NoteDisciplinariTool(),
      // assenze
      AssenzeElencoTool(), AssenzeRiepilogoTool(),
      // statistiche
      StatisticheTool(), ProfessoreTool(), PunteggioStudenteTool(),
      // didattica
      MaterialiCercaTool(), DocumentiTool(), LibriDiTestoTool(),
      // app
      ApriTool(), ImpostazioneTool(), BachecaSegnaLetteTool(), BachecaPresaVisioneTool(), AgendaAggiungiEventoTool(), ObiettivoSalvaTool(), AggiornaDatiTool(), StatoSyncTool(),
    ),
    groups = RegistroToolGroup.entries,
    actionGroup = RegistroToolGroup.APP,
  )

  private fun traced(vararg tools: AiTool<AssistantToolContext>): List<AiTool<AssistantToolContext>> = tools.map { TracedTool(it) }
}
