package design.techbytes.rollora.transfer
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small OOXML writer. Text uses inline strings (never formulas), preserving roll numbers. */
object XlsxWriter {
 /** intCols/decimalCols mark 0-based column indices that hold numeric totals (counts / percentages).
  * A cell in one of those columns is written as a genuine numeric cell only when its text actually
  * parses as a number; non-numeric values (e.g. "N/A") fall back to inline string automatically. */
 data class Sheet(val name: String,val rows: List<List<String>>,val headerRow: Int = 1,val intCols: Set<Int> = emptySet(),val decimalCols: Set<Int> = emptySet())
 fun xml(value: String): String = buildString {
  value.forEach { c -> when(c) {
   '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&apos;")
   else -> if(c=='\n' || c=='\r' || c=='\t' || c >= ' ' && c != '\uFFFE' && c != '\uFFFF') append(c)
  } }
 }
 fun column(index: Int): String { var n=index+1; var s=""; while(n>0) { n--; s=('A'.code+n%26).toChar()+s; n/=26 }; return s }
 fun write(out: OutputStream,sheets: List<Sheet>) {
  require(sheets.isNotEmpty() && sheets.size<=20)
  val ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
  val rel="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  ZipOutputStream(out).use { zip ->
   fun entry(path: String,text: String) { zip.putNextEntry(ZipEntry(path)); zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+text).toByteArray()); zip.closeEntry() }
   entry("[Content_Types].xml","""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>"""+sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it+1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" }+"</Types>")
   entry("_rels/.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
   entry("xl/workbook.xml","""<workbook xmlns="$ns" xmlns:r="$rel"><sheets>"""+sheets.mapIndexed { i,s -> """<sheet name="${xml(s.name)}" sheetId="${i+1}" r:id="rId${i+1}"/>""" }.joinToString("")+"</sheets></workbook>")
   entry("xl/_rels/workbook.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""+sheets.indices.joinToString("") { """<Relationship Id="rId${it+1}" Type="$rel/worksheet" Target="worksheets/sheet${it+1}.xml"/>""" }+"""<Relationship Id="styles" Type="$rel/styles" Target="styles.xml"/></Relationships>""")
   entry("xl/styles.xml","""<styleSheet xmlns="$ns"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF353C68"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"><alignment vertical="center"/></xf><xf numFmtId="49" fontId="1" fillId="2" borderId="0" xfId="0" applyFill="1" applyFont="1"><alignment wrapText="1" vertical="center"/></xf><xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"><alignment vertical="center"/></xf><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"><alignment vertical="center"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
   sheets.forEachIndexed { index,sheet ->
    require(sheet.rows.size<=1048576 && sheet.rows.all { it.size<=16384 && it.all { cell->cell.length<=32767 } })
    val cols=sheet.rows.maxOfOrNull { it.size } ?: 1
    val last="${column((cols-1).coerceAtLeast(0))}${sheet.rows.size}"
    val data=sheet.rows.mapIndexed { rowIndex,row ->
     "<row r=\"${rowIndex+1}\" ht=\"${if(rowIndex+1==sheet.headerRow) 32 else 22}\" customHeight=\"1\">"+row.mapIndexed { col,v ->
      val ref="${column(col)}${rowIndex+1}"
      val header=rowIndex+1==sheet.headerRow
      val asNumber=if(!header && col in sheet.intCols) v.toLongOrNull() else if(!header && col in sheet.decimalCols) v.toDoubleOrNull() else null
      when {
       header -> "<c r=\"$ref\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(v)}</t></is></c>"
       asNumber!=null && col in sheet.intCols -> "<c r=\"$ref\" s=\"2\" t=\"n\"><v>$asNumber</v></c>"
       asNumber!=null -> "<c r=\"$ref\" s=\"3\" t=\"n\"><v>$asNumber</v></c>"
       else -> "<c r=\"$ref\" s=\"0\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(v)}</t></is></c>"
      }
     }.joinToString("")+"</row>"
    }.joinToString("")
    entry("xl/worksheets/sheet${index+1}.xml","""<worksheet xmlns="$ns"><dimension ref="A1:$last"/><sheetViews><sheetView workbookViewId="0"><pane xSplit="2" ySplit="${sheet.headerRow}" topLeftCell="C${sheet.headerRow+1}" activePane="bottomRight" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="1" width="20" customWidth="1"/><col min="2" max="2" width="30" customWidth="1"/><col min="3" max="${cols.coerceAtLeast(3)}" width="14" customWidth="1"/></cols><sheetData>$data</sheetData><autoFilter ref="A${sheet.headerRow}:$last"/><pageMargins left="0.25" right="0.25" top="0.5" bottom="0.5" header="0.2" footer="0.2"/><pageSetup orientation="landscape" paperSize="9"/></worksheet>""")
   }
  }
 }
}
