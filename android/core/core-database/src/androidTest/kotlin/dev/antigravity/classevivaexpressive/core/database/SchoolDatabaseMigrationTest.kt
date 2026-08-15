package dev.antigravity.classevivaexpressive.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.classevivaexpressive.core.database.database.MIGRATION_6_7
import dev.antigravity.classevivaexpressive.core.database.database.MIGRATION_7_8
import dev.antigravity.classevivaexpressive.core.database.database.MIGRATION_8_9
import dev.antigravity.classevivaexpressive.core.database.database.MIGRATION_9_10
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchoolDatabaseMigrationTest {

  @get:Rule
  val migrationTestHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    IsolationMigrationDatabase::class.java,
  )

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val dbName = "school-database-migration-test.db"
  private var helper: SupportSQLiteOpenHelper? = null

  @Before
  fun setUp() {
    context.deleteDatabase(dbName)
  }

  @After
  fun tearDown() {
    helper?.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun migration6To10_createsNewTablesAndKeepsGradeAgendaSeenRows() {
    val db = openDatabase(version = 6)
    createVersion6CoreTables(db)
    createVersion9IsolationTables(db)
    insertGrade(db)
    insertAgendaItem(db)
    insertSeenGrade(db)

    MIGRATION_6_7.migrate(db)
    MIGRATION_7_8.migrate(db)
    MIGRATION_8_9.migrate(db)
    MIGRATION_9_10.migrate(db)

    assertTrue(tableExists(db, "attachment_cache"))
    assertTrue(tableExists(db, "change_history"))
    assertTrue(columnExists(db, "grades", "firstSeenAtMs"))
    assertEquals(1, tableCount(db, "grades"))
    assertEquals(1, tableCount(db, "agenda_items"))
    assertEquals(1, tableCount(db, "seen_grades"))
    assertTrue(tableExists(db, "sync_metadata"))
    assertEquals(listOf("studentId", "schoolYearId", "id"), primaryKeyColumns(db, "materials"))
  }

  @Test
  fun migration7To8_createsChangeHistoryAndKeepsAttachmentCacheRows() {
    val db = openDatabase(version = 7)
    createAttachmentCacheTable(db)
    db.execSQL(
      """
      INSERT INTO attachment_cache (
        urlKey, sourceUrl, localPath, fileName, mimeType, downloadedAtMs, lastAccessedMs
      ) VALUES (
        'u1', 'https://example.test/file.pdf', '/tmp/file.pdf', 'file.pdf', 'application/pdf', 1000, 2000
      )
      """.trimIndent(),
    )

    MIGRATION_7_8.migrate(db)

    assertTrue(tableExists(db, "change_history"))
    assertEquals(1, tableCount(db, "attachment_cache"))
  }

  @Test
  fun migration8To9_addsGradeFirstSeenWithoutLosingRows() {
    val db = openDatabase(version = 8)
    createVersion6CoreTables(db)
    insertGrade(db)

    MIGRATION_8_9.migrate(db)

    assertTrue(columnExists(db, "grades", "firstSeenAtMs"))
    assertEquals(1, tableCount(db, "grades"))
  }

  @Test
  fun migration9To10_isolatesMaterialAndDocumentIdsAndDropsUnownedSnapshots() {
    val db = openDatabase(version = 9)
    createVersion9IsolationTables(db)
    db.execSQL(
      """
      INSERT INTO materials VALUES (
        'shared-id', 'student-a', '2025-2026', 'teacher', 'Docente', 'folder',
        'Cartella', 'Materiale', 'object', 'FILE', '2026-04-01', '{}', '[]'
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT INTO documents VALUES (
        'shared-id', 'student-a', '2025-2026', 'Documento', 'Dettaglio',
        'https://example.test/view', NULL, '{}'
      )
      """.trimIndent(),
    )
    db.execSQL("INSERT INTO snapshot_cache VALUES ('2025-2026::documents', '[]', 1000)")

    MIGRATION_9_10.migrate(db)

    db.execSQL(
      """
      INSERT INTO materials VALUES (
        'shared-id', 'student-b', '2025-2026', 'teacher', 'Docente', 'folder',
        'Cartella', 'Materiale B', 'object', 'FILE', '2026-04-01', '{}', '[]'
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT INTO documents (
        id, studentId, schoolYearId, title, detail, kind, capabilityState
      ) VALUES (
        'shared-id', 'student-b', '2025-2026', 'Documento B', 'Dettaglio', 'DOCUMENT', '{}'
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT INTO documents (
        id, studentId, schoolYearId, title, detail, kind, capabilityState
      ) VALUES (
        'SCHOOL_REPORT::shared-id', 'student-a', '2025-2026', 'Pagella', 'Dettaglio',
        'SCHOOL_REPORT', '{}'
      )
      """.trimIndent(),
    )

    assertEquals(2, tableCount(db, "materials"))
    assertEquals(3, tableCount(db, "documents"))
    assertEquals(2, rowsForStudentAndRemoteSuffix(db, "student-a", "shared-id"))
    assertEquals(0, tableCount(db, "snapshot_cache"))
    assertTrue(tableExists(db, "sync_metadata"))
    assertEquals(listOf("studentId", "schoolYearId", "id"), primaryKeyColumns(db, "materials"))
    assertEquals(listOf("studentId", "schoolYearId", "id"), primaryKeyColumns(db, "documents"))
    assertTrue(columnExists(db, "documents", "remoteHash"))
    assertTrue(columnExists(db, "documents", "portalViewUrl"))
  }

  @Test
  fun migration9To10_isValidatedByRoomAgainstExportedSchema() {
    val name = "isolation-migration-helper.db"
    migrationTestHelper.createDatabase(name, 9).apply {
      execSQL(
        """
        INSERT INTO materials VALUES (
          'material-1', 'student-a', '2025-2026', 'teacher', 'Docente', 'folder',
          'Cartella', 'Materiale', 'object', 'FILE', '2026-04-01', '{}', '[]'
        )
        """.trimIndent(),
      )
      execSQL(
        """
        INSERT INTO documents VALUES (
          'document-1', 'student-a', '2025-2026', 'Documento', 'Dettaglio', NULL, NULL, '{}'
        )
        """.trimIndent(),
      )
      execSQL("INSERT INTO snapshot_cache VALUES ('documents', '[]', 1000)")
      close()
    }

    val migrated = migrationTestHelper.runMigrationsAndValidate(
      name,
      10,
      true,
      MIGRATION_9_10,
    )

    assertEquals(1, tableCount(migrated, "materials"))
    assertEquals(1, tableCount(migrated, "documents"))
    assertEquals(0, tableCount(migrated, "snapshot_cache"))
    assertEquals(listOf("studentId", "schoolYearId", "id"), primaryKeyColumns(migrated, "documents"))
  }

  private fun openDatabase(version: Int): SupportSQLiteDatabase {
    val config = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(dbName)
      .callback(
        object : SupportSQLiteOpenHelper.Callback(version) {
          override fun onCreate(db: SupportSQLiteDatabase) = Unit
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
      )
      .build()
    helper = FrameworkSQLiteOpenHelperFactory().create(config)
    return helper!!.writableDatabase
  }

  private fun createVersion6CoreTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE grades (
        id TEXT NOT NULL PRIMARY KEY,
        studentId TEXT NOT NULL,
        schoolYearId TEXT NOT NULL,
        subject TEXT NOT NULL,
        valueLabel TEXT NOT NULL,
        numericValue REAL,
        description TEXT,
        date TEXT NOT NULL,
        type TEXT NOT NULL,
        weight REAL,
        notes TEXT,
        period TEXT,
        periodCode TEXT,
        teacher TEXT,
        color TEXT
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE agenda_items (
        id TEXT NOT NULL PRIMARY KEY,
        studentId TEXT NOT NULL,
        schoolYearId TEXT NOT NULL,
        title TEXT NOT NULL,
        subtitle TEXT NOT NULL,
        date TEXT NOT NULL,
        time TEXT,
        detail TEXT,
        subject TEXT,
        teacher TEXT,
        category TEXT NOT NULL,
        sharePayload TEXT,
        createdAt TEXT,
        firstSeenAtMs INTEGER
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE seen_grades (
        id TEXT NOT NULL PRIMARY KEY,
        studentId TEXT NOT NULL,
        gradeId TEXT NOT NULL,
        seenAtEpochMillis INTEGER NOT NULL
      )
      """.trimIndent(),
    )
  }

  private fun createAttachmentCacheTable(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE attachment_cache (
        urlKey TEXT NOT NULL PRIMARY KEY,
        sourceUrl TEXT NOT NULL,
        localPath TEXT NOT NULL,
        fileName TEXT NOT NULL,
        mimeType TEXT,
        downloadedAtMs INTEGER NOT NULL,
        lastAccessedMs INTEGER NOT NULL
      )
      """.trimIndent(),
    )
  }

  private fun createVersion9IsolationTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE snapshot_cache (
        cacheKey TEXT NOT NULL PRIMARY KEY,
        payload TEXT NOT NULL,
        updatedAtEpochMillis INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE materials (
        id TEXT NOT NULL PRIMARY KEY,
        studentId TEXT NOT NULL,
        schoolYearId TEXT NOT NULL,
        teacherId TEXT NOT NULL,
        teacherName TEXT NOT NULL,
        folderId TEXT NOT NULL,
        folderName TEXT NOT NULL,
        title TEXT NOT NULL,
        objectId TEXT NOT NULL,
        objectType TEXT NOT NULL,
        sharedAt TEXT NOT NULL,
        capabilityState TEXT NOT NULL,
        attachments TEXT NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE documents (
        id TEXT NOT NULL PRIMARY KEY,
        studentId TEXT NOT NULL,
        schoolYearId TEXT NOT NULL,
        title TEXT NOT NULL,
        detail TEXT NOT NULL,
        viewUrl TEXT,
        confirmUrl TEXT,
        capabilityState TEXT NOT NULL
      )
      """.trimIndent(),
    )
  }

  private fun insertGrade(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      INSERT INTO grades (
        id, studentId, schoolYearId, subject, valueLabel, numericValue, description,
        date, type, weight, notes, period, periodCode, teacher, color
      ) VALUES (
        'g1', '55', '2025-2026', 'Matematica', '7', 7.0, 'Prima versione',
        '2026-03-20', 'Scritto', 1.0, NULL, NULL, NULL, 'Prof Rossi', NULL
      )
      """.trimIndent(),
    )
  }

  private fun insertAgendaItem(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      INSERT INTO agenda_items (
        id, studentId, schoolYearId, title, subtitle, date, time, detail, subject,
        teacher, category, sharePayload, createdAt, firstSeenAtMs
      ) VALUES (
        'a1', '55', '2025-2026', 'Verifica', 'Storia', '2026-04-10', NULL,
        'Capitolo 1', 'Storia', 'Prof Verdi', 'ASSESSMENT', NULL, NULL, 1000
      )
      """.trimIndent(),
    )
  }

  private fun insertSeenGrade(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      INSERT INTO seen_grades (
        id, studentId, gradeId, seenAtEpochMillis
      ) VALUES (
        '55::g1', '55', 'g1', 2000
      )
      """.trimIndent(),
    )
  }

  private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
    db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'").use { cursor ->
      return cursor.moveToFirst()
    }
  }

  private fun tableCount(db: SupportSQLiteDatabase, tableName: String): Int {
    db.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
      cursor.moveToFirst()
      return cursor.getInt(0)
    }
  }

  private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
    db.query("PRAGMA table_info($tableName)").use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == columnName) return true
      }
    }
    return false
  }

  private fun primaryKeyColumns(db: SupportSQLiteDatabase, tableName: String): List<String> {
    return db.query("PRAGMA table_info($tableName)").use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      val pkIndex = cursor.getColumnIndex("pk")
      buildList {
        while (cursor.moveToNext()) {
          val position = cursor.getInt(pkIndex)
          if (position > 0) add(position to cursor.getString(nameIndex))
        }
      }.sortedBy { it.first }.map { it.second }
    }
  }

  private fun rowsForStudentAndRemoteSuffix(
    db: SupportSQLiteDatabase,
    studentId: String,
    remoteId: String,
  ): Int {
    return db.query(
      "SELECT COUNT(*) FROM documents WHERE studentId = ? AND id LIKE ?",
      arrayOf(studentId, "%::$remoteId"),
    ).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }
  }
}
