package io.atmos.weather.earthquake

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local CWA API key. Never write the key into source or GitHub Actions secrets. */
object EarthquakeSecurity {
    private const val alias = "thinn_cwa_earthquake_aes_v15"
    private const val pref = "thinn_seismic_v15"
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(context: Context, key: String) {
        val store = context.getSharedPreferences(pref, Context.MODE_PRIVATE)
        if (key.isBlank()) { store.edit().remove("cwa_key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(key.trim().toByteArray(Charsets.UTF_8))
        val data = cipher.iv + encrypted
        store.edit().putString("cwa_key", Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }
    fun load(context: Context): String? {
        return runCatching {
            val raw = context.getSharedPreferences(pref, Context.MODE_PRIVATE).getString("cwa_key", null)
                ?: return@runCatching null
            val decoded = Base64.decode(raw, Base64.NO_WRAP)
            if (decoded.size <= 28) return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decoded.copyOfRange(0, 12)))
            String(cipher.doFinal(decoded.copyOfRange(12, decoded.size)), Charsets.UTF_8)
        }.getOrNull()
    }
    fun enabled(context: Context): Boolean = !load(context).isNullOrBlank()
}
