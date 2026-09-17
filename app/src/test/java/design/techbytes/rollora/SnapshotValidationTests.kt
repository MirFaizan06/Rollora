package design.techbytes.rollora
import org.junit.Test
import design.techbytes.rollora.data.*
import design.techbytes.rollora.domain.Rules

class SnapshotValidationTests {
 private fun valid(): Snapshot {
  val g=ClassGroup(batch="2024",semester="5",type="Major",component="CT2",subject="Java",defaultTime="10:00")
  val st=Student(groupId=g.id,roll="001",rollKey="001",name="Student One",joined="2026-01-01")
  val session=Session(groupId=g.id,date="2026-09-16",classTime="10:00",createdAt=1,updatedAt=2,zone="Asia/Kolkata",teacher="Teacher",groupLabel="Java",finalised=true,revision=1)
  return Snapshot(groups=listOf(g),students=listOf(st),sessions=listOf(session),marks=listOf(Mark(session.id,st.id,"001","Student One","P",1)),profile=Profile(teacher="Teacher"))
 }
 @Test fun validSnapshotAccepted() { SnapshotCodec.validate(valid()) }
 @Test(expected=IllegalArgumentException::class) fun orphanStudentRejected() { val s=valid();SnapshotCodec.validate(s.copy(groups=emptyList())) }
 @Test(expected=IllegalArgumentException::class) fun duplicateDateRejected() {val s=valid();SnapshotCodec.validate(s.copy(sessions=s.sessions+ s.sessions.first().copy(id=newId())))}
 @Test(expected=IllegalArgumentException::class) fun duplicateRollRejected() {val s=valid();SnapshotCodec.validate(s.copy(students=s.students+s.students.first().copy(id=newId())))}
 @Test(expected=IllegalArgumentException::class) fun duplicateMarkRejected() {val s=valid();SnapshotCodec.validate(s.copy(marks=s.marks+s.marks.first()))}
 @Test(expected=IllegalArgumentException::class) fun finalUnmarkedRejected() {val s=valid();SnapshotCodec.validate(s.copy(marks=listOf(s.marks.first().copy(status=""))))}
 @Test(expected=IllegalArgumentException::class) fun finalWithoutRosterRejected() {val s=valid();SnapshotCodec.validate(s.copy(marks=emptyList()))}
 @Test(expected=IllegalArgumentException::class) fun crossGroupMarkRejected() {val s=valid();val g=s.groups.first().copy(id=newId());SnapshotCodec.validate(s.copy(groups=s.groups+g,students=listOf(s.students.first().copy(groupId=g.id))))}
 @Test fun pastNameSnapshotSurvivesStudentRename() {val s=valid();SnapshotCodec.validate(s.copy(students=listOf(s.students.first().copy(name="Corrected Name"))))}
 @Test fun pastAttendanceSurvivesLeaving() {val s=valid();SnapshotCodec.validate(s.copy(students=listOf(s.students.first().copy(left="2026-09-01"))))}
}
