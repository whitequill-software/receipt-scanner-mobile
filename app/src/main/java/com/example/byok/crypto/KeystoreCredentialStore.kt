package com.example.byok.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.byok.model.AiProvider
import com.example.byok.model.ProviderConfig
import com.example.byok.model.ProviderProfile
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

import java.security.Security

class KeystoreCredentialStore(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "ReceiptScanner_BYOK_AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val CREDENTIALS_FILE = "byok_credentials.enc"
        private const val PROFILES_FILE = "byok_profiles.json"
    }

    private val isAndroidKeyStoreAvailable: Boolean = Security.getProvider(KEYSTORE_PROVIDER) != null

    private val keyStore: KeyStore? = if (isAndroidKeyStoreAvailable) {
        try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }

    private var jvmFallbackKey: SecretKey? = null

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore != null) {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        } else {
            // Graceful AES-256 fallback for JVM unit testing environments
            if (jvmFallbackKey == null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                jvmFallbackKey = keyGen.generateKey()
            }
            return jvmFallbackKey!!
        }
    }

    @Synchronized
    private fun encryptBytes(plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 12 bytes for GCM
        val ciphertext = cipher.doFinal(plaintext)

        val buffer = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    @Synchronized
    private fun decryptBytes(encryptedPayload: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encryptedPayload)
        val ivSize = buffer.int
        if (ivSize <= 0 || ivSize > 32) {
            throw IllegalArgumentException("Corrupted IV size in encrypted payload")
        }
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    @Synchronized
    fun saveProviderKey(providerId: String, apiKey: String) {
        val allEncryptedKeys = loadAllDecryptedKeys().toMutableMap()
        if (apiKey.isBlank()) {
            allEncryptedKeys.remove(providerId)
        } else {
            allEncryptedKeys[providerId] = apiKey.trim()
        }

        val json = JSONObject()
        allEncryptedKeys.forEach { (k, v) -> json.put(k, v) }
        val encrypted = encryptBytes(json.toString().toByteArray(Charsets.UTF_8))

        val file = File(context.filesDir, CREDENTIALS_FILE)
        FileOutputStream(file).use { it.write(encrypted) }
    }

    @Synchronized
    fun getProviderKey(providerId: String): String {
        val all = loadAllDecryptedKeys()
        return all[providerId] ?: ""
    }

    @Synchronized
    fun deleteProviderKey(providerId: String) {
        val allEncryptedKeys = loadAllDecryptedKeys().toMutableMap()
        allEncryptedKeys.remove(providerId)

        val json = JSONObject()
        allEncryptedKeys.forEach { (k, v) -> json.put(k, v) }
        val encrypted = encryptBytes(json.toString().toByteArray(Charsets.UTF_8))

        val file = File(context.filesDir, CREDENTIALS_FILE)
        FileOutputStream(file).use { it.write(encrypted) }
    }

    @Synchronized
    fun clearAllCredentials() {
        val file = File(context.filesDir, CREDENTIALS_FILE)
        if (file.exists()) file.delete()
        val profFile = File(context.filesDir, PROFILES_FILE)
        if (profFile.exists()) profFile.delete()
        if (keyStore?.containsAlias(KEY_ALIAS) == true) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
        jvmFallbackKey = null
    }

    private fun loadAllDecryptedKeys(): Map<String, String> {
        val file = File(context.filesDir, CREDENTIALS_FILE)
        if (!file.exists()) return emptyMap()

        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val decryptedBytes = decryptBytes(bytes)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                result[key] = json.optString(key)
            }
            result
        } catch (_: Exception) {
            // Corrupted or invalidated keystore; handle gracefully without crash
            emptyMap()
        }
    }

    // --- Profile Metadata Management (Provider model choice, endpoint URL, hostname confirmation) ---

    @Synchronized
    fun saveProfileMetadata(profile: ProviderProfile) {
        val profiles = loadAllProfiles().toMutableMap()
        profiles[profile.provider.id] = profile
        saveAllProfiles(profiles)
    }

    @Synchronized
    fun getProfile(provider: AiProvider): ProviderProfile {
        val profiles = loadAllProfiles()
        val hasKey = getProviderKey(provider.id).isNotBlank()
        val existing = profiles[provider.id]
        return if (existing != null) {
            existing.copy(hasKey = hasKey)
        } else {
            ProviderProfile(
                provider = provider,
                selectedModel = provider.defaultModel,
                customEndpointUrl = "",
                confirmedHostname = "",
                hasKey = hasKey
            )
        }
    }

    @Synchronized
    fun getFullConfig(provider: AiProvider): ProviderConfig {
        val profile = getProfile(provider)
        val key = getProviderKey(provider.id)
        return ProviderConfig(
            provider = provider,
            apiKey = key,
            selectedModel = profile.selectedModel,
            customEndpointUrl = profile.customEndpointUrl,
            confirmedHostname = profile.confirmedHostname
        )
    }

    private fun loadAllProfiles(): Map<String, ProviderProfile> {
        val file = File(context.filesDir, PROFILES_FILE)
        if (!file.exists()) return emptyMap()

        return try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, ProviderProfile>()
            json.keys().forEach { pId ->
                val obj = json.getJSONObject(pId)
                val provider = AiProvider.fromId(pId)
                result[pId] = ProviderProfile(
                    provider = provider,
                    selectedModel = obj.optString("selectedModel", provider.defaultModel),
                    customEndpointUrl = obj.optString("customEndpointUrl", ""),
                    confirmedHostname = obj.optString("confirmedHostname", "")
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveAllProfiles(profiles: Map<String, ProviderProfile>) {
        val json = JSONObject()
        profiles.forEach { (pId, profile) ->
            val obj = JSONObject().apply {
                put("selectedModel", profile.selectedModel)
                put("customEndpointUrl", profile.customEndpointUrl)
                put("confirmedHostname", profile.confirmedHostname)
            }
            json.put(pId, obj)
        }
        val file = File(context.filesDir, PROFILES_FILE)
        file.writeText(json.toString(), Charsets.UTF_8)
    }
}
