package design.techbytes.rollora.ui
import android.app.Activity
import android.content.Intent
import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import design.techbytes.rollora.BuildConfig
import design.techbytes.rollora.R
import java.io.File

@Composable fun RolloraRoot(vm: AppViewModel,activity: FragmentActivity) {
 RolloraTheme(vm.data.profile.theme) {
  val lightBars=MaterialTheme.colorScheme.background.luminance()>0.5f
  SideEffect { WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=lightBars; isAppearanceLightNavigationBars=lightBars } }
  val snackbar=remember { SnackbarHostState() }
  LaunchedEffect(vm.notice) { vm.notice?.let { message -> vm.notice=null; snackbar.showSnackbar(message) } }
  Scaffold(snackbarHost={SnackbarHost(snackbar)},containerColor=MaterialTheme.colorScheme.background) { inset ->
   Box(Modifier.fillMaxSize().padding(inset)) {
    when {
     vm.loadError!=null -> Column(Modifier.padding(24.dp).align(Alignment.Center)) { Text(vm.loadError!!); Action("Retry opening records") { vm.refresh() } }
     !vm.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
     !vm.app.security.configured || vm.data.profile.teacher.isBlank() -> Welcome(vm)
     !vm.unlocked -> Unlock(vm,activity)
     else -> Workspace(vm)
    }
    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    if(vm.unlocked && vm.tutorialStep!=null) TutorialOverlay(vm)
   }
  }
  if(vm.unlocked && vm.tutorialStep==null) WhatsNewDialog(vm)
  if(vm.unlocked) FileDialogs(vm,activity)
 }
}
@Composable private fun Welcome(vm: AppViewModel) {
 var name by rememberSaveable { mutableStateOf("") }; var college by rememberSaveable { mutableStateOf("") }
 var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
 var accepted by rememberSaveable { mutableStateOf(false) }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  Image(painterResource(R.drawable.ic_mark),"Rollora logo",Modifier.size(90.dp)); Text("A calmer roll call.",fontSize=30.sp,fontWeight=FontWeight.Bold)
  Hint("ROLLORA  ·  YOUR LOCAL ATTENDANCE WORKSPACE")
  Field("Teacher name",name,{name=it}); Field("College (optional)",college,{college=it})
  Field("Six-digit app PIN",pin,{pin=it.take(6)},secret=true,numeric=true); Field("Confirm PIN",confirm,{confirm=it.take(6)},secret=true,numeric=true)
  Panel {
   Text("Your records stay on this device.",fontWeight=FontWeight.Bold)
   Hint("Local backups are automatic. Regularly share an encrypted backup to Drive or another device. Uninstalling or losing this device can remove local records and local backups.")
   Hint("There is no online PIN reset. Keep your PIN and portable backup passphrase safe. Device time is recorded; it cannot be independently verified offline.")
   Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(accepted,{accepted=it}); Text("I understand local storage and backups.",style=MaterialTheme.typography.bodySmall) }
  }
  Action("Create workspace",accepted && !vm.busy) { vm.initialise(name,college,pin,confirm) }
  Hint("Mir Faizan  ·  Tech Bytes Design  ·  ${BuildConfig.VERSION_NAME}")
 }
}
@Composable private fun Unlock(vm: AppViewModel,activity: FragmentActivity) {
 var pin by remember { mutableStateOf("") }
 Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
  Image(painterResource(R.drawable.ic_mark),"Rollora logo",Modifier.size(96.dp)); Spacer(Modifier.height(16.dp))
  Text("Welcome back",fontSize=30.sp,fontWeight=FontWeight.Bold); Hint("Unlock your attendance workspace")
  Spacer(Modifier.height(24.dp)); Field("Six-digit PIN",pin,{pin=it.take(6)},true,true)
  Spacer(Modifier.height(16.dp)); Action("Unlock",!vm.busy && pin.length==6) { vm.unlock(pin); pin="" }
  if(vm.data.profile.biometrics) TextButton(onClick={
   val manager=BiometricManager.from(activity)
   if(manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS) {
    BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object: BiometricPrompt.AuthenticationCallback() {
     override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.unlocked=true }
     override fun onAuthenticationError(errorCode: Int,errString: CharSequence) { if(errorCode!=BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode!=BiometricPrompt.ERROR_USER_CANCELED) vm.say(errString.toString()) }
    }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Rollora").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use PIN").build())
   } else vm.say("A strong biometric is not available. Use your PIN.")
  }) { Text("Use fingerprint / biometric") }
  Hint("Forgot PIN? Use an enabled biometric, or reinstall and restore a portable backup. Reinstalling removes local data.")
 }
}
@Composable private fun Workspace(vm: AppViewModel) {
 LaunchedEffect(Unit) { if(!vm.maybeOfferTutorialOnFirstRun()) vm.maybeShowWhatsNew() }
 var discard by remember { mutableStateOf(false) }
 fun back() { when { vm.draft!=null -> if(vm.draft!!.dirty) discard=true else vm.draft=null; vm.showChangelog -> vm.showChangelog=false; else -> vm.groupId=null } }
 BackHandler(vm.draft!=null || vm.groupId!=null || vm.showChangelog) { back() }
 if(discard) FormDialog("Discard unsaved changes?",{discard=false},"Discard",onConfirm={vm.draft=null;discard=false}) { Text("Only changes since the last save will be discarded.") }
 BoxWithConstraints(Modifier.fillMaxSize()) {
  val wide=maxWidth>=840.dp
  val nav=listOf("Today" to "home","Classes" to "groups","Stats" to "stats","Calendar" to "calendar","Settings" to "settings")
  Row(Modifier.fillMaxSize()) {
   if(wide && vm.draft==null) NavigationRail(windowInsets=WindowInsets(0.dp)) {
    Image(painterResource(R.drawable.ic_mark),"Rollora",Modifier.size(64.dp).padding(8.dp))
    nav.forEachIndexed { i,n -> NavigationRailItem(selected=vm.tab==i,onClick={vm.tab=i;vm.groupId=null;vm.showChangelog=false},icon={Glyph(n.second)},label={Text(n.first)}) }
   }
   Column(Modifier.weight(1f)) {
    if(vm.groupId!=null || vm.draft!=null || vm.showChangelog) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
     TextButton(onClick={back()},enabled=!vm.busy) { Glyph("back"); Spacer(Modifier.width(8.dp)); Text("Back") }
     Spacer(Modifier.weight(1f)); Text("ROLLORA",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
     Box(Modifier.widthIn(max=1080.dp).fillMaxSize()) {
      when {
       vm.draft!=null -> AttendanceScreen(vm)
       vm.groupId!=null -> GroupScreen(vm,vm.groupId!!)
       vm.showChangelog -> ChangelogScreen(vm)
       else -> AnimatedContent(vm.tab,transitionSpec={fadeIn(tween(180)) togetherWith fadeOut(tween(120))},label="screen") { tab ->
        when(tab) { 0->TodayScreen(vm); 1->GroupsScreen(vm); 2->StatsScreen(vm); 3->CalendarScreen(vm); else->SettingsScreen(vm) }
       }
      }
     }
    }
    // windowInsets=0: the outer Scaffold already pads content for the system navigation bar via
    // `inset` in RolloraRoot; NavigationBar's own default inset would double that gap, pushing the
    // bar's background up and away from the screen edge instead of sitting flush against it.
    if(!wide && vm.draft==null) NavigationBar(windowInsets=WindowInsets(0.dp)) {
     nav.forEachIndexed { i,n -> NavigationBarItem(selected=vm.tab==i,onClick={vm.tab=i;vm.groupId=null;vm.showChangelog=false},icon={Glyph(n.second)},label={Text(n.first,maxLines=1)}) }
    }
   }
  }
 }
}
@Composable private fun FileDialogs(vm: AppViewModel,activity: Activity) {
 var saving by remember { mutableStateOf<File?>(null) }
 val saver=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val file=saving; val uri=result.data?.data
  if(result.resultCode==Activity.RESULT_OK && uri!=null && file!=null) vm.saveOutput(uri,file)
  saving=null
 }
 vm.output?.let { file ->
  val mime=if(file.extension=="xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/octet-stream"
  AlertDialog(onDismissRequest={vm.output=null},title={Text("Your file is ready")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(file.name); Hint(if(file.extension=="xlsx") "Excel exports contain readable student data. Share only with intended recipients." else "This backup is encrypted with your passphrase. Keep the passphrase separately.")
   Action("Share / Save to Drive") {
    try {
     val uri=FileProvider.getUriForFile(activity,"${activity.packageName}.files",file)
     val intent=Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
     intent.clipData=ClipData.newRawUri("Rollora file",uri); activity.startActivity(Intent.createChooser(intent,"Share Rollora file"))
    } catch(_: Exception) { vm.say("No compatible sharing app is available. Use Save file.") }
   }
   Hint("Google Drive appears in the share sheet when installed and available. Rollora does not upload in the background.")
  }},confirmButton={TextButton(onClick={saving=file;saver.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,file.name))}){Text("Save file")}},dismissButton={TextButton(onClick={vm.output=null}){Text("Close")}})
 }
 vm.pendingRestore?.let { s -> FormDialog("Replace this workspace?",{vm.pendingRestore=null},"Restore backup",!vm.busy,{vm.restore()}) {
  Text("Backup for ${s.profile.teacher}: ${s.groups.size} groups, ${s.students.size} memberships and ${s.sessions.size} class records.")
  Text("This replaces current records. A local recovery snapshot is created first. Your current app PIN stays unchanged; biometric unlock will be disabled.")
 } }
}
