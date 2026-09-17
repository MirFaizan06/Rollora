package design.techbytes.rollora.security
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
 const val MAX_BYTES = 32 * 1024 * 1024
 private val magic = "ROLLORA1".toByteArray()
 fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
 fun derive(pass: CharArray, salt: ByteArray): ByteArray {
  val spec = PBEKeySpec(pass,salt,210000,256)
  return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
 }
 fun encrypt(bytes: ByteArray, key: SecretKey, aad: ByteArray = magic): ByteArray {
  require(bytes.size<=MAX_BYTES)
  val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key); c.updateAAD(aad)
  return c.iv+c.doFinal(bytes)
 }
 fun decrypt(bytes: ByteArray,key: SecretKey,aad: ByteArray = magic): ByteArray {
  require(bytes.size in 28..MAX_BYTES+128) { "Invalid encrypted file size." }
  val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12))); c.updateAAD(aad)
  return c.doFinal(bytes.copyOfRange(12,bytes.size))
 }
 fun portable(bytes: ByteArray,pass: CharArray): ByteArray {
  require(pass.size>=12) { "Use a backup passphrase of at least 12 characters." }
  val salt=random(16); val raw=derive(pass,salt)
  return try { magic+salt+encrypt(bytes,SecretKeySpec(raw,"AES"),magic+salt) } finally { raw.fill(0) }
 }
 fun openPortable(bytes: ByteArray,pass: CharArray): ByteArray {
  require(bytes.size in 52..MAX_BYTES+128 && bytes.copyOfRange(0,8).contentEquals(magic)) { "Not a Rollora backup." }
  val salt=bytes.copyOfRange(8,24); val raw=derive(pass,salt)
  return try { decrypt(bytes.copyOfRange(24,bytes.size),SecretKeySpec(raw,"AES"),magic+salt) } finally { raw.fill(0) }
 }
}
