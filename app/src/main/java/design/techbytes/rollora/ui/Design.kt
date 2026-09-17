package design.techbytes.rollora.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

val Mint=Color(0xFF75E3C0)
@Composable fun RolloraTheme(mode: String,content: @Composable ()->Unit) {
 val dark=mode=="dark" || mode=="system" && isSystemInDarkTheme()
 val colors=if(dark) darkColorScheme(primary=Color(0xFFADB5FF),onPrimary=Color(0xFF252B60),secondary=Mint,
  background=Color(0xFF17181F),surface=Color(0xFF22242E),surfaceVariant=Color(0xFF303341),onBackground=Color(0xFFF2F2F7),onSurface=Color(0xFFF2F2F7),onSurfaceVariant=Color(0xFFB3B6C9),outline=Color(0xFF646879))
 else lightColorScheme(primary=Color(0xFF4B58B6),onPrimary=Color.White,secondary=Color(0xFF116C55),background=Color(0xFFF5F5FA),surface=Color.White,surfaceVariant=Color(0xFFE8EAF4),onBackground=Color(0xFF202230),onSurface=Color(0xFF202230),onSurfaceVariant=Color(0xFF555C70),outline=Color(0xFF72798D))
 MaterialTheme(colorScheme=colors,shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(24.dp)),content=content)
}
@Composable fun Glyph(kind: String,modifier: Modifier=Modifier,color: Color=MaterialTheme.colorScheme.onSurfaceVariant) {
 Canvas(modifier.size(24.dp).semantics { contentDescription=kind }) {
  scale(size.width/24,size.height/24,pivot=Offset.Zero) {
   fun line(a: Float,b: Float,c: Float,d: Float) = drawLine(color,Offset(a,b),Offset(c,d),2f)
   when(kind) {
    "home" -> { val p=Path().apply { moveTo(3f,11f); lineTo(12f,3f); lineTo(21f,11f); moveTo(6f,10f); lineTo(6f,21f); lineTo(18f,21f); lineTo(18f,10f) }; drawPath(p,color,style=Stroke(2f)) }
    "groups" -> { drawRect(color,Offset(3f,4f),Size(18f,16f),style=Stroke(2f)); line(3f,9f,21f,9f); line(8f,9f,8f,20f) }
    "stats" -> { line(5f,20f,5f,13f); line(12f,20f,12f,7f); line(19f,20f,19f,3f) }
    "calendar" -> { drawRect(color,Offset(3f,5f),Size(18f,16f),style=Stroke(2f)); line(3f,10f,21f,10f); line(8f,2f,8f,7f); line(16f,2f,16f,7f) }
    "settings" -> { line(3f,7f,21f,7f); line(3f,17f,21f,17f); drawCircle(color,3f,Offset(9f,7f),style=Stroke(2f)); drawCircle(color,3f,Offset(16f,17f),style=Stroke(2f)) }
    "plus" -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
    "back" -> { line(20f,12f,4f,12f); line(4f,12f,11f,5f); line(4f,12f,11f,19f) }
    "lock" -> { drawRect(color,Offset(5f,10f),Size(14f,11f),style=Stroke(2f)); drawArc(color,180f,180f,false,Offset(8f,2f),Size(8f,15f),style=Stroke(2f)) }
    "check" -> { line(4f,12f,10f,18f); line(10f,18f,21f,5f) }
    "speaker" -> { val p=Path().apply { moveTo(4f,9f); lineTo(9f,9f); lineTo(15f,4f); lineTo(15f,20f); lineTo(9f,15f); lineTo(4f,15f); close() }; drawPath(p,color,style=Stroke(2f)); drawArc(color,300f,120f,false,Offset(15f,7f),Size(8f,10f),style=Stroke(2f)) }
    "mute" -> { val p=Path().apply { moveTo(4f,9f); lineTo(9f,9f); lineTo(15f,4f); lineTo(15f,20f); lineTo(9f,15f); lineTo(4f,15f); close() }; drawPath(p,color,style=Stroke(2f)); line(17f,7f,22f,17f); line(22f,7f,17f,17f) }
    else -> { drawCircle(color,8f,Offset(12f,12f),style=Stroke(2f)); line(12f,7f,12f,13f); drawCircle(color,1f,Offset(12f,17f)) }
   }
  }
 }
}
@Composable fun Heading(title: String,subtitle: String,action: (@Composable ()->Unit)?=null) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f)) { Text(title,fontSize=29.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium) }
  action?.invoke()
 }
}
@Composable fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
 Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
}
@Composable fun Hint(text: String) { Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
@Composable fun Pill(text: String) { Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(7.dp)) { Text(text,Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSecondaryContainer) } }
@Composable fun Field(label: String,value: String,change: (String)->Unit,secret: Boolean=false,numeric: Boolean=false,multiline: Boolean=false) {
 OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=!multiline,minLines=if(multiline) 4 else 1,
  visualTransformation=if(secret) PasswordVisualTransformation() else VisualTransformation.None,
  keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.NumberPassword else KeyboardType.Text),shape=RoundedCornerShape(12.dp))
}
@Composable fun Action(text: String,enabled: Boolean=true,onClick: ()->Unit) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(12.dp)) { Text(text) } }
@Composable fun FormDialog(title: String,onDismiss: ()->Unit,confirm: String="Save",enabled: Boolean=true,onConfirm: ()->Unit,content: @Composable ColumnScope.()->Unit) {
 AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)},
  confirmButton={TextButton(onClick=onConfirm,enabled=enabled){Text(confirm)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable fun ScreenList(content: LazyListScope.()->Unit) { LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content) }
@Composable fun EmptyState(title: String,body: String) { Panel { Glyph("info"); Text(title,fontWeight=FontWeight.Bold); Hint(body) } }
