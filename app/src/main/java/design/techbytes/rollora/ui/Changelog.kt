package design.techbytes.rollora.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChangelogEntry(val versionCode: Int, val versionName: String, val date: String, val changes: List<String>)

/** Newest first. Add one entry per release here — the What's New popup and the Version history
 * screen are both driven entirely from this list, so nothing else needs to change per release. */
object Changelog {
    val entries = listOf(
        ChangelogEntry(2, "1.0.1 Beta", "2026-09-17", listOf(
            "Added this What's New popup and a full Version history screen, available any time from Settings.",
        )),
        ChangelogEntry(1, "1.0.0 Beta", "2026-09-17", listOf(
            "First teacher beta: class groups, students, and attendance with drafts, final review and audited corrections.",
            "Automatic Sundays and holidays, with statistics based only on classes actually conducted.",
            "Real Excel export, encrypted local snapshots and portable encrypted backups.",
            "A guided in-app tutorial with optional spoken narration.",
            "Public GitHub-based update checks, with checksum and signing-certificate verification before installing.",
        )),
    )
}

@Composable fun WhatsNewDialog(vm: AppViewModel) {
    val entries = vm.whatsNew
    if (entries.isEmpty()) return
    AlertDialog(
        onDismissRequest = { vm.dismissWhatsNew() },
        title = { Text("What's new") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.versionName, fontWeight = FontWeight.Bold)
                            Hint(entry.date)
                        }
                        entry.changes.forEach { change -> Text("•  $change", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.dismissWhatsNew() }) { Text("Got it") } }
    )
}

@Composable fun ChangelogScreen(vm: AppViewModel) {
    ScreenList {
        item { Heading("Version history", "Every Rollora release, listed automatically") }
        items(Changelog.entries, key = { it.versionCode }) { entry ->
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.versionName, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Pill(entry.date)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.changes.forEach { change -> Hint("•  $change") }
                }
            }
        }
    }
}
