package tw.littleyi.mobileagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class Configuration(
    val useCodex: Boolean = true,
    val endpoint: String = "https://openrouter.ai/api/v1",
    val apiKey: String = "",
    val model: String = "",
)

/** API keys are encrypted with Android Keystore and excluded from backup/logs. */
class SecureConfiguration(context: Context, private val json: Json) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "configuration.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("littleyi_configuration", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("littleyi_configuration", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): Configuration {
        if (!file.baseFile.exists()) return Configuration()
        val bytes = file.readFully()
        require(bytes.size > 28) { "設定檔損毀，請重新設定。" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return json.decodeFromString(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
    }
    fun write(value: Configuration) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val output = file.startWrite()
        try {
            output.write(cipher.iv + cipher.doFinal(json.encodeToString(value).encodeToByteArray()))
            file.finishWrite(output)
        } catch (e: Throwable) {
            file.failWrite(output)
            throw e
        }
    }
}
