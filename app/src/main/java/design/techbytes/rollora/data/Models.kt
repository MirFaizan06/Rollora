package design.techbytes.rollora.data

import androidx.room.*
import java.util.UUID
fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "groups")
data class ClassGroup(
 @PrimaryKey val id: String = newId(), val batch: String, val semester: String,
 val type: String, val component: String, val subject: String,
 val defaultTime: String, val archived: Boolean = false
) { fun title() = listOf(type, component, subject).filter { it.isNotBlank() }.joinToString(" · ") }

@Entity(tableName = "students", indices = [Index(value = ["groupId", "rollKey"], unique = true)],
 foreignKeys = [ForeignKey(entity = ClassGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.RESTRICT)])
data class Student(
 @PrimaryKey val id: String = newId(), val groupId: String, val roll: String,
 val rollKey: String, val name: String, val joined: String, val left: String? = null
)

@Entity(tableName = "sessions", indices = [Index(value = ["groupId", "date"], unique = true)],
 foreignKeys = [ForeignKey(entity = ClassGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.RESTRICT)])
data class Session(
 @PrimaryKey val id: String = newId(), val groupId: String, val date: String,
 val classTime: String, val createdAt: Long, val updatedAt: Long, val zone: String,
 val teacher: String, val groupLabel: String, val finalised: Boolean = false,
 val cancelled: Boolean = false, val revision: Int = 0, val overrideReason: String = ""
)

@Entity(tableName = "marks", primaryKeys = ["sessionId", "studentId"], indices = [Index("studentId")],
 foreignKeys = [ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
 ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)])
data class Mark(val sessionId: String, val studentId: String, val roll: String, val name: String,
 val status: String, val markedAt: Long)

@Entity(tableName = "holidays", indices = [Index("groupId")],
 foreignKeys = [ForeignKey(entity = ClassGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.RESTRICT)])
data class Holiday(@PrimaryKey val id: String = newId(), val groupId: String?, val date: String, val title: String)

@Entity(tableName = "audit")
data class Audit(@PrimaryKey val id: String = newId(), val entityId: String, val at: Long,
 val action: String, val detail: String)

@Entity(tableName = "profile")
data class Profile(@PrimaryKey val id: Int = 1, val teacher: String = "", val college: String = "",
 val theme: String = "system", val biometrics: Boolean = false)

data class Snapshot(val groups: List<ClassGroup> = emptyList(), val students: List<Student> = emptyList(),
 val sessions: List<Session> = emptyList(), val marks: List<Mark> = emptyList(),
 val holidays: List<Holiday> = emptyList(), val audit: List<Audit> = emptyList(), val profile: Profile = Profile())
