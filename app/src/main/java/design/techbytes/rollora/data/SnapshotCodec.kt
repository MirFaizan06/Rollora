package design.techbytes.rollora.data
import org.json.JSONObject
import org.json.JSONArray
import design.techbytes.rollora.domain.Rules
import java.time.ZoneId

/** Explicit schema; no reflection, executable payloads or untrusted SQL. */
object SnapshotCodec {
 fun encode(s: Snapshot): ByteArray = JSONObject().put("format",1).put("createdAt",System.currentTimeMillis())
  .put("groups",JSONArray(s.groups.map { encode(it) }))
  .put("students",JSONArray(s.students.map { encode(it) }))
  .put("sessions",JSONArray(s.sessions.map { encode(it) }))
  .put("marks",JSONArray(s.marks.map { encode(it) }))
  .put("holidays",JSONArray(s.holidays.map { encode(it) }))
  .put("audit",JSONArray(s.audit.map { encode(it) }))
  .put("profile",encode(s.profile)).toString().toByteArray(Charsets.UTF_8)
 private fun encode(v: ClassGroup) = JSONObject().put("id",v.id).put("batch",v.batch).put("semester",v.semester).put("type",v.type).put("component",v.component).put("subject",v.subject).put("defaultTime",v.defaultTime).put("archived",v.archived)
 private fun decodeClassGroup(o: JSONObject) = ClassGroup(id=o.getString("id"),batch=o.getString("batch"),semester=o.getString("semester"),type=o.getString("type"),component=o.getString("component"),subject=o.getString("subject"),defaultTime=o.getString("defaultTime"),archived=o.getBoolean("archived"))
 private fun encode(v: Student) = JSONObject().put("id",v.id).put("groupId",v.groupId).put("roll",v.roll).put("rollKey",v.rollKey).put("name",v.name).put("joined",v.joined).put("left",v.left ?: JSONObject.NULL)
 private fun decodeStudent(o: JSONObject) = Student(id=o.getString("id"),groupId=o.getString("groupId"),roll=o.getString("roll"),rollKey=o.getString("rollKey"),name=o.getString("name"),joined=o.getString("joined"),left=if(o.isNull("left")) null else o.getString("left"))
 private fun encode(v: Session) = JSONObject().put("id",v.id).put("groupId",v.groupId).put("date",v.date).put("classTime",v.classTime).put("createdAt",v.createdAt).put("updatedAt",v.updatedAt).put("zone",v.zone).put("teacher",v.teacher).put("groupLabel",v.groupLabel).put("finalised",v.finalised).put("cancelled",v.cancelled).put("revision",v.revision).put("overrideReason",v.overrideReason)
 private fun decodeSession(o: JSONObject) = Session(id=o.getString("id"),groupId=o.getString("groupId"),date=o.getString("date"),classTime=o.getString("classTime"),createdAt=o.getLong("createdAt"),updatedAt=o.getLong("updatedAt"),zone=o.getString("zone"),teacher=o.getString("teacher"),groupLabel=o.getString("groupLabel"),finalised=o.getBoolean("finalised"),cancelled=o.getBoolean("cancelled"),revision=o.getInt("revision"),overrideReason=o.getString("overrideReason"))
 private fun encode(v: Mark) = JSONObject().put("sessionId",v.sessionId).put("studentId",v.studentId).put("roll",v.roll).put("name",v.name).put("status",v.status).put("markedAt",v.markedAt)
 private fun decodeMark(o: JSONObject) = Mark(sessionId=o.getString("sessionId"),studentId=o.getString("studentId"),roll=o.getString("roll"),name=o.getString("name"),status=o.getString("status"),markedAt=o.getLong("markedAt"))
 private fun encode(v: Holiday) = JSONObject().put("id",v.id).put("groupId",v.groupId ?: JSONObject.NULL).put("date",v.date).put("title",v.title)
 private fun decodeHoliday(o: JSONObject) = Holiday(id=o.getString("id"),groupId=if(o.isNull("groupId")) null else o.getString("groupId"),date=o.getString("date"),title=o.getString("title"))
 private fun encode(v: Audit) = JSONObject().put("id",v.id).put("entityId",v.entityId).put("at",v.at).put("action",v.action).put("detail",v.detail)
 private fun decodeAudit(o: JSONObject) = Audit(id=o.getString("id"),entityId=o.getString("entityId"),at=o.getLong("at"),action=o.getString("action"),detail=o.getString("detail"))
 private fun encode(v: Profile) = JSONObject().put("id",v.id).put("teacher",v.teacher).put("college",v.college).put("theme",v.theme).put("biometrics",v.biometrics)
 private fun decodeProfile(o: JSONObject) = Profile(id=o.getInt("id"),teacher=o.getString("teacher"),college=o.getString("college"),theme=o.getString("theme"),biometrics=o.getBoolean("biometrics"))
 private fun <T> read(o: JSONObject, key: String, decoder: (JSONObject)->T): List<T> {
  val a = o.getJSONArray(key); require(a.length() <= 300000) { "Backup is too large." }
  return (0 until a.length()).map { decoder(a.getJSONObject(it)) }
 }
 fun decode(bytes: ByteArray): Snapshot {
  require(bytes.size <= 32*1024*1024) { "Backup exceeds the 32 MiB safety limit." }
  val o = JSONObject(bytes.toString(Charsets.UTF_8)); require(o.getInt("format") == 1) { "Unsupported backup version. Update Rollora first." }
  val s = Snapshot(
   groups=read(o,"groups",::decodeClassGroup),
   students=read(o,"students",::decodeStudent),
   sessions=read(o,"sessions",::decodeSession),
   marks=read(o,"marks",::decodeMark),
   holidays=read(o,"holidays",::decodeHoliday),
   audit=read(o,"audit",::decodeAudit),
   profile=decodeProfile(o.getJSONObject("profile")))
  validate(s); return s
 }

