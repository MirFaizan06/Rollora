package design.techbytes.rollora
import android.app.Application
import androidx.room.Room
import androidx.work.*
import design.techbytes.rollora.data.*
import design.techbytes.rollora.security.AppSecurity
import design.techbytes.rollora.transfer.BackupWorker
import design.techbytes.rollora.transfer.Backups
import java.util.concurrent.TimeUnit

class RolloraApp: Application() {
 val db by lazy { Room.databaseBuilder(this,RolloraDatabase::class.java,"rollora.db").build() }
 val repo by lazy { Repository(db) }
 val security by lazy { AppSecurity(this) }
 val backupManager by lazy { Backups(this,repo,security) }
 override fun onCreate() {
  super.onCreate()
  WorkManager.getInstance(this).enqueueUniquePeriodicWork("local-backup",ExistingPeriodicWorkPolicy.KEEP,
   PeriodicWorkRequestBuilder<BackupWorker>(24,TimeUnit.HOURS).build())
 }
}
