package design.techbytes.rollora
import org.junit.Assert.*
import org.junit.Test
import design.techbytes.rollora.domain.Rules
import design.techbytes.rollora.security.Crypto
import design.techbytes.rollora.transfer.XlsxWriter
import design.techbytes.rollora.ui.Tutorial
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CoreTests {
 @Test fun rollNumbersKeepLeadingZeroes() { assertEquals("001301",Rules.roll(" 001301 ")); assertEquals("AB-01",Rules.rollKey("ab-01")) }
 @Test(expected=IllegalArgumentException::class) fun invalidRollRejected() { Rules.roll("12, 14") }
 @Test(expected=IllegalArgumentException::class) fun blankNameRejected() { Rules.text("  ","Name") }
 @Test fun leaveOnlyHasNoPercentage() { assertNull(Rules.percentage(0,0)); assertEquals(75.0,Rules.percentage(3,1)!!,0.001) }
 @Test fun membershipUsesExclusiveEndDate() { assertTrue(Rules.eligible("2026-09-16","2026-09-01","2026-09-17")); assertFalse(Rules.eligible("2026-09-17","2026-09-01","2026-09-17")); assertFalse(Rules.eligible("2026-08-30","2026-09-01",null)) }
 @Test(expected=IllegalArgumentException::class) fun incompleteAttendanceRejected() { Rules.complete(setOf("1","2"),mapOf("1" to "P")) }
 @Test(expected=IllegalArgumentException::class) fun foreignStudentRejected() { Rules.complete(setOf("1"),mapOf("1" to "P","2" to "A")) }
 @Test(expected=IllegalArgumentException::class) fun invalidStatusRejected() { Rules.complete(setOf("1"),mapOf("1" to "X")) }
 @Test fun presentAbsentLeaveAccepted() { Rules.complete(setOf("1","2","3"),mapOf("1" to "P","2" to "A","3" to "L")) }
 @Test(expected=IllegalArgumentException::class) fun reversedRangeRejected() { Rules.validateRange("2026-09-17","2026-09-16") }
 @Test fun leapDayValid() { assertEquals("2024-02-29",Rules.date("2024-02-29").toString()) }
 @Test(expected=IllegalStateException::class) fun invalidLeapDayRejected() { Rules.date("2025-02-29") }
 @Test fun validTimeRetained() { assertEquals("09:15",Rules.time("09:15")) }
 @Test(expected=IllegalStateException::class) fun invalidTimeRejected() { Rules.time("27:00") }
 @Test fun portableBackupRoundTrip() { val original="Roll numbers, names and records".toByteArray();val bytes=Crypto.portable(original,"long-passphrase".toCharArray());assertArrayEquals(original,Crypto.openPortable(bytes,"long-passphrase".toCharArray())) }
 @Test(expected=javax.crypto.AEADBadTagException::class) fun wrongPassphraseRejected() { val bytes=Crypto.portable("private".toByteArray(),"long-passphrase".toCharArray());Crypto.openPortable(bytes,"wrong-passphrase".toCharArray()) }
 @Test(expected=javax.crypto.AEADBadTagException::class) fun tamperedBackupRejected() { val bytes=Crypto.portable("private".toByteArray(),"long-passphrase".toCharArray());bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();Crypto.openPortable(bytes,"long-passphrase".toCharArray()) }
 @Test fun encryptionRandomised() { val p="long-passphrase".toCharArray();assertFalse(Crypto.portable(byteArrayOf(1),p).contentEquals(Crypto.portable(byteArrayOf(1),p))) }
 @Test(expected=IllegalArgumentException::class) fun shortPassphraseRejected() {Crypto.portable(byteArrayOf(1),"short".toCharArray())}
 @Test(expected=IllegalArgumentException::class) fun truncatedBackupRejected() {Crypto.openPortable(byteArrayOf(1),"long-passphrase".toCharArray())}
 @Test fun spreadsheetColumns() {assertEquals("A",XlsxWriter.column(0));assertEquals("Z",XlsxWriter.column(25));assertEquals("AA",XlsxWriter.column(26));assertEquals("XFD",XlsxWriter.column(16383))}
 @Test fun workbookIsValidXmlAndTextNeverBecomesFormula() {
  val out=ByteArrayOutputStream();XlsxWriter.write(out,listOf(XlsxWriter.Sheet("Register",listOf(listOf("Roll","Name"),listOf("0013","=HYPERLINK(\"bad\")"),listOf("0014","A & B < C")))))
  val entries=mutableMapOf<String,ByteArray>()
  ZipInputStream(out.toByteArray().inputStream()).use { z->while(true){val e=z.nextEntry ?: break;entries[e.name]=z.readBytes()} }
  assertTrue(entries.keys.containsAll(listOf("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/worksheets/sheet1.xml","xl/styles.xml")))
  val factory=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}
  entries.values.forEach { factory.newDocumentBuilder().parse(it.inputStream()) }
  val sheet=entries.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
  assertTrue(sheet.contains("0013"));assertTrue(sheet.contains("t=\"inlineStr\""));assertFalse(sheet.contains("<f>"));assertTrue(sheet.contains("A &amp; B &lt; C"))
 }
 @Test fun numericTotalsUseRealNumberCellsAndRollsStayText() {
  val out=ByteArrayOutputStream()
  XlsxWriter.write(out,listOf(XlsxWriter.Sheet("Register",
   listOf(listOf("Roll","Name","Present","Attendance %"),listOf("0013","Student One","3","75.00"),listOf("0014","All leave","0","N/A")),
   intCols=setOf(2),decimalCols=setOf(3))))
  val entries=mutableMapOf<String,ByteArray>()
  ZipInputStream(out.toByteArray().inputStream()).use { z->while(true){val e=z.nextEntry ?: break;entries[e.name]=z.readBytes()} }
  val sheet=entries.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
  assertTrue(sheet.contains("<c r=\"A2\" s=\"0\" t=\"inlineStr\"><is><t xml:space=\"preserve\">0013</t></is></c>"))
  assertTrue(sheet.contains("<c r=\"C2\" s=\"2\" t=\"n\"><v>3</v></c>"))
  assertTrue(sheet.contains("<c r=\"D2\" s=\"3\" t=\"n\"><v>75.0</v></c>"))
  assertTrue(sheet.contains("<c r=\"D3\" s=\"0\" t=\"inlineStr\"><is><t xml:space=\"preserve\">N/A</t></is></c>"))
 }
 @Test fun tutorialStepsAreWellFormed() {
  assertTrue("Tutorial should have a reasonable number of steps",Tutorial.steps.size in 5..20)
  Tutorial.steps.forEach { step ->
   assertTrue("Title must not be blank",step.title.isNotBlank())
   assertTrue("Body must not be blank",step.body.isNotBlank())
   assertTrue("Title should be short enough for a header",step.title.length<=60)
  }
  assertEquals("Titles should be unique",Tutorial.steps.size,Tutorial.steps.map { it.title }.toSet().size)
 }
}
