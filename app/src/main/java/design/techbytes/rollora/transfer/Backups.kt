package design.techbytes.rollora.transfer
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import design.techbytes.rollora.RolloraApp
import design.techbytes.rollora.data.*
import design.techbytes.rollora.security.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

class Backups(private val context: Context,private val repo: Repository,private val security: AppSecurity) {
 private val dir=File(context.noBackupFilesDir,"snapshots").apply { mkdirs() }
 private val gate=Mutex()
 suspend fun auto(): File? = gate.withLock {
  val snap=repo.snapshot(); if(snap.profile.teacher.isBlank()) return@withLock null
  val file=File(dir,"snapshot-${System.currentTimeMillis()}-${newId().take(8)}.local")
  atomic(file,Crypto.encrypt(SnapshotCodec.encode(snap),security.localKey()))
  list().drop(14).forEach { it.delete() }; file
 }
 fun list(): List<File> = dir.listFiles()?.filter { it.extension=="local" }?.sortedByDescending { it.name } ?: emptyList()
 suspend fun portable(pass: CharArray): ByteArray = try { Crypto.portable(SnapshotCodec.encode(repo.snapshot()),pass) } finally { pass.fill('\u0000') }
 fun inspect(bytes: ByteArray,pass: CharArray): Snapshot = try { SnapshotCodec.decode(Crypto.openPortable(bytes,pass)) } finally { pass.fill('\u0000') }
 fun inspectLocal(file: File): Snapshot {
  require(file.canonicalFile.parentFile==dir.canonicalFile)
  return SnapshotCodec.decode(Crypto.decrypt(file.readBytes(),security.localKey()))
 }
 /** A pre-restore snapshot is mandatory: if it cannot be written, the restore is refused and nothing changes.
  * The caller (AppViewModel.write) already takes its own post-op snapshot on success, so no snapshot is
  * taken here after a successful restore — that would risk reporting a successful restore as a failure
  * if that redundant snapshot write happened to fail. */
 suspend fun restore(s: Snapshot) { checkNotNull(auto()) { "Could not create the required pre-restore backup. Set a teacher name first." }; repo.restore(s) }
 companion object {
  fun atomic(file: File,bytes: ByteArray) {
   val temp=File(file.parentFile,file.name+".tmp")
   try { FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }; check(temp.renameTo(file)) { "Could not save file." } }
   finally { temp.delete() }
  }
 }
}
class BackupWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
 override suspend fun doWork(): Result = try { (applicationContext as RolloraApp).backupManager.auto(); Result.success() } catch (_: Exception) { Result.retry() }
}
