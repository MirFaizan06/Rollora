package design.techbytes.rollora.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import design.techbytes.rollora.data.*
import design.techbytes.rollora.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun TodayScreen(vm: AppViewModel) {
 val today=LocalDate.now().toString(); val s=vm.data
 val active=s.groups.filter { !it.archived }.sortedBy { it.defaultTime }
 val done=s.sessions.count { it.date==today && it.finalised && !it.cancelled }
 ScreenList {
  item { Heading("Today",LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) { IconButton(onClick={vm.unlocked=false}) { Glyph("lock") } } }
  item { Panel {
   Pill("LOCAL WORKSPACE"); Text("Hello, ${s.profile.teacher}",fontSize=22.sp,fontWeight=FontWeight.Bold)
   Text("$done saved today",fontSize=34.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
   Hint("Choose a class below to begin. Each group has one register per date.")
  } }
  if(vm.release!=null) item { Panel { Text("${vm.release!!.name} is available.",fontWeight=FontWeight.Bold); TextButton(onClick={vm.tab=4}){Text("View update")}} }
  if(active.isEmpty()) item { EmptyState("Your first class starts here","Add a class group, then add students by roll number and name."); Action("Add a class"){vm.tab=1} }
  items(active,key={it.id}) { group ->
   val record=s.sessions.singleOrNull { it.groupId==group.id && it.date==today }
   Panel {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Pill(group.defaultTime); Text(record?.let { if(it.cancelled) "Cancelled" else if(it.finalised) "Saved" else "Draft" } ?: "Not recorded",style=MaterialTheme.typography.labelMedium) }
    Text(group.title(),fontWeight=FontWeight.Bold,fontSize=20.sp); Hint("Batch ${group.batch} · Semester ${group.semester}")
    vm.app.repo.dayOff(s,group.id,today)?.let { Hint("Calendar: $it. A reason is required if a class is conducted.") }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Button(onClick={vm.groupId=group.id;vm.openAttendance(group,today)},enabled=!vm.busy && record?.cancelled!=true){Text(if(record?.finalised==true) "Review attendance" else "Take attendance")}
     TextButton(onClick={vm.groupId=group.id}){Text("Manage")}
    }
   }
  }
  item { Hint("Local app · Keep encrypted backups somewhere safe outside this device.") }
 }
}
@Composable fun GroupsScreen(vm: AppViewModel) {
 var add by remember { mutableStateOf(false) }; var search by rememberSaveable { mutableStateOf("") }; var archived by rememberSaveable { mutableStateOf(false) }
 val groups=vm.data.groups.filter { (archived || !it.archived) && (it.title()+it.batch+it.semester).contains(search,true) }
 if(add) GroupForm(vm,null){add=false}
 ScreenList {
  item { Heading("Class groups","A roster for every paper and semester") { FilledTonalIconButton(onClick={add=true}){Glyph("plus")} } }
  item { Field("Search classes",search,{search=it}); Row(verticalAlignment=Alignment.CenterVertically){Switch(archived,{archived=it}); Spacer(Modifier.width(8.dp)); Text("Show archived groups")} }
  if(groups.isEmpty()) item { EmptyState("No classes here yet","Create a group with a batch, semester, course type and subject."); Action("Create class group"){add=true} }
  items(groups,key={it.id}) { g-> Panel(Modifier.clickable { vm.groupId=g.id }) {
   Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(g.title(),fontWeight=FontWeight.Bold,fontSize=19.sp);Hint("Batch ${g.batch} · Semester ${g.semester}")};if(g.archived) Pill("Archived") }
   Hint("${vm.data.students.count { it.groupId==g.id && Rules.eligible(LocalDate.now().toString(),it.joined,it.left) }} active students · ${g.defaultTime}")
  } }
 }
}
@Composable private fun GroupForm(vm: AppViewModel,old: ClassGroup?,dismiss: ()->Unit) {
 var batch by remember { mutableStateOf(old?.batch ?: LocalDate.now().year.toString()) }; var semester by remember { mutableStateOf(old?.semester ?: "1") }
 var type by remember { mutableStateOf(old?.type ?: "Major") }; var component by remember { mutableStateOf(old?.component ?: "") }
 var subject by remember { mutableStateOf(old?.subject ?: "") }; var time by remember { mutableStateOf(old?.defaultTime ?: "10:00") }
 FormDialog(if(old==null) "Create class group" else "Edit class group",dismiss,enabled=!vm.busy,onConfirm={
  vm.write("Class group saved.",dismiss) { vm.app.repo.saveGroup(ClassGroup(old?.id ?: newId(),batch.trim(),semester.trim(),type.trim(),component.trim(),subject.trim(),time.trim(),old?.archived ?: false)) }
 }) {
  Field("Batch / admission year",batch,{batch=it}); Field("Semester",semester,{semester=it})
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { Rules.courseTypes.forEach { t->FilterChip(selected=type==t,onClick={type=t},label={Text(t)}) } }
  Field("Course type (custom allowed)",type,{type=it}); Field("Component (optional: CT1 / CT2 / CT3)",component,{component=it})
  Field("Subject / paper name",subject,{subject=it}); Field("Default class time (HH:mm)",time,{time=it})
  Hint("No semester restrictions are imposed. Default time is a convenience, not evidence that a class was conducted.")
 }
}
@Composable fun GroupScreen(vm: AppViewModel,id: String) {
 val g=vm.data.groups.singleOrNull { it.id==id } ?: return
 var edit by remember { mutableStateOf(false) }; var add by remember { mutableStateOf(false) }; var bulk by remember { mutableStateOf(false) }
 var student by remember { mutableStateOf<Student?>(null) }; var exporting by remember { mutableStateOf(false) }
 var archive by remember { mutableStateOf(false) }; var day by rememberSaveable(id) { mutableStateOf(LocalDate.now().toString()) }
 var page by rememberSaveable(id) { mutableIntStateOf(0) }; var search by rememberSaveable { mutableStateOf("") }
 if(edit) GroupForm(vm,g){edit=false}
 if(add || student!=null) StudentForm(vm,g,student){add=false;student=null}
 if(bulk) BulkForm(vm,g){bulk=false}
 if(exporting) ExportForm(vm,g){exporting=false}
 if(archive) FormDialog(if(g.archived) "Unarchive class?" else "Archive class?",{archive=false},"Confirm",!vm.busy,{
  vm.write("Class updated.",{archive=false}) { vm.app.repo.saveGroup(g.copy(archived=!g.archived)) }
 }) { Text("Historical attendance remains available. Archived classes cannot receive attendance edits until unarchived.") }
 val roster=vm.data.students.filter { it.groupId==id && (it.name+it.roll).contains(search,true) }
 ScreenList {
  item { Heading(g.subject,"Batch ${g.batch} · Semester ${g.semester} · ${g.type} ${g.component}") }
  item { Panel {
   Field("Class date (YYYY-MM-DD)",day,{day=it})
   Action("Open attendance",!g.archived && !vm.busy){vm.openAttendance(g,day)}
   Row(Modifier.horizontalScroll(rememberScrollState())) { TextButton(onClick={edit=true}){Text("Edit class")};TextButton(onClick={exporting=true}){Text("Export Excel")};TextButton(onClick={archive=true}){Text(if(g.archived) "Unarchive" else "Archive")} }
  } }
  item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(page==0,{page=0},label={Text("Roster (${roster.size})")});FilterChip(page==1,{page=1},label={Text("History")}) } }
  if(page==0) {
   item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={add=true},enabled=!g.archived){Text("Add student")};OutlinedButton(onClick={bulk=true},enabled=!g.archived){Text("Paste roster")}} }
   item { Field("Search name or roll number",search,{search=it}) }
   if(roster.isEmpty()) item { EmptyState("No students yet","Add individually or paste multiple roll numbers and names. Joining dates control eligibility for new registers.") }
   items(roster,key={it.id}) { st -> Panel(Modifier.clickable(enabled=!g.archived){student=st}) {
    Text(st.name,fontWeight=FontWeight.Bold); Hint(st.roll)
    Hint("Joined ${st.joined}"+(st.left?.let { " · Inactive from $it" } ?: " · Active"))
   } }
  } else {
   val records=vm.data.sessions.filter { it.groupId==id }
   if(records.isEmpty()) item { EmptyState("No saved registers","Completed registers and saved drafts will appear here.") }
   items(records,key={it.id}) { session->HistoryCard(vm,g,session) }
  }
 }
}
@Composable private fun StudentForm(vm: AppViewModel,group: ClassGroup,old: Student?,dismiss: ()->Unit) {
 var roll by remember { mutableStateOf(old?.roll ?: "") }; var name by remember { mutableStateOf(old?.name ?: "") }
 var joined by remember { mutableStateOf(old?.joined ?: LocalDate.now().toString()) }; var left by remember { mutableStateOf(old?.left ?: "") }
 FormDialog(if(old==null) "Add student" else "Edit membership",dismiss,enabled=!vm.busy,onConfirm={vm.write("Student saved.",dismiss) {
  vm.app.repo.saveStudents(listOf(Student(old?.id ?: newId(),group.id,Rules.roll(roll),Rules.rollKey(roll),Rules.text(name,"Name"),joined.trim(),left.trim().ifBlank { null })))
 }}) {
  Field("Roll number",roll,{roll=it}); Field("Name",name,{name=it}); Field("Joining date (YYYY-MM-DD)",joined,{joined=it}); Field("Inactive from (optional, YYYY-MM-DD)",left,{left=it})
  Hint("The inactive date is excluded from new registers. Past saved registers keep the original roster and names. Clear the inactive date to reactivate this membership.")
 }
}
@Composable private fun BulkForm(vm: AppViewModel,group: ClassGroup,dismiss: ()->Unit) {
 var text by rememberSaveable { mutableStateOf("") }; var joined by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
 FormDialog("Paste students",dismiss,"Import all",!vm.busy,{
  vm.write("Roster imported.",dismiss) {
   val rows=text.lines().filter { it.isNotBlank() }.mapIndexed { i,line->
    val split=line.indexOfFirst { it=='\t' || it==',' }; require(split>0) { "Line ${i+1}: use roll number, name." }
    val roll=Rules.roll(line.substring(0,split)); val name=Rules.text(line.substring(split+1),"Name on line ${i+1}")
    Student(groupId=group.id,roll=roll,rollKey=Rules.rollKey(roll),name=name,joined=joined.trim())
   }; vm.app.repo.saveStudents(rows)
  }
 }) {
  Hint("One student per line: 2401301, Student Name. Tabs copied from Excel also work. Do not include a header row. The whole import is validated before saving.")
  Field("Joining date (YYYY-MM-DD)",joined,{joined=it}); Field("Roll number, name",text,{text=it},multiline=true)
 }
}
@Composable private fun ExportForm(vm: AppViewModel,group: ClassGroup,dismiss: ()->Unit) {
 var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }; var to by remember { mutableStateOf(LocalDate.now().toString()) }
 FormDialog("Export register",dismiss,"Create Excel",!vm.busy,{vm.export(group,from,to);dismiss()}) {
  Field("From (YYYY-MM-DD)",from,{from=it});Field("To (YYYY-MM-DD)",to,{to=it})
  Hint("Includes daily register, class timestamps, and original marked names. Only final, non-cancelled classes count. Leave is excluded from attendance percentage.")
 }
}
@Composable private fun HistoryCard(vm: AppViewModel,group: ClassGroup,s: Session) {
 var confirm by remember { mutableStateOf(false) }; var audit by remember { mutableStateOf(false) }; var reason by remember { mutableStateOf("") }
 var addOmitted by remember { mutableStateOf(false) }
 if(confirm) FormDialog(if(s.cancelled) "Restore class?" else "Cancel conducted class?",{confirm=false},"Confirm",!vm.busy,{
  vm.write("Class record updated.",{confirm=false}){vm.app.repo.cancelSession(s.id,!s.cancelled,reason)}
 }) { Field("Reason",reason,{reason=it});Hint("Cancellation excludes the class from totals. Original marks and edit history remain available.") }
 if(addOmitted) AddOmittedStudentForm(vm,group,s){addOmitted=false}
 if(audit) AlertDialog(onDismissRequest={audit=false},title={Text("Revision history")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  vm.data.audit.filter { it.entityId==s.id }.forEach { a->Text("${Instant.ofEpochMilli(a.at)}\n${a.action}\n${a.detail}",style=MaterialTheme.typography.bodySmall) }
 }},confirmButton={TextButton(onClick={audit=false}){Text("Close")}})
 Panel {
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(s.date,fontWeight=FontWeight.Bold);Pill(if(s.cancelled) "Cancelled" else if(s.finalised) "Final" else "Draft")}
  Hint("${s.classTime} · Revision ${s.revision} · ${s.zone}")
  Hint("Saved ${Instant.ofEpochMilli(s.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"))}")
  Row(Modifier.horizontalScroll(rememberScrollState())) {
   TextButton(onClick={vm.openAttendance(group,s.date)},enabled=!s.cancelled && !group.archived){Text("Open")}
   TextButton(onClick={audit=true}){Text("Audit")}
   TextButton(onClick={addOmitted=true},enabled=!s.cancelled && !group.archived){Text("Add missed student")}
   TextButton(onClick={confirm=true},enabled=!group.archived){Text(if(s.cancelled) "Restore class" else "Cancel class")}
  }
 }
}
@Composable private fun AddOmittedStudentForm(vm: AppViewModel,group: ClassGroup,session: Session,dismiss: ()->Unit) {
 val onRoster=vm.data.marks.filter { it.sessionId==session.id }.map { it.studentId }.toSet()
 val candidates=vm.data.students.filter { it.groupId==group.id && it.id !in onRoster && Rules.eligible(session.date,it.joined,it.left) }.sortedBy { it.rollKey }
 var studentId by remember { mutableStateOf(candidates.firstOrNull()?.id ?: "") }
 var status by remember { mutableStateOf("P") }
 var reason by remember { mutableStateOf("") }
 FormDialog("Add student omitted from this register",dismiss,"Add & audit",!vm.busy && candidates.isNotEmpty(),{
  vm.write("Student added to the register as a correction.",dismiss) { vm.app.repo.addOmittedStudent(session.id,studentId,status,reason) }
 }) {
  if(candidates.isEmpty()) Text("Every eligible student for ${session.date} is already on this register.")
  else {
   Hint("Choose the student who was missed when this register was first saved.")
   candidates.forEach { st -> Row(verticalAlignment=Alignment.CenterVertically) {
    RadioButton(selected=studentId==st.id,onClick={studentId=st.id}); Spacer(Modifier.width(4.dp)); Text("${st.roll} · ${st.name}")
   } }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Attendance.entries.forEach { a->FilterChip(selected=status==a.name,onClick={status=a.name},label={Text(a.label)}) } }
   Field("Reason this student was missing",reason,{reason=it})
   Hint("This is recorded as an audited correction. No other student's marks are changed, and the original register is not silently rewritten.")
  }
 }
}
@Composable fun AttendanceScreen(vm: AppViewModel) {
 val d=vm.draft ?: return; val group=vm.data.groups.single { it.id==d.groupId }
 var search by rememberSaveable(d.groupId,d.date) { mutableStateOf("") }; var bulk by remember { mutableStateOf(false) }; var finalise by remember { mutableStateOf(false) }
 if(bulk) FormDialog("Mark everyone Present?",{bulk=false},"Mark all",onConfirm={vm.allPresent();bulk=false}) {Text("This changes all ${d.roster.size} students, including those hidden by search. Review exceptions before saving.")}
 if(finalise) FormDialog("Save attendance?",{finalise=false},"Save final register",!vm.busy,{vm.saveAttendance(true);finalise=false}) {
  Text("${d.date} · ${d.time}\n${d.marks.values.count { it=="P" }} Present · ${d.marks.values.count { it=="A" }} Absent · ${d.marks.values.count { it=="L" }} Leave")
  Hint("Every student must be marked. Corrections to final registers require a reason.")
 }
 Column(Modifier.fillMaxSize()) {
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item { Heading("Roll call", "${group.subject} · ${d.date}") }
   item { Panel {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${d.marks.values.count { it.isNotEmpty() }} / ${d.roster.size} marked",fontWeight=FontWeight.Bold);Pill(if(d.wasFinal) "Correction" else "In progress")}
    Field("Actual class time (HH:mm)",d.time,{vm.draft=d.copy(time=it,dirty=true)})
    val holiday=vm.app.repo.dayOff(vm.data,d.groupId,d.date)
    if(holiday!=null) Hint("Calendar: $holiday. Explain why a class was conducted.")
    if(holiday!=null || d.wasFinal) Field("Reason for calendar override / correction",d.reason,{vm.draft=d.copy(reason=it,dirty=true)})
    TextButton(onClick={bulk=true},enabled=!vm.busy){Text("Mark all Present")}
   } }
   item { Field("Find by name or roll",search,{search=it}) }
   items(d.roster.filter { (it.roll+it.name).contains(search,true) },key={it.id}) { st ->
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
     Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
      Text(st.name,fontWeight=FontWeight.SemiBold);Hint(st.roll)
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Attendance.entries.forEach { status->FilterChip(selected=d.marks[st.id]==status.name,onClick={vm.mark(st.id,status.name)},enabled=!vm.busy,label={Text(status.label)},modifier=Modifier.weight(1f)) }
      }
     }
    }
   }
  }
  Surface(shadowElevation=8.dp) { Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   if(!d.wasFinal) OutlinedButton(onClick={vm.saveAttendance(false)},enabled=!vm.busy,modifier=Modifier.weight(1f)){Text("Save draft")}
   Button(onClick={finalise=true},enabled=!vm.busy,modifier=Modifier.weight(1f)){Text("Review & save")}
  } }
 }
}
