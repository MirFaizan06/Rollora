package design.techbytes.rollora.transfer
import design.techbytes.rollora.data.*
import design.techbytes.rollora.domain.Rules
import java.io.OutputStream
import java.time.*
import java.util.Locale

object RegisterExport {
 fun write(out: OutputStream,s: Snapshot,group: ClassGroup,from: String,to: String) {
  Rules.validateRange(from,to)
  val days=generateSequence(Rules.date(from)) { it.plusDays(1) }.takeWhile { it<=Rules.date(to) }.toList()
  val sessions=s.sessions.filter { it.groupId==group.id && it.date>=from && it.date<=to && it.finalised && !it.cancelled }.sortedBy { it.date }
  val byDay=sessions.associateBy { it.date }; val ids=sessions.map { it.id }.toSet()
  val marks=s.marks.filter { it.sessionId in ids }; val byPair=marks.associateBy { it.sessionId to it.studentId }
  val students=s.students.filter { it.groupId==group.id && (Rules.eligible(to,it.joined,it.left) || it.joined<=to && (it.left==null || it.left>from) || marks.any { m->m.studentId==it.id }) }.sortedBy { it.rollKey }
  val head=listOf(
   listOf("Rollora · Attendance register",s.profile.college),listOf("Teacher",s.profile.teacher),
   listOf("Batch / Semester", "${group.batch} / ${group.semester}"),listOf("Class",group.title()),
   listOf("Period","$from to $to"),listOf("Generated",ZonedDateTime.now().toString()),
   listOf("Legend","P Present; A Absent; L Leave; S Sunday; H Holiday; NC No saved class; — not on session roster"),
   listOf("Percentage","P ÷ (P + A) × 100. Leave excluded. Only final, non-cancelled classes count."),
   listOf("Conducted classes",sessions.size.toString()),
   listOf("Roll number","Name")+days.map { it.toString() }+listOf("Present","Absent","Leave","Attendance %"))
  val body=students.map { st ->
   val own=marks.filter { it.studentId==st.id }; val p=own.count { it.status=="P" }; val a=own.count { it.status=="A" }; val l=own.count { it.status=="L" }
   listOf(st.roll,st.name)+days.map { day ->
    val session=byDay[day.toString()]
    if(session!=null) byPair[session.id to st.id]?.status ?: "—"
    else if(s.holidays.any { it.date==day.toString() && (it.groupId==null || it.groupId==group.id) }) "H"
    else if(day.dayOfWeek==DayOfWeek.SUNDAY) "S" else "NC"
   }+listOf(p.toString(),a.toString(),l.toString(),Rules.percentage(p,a)?.let { String.format(Locale.ROOT,"%.2f",it) } ?: "N/A")
  }
  val history=listOf(listOf("Date","Teacher at recording","Class at recording","Class time","Time zone","Created UTC","Last saved UTC","Revision","Reason"))+
   sessions.map { listOf(it.date,it.teacher,it.groupLabel,it.classTime,it.zone,Instant.ofEpochMilli(it.createdAt).toString(),Instant.ofEpochMilli(it.updatedAt).toString(),it.revision.toString(),it.overrideReason) }
  val details=listOf(listOf("Date","Roll at recording","Name at recording","Status","Marked UTC"))+marks.sortedWith(compareBy({ m->sessions.single { it.id==m.sessionId }.date },{it.roll})).map { m -> listOf(sessions.single { it.id==m.sessionId }.date,m.roll,m.name,m.status,Instant.ofEpochMilli(m.markedAt).toString()) }
  val presentCol=2+days.size
  val revisionCol=7
  XlsxWriter.write(out,listOf(
   XlsxWriter.Sheet("Register",head+body,10,intCols=setOf(presentCol,presentCol+1,presentCol+2),decimalCols=setOf(presentCol+3)),
   XlsxWriter.Sheet("Recorded classes",history,intCols=setOf(revisionCol)),
   XlsxWriter.Sheet("Recorded marks",details)))
 }
}
