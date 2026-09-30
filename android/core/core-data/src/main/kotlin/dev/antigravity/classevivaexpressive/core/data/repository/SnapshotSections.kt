package dev.antigravity.classevivaexpressive.core.data.repository

internal const val ProfileSection = "profile"
internal const val GradesSection = "grades"
internal const val PeriodsSection = "periods"
internal const val SubjectsSection = "subjects"
internal const val LessonsSection = "lessons"
internal const val HomeworkSection = "homeworks"
internal const val AgendaSection = "agenda"
internal const val AbsencesSection = "absences"
internal const val CommunicationsSection = "communications"
internal const val NotesSection = "notes"
internal const val MaterialsSection = "materials"
internal const val DocumentsSection = "documents"
internal const val SchoolbooksSection = "schoolbooks"
internal const val MeetingTeachersSection = "meeting_teachers"
internal const val MeetingSlotsSection = "meeting_slots"
internal const val MeetingBookingsSection = "meeting_bookings"

/**
 * L'ultima lettura della sezione Compiti del registro, com'era, prima dell'unione con l'agenda.
 *
 * Non e' una sezione da sincronizzare: e' la memoria di quella fonte. Serve a confrontare due
 * letture per lo storico, a non perdere i compiti quando la fonte non risponde, e — quando manca
 * del tutto — a sapere che la prima lettura non deve annunciare come nuovi i compiti dell'anno.
 */
internal const val HomeworkDedicatedSection = "homeworks_dedicated"

internal const val HistoryKindGrade = "grade"
internal const val HistoryKindAgenda = "agenda"
internal const val HistoryKindHomework = "homework"
