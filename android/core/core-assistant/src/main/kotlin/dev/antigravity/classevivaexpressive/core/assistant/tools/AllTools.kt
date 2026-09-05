package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.fluidengine.ai.tools.ToolRegistry

/** Il catalogo del registro: trentadue strumenti in otto gruppi. L'ordine e' quello del prompt. */
object AllTools {
  fun registry(): ToolRegistry<AssistantToolContext> = ToolRegistry(
    tools = listOf(
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
}
