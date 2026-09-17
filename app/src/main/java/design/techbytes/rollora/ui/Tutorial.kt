package design.techbytes.rollora.ui
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

data class TutorialStep(val title: String, val body: String)

/** Content only — no Android dependency, so it can be unit tested on the plain JVM. */
object Tutorial {
    val steps = listOf(
        TutorialStep(
            "Welcome to Rollora",
            "Rollora is your local attendance workspace. Everything you record stays on this device. There is no account, no login and no attendance data ever leaves your phone unless you choose to export or back it up yourself."
        ),
        TutorialStep(
            "Unlocking the app",
            "Rollora is protected by the six digit PIN you chose, and a fingerprint or face unlock if your device supports it and you turn it on in Settings. The app locks itself again about thirty seconds after you leave it, and screenshots of your data are blocked."
        ),
        TutorialStep(
            "Create a class group",
            "Open the Classes tab and add a group: batch, semester, a course type such as Major or Minor, an optional CT component, the subject name, and a default class time. You can type a custom course type any time. Semesters are never restricted to fixed types."
        ),
        TutorialStep(
            "Add your students",
            "Inside a class group, add students one at a time, or paste a whole roster at once as roll number, comma, name, one student per line. Leading zeroes in roll numbers are always kept exactly as typed. Search finds a student instantly by name or roll number."
        ),
        TutorialStep(
            "Take attendance",
            "From Today, tap a class to open its register for that date. Mark each student Present, Absent or Leave, or mark everyone Present first and then fix the exceptions. You can save an incomplete register as a draft and finish it later."
        ),
        TutorialStep(
            "Finishing and correcting a register",
            "A final register requires every student to be marked. Once it is saved, correcting it later always asks for a reason, and every change is written to that class's audit history. If you forgot to add a student before you first saved, open that class's History and use Add missed student, which records the addition as an audited correction rather than silently rewriting what you already saved."
        ),
        TutorialStep(
            "Calendar and holidays",
            "Sundays are marked automatically, no setup needed. Add holidays that apply to every class, or just one. Recording a class on a day off always asks for a reason, and adding a holiday afterwards never changes attendance you already saved."
        ),
        TutorialStep(
            "Reading your statistics",
            "Statistics only count classes that were actually conducted, finalised, and not cancelled. The percentage shown is Present divided by Present plus Absent. Leave is reported on its own and never counts against a student."
        ),
        TutorialStep(
            "Exporting to Excel",
            "From a class group, export any date range as a real Excel workbook: a daily register, present, absent and leave totals, percentages, and the original recorded names and timestamps, ready to print or share."
        ),
        TutorialStep(
            "Backing up your workspace",
            "Rollora keeps rotating encrypted snapshots on this device automatically. For real safety, create a portable encrypted backup from Settings regularly, and share it to Google Drive or another device using the passphrase you choose. Without an off-device backup, losing this phone means losing your records."
        ),
        TutorialStep(
            "Staying up to date",
            "Settings can check for new versions of Rollora directly from GitHub in the background. Every update is verified for authenticity before you are asked to install it, and your attendance data is preserved through the update."
        ),
        TutorialStep(
            "You're ready",
            "That covers the essentials: classes, students, attendance, statistics, exports and backups. You can replay this tutorial any time from Settings. Good luck with your first roll call."
        ),
    )
}

/** Thin wrapper around Android's on-device TextToSpeech engine. No network, no cloud service. */
class TutorialSpeech(context: Context) {
    private var engine: TextToSpeech? = null
    var available by mutableStateOf(false); private set
    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            val language = if (ok) engine?.setLanguage(Locale.getDefault()) else null
            available = ok && language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }
    fun speak(text: String) {
        if (!available) return
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rollora-tutorial")
    }
    fun stop() { engine?.stop() }
    fun shutdown() { engine?.stop(); engine?.shutdown(); engine = null }
}

@Composable fun TutorialOverlay(vm: AppViewModel) {
    val index = vm.tutorialStep ?: return
    val step = Tutorial.steps[index]
    val last = index == Tutorial.steps.lastIndex
    BackHandler { vm.skipTutorial() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Pill("TUTORIAL ${index + 1} / ${Tutorial.steps.size}")
                    TextButton(onClick = { vm.skipTutorial() }) { Text("Skip") }
                }
                LinearProgressIndicator(progress = { (index + 1f) / Tutorial.steps.size }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(step.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(step.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (vm.voiceAvailable) {
                        IconButton(onClick = { vm.toggleVoiceGuidance() }) { Glyph(if (vm.voiceGuidanceEnabled) "speaker" else "mute") }
                        Text(if (vm.voiceGuidanceEnabled) "Voice guidance on" else "Voice guidance off", style = MaterialTheme.typography.labelMedium)
                        if (vm.voiceGuidanceEnabled) TextButton(onClick = { vm.replayTutorialStep() }) { Text("Replay") }
                    } else {
                        Hint("Voice guidance is not available on this device.")
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (index > 0) OutlinedButton(onClick = { vm.previousTutorialStep() }, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = { if (last) vm.finishTutorial() else vm.nextTutorialStep() }, modifier = Modifier.weight(1f)) { Text(if (last) "Finish" else "Next") }
            }
        }
    }
}
