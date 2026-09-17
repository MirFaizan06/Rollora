package design.techbytes.rollora.data
import androidx.room.*

@Dao
interface RolloraDao {
 @Query("SELECT * FROM groups ORDER BY archived, batch DESC, subject") suspend fun groups(): List<ClassGroup>
 @Query("SELECT * FROM students ORDER BY rollKey") suspend fun students(): List<Student>
 @Query("SELECT * FROM sessions ORDER BY date DESC") suspend fun sessions(): List<Session>
 @Query("SELECT * FROM marks") suspend fun marks(): List<Mark>
 @Query("SELECT * FROM holidays ORDER BY date") suspend fun holidays(): List<Holiday>
 @Query("SELECT * FROM audit ORDER BY at DESC") suspend fun audit(): List<Audit>
 @Query("SELECT * FROM profile WHERE id=1") suspend fun profile(): Profile?
 @Upsert suspend fun group(value: ClassGroup)
 /** Session/Student are NOT plain @Upsert: Room resolves @Upsert conflicts by primary key only, so
  * inserting a new row (fresh UUID) that collides with an existing row only on a secondary unique
  * index (groupId+date, groupId+rollKey) would silently update zero rows instead of failing. Splitting
  * insert/update keeps UNIQUE(groupId,date) and UNIQUE(groupId,rollKey) as real persistence-level
  * backstops, not just an application-level convention. */
 @Insert suspend fun insertSession(value: Session)
 @Update suspend fun updateSession(value: Session)
 @Insert suspend fun insertSessions(values: List<Session>)
 @Insert suspend fun insertStudent(value: Student)
 @Update suspend fun updateStudent(value: Student)
 @Insert suspend fun insertStudents(values: List<Student>)
 @Upsert suspend fun marks(value: List<Mark>)
 @Upsert suspend fun holiday(value: Holiday)
 @Insert suspend fun audit(value: Audit)
 @Upsert suspend fun profile(value: Profile)
 @Query("DELETE FROM holidays WHERE id=:id") suspend fun deleteHoliday(id: String)
 @Query("DELETE FROM marks") suspend fun clearMarks()
 @Query("DELETE FROM sessions") suspend fun clearSessions()
 @Query("DELETE FROM students") suspend fun clearStudents()
 @Query("DELETE FROM holidays") suspend fun clearHolidays()
 @Query("DELETE FROM groups") suspend fun clearGroups()
 @Query("DELETE FROM audit") suspend fun clearAudit()
 @Query("DELETE FROM profile") suspend fun clearProfile()
}
@Database(entities = [ClassGroup::class, Student::class, Session::class, Mark::class, Holiday::class, Audit::class, Profile::class], version = 1, exportSchema = true)
abstract class RolloraDatabase : RoomDatabase() { abstract fun dao(): RolloraDao }
