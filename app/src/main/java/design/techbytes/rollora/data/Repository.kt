package design.techbytes.rollora.data

import androidx.room.withTransaction
import design.techbytes.rollora.domain.Rules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

/** The only writer. Every business operation is atomic and serialized with restore. */
class Repository(val db: RolloraDatabase) {
 private val gate = Mutex()
 private val dao = db.dao()
 suspend fun snapshot(): Snapshot = db.withTransaction {
  Snapshot(dao.groups(), dao.students(), dao.sessions(), dao.marks(), dao.holidays(), dao.audit(), dao.profile() ?: Profile())
 }
 private suspend fun event(id: String, action: String, detail: String) = dao.audit(Audit(entityId=id, at=System.currentTimeMillis(), action=action, detail=detail))
 suspend fun profile(value: Profile) = gate.withLock { db.withTransaction {
  Rules.text(value.teacher, "Teacher name"); require(value.college.length <= 150)
  require(value.theme in listOf("system", "dark", "light"))
  dao.profile(value); event("profile", "PROFILE", "Teacher profile or preferences updated")
 } }
 suspend fun saveGroup(value: ClassGroup) = gate.withLock { db.withTransaction {
  Rules.text(value.batch,"Batch",30); Rules.text(value.semester,"Semester",30)
  Rules.text(value.type,"Course type",30); Rules.text(value.subject,"Subject",100)
  require(value.component.length <= 30); Rules.time(value.defaultTime)
  val key: (ClassGroup) -> String = { listOf(it.batch,it.semester,it.type,it.component,it.subject).joinToString("|").lowercase() }
  require(dao.groups().none { it.id != value.id && key(it) == key(value) }) { "This class group already exists." }
  dao.group(value); event(value.id,"GROUP",value.toString())
 } }
 suspend fun saveStudents(values: List<Student>) = gate.withLock { db.withTransaction {
  require(values.isNotEmpty() && values.size <= 2000) { "Add between 1 and 2,000 students at a time." }
  val current = dao.students().associateBy { it.id }
  val groups = dao.groups().associateBy { it.id }
  val merged = current.toMutableMap()
  values.forEach { s ->
   require(groups[s.groupId]?.archived == false) { "Select an active group." }
   Rules.roll(s.roll); require(s.rollKey == Rules.rollKey(s.roll)); Rules.text(s.name,"Student name")
   Rules.date(s.joined); s.left?.let { require(Rules.date(it) >= Rules.date(s.joined)) { "Leaving date precedes joining date." } }
   val old = current[s.id]
   require(old == null || old.groupId == s.groupId) { "Create a new membership for another group." }
   merged[s.id] = s
  }
  require(merged.values.groupBy { it.groupId to it.rollKey }.values.none { it.size > 1 }) { "A roll number is duplicated in this group (including archived students)." }
  values.forEach { if (current[it.id] == null) dao.insertStudent(it) else dao.updateStudent(it); event(it.id,"STUDENT", "${current[it.id]} → $it") }
 } }
 fun dayOff(s: Snapshot, group: String, date: String): String? {
  val labels = s.holidays.filter { it.date == date && (it.groupId == null || it.groupId == group) }.map { it.title }.toMutableList()
  if (Rules.date(date).dayOfWeek == DayOfWeek.SUNDAY) labels.add("Sunday")
  return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
 }
 suspend fun saveHoliday(value: Holiday) = gate.withLock { db.withTransaction {
  Rules.date(value.date); Rules.text(value.title,"Holiday name")
  require(dao.holidays().none { it.id != value.id && it.groupId == value.groupId && it.date == value.date }) { "A holiday already exists for this date and scope." }
  dao.holiday(value); event(value.id,"HOLIDAY",value.toString())
 } }
 suspend fun deleteHoliday(id: String) = gate.withLock { db.withTransaction {
  dao.deleteHoliday(id); event(id,"HOLIDAY_REMOVED","Calendar entry removed; attendance retained")
 } }
 suspend fun saveAttendance(groupId: String, date: String, time: String, statuses: Map<String,String>,
  markedTimes: Map<String,Long>, finalise: Boolean, expectedRevision: Int?, reason: String): Session = gate.withLock {
  db.withTransaction {
   val s = snapshot(); val group = s.groups.single { it.id == groupId }
   require(!group.archived) { "Unarchive the group before changing attendance." }
   require(Rules.date(date) <= LocalDate.now()) { "Future attendance cannot be recorded." }; Rules.time(time)
   val old = s.sessions.singleOrNull { it.groupId == groupId && it.date == date }
   require(old?.revision == expectedRevision) { "This class changed. Reopen it before saving." }
   require(old?.cancelled != true) { "This class was cancelled. Restore it from History first." }
   val oldMarks = s.marks.filter { it.sessionId == old?.id }.associateBy { it.studentId }
   val roster = if (old != null) oldMarks.keys else s.students.filter { it.groupId == groupId && Rules.eligible(date,it.joined,it.left) }.map { it.id }.toSet()
   require(statuses.keys.all { it in roster }) { "Roster changed. Reopen attendance." }
   require(statuses.values.all { it in listOf("P","A","L","") }) { "Invalid attendance state." }
   require(!old.orFalseFinal() || finalise) { "A final register cannot become a draft." }
   if (finalise) Rules.complete(roster,statuses)
   require(roster.isNotEmpty()) { "No students are eligible on this date." }
   if (dayOff(s,groupId,date) != null || old?.finalised == true) Rules.text(reason,"Reason",300)
   val now = System.currentTimeMillis()
   val next = (old ?: Session(groupId=groupId,date=date,classTime=time,createdAt=now,updatedAt=now,
    zone=ZoneId.systemDefault().id, teacher=s.profile.teacher,
    groupLabel="Batch ${group.batch} · Semester ${group.semester} · ${group.title()}"))
    .copy(classTime=time, updatedAt=now, finalised=finalise, revision=(old?.revision ?: 0)+1, overrideReason=reason)
   if (old == null) dao.insertSession(next) else dao.updateSession(next)
   dao.marks(roster.map { id ->
    val student = s.students.single { it.id == id }; val before = oldMarks[id]
    val status = statuses[id] ?: ""
    val at = if (before != null && before.status == status) before.markedAt else markedTimes[id] ?: now
    require(at >= 0 && at <= now + 60000) { "Invalid marking timestamp." }
    if (before?.status != status) event(next.id,"MARK", "$id: ${before?.status ?: "unmarked"} → $status; tap=$at; save=$now")
    Mark(next.id,id,before?.roll ?: student.roll,before?.name ?: student.name,status,at)
   })
   event(next.id,if(finalise) "FINALISED" else "DRAFT", "revision=${next.revision}; date=$date; time=$time; reason=$reason; deviceZone=${ZoneId.systemDefault()}")
   next
  }
 }
 private fun Session?.orFalseFinal() = this?.finalised == true
 /** Deliberate, audited correction for a student a teacher forgot to include when a register was first
  * saved. The frozen roster of the session itself is never rewritten silently: this adds exactly one new
  * mark, requires a reason, and records the correction in the audit trail alongside the ordinary revision. */
 suspend fun addOmittedStudent(sessionId: String, studentId: String, status: String, reason: String) = gate.withLock { db.withTransaction {
  val s = snapshot()
  val session = s.sessions.singleOrNull { it.id == sessionId } ?: error("This class record no longer exists.")
  require(!session.cancelled) { "This class was cancelled. Restore it from History first." }
  val group = s.groups.single { it.id == session.groupId }
  require(!group.archived) { "Unarchive the group before changing attendance." }
  val student = s.students.singleOrNull { it.id == studentId } ?: error("This student no longer exists.")
  require(student.groupId == session.groupId) { "This student is not a member of this class group." }
  require(Rules.eligible(session.date, student.joined, student.left)) { "This student's membership does not cover this class date." }
  require(s.marks.none { it.sessionId == sessionId && it.studentId == studentId }) { "This student is already on this register." }
  require(status in listOf("P", "A", "L")) { "Mark the omitted student Present, Absent or Leave." }
  Rules.text(reason, "Reason", 300)
  val now = System.currentTimeMillis()
  dao.updateSession(session.copy(updatedAt = now, revision = session.revision + 1, overrideReason = reason))
  dao.marks(listOf(Mark(sessionId, studentId, student.roll, student.name, status, now)))
  event(sessionId, "ROSTER_CORRECTION", "Added ${student.roll} (${student.name}) omitted from the original register; status=$status; reason=$reason")
 } }
 suspend fun cancelSession(id: String, cancel: Boolean, reason: String) = gate.withLock { db.withTransaction {
  Rules.text(reason,"Reason",300)
  val old = dao.sessions().single { it.id == id }
  dao.updateSession(old.copy(cancelled=cancel,updatedAt=System.currentTimeMillis(),revision=old.revision+1))
  event(id,if(cancel) "CANCELLED" else "RESTORED",reason)
 } }
 suspend fun restore(s: Snapshot) = gate.withLock { db.withTransaction {
  SnapshotCodec.validate(s)
  dao.clearMarks(); dao.clearSessions(); dao.clearStudents(); dao.clearHolidays(); dao.clearGroups(); dao.clearAudit(); dao.clearProfile()
  s.groups.forEach { dao.group(it) }; dao.insertStudents(s.students); dao.insertSessions(s.sessions)
  dao.marks(s.marks); s.holidays.forEach { dao.holiday(it) }; s.audit.forEach { dao.audit(it) }; dao.profile(s.profile)
  event("restore","RESTORE","Validated portable/local snapshot restored")
 } }
}
