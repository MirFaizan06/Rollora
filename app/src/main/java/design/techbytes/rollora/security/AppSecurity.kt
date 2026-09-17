package design.techbytes.rollora.security
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class AppSecurity(context: Context) {
 private val prefs=context.getSharedPreferences("security",Context.MODE_PRIVATE)
 private fun b(v: ByteArray)=Base64.encodeToString(v,Base64.NO_WRAP)
 private fun un(v: String)=Base64.decode(v,Base64.NO_WRAP)
 val configured get()=prefs.contains("pin")
 @Synchronized fun localKey(): SecretKey = key("rollora.backups",false)
 @Synchronized private fun key(alias: String,hmac: Boolean): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias,null) as? SecretKey)?.let { return it }
  val algorithm=if(hmac) KeyProperties.KEY_ALGORITHM_HMAC_SHA256 else KeyProperties.KEY_ALGORITHM_AES
  val spec=KeyGenParameterSpec.Builder(alias,if(hmac) KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY else KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
  if(hmac) spec.setDigests(KeyProperties.DIGEST_SHA256) else spec.setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
  return KeyGenerator.getInstance(algorithm,"AndroidKeyStore").apply { init(spec.build()) }.generateKey()
 }
 private fun verifier(pin: String,salt: ByteArray): ByteArray {
  val chars=pin.toCharArray(); val stretched=try { Crypto.derive(chars,salt) } finally { chars.fill('\u0000') }
  return try { Mac.getInstance("HmacSHA256").apply { init(key("rollora.pin",true)) }.doFinal(stretched) } finally { stretched.fill(0) }
 }
 fun setPin(pin: String) {
  require(pin.matches(Regex("[0-9]{6}"))) { "Choose a six-digit PIN." }
  val salt=Crypto.random(16); val result=verifier(pin,salt)
  check(prefs.edit().putString("salt",b(salt)).putString("pin",b(result)).putInt("attempts",0).putLong("lockedUntil",0).commit()) { "PIN could not be saved." }
 }
 @Synchronized fun verify(pin: String): Boolean {
  val now=System.currentTimeMillis(); val until=prefs.getLong("lockedUntil",0)
  require(now>=until) { "Too many attempts. Try again in ${(until-now)/1000+1} seconds." }
  if(!configured) return false
  val ok=MessageDigest.isEqual(verifier(pin,un(prefs.getString("salt","")!!)),un(prefs.getString("pin","")!!))
  val attempts=if(ok) 0 else prefs.getInt("attempts",0)+1
  val delay=if(attempts>=5) (30000L shl (attempts-5).coerceAtMost(6)).coerceAtMost(1800000L) else 0L
  check(prefs.edit().putInt("attempts",attempts).putLong("lockedUntil",if(ok) 0 else now+delay).commit())
  return ok
 }
}