 fun validate(s: Snapshot) {
  fun unique(ids: List<String>) { require(ids.size == ids.toSet().size && ids.all { runCatching { java.util.UUID.fromString(it) }.isSuccess }) { "Invalid or duplicate record IDs." } }
  unique(s.groups.map { it.id }); unique(s.students.map { it.id }); unique(s.sessions.map { it.id }); unique(s.holidays.map { it.id }); unique(s.audit.map { it.id })
  require(s.groups.size <= 500 && s.students.size <= 50000 && s.sessions.size <= 50000)
  val groups = s.groups.associateBy { it.id }; val students = s.students.associateBy { it.id }; val sessions = s.sessions.associateBy { it.id }
  require(s.profile.id == 1 && s.profile.theme in listOf("dark","light","system"))
  Rules.text(s.profile.teacher,"Teacher name"); require(s.profile.college.length <= 150)
  s.groups.forEach { Rules.text(it.batch,"Batch",30); Rules.text(it.semester,"Semester",30); Rules.text(it.type,"Type",30); Rules.text(it.subject,"Subject"); require(it.component.length<=30); Rules.time(it.defaultTime) }
  require(s.students.map { it.groupId to it.rollKey }.toSet().size == s.students.size)
  s.students.forEach { require(it.groupId in groups); Rules.roll(it.roll); require(it.rollKey==Rules.rollKey(it.roll)); Rules.text(it.name,"Name"); Rules.date(it.joined); it.left?.let { end -> require(Rules.date(end)>=Rules.date(it.joined)) } }
  require(s.sessions.map { it.groupId to it.date }.toSet().size == s.sessions.size)
  s.sessions.forEach { require(it.groupId in groups && it.revision>0 && it.createdAt>0 && it.updatedAt>0); Rules.date(it.date); Rules.time(it.classTime); ZoneId.of(it.zone); Rules.text(it.teacher,"Teacher"); Rules.text(it.groupLabel,"Class label",400); require(it.overrideReason.length<=300) }
  require(s.marks.map { it.sessionId to it.studentId }.toSet().size==s.marks.size)
  s.marks.forEach { m ->
   val session=sessions[m.sessionId] ?: error("Orphan attendance entry.")
   require(students[m.studentId]?.groupId==session.groupId && m.markedAt>=0)
   Rules.roll(m.roll); Rules.text(m.name,"Name"); require(m.status in listOf("","P","A","L"))
   if(session.finalised) require(m.status.isNotEmpty())
  }
  val bySession=s.marks.groupBy { it.sessionId }
  s.sessions.forEach { require(!bySession[it.id].isNullOrEmpty()) { "Attendance session has no roster." } }
  s.holidays.forEach { require(it.groupId==null || it.groupId in groups); Rules.date(it.date); Rules.text(it.title,"Holiday") }
  require(s.holidays.map { it.groupId to it.date }.toSet().size==s.holidays.size)
  s.audit.forEach { require(it.at>0 && it.action.length<=50 && it.detail.length<=10000 && it.entityId.length<=100) }
 }
}
