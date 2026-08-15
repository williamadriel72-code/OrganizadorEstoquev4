package com.stockmaster.clone.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credenciais locais protegidas por uma chave AES mantida no Android Keystore. */
data class SavedCredentials(
    val login: String,
    val password: String
)

class AwsCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(login: String, password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val payload = "${login.trim()}\u0000$password".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(payload)

        prefs.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): SavedCredentials? {
        val encryptedText = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val ivText = prefs.getString(KEY_IV, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))

            val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
            val plain = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            val parts = plain.split('\u0000', limit = 2)
            require(parts.size == 2)
            SavedCredentials(parts[0], parts[1])
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "aws_secure_credentials"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_IV = "iv"
        private const val KEY_ALIAS = "aws_login_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
