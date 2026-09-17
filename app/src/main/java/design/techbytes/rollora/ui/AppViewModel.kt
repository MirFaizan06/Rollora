package design.techbytes.rollora.ui
import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import design.techbytes.rollora.*
import design.techbytes.rollora.data.*
import design.techbytes.rollora.domain.Rules
import design.techbytes.rollora.transfer.*
import design.techbytes.rollora.security.Crypto
import design.techbytes.rollora.update.Release
import design.techbytes.rollora.update.Updater
import design.techbytes.rollora.update.readBytesLimited
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

data class Draft(val groupId: String,val date: String,val time: String,val revision: Int?,val wasFinal: Boolean,
 val roster: List<Student>,val marks: Map<String,String>,val times: Map<String,Long>,val reason: String="",val dirty: Boolean=false)
class AppViewModel(application: Application): AndroidViewModel(application) {
 val app=application as RolloraApp
 var data by mutableStateOf(Snapshot()); private set
 var loaded by mutableStateOf(false); private set
 var loadError by mutableStateOf<String?>(null); private set
 var unlocked by mutableStateOf(false)
 var busy by mutableStateOf(false); private set
 var notice by mutableStateOf<String?>(null)
 var tab by mutableIntStateOf(0)
 var groupId by mutableStateOf<String?>(null)
 var draft by mutableStateOf<Draft?>(null)
 var output by mutableStateOf<File?>(null)
 var pendingRestore by mutableStateOf<Snapshot?>(null)
 var release by mutableStateOf<Release?>(null)
 var downloadProgress by mutableStateOf<Float?>(null)
 var updateFile by mutableStateOf<File?>(null)
 var updateStatus by mutableStateOf("")
 var localBackups by mutableStateOf<List<File>>(emptyList())
 private var downloadJob: Job?=null
 private val githubUpdater = Updater(app)
 private val tutorialPrefs by lazy { app.getSharedPreferences("tutorial",android.content.Context.MODE_PRIVATE) }
 private val speechLazy = lazy { TutorialSpeech(app) }
 private val speech by speechLazy
 var tutorialStep by mutableStateOf<Int?>(null); private set
 var voiceGuidanceEnabled by mutableStateOf(true); private set
 val voiceAvailable: Boolean get() = speech.available
 init { refresh(); checkUpdate(false); voiceGuidanceEnabled=tutorialPrefs.getBoolean("voice",true) }
 override fun onCleared() { super.onCleared(); if(speechLazy.isInitialized()) speech.shutdown() }
 fun say(text: String) { notice=text }
 fun maybeOfferTutorialOnFirstRun() { if(tutorialStep==null && !tutorialPrefs.getBoolean("seen",false)) startTutorial() }
 fun startTutorial() { tutorialStep=0; speakCurrentStep() }
 fun nextTutorialStep() { tutorialStep=((tutorialStep ?: 0)+1).coerceAtMost(Tutorial.steps.lastIndex); speakCurrentStep() }
 fun previousTutorialStep() { tutorialStep=((tutorialStep ?: 0)-1).coerceAtLeast(0); speakCurrentStep() }
 fun replayTutorialStep() { tutorialStep?.let { Tutorial.steps.getOrNull(it) }?.let { speech.speak("${it.title}. ${it.body}") } }
 fun toggleVoiceGuidance() {
  voiceGuidanceEnabled=!voiceGuidanceEnabled
  tutorialPrefs.edit { putBoolean("voice",voiceGuidanceEnabled) }
  if(!voiceGuidanceEnabled) speech.stop() else replayTutorialStep()
 }
 fun skipTutorial() { speech.stop(); tutorialStep=null; tutorialPrefs.edit { putBoolean("seen",true) } }
 fun finishTutorial() = skipTutorial()
 private fun speakCurrentStep() { if(voiceGuidanceEnabled) replayTutorialStep() }
 private fun failure(e: Throwable) { if(e is CancellationException) throw e; say(e.message ?: "Operation failed. Your saved data is unchanged.") }
 fun refresh() { viewModelScope.launch { try {
  data=withContext(Dispatchers.IO) { app.repo.snapshot() }; localBackups=withContext(Dispatchers.IO) { app.backupManager.list() }; loaded=true; loadError=null
 } catch(e: Exception) { loadError="Could not open local records. Nothing was reset. Retry or contact your app maintainer with this message: ${e.javaClass.simpleName}"; failure(e) } } }
 fun write(label: String,after: ()->Unit={},op: suspend ()->Unit) {
  if(busy) return
  busy=true
  viewModelScope.launch {
   try {
    withContext(Dispatchers.IO) { op() }
    data=withContext(Dispatchers.IO) { app.repo.snapshot() }
    val backupOk=withContext(Dispatchers.IO) { runCatching { app.backupManager.auto() }.isSuccess }
    localBackups=withContext(Dispatchers.IO) { app.backupManager.list() }; after()
    say(if(backupOk) label else "$label Automatic backup failed; make a portable backup soon.")
   } catch(e: Exception) { failure(e) } finally { busy=false }
  }
 }
 fun initialise(teacher: String,college: String,pin: String,confirm: String) {
  if(busy) return
  write("Your local workspace is ready.", { unlocked=true }) {
   require(pin==confirm) { "PINs do not match." }; Rules.text(teacher,"Teacher name")
   require(pin.matches(Regex("[0-9]{6}"))) { "Choose a six-digit PIN." }
   app.repo.profile(Profile(teacher=teacher.trim(),college=college.trim()))
   app.security.setPin(pin)
  }
 }
 fun unlock(pin: String) {
  if(busy) return
  busy=true
  viewModelScope.launch { try {
   val ok=withContext(Dispatchers.IO) { app.security.verify(pin) }
   if(ok) unlocked=true else say("Incorrect PIN.")
  } catch(e: Exception) { failure(e) } finally { busy=false } }
 }
 fun changePin(old: String,next: String,confirm: String) = write("PIN updated.") {
  require(app.security.verify(old)) { "Current PIN is incorrect." }; require(next==confirm) { "New PINs do not match." }; app.security.setPin(next)
 }
 fun openAttendance(group: ClassGroup,day: String) {
  try {
   require(!group.archived) { "Unarchive this group before recording attendance." }
   require(Rules.date(day)<=LocalDate.now()) { "Choose today or an earlier date." }
   val session=data.sessions.singleOrNull { it.groupId==group.id && it.date==day }
   require(session?.cancelled!=true) { "Restore this cancelled class from History first." }
   val marks=data.marks.filter { it.sessionId==session?.id }
   val roster=if(session!=null) marks.map { m->data.students.single { it.id==m.studentId }.copy(roll=m.roll,name=m.name) } else data.students.filter { it.groupId==group.id && Rules.eligible(day,it.joined,it.left) }
   require(roster.isNotEmpty()) { "Add students whose joining date is on or before this class date." }
   draft=Draft(group.id,day,session?.classTime ?: group.defaultTime,session?.revision,session?.finalised ?: false,roster.sortedBy { it.rollKey },marks.associate { it.studentId to it.status },marks.associate { it.studentId to it.markedAt })
  } catch(e: Exception) { failure(e) }
 }
 fun mark(id: String,status: String) { if(busy) return; draft=draft?.let { it.copy(marks=it.marks+(id to status),times=it.times+(id to System.currentTimeMillis()),dirty=true) } }
 fun allPresent() { draft=draft?.let { d->val now=System.currentTimeMillis(); d.copy(marks=d.roster.associate { it.id to "P" },times=d.roster.associate { it.id to now },dirty=true) } }
 fun saveAttendance(finalise: Boolean) {
  val d=draft ?: return
  write(if(finalise) "Attendance saved." else "Draft saved.",{ draft=null }) {
   app.repo.saveAttendance(d.groupId,d.date,d.time,d.marks,d.times,finalise,d.revision,d.reason)
  }
 }
 private fun shared(name: String): File {
  val dir=File(app.cacheDir,"shared").apply { mkdirs() }
  dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>86400000L }?.forEach { it.delete() }
  return File(dir,name)
 }
 fun export(group: ClassGroup,from: String,to: String) {
  if(busy) return; busy=true
  viewModelScope.launch { try {
   output=withContext(Dispatchers.IO) {
    val snap=app.repo.snapshot(); Rules.validateRange(from,to)
    shared("Rollora-${group.id.take(8)}-$from-$to.xlsx").also { file ->
     val tmp=File(file.parentFile,file.name+".tmp")
     try { tmp.outputStream().use { RegisterExport.write(it,snap,group,from,to) }; check(tmp.renameTo(file)) } finally { tmp.delete() }
    }
   }
  } catch(e: Exception) { failure(e) } finally { busy=false } }
 }
 fun backup(pass: String,confirm: String) {
  if(busy) return; busy=true
  viewModelScope.launch { try {
   require(pass==confirm) { "Passphrases do not match." }
   output=withContext(Dispatchers.IO) { shared("Rollora-${System.currentTimeMillis()}.rollora").also { Backups.atomic(it,app.backupManager.portable(pass.toCharArray())) } }
  } catch(e: Exception) { failure(e) } finally { busy=false } }
 }
 fun stageRestore(uri: Uri,pass: String) {
  if(busy) return; busy=true
  viewModelScope.launch { try {
   pendingRestore=withContext(Dispatchers.IO) {
    val bytes=app.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(Crypto.MAX_BYTES+128) } ?: error("Could not open the backup.")
    try { app.backupManager.inspect(bytes,pass.toCharArray()) } catch(e: javax.crypto.AEADBadTagException) { error("Wrong passphrase or damaged backup.") }
   }
  } catch(e: Exception) { failure(e) } finally { busy=false } }
 }
 fun stageLocal(file: File) {
  if(busy) return; busy=true
  viewModelScope.launch { try { pendingRestore=withContext(Dispatchers.IO) { app.backupManager.inspectLocal(file) } }
   catch(e: Exception) { failure(e) } finally { busy=false } }
 }
 fun restore() {
  val snap=pendingRestore ?: return
  write("Backup restored. Your current PIN is unchanged.",{ pendingRestore=null; draft=null; groupId=null; tab=0 }) {
   app.backupManager.restore(snap.copy(profile=snap.profile.copy(biometrics=false)))
  }
 }
 fun saveOutput(uri: Uri,file: File) {
  viewModelScope.launch { try {
   withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri,"wt")?.use { out->file.inputStream().use { it.copyTo(out) } } ?: error("Cannot write to this location.") }
   output=null; say("File saved. Keep a copy on another device or Drive.")
  } catch(e: Exception) { failure(e) } }
 }
 fun checkUpdate(manual: Boolean) {
  if(BuildConfig.UPDATE_REPO.isBlank()) { if(manual) say("Update repository is not configured in this build."); return }
  viewModelScope.launch { try {
   updateStatus="Checking GitHub…"; release=withContext(Dispatchers.IO) { githubUpdater.check() }
   updateStatus=if(release==null) "No compatible update found." else "${release!!.name} is available."
   if(manual && release==null) say(updateStatus)
  } catch(e: Exception) { if(e is CancellationException) throw e; updateStatus="Could not check updates. Local attendance remains available."; if(manual) say(updateStatus) } }
 }
 fun downloadUpdate() {
  val r=release ?: return; if(downloadJob?.isActive==true) return
  downloadJob=viewModelScope.launch { try {
   downloadProgress=0f; updateFile=null
   withContext(Dispatchers.IO) { app.backupManager.auto() }
   updateFile=withContext(Dispatchers.IO) { githubUpdater.download(r) { progress->downloadProgress=progress } }
   updateStatus="Verified. Ready for Android installation."; downloadProgress=null
  } catch(e: Exception) { if(e !is CancellationException) failure(e) } finally { downloadProgress=null } }
 }
 fun cancelDownload() { downloadJob?.cancel(); updateStatus="Download cancelled." }
 fun installUpdate() { updateFile?.let { file->try { if(!githubUpdater.install(file)) say("Allow installs from Rollora, return here, then tap Install again.") } catch(e: Exception) { failure(e) } } }
}
