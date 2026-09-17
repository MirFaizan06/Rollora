package design.techbytes.rollora.ui
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import design.techbytes.rollora.BuildConfig
import design.techbytes.rollora.data.*
import design.techbytes.rollora.domain.Rules
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun GroupPicker(vm: AppViewModel,selected: String?,change: (String?)->Unit,allLabel: String="All classes") {
 var open by remember { mutableStateOf(false) }
 Box {
  OutlinedButton(onClick={open=true}){Text(selected?.let { id->vm.data.groups.find { it.id==id }?.title() } ?: allLabel)}
  DropdownMenu(expanded=open,onDismissRequest={open=false}) {
   DropdownMenuItem(text={Text(allLabel)},onClick={change(null);open=false})
   vm.data.groups.forEach { group->DropdownMenuItem(text={Text("${group.title()} · ${group.batch}/${group.semester}")},onClick={change(group.id);open=false}) }
  }
 }
}
@Composable fun StatsScreen(vm: AppViewModel) {
 var group by rememberSaveable { mutableStateOf<String?>(null) }
 var from by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }; var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
 val range=runCatching { Rules.validateRange(from,to) }
 val sessions=if(range.isSuccess) vm.data.sessions.filter { (group==null || it.groupId==group) && it.date>=from && it.date<=to && it.finalised && !it.cancelled } else emptyList()
 val ids=sessions.map { it.id }.toSet();val marks=vm.data.marks.filter { it.sessionId in ids }
 val p=marks.count { it.status=="P" };val a=marks.count { it.status=="A" };val l=marks.count { it.status=="L" }
 fun pct(p: Int,a: Int)=Rules.percentage(p,a)?.let { String.format(Locale.getDefault(),"%.1f%%",it) } ?: "N/A"
 ScreenList {
  item { Heading("Attendance insights","Based on classes actually conducted") }
  item { Panel { GroupPicker(vm,group,{group=it});Field("From (YYYY-MM-DD)",from,{from=it});Field("To (YYYY-MM-DD)",to,{to=it});if(range.isFailure) Hint(range.exceptionOrNull()?.message ?: "Invalid dates") } }
  item { Panel {
   Text(pct(p,a),fontSize=42.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
   Text("${sessions.size} conducted classes",fontWeight=FontWeight.Bold)
   Text("$p Present  ·  $a Absent  ·  $l Leave")
   Hint("Percentage = Present ÷ (Present + Absent). Leave, drafts and cancelled classes are excluded. Across classes this is weighted by recorded student marks.")
  } }
  item { Text("Student breakdown",style=MaterialTheme.typography.titleLarge) }
  val byStudent=marks.groupBy { it.studentId }
  if(byStudent.isEmpty()) item { EmptyState("No attendance in this period","Save a final register to see statistics here.") }
  items(vm.data.students.filter { it.id in byStudent },key={it.id}) { student ->
   val own=byStudent[student.id].orEmpty();val present=own.count { it.status=="P" };val absent=own.count { it.status=="A" }
   Panel {
    Row { Column(Modifier.weight(1f)){Text(student.name,fontWeight=FontWeight.Bold);Hint(student.roll)};Text(pct(present,absent),fontWeight=FontWeight.Bold) }
    LinearProgressIndicator(progress={ (Rules.percentage(present,absent)?.toFloat() ?: 0f)/100 },modifier=Modifier.fillMaxWidth())
    Hint("$present P · $absent A · ${own.count { it.status=="L" }} L · ${vm.data.groups.find { it.id==student.groupId }?.title()}")
   }
  }
 }
}
@Composable fun CalendarScreen(vm: AppViewModel) {
 var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) };val month=YearMonth.parse(monthText)
 var scope by rememberSaveable { mutableStateOf<String?>(null) };var add by remember { mutableStateOf(false) };var edit by remember { mutableStateOf<Holiday?>(null) };var remove by remember { mutableStateOf<Holiday?>(null) }
 val holidays=vm.data.holidays.filter { it.date.startsWith(monthText) && (scope==null || it.groupId==null || it.groupId==scope) }
 if(add || edit!=null) HolidayForm(vm,edit,scope){add=false;edit=null}
 remove?.let { holiday->FormDialog("Remove holiday?",{remove=null},"Remove",!vm.busy,{vm.write("Holiday removed.",{remove=null}){vm.app.repo.deleteHoliday(holiday.id)}}){Text("${holiday.date}: ${holiday.title}. Saved attendance is never changed by calendar edits.")} }
 ScreenList {
  item { Heading("Calendar","Sundays and your college holidays") {FilledTonalIconButton(onClick={add=true}){Glyph("plus")}} }
  item { Panel {
   GroupPicker(vm,scope,{scope=it})
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={monthText=month.minusMonths(1).toString()}){Text("Previous")};Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy")),fontWeight=FontWeight.Bold);TextButton(onClick={monthText=month.plusMonths(1).toString()}){Text("Next")}}
   Row { listOf("M","T","W","T","F","S","S").forEach { label->Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Hint(label)} } }
   val start=month.atDay(1).dayOfWeek.value-1
   val cells=List(start){0}+(1..month.lengthOfMonth()).toList()
   cells.chunked(7).forEach { week->Row {
    (0..6).forEach { i->val day=week.getOrNull(i) ?: 0
     val date=if(day>0) month.atDay(day) else null
     val holiday=holidays.any { it.date==date?.toString() };val sunday=date?.dayOfWeek==DayOfWeek.SUNDAY
     Surface(Modifier.weight(1f).padding(2.dp).heightIn(min=40.dp),color=if(holiday) MaterialTheme.colorScheme.secondaryContainer else if(sunday) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.small) {
      Box(contentAlignment=Alignment.Center){Text(if(day==0) "" else day.toString(),color=if(holiday) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)}
     }
    }
   } }
   Hint("Tinted cells mark Sundays and listed holidays. Holidays do not create or delete attendance records.")
  } }
  if(holidays.isEmpty()) item { EmptyState("No added holidays this month","Sundays are calculated automatically. Add holidays for all groups or one class.") }
  items(holidays,key={it.id}) { h->Panel {
   Text(h.title,fontWeight=FontWeight.Bold);Hint("${h.date} · ${h.groupId?.let { id->vm.data.groups.find { it.id==id }?.title() } ?: "All classes"}")
   Row{TextButton(onClick={edit=h}){Text("Edit")};TextButton(onClick={remove=h}){Text("Remove")}}
  } }
 }
}
@Composable private fun HolidayForm(vm: AppViewModel,old: Holiday?,scope: String?,dismiss: ()->Unit) {
 var date by remember { mutableStateOf(old?.date ?: LocalDate.now().toString()) };var title by remember { mutableStateOf(old?.title ?: "") };var group by remember { mutableStateOf(if(old!=null) old.groupId else scope) }
 FormDialog(if(old==null) "Add holiday" else "Edit holiday",dismiss,enabled=!vm.busy,onConfirm={vm.write("Holiday saved.",dismiss){vm.app.repo.saveHoliday(Holiday(old?.id ?: newId(),group,date.trim(),title.trim()))}}) {
  Field("Date (YYYY-MM-DD)",date,{date=it});Field("Holiday name",title,{title=it});GroupPicker(vm,group,{group=it},"Global · all classes")
  Hint("Already conducted classes still count. To exclude a class, explicitly cancel that register from its History.")
 }
}
@Composable fun SettingsScreen(vm: AppViewModel) {
 var profile by remember { mutableStateOf(false) };var pin by remember { mutableStateOf(false) };var backup by remember { mutableStateOf(false) }
 var restoring by remember { mutableStateOf(false) };var restoreUri by remember { mutableStateOf<Uri?>(null) };var local by remember { mutableStateOf(false) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){restoreUri=uri;restoring=true}}
 if(profile) ProfileForm(vm){profile=false}
 if(pin) PinForm(vm){pin=false}
 if(backup) BackupForm(vm){backup=false}
 if(restoring) {
  var pass by remember { mutableStateOf("") }
  FormDialog("Open encrypted backup",{restoring=false;restoreUri=null},"Validate backup",!vm.busy,{
   restoreUri?.let { vm.stageRestore(it,pass) };pass="";restoring=false;restoreUri=null
  }) { Field("Backup passphrase",pass,{pass=it},true);Hint("Validation runs before any replacement. You will review the backup and confirm restore next.") }
 }
 if(local) AlertDialog(onDismissRequest={local=false},title={Text("Local recovery snapshots")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Hint("These snapshots work only on this installation. Use a portable backup to transfer to another device.")
  vm.localBackups.forEach { file->TextButton(onClick={vm.stageLocal(file);local=false}){Text(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss")))}}
  if(vm.localBackups.isEmpty()) Text("No snapshots yet.")
 }},confirmButton={TextButton(onClick={local=false}){Text("Close")}})
 ScreenList {
  item { Heading("Workspace settings","Private by default. Yours to manage.") }
  item { Panel {
   Text(vm.data.profile.teacher,fontWeight=FontWeight.Bold,fontSize=21.sp);Hint(vm.data.profile.college.ifBlank { "Teacher workspace" })
   TextButton(onClick={profile=true}){Text("Edit teacher profile")}
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("system","dark","light").forEach { mode->FilterChip(vm.data.profile.theme==mode,{vm.write("Theme updated."){vm.app.repo.profile(vm.data.profile.copy(theme=mode))}},enabled=!vm.busy,label={Text(mode.replaceFirstChar { it.uppercase() })}) }
   }
  } }
  item { Panel {
   Text("Help & tutorial",fontWeight=FontWeight.Bold)
   Hint("A short guided tour of Rollora's workflow, with optional spoken narration.")
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Voice guidance");Hint(if(vm.voiceAvailable) "Reads each tutorial step aloud on this device." else "Not available on this device.")};if(vm.voiceAvailable) Switch(vm.voiceGuidanceEnabled,{vm.toggleVoiceGuidance()})}
   Action("Replay tutorial"){vm.startTutorial()}
  } }
  item { Panel {
   Text("Quick access",fontWeight=FontWeight.Bold)
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Biometric unlock");Hint("Uses a strong biometric enrolled on this device.")};Switch(vm.data.profile.biometrics,{enabled->vm.write("Security preference saved."){vm.app.repo.profile(vm.data.profile.copy(biometrics=enabled))}},enabled=!vm.busy)}
   TextButton(onClick={pin=true}){Text("Change PIN")};TextButton(onClick={vm.unlocked=false}){Text("Lock now")}
   Hint("Auto-lock after 30 seconds away. Screenshots and recent-app previews are protected. Keep your device screen lock enabled.")
  } }
  item { Panel {
   Text("Backups & recovery",fontWeight=FontWeight.Bold)
   Hint("All attendance is local. Fourteen encrypted recovery snapshots rotate automatically after changes and approximately daily. They are not protection against device loss or uninstalling.")
   Action("Create portable encrypted backup",!vm.busy){backup=true}
   OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("Restore portable backup")}
   TextButton(onClick={local=true}){Text("View ${vm.localBackups.size} local snapshots")}
   Hint("Share backups and Excel exports to Google Drive or another device using the system share sheet. Store your backup passphrase separately.")
  } }
  item { Panel {
   Text("App updates",fontWeight=FontWeight.Bold)
   Hint("Installed: ${BuildConfig.VERSION_NAME}")
   Hint(if(BuildConfig.UPDATE_REPO.isBlank()) "No release repository configured. Set rollora.updateRepo when building." else "Public releases: ${BuildConfig.UPDATE_REPO}")
   if(vm.updateStatus.isNotBlank()) Text(vm.updateStatus)
   TextButton(onClick={vm.checkUpdate(true)}){Text("Check GitHub now")}
   vm.release?.let { r->
    Text(r.name,fontWeight=FontWeight.Bold);Hint(r.notes)
    if(vm.downloadProgress!=null) {LinearProgressIndicator(progress={vm.downloadProgress ?: 0f},modifier=Modifier.fillMaxWidth());Text("${((vm.downloadProgress ?: 0f)*100).toInt()}% downloaded");TextButton(onClick={vm.cancelDownload()}){Text("Cancel download")}}
    else if(vm.updateFile!=null) Action("Install verified update"){vm.installUpdate()}
    else Action("Download update (${r.bytes/(1024*1024)} MB)",!vm.busy){vm.downloadUpdate()}
   }
   Hint("Android asks for installation approval. After installation, tap Open to return. Updates require the same package name and signing key; never uninstall to update.")
  } }
  item { Panel {
   Text("Rollora",fontWeight=FontWeight.Bold,fontSize=23.sp);Text("1.0.0 Beta");Text("Developer · Mir Faizan");Text("Tech Bytes Design")
   Hint("No accounts, ads, analytics or attendance uploads. GitHub receives ordinary update requests. Your chosen sharing app handles files you explicitly share.")
   Hint("Timestamps use device time and time zone. Offline records are not independently certified. App PIN protects the interface; Android's app sandbox and device encryption protect the database. Portable backups use passphrase encryption.")
  } }
 }
}
@Composable private fun ProfileForm(vm: AppViewModel,dismiss: ()->Unit) {
 var name by remember { mutableStateOf(vm.data.profile.teacher) };var college by remember { mutableStateOf(vm.data.profile.college) }
 FormDialog("Teacher profile",dismiss,enabled=!vm.busy,onConfirm={vm.write("Profile saved.",dismiss){vm.app.repo.profile(vm.data.profile.copy(teacher=name.trim(),college=college.trim()))}}){Field("Teacher name",name,{name=it});Field("College",college,{college=it})}
}
@Composable private fun PinForm(vm: AppViewModel,dismiss: ()->Unit) {
 var old by remember { mutableStateOf("") };var pin by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") }
 FormDialog("Change app PIN",dismiss,enabled=!vm.busy,onConfirm={vm.changePin(old,pin,confirm);dismiss()}){Field("Current PIN",old,{old=it.take(6)},true,true);Field("New six-digit PIN",pin,{pin=it.take(6)},true,true);Field("Confirm new PIN",confirm,{confirm=it.take(6)},true,true)}
}
@Composable private fun BackupForm(vm: AppViewModel,dismiss: ()->Unit) {
 var pass by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") }
 FormDialog("Create portable backup",dismiss,"Create backup",!vm.busy,{vm.backup(pass,confirm);pass="";confirm="";dismiss()}) {
  Field("Passphrase (12+ characters)",pass,{pass=it},true);Field("Confirm passphrase",confirm,{confirm=it},true)
  Hint("Includes profile, groups, roster, attendance, holidays, audit history and theme. App PIN and device keys are never exported. This passphrase is required to restore; there is no recovery service.")
 }
}
