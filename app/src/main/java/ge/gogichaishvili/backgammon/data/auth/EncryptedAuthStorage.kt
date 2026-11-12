package ge.gogichaishvili.backgammon.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM, per-entry IV and key-name AAD. Never stores plaintext or shares offline game files. */
class EncryptedAuthStorage(context: Context) : AuthStorage {
    private val app = context.applicationContext
    private val alias = app.packageName + ".auth.aes.v1"
    private val mutex = Mutex()
    private val preferences by lazy { app.getSharedPreferences("auth_secure_v1", Context.MODE_PRIVATE) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() } catch (_: Exception) { throw AuthStorageException() }
        }
    }
    override suspend fun read(key: String): String? = access {
        val value = preferences.getString(key, null) ?: return@access null
        try {
            val parts = value.split(':')
            require(parts.size == 3 && parts[0] == "1")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)))
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // Lost/invalidated key or tampered ciphertext must never restore an identity.
            if (!preferences.edit().clear().commit()) throw AuthStorageException()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            throw AuthStorageException()
        }
    }
    override suspend fun write(key: String, value: String?) = access {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            val encoded = "1:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            editor.putString(key, encoded)
        }
        if (!editor.commit()) throw AuthStorageException()
    }
    override suspend fun clear() = access {
        // Destroying the encryption key also prevents resurrection from a leftover ciphertext file.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        if (!preferences.edit().clear().commit()) throw AuthStorageException()
    }
}
