package design.techbytes.rollora.update
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import design.techbytes.rollora.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(val code: Long,val name: String,val url: String,val sha256: String,val bytes: Long,val minSdk: Int,val notes: String)
class Updater(private val context: Context) {
 companion object { const val MAX_APK = 150L*1024*1024 }
 private fun allowed(url: URL): Boolean = url.protocol=="https" && url.userInfo==null && (url.port==-1 || url.port==443) &&
  (url.host in listOf("github.com","api.github.com","raw.githubusercontent.com") || url.host.endsWith(".githubusercontent.com"))
 private fun connection(address: String): HttpURLConnection {
  var url=URL(address)
  repeat(7) {
   require(allowed(url)) { "Update URL is not a trusted HTTPS GitHub address." }
   val c=(url.openConnection() as HttpURLConnection).apply { connectTimeout=15000; readTimeout=20000; instanceFollowRedirects=false; setRequestProperty("User-Agent","Rollora/${BuildConfig.VERSION_NAME}") }
   val status=c.responseCode
   if(status in listOf(301,302,303,307,308)) {
    val location=c.getHeaderField("Location") ?: error("Missing redirect."); c.disconnect(); url=URL(url,location)
   } else { if(status!=200) { c.disconnect(); error("GitHub returned HTTP $status.") }; return c }
  }
  error("Too many update redirects.")
 }
 fun check(): Release? {
  val repo=BuildConfig.UPDATE_REPO
  if(repo.isBlank()) return null
  val c=connection("https://github.com/$repo/releases/latest/download/update.json")
  val json=try { c.inputStream.use { input -> val b=input.readBytesLimited(65536); JSONObject(b.toString(Charsets.UTF_8)) } } finally { c.disconnect() }
  val r=Release(json.getLong("versionCode"),json.getString("versionName"),json.getString("apkUrl"),json.getString("sha256").lowercase(),json.getLong("bytes"),json.getInt("minSdk"),json.getString("notes"))
  require(r.sha256.matches(Regex("[a-f0-9]{64}")) && r.bytes in 1..MAX_APK && r.name.length<=100 && r.notes.length<=10000)
  val url=URL(r.url)
  require(allowed(url) && url.host=="github.com" && url.path.startsWith("/$repo/releases/download/") && url.query==null && url.ref==null) { "APK does not belong to the configured release repository." }
  require(r.minSdk>=26)
  if(r.code<=BuildConfig.VERSION_CODE || r.minSdk>Build.VERSION.SDK_INT) return null
  return r
 }
 suspend fun download(r: Release,onProgress: (Float)->Unit): File {
  val dir=File(context.cacheDir,"updates").apply { mkdirs() }
  val partial=File(dir,"update.part"); val file=File(dir,"rollora-update.apk")
  file.delete(); partial.delete()
  val c=connection(r.url)
  try {
   val length=c.contentLengthLong
   require(length<0 || length==r.bytes) { "APK length differs from manifest." }
   val hash=MessageDigest.getInstance("SHA-256"); var total=0L
   c.inputStream.use { input -> partial.outputStream().use { out ->
    val buf=ByteArray(64*1024)
    while(true) { currentCoroutineContext().ensureActive(); val n=input.read(buf); if(n<0) break
     total+=n; require(total<=r.bytes && total<=MAX_APK); hash.update(buf,0,n); out.write(buf,0,n); onProgress(total.toFloat()/r.bytes)
    }
    out.fd.sync()
   } }
   require(total==r.bytes && hash.digest().joinToString("") { "%02x".format(it) }==r.sha256) { "APK integrity check failed." }
   verifyPackage(partial,r.code)
   check(partial.renameTo(file)) { "Could not prepare the installer." }; return file
  } finally { c.disconnect(); partial.delete() }
 }
 @Suppress("DEPRECATION")
 fun verifyPackage(file: File,expectedCode: Long) {
  val pm=context.packageManager
  val flag=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val archive=pm.getPackageArchiveInfo(file.absolutePath,flag) ?: error("Invalid APK.")
  val installed=pm.getPackageInfo(context.packageName,flag)
  require(archive.packageName==context.packageName) { "This APK is for a different app or build variant." }
  val code=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
  require(code==expectedCode && code>BuildConfig.VERSION_CODE) { "APK version does not match the update." }
  fun certs(p: android.content.pm.PackageInfo): Set<String> {
   val signatures=if(Build.VERSION.SDK_INT>=28) p.signingInfo?.apkContentsSigners else p.signatures
   return signatures?.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }?.toSet() ?: emptySet()
  }
  require(certs(installed).isNotEmpty() && certs(archive)==certs(installed)) { "Signing certificate mismatch. Update rejected." }
 }
 fun install(file: File): Boolean {
  if(!context.packageManager.canRequestPackageInstalls()) {
   context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,"package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return false
  }
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
  context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
  return true
 }
}
fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
 val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0
 while(true) { val n=read(buffer); if(n<0) break; total+=n; require(total<=limit) { "File exceeds the allowed size." }; out.write(buffer,0,n) }; return out.toByteArray()
}
