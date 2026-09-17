package design.techbytes.rollora
import org.junit.Test
import design.techbytes.rollora.data.*
import design.techbytes.rollora.transfer.RegisterExport
import java.io.File

/**
 * Writes a realistic register workbook to build/verification/ so it can be opened by a
 * spreadsheet reader outside this codebase (Excel, LibreOffice, openpyxl, ...) as an
 * independent check on special characters, leading-zero rolls and numeric totals.
 * This file is a build output, not part of the shipped source or app.
 */
class ExportVerificationTests {
 @Test fun writesInspectableSampleWorkbook() {
  val group = ClassGroup(batch = "2024", semester = "5", type = "Major", component = "CT2", subject = "Operating Systems", defaultTime = "10:00")
  val students = listOf(
   Student(groupId = group.id, roll = "007", rollKey = "007", name = "Student Seven", joined = "2026-01-01"),
   Student(groupId = group.id, roll = "0013", rollKey = "0013", name = "O'Brien & Sons <Ltd>", joined = "2026-01-01"),
   Student(groupId = group.id, roll = "014", rollKey = "014", name = "=HYPERLINK(\"http://example.com\")", joined = "2026-01-01"),
   Student(groupId = group.id, roll = "015", rollKey = "015", name = "All Leave Student", joined = "2026-01-01"),
  )
  val session1 = Session(groupId = group.id, date = "2026-09-01", classTime = "10:00", createdAt = 1, updatedAt = 1,
   zone = "Asia/Kolkata", teacher = "Mir Faizan", groupLabel = group.title(), finalised = true, revision = 1)
  val session2 = Session(groupId = group.id, date = "2026-09-02", classTime = "10:00", createdAt = 2, updatedAt = 2,
   zone = "Asia/Kolkata", teacher = "Mir Faizan", groupLabel = group.title(), finalised = true, revision = 1)
  val marks = listOf(
   Mark(session1.id, students[0].id, students[0].roll, students[0].name, "P", 10),
   Mark(session1.id, students[1].id, students[1].roll, students[1].name, "A", 11),
   Mark(session1.id, students[2].id, students[2].roll, students[2].name, "P", 12),
   Mark(session1.id, students[3].id, students[3].roll, students[3].name, "L", 13),
   Mark(session2.id, students[0].id, students[0].roll, students[0].name, "P", 20),
   Mark(session2.id, students[1].id, students[1].roll, students[1].name, "P", 21),
   Mark(session2.id, students[2].id, students[2].roll, students[2].name, "A", 22),
   Mark(session2.id, students[3].id, students[3].roll, students[3].name, "L", 23),
  )
  val snapshot = Snapshot(groups = listOf(group), students = students, sessions = listOf(session1, session2),
   marks = marks, profile = Profile(teacher = "Mir Faizan", college = "Tech Bytes Design College"))
  val out = File(File(System.getProperty("user.dir"), "build/verification").apply { mkdirs() }, "sample-register.xlsx")
  out.outputStream().use { RegisterExport.write(it, snapshot, group, "2026-09-01", "2026-09-02") }
  check(out.exists() && out.length() > 0) { "Workbook was not written." }
 }
}
