package com.maheshkatakam.vani.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys are encrypted with an AES key that lives in the Android Keystore,
 * so they never sit in plain text on disk.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("vani_keys", Context.MODE_PRIVATE)
    private val cache = HashMap<Provider, String>()
    private val _version = MutableStateFlow(0)

    /** Bumps whenever a key changes, so UI can refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun get(p: Provider): String {
        cache[p]?.let { return it }
        val stored = prefs.getString(p.name, null) ?: return ""
        val plain = runCatching { decrypt(stored) }.getOrDefault("")
        cache[p] = plain
        return plain
    }

    fun has(p: Provider): Boolean = get(p).isNotBlank()

    @Synchronized
    fun set(p: Provider, key: String) {
        val v = key.trim()
        if (v.isEmpty()) {
            prefs.edit().remove(p.name).apply()
            cache.remove(p)
        } else {
            prefs.edit().putString(p.name, encrypt(v)).apply()
            cache[p] = v
        }
        _version.value = _version.value + 1
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return enc.encodeToString(cipher.iv) + ":" + enc.encodeToString(ct)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(":", limit = 2)
        if (parts.size != 2) return ""
        val dec = Base64.getDecoder()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, dec.decode(parts[0])))
        return String(cipher.doFinal(dec.decode(parts[1])), Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "vani_api_keys"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
