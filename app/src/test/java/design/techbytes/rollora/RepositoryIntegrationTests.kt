package design.techbytes.rollora
import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import design.techbytes.rollora.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Room/SQLite persistence layer (an in-memory database, not a mock) so that
 * constraints declared on the entities — one class per group per date, unique roll numbers per
 * group — and Repository invariants — transactional restore, frozen rosters, audited corrections —
 * are proven against actual SQLite behaviour rather than application logic alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RepositoryIntegrationTests {
 private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RolloraDatabase::class.java)
  .allowMainThreadQueries().build()
 private fun setup(repo: Repository) = runBlocking {
  repo.profile(Profile(teacher = "Teacher", college = "College"))
 }
 private fun group(repo: Repository): ClassGroup = runBlocking {
  val g = ClassGroup(batch = "2024", semester = "5", type = "Major", component = "CT1", subject = "OS", defaultTime = "10:00")
  repo.saveGroup(g); g
 }

 @Test fun oneSessionPerGroupPerDateIsEnforcedByTheDatabaseItself() {
  val db = newDb()
  runBlocking {
   val g = ClassGroup(batch = "1", semester = "1", type = "Major", component = "", subject = "X", defaultTime = "09:00")
   db.dao().group(g)
   db.dao().insertSession(Session(groupId = g.id, date = "2026-09-01", classTime = "09:00", createdAt = 1, updatedAt = 1, zone = "UTC", teacher = "T", groupLabel = "L", finalised = true, revision = 1))
   try {
    db.dao().insertSession(Session(groupId = g.id, date = "2026-09-01", classTime = "09:00", createdAt = 2, updatedAt = 2, zone = "UTC", teacher = "T", groupLabel = "L", finalised = true, revision = 1))
    fail("A second distinct session row for the same group and date must violate the UNIQUE(groupId,date) index.")
   } catch (_: SQLiteConstraintException) { /* expected: enforced in persistence, not just app logic */ }
  }
  db.close()
 }

 @Test fun rollNumberUniquenessPerGroupIsEnforcedByTheDatabaseItself() {
  val db = newDb()
  runBlocking {
   val g = ClassGroup(batch = "1", semester = "1", type = "Major", component = "", subject = "X", defaultTime = "09:00")
   db.dao().group(g)
   db.dao().insertStudent(Student(groupId = g.id, roll = "007", rollKey = "007", name = "A", joined = "2026-01-01"))
   try {
    db.dao().insertStudent(Student(groupId = g.id, roll = "007", rollKey = "007", name = "B", joined = "2026-01-01"))
    fail("A second student with the same normalised roll key in the same group must violate the unique index.")
   } catch (_: SQLiteConstraintException) { }
  }
  db.close()
 }

 @Test fun draftThenFinaliseThenCorrectionRoundTripsThroughRealRoom() {
  val repo = Repository(newDb()); setup(repo); val g = group(repo)
  runBlocking {
   repo.saveStudents(listOf(
    Student(groupId = g.id, roll = "001", rollKey = "001", name = "Alpha", joined = "2026-01-01"),
    Student(groupId = g.id, roll = "002", rollKey = "002", name = "Beta", joined = "2026-01-01")))
   var s = repo.snapshot()
   val ids = s.students.map { it.id }
   val draft = repo.saveAttendance(g.id, "2026-09-01", "10:00", mapOf(ids[0] to "P"), mapOf(ids[0] to 100L), finalise = false, expectedRevision = null, reason = "")
   assertFalse(draft.finalised); assertEquals(1, draft.revision)
   val final = repo.saveAttendance(g.id, "2026-09-01", "10:00", mapOf(ids[0] to "P", ids[1] to "A"), mapOf(ids[0] to 100L, ids[1] to 200L), finalise = true, expectedRevision = draft.revision, reason = "")
   assertTrue(final.finalised); assertEquals(2, final.revision)
   try {
    repo.saveAttendance(g.id, "2026-09-01", "10:00", mapOf(ids[0] to "A", ids[1] to "A"), mapOf(ids[0] to 300L, ids[1] to 200L), finalise = true, expectedRevision = final.revision, reason = "")
    fail("Correcting a finalised register without a reason must be rejected.")
   } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
   val corrected = repo.saveAttendance(g.id, "2026-09-01", "10:00", mapOf(ids[0] to "A", ids[1] to "A"), mapOf(ids[0] to 300L, ids[1] to 200L), finalise = true, expectedRevision = final.revision, reason = "Marked wrong the first time")
   assertEquals(3, corrected.revision)
   s = repo.snapshot()
   assertEquals("A", s.marks.single { it.sessionId == corrected.id && it.studentId == ids[0] }.status)
   assertTrue(s.audit.any { it.action == "MARK" })
  }
 }

 @Test fun omittedStudentCorrectionAddsExactlyOneAuditedMark() {
  val repo = Repository(newDb()); setup(repo); val g = group(repo)
  runBlocking {
   // Only Alpha is entered as a student when the register is first saved and finalised.
   repo.saveStudents(listOf(Student(groupId = g.id, roll = "001", rollKey = "001", name = "Alpha", joined = "2026-01-01")))
   val alpha = repo.snapshot().students.single().id
   val session = repo.saveAttendance(g.id, "2026-09-01", "10:00", mapOf(alpha to "P"), mapOf(alpha to 100L), finalise = true, expectedRevision = null, reason = "")
   assertTrue(session.finalised)
   // The teacher later realises Beta (backdated joining date) was never entered and so never appeared
   // on that day's roster at all. Re-saving the session cannot add her: the roster was frozen at creation.
   repo.saveStudents(listOf(Student(groupId = g.id, roll = "002", rollKey = "002", name = "Beta", joined = "2026-01-01")))
   val beta = repo.snapshot().students.single { it.name == "Beta" }.id
   try {
    repo.saveAttendance(g.id, "2026-09-01", session.classTime, mapOf(alpha to "P", beta to "A"), mapOf(alpha to 100L, beta to 400L), finalise = true, expectedRevision = session.revision, reason = "Add Beta")
    fail("The frozen roster must reject a student who was not part of it when the session was created.")
   } catch (_: IllegalArgumentException) { }
   // The dedicated, audited correction path is what should be used instead.
   repo.addOmittedStudent(session.id, beta, "A", "Forgot to enter this student before saving the register")
   val s = repo.snapshot()
   assertEquals(1, s.marks.count { it.sessionId == session.id && it.studentId == beta })
   assertEquals("A", s.marks.single { it.sessionId == session.id && it.studentId == beta }.status)
   assertEquals("P", s.marks.single { it.sessionId == session.id && it.studentId == alpha }.status)
   assertTrue(s.audit.any { it.action == "ROSTER_CORRECTION" && it.entityId == session.id })
   assertEquals(session.revision + 1, s.sessions.single { it.id == session.id }.revision)
   try {
    repo.addOmittedStudent(session.id, beta, "P", "Trying again")
    fail("The same student cannot be added twice.")
   } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
  }
 }

 @Test fun invalidRestoreLeavesExistingDataUntouched() {
  val repo = Repository(newDb()); setup(repo); val g = group(repo)
  runBlocking {
   repo.saveStudents(listOf(Student(groupId = g.id, roll = "001", rollKey = "001", name = "Alpha", joined = "2026-01-01")))
   val before = repo.snapshot()
   val broken = before.copy(students = before.students + before.students.first().copy(id = newId()))
   try { repo.restore(broken); fail("An invalid snapshot (duplicate roll key) must be rejected before any write.") }
   catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
   val after = repo.snapshot()
   assertEquals(before.students.size, after.students.size)
   assertEquals(before.groups, after.groups)
  }
 }

 @Test fun validRestoreReplacesWorkspaceTransactionally() {
  val repo = Repository(newDb()); setup(repo); group(repo)
  runBlocking {
   val newGroup = ClassGroup(batch = "2025", semester = "1", type = "Minor", component = "", subject = "New", defaultTime = "11:00")
   val newStudent = Student(groupId = newGroup.id, roll = "500", rollKey = "500", name = "Restored Student", joined = "2026-01-01")
   val incoming = Snapshot(groups = listOf(newGroup), students = listOf(newStudent), profile = Profile(teacher = "Other Teacher"))
   repo.restore(incoming)
   val after = repo.snapshot()
   assertEquals(1, after.groups.size); assertEquals("New", after.groups.first().subject)
   assertEquals("Restored Student", after.students.single().name)
   assertEquals("Other Teacher", after.profile.teacher)
  }
 }
}
