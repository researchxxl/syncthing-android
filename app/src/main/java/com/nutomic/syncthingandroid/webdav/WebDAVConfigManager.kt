package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutomic.syncthingandroid.webdav.model.SyncFolderConfig
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * WebDAV configuration manager
 *
 * Handles storage and retrieval of WebDAV server configurations
 * and sync folder configurations with encryption support.
 */
class WebDAVConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "WebDAVConfigManager"

        // SharedPreferences file name
        private const val PREFS_NAME = "webdav_config"

        // Server config keys
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_AUTH_TYPE = "auth_type"
        private const val KEY_CONNECT_TIMEOUT = "connect_timeout"
        private const val KEY_READ_TIMEOUT = "read_timeout"

        // Folder config keys
        private const val KEY_FOLDER_CONFIGS = "folder_configs"
        private const val KEY_FOLDER_PREFIX = "folder_"

        // Encryption settings
        private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    /**
     * Save WebDAV server configuration
     *
     * @param config Connection configuration
     */
    fun saveServerConfig(config: WebDAVClient.ConnectionConfig) {
        Log.d(TAG, "Saving WebDAV server configuration")

        prefs.edit().apply {
            putString(KEY_SERVER_URL, config.serverUrl)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, encryptPassword(config.password))
            putString(KEY_AUTH_TYPE, config.authType.name)
            putInt(KEY_CONNECT_TIMEOUT, config.connectTimeoutMs)
            putInt(KEY_READ_TIMEOUT, config.readTimeoutMs)
            apply()
        }

        Log.i(TAG, "WebDAV server configuration saved")
    }

    /**
     * Get WebDAV server configuration
     *
     * @return Connection configuration or null if not configured
     */
    fun getServerConfig(): WebDAVClient.ConnectionConfig? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val encryptedPassword = prefs.getString(KEY_PASSWORD, null)
        val authTypeName = prefs.getString(KEY_AUTH_TYPE, WebDAVClient.AuthType.BASIC.name)
        val connectTimeout = prefs.getInt(KEY_CONNECT_TIMEOUT, 30000)
        val readTimeout = prefs.getInt(KEY_READ_TIMEOUT, 60000)

        if (serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || encryptedPassword.isNullOrEmpty()) {
            Log.d(TAG, "WebDAV server configuration not found")
            return null
        }

        return try {
            WebDAVClient.ConnectionConfig(
                serverUrl = serverUrl,
                username = username,
                password = decryptPassword(encryptedPassword),
                authType = WebDAVClient.AuthType.valueOf(authTypeName),
                connectTimeoutMs = connectTimeout,
                readTimeoutMs = readTimeout
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load WebDAV server configuration", e)
            null
        }
    }

    /**
     * Check if server configuration exists
     *
     * @return true if configured
     */
    fun hasServerConfig(): Boolean {
        return getServerConfig() != null
    }

    /**
     * Clear server configuration
     */
    fun clearServerConfig() {
        Log.d(TAG, "Clearing WebDAV server configuration")

        prefs.edit().apply {
            remove(KEY_SERVER_URL)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            remove(KEY_AUTH_TYPE)
            remove(KEY_CONNECT_TIMEOUT)
            remove(KEY_READ_TIMEOUT)
            apply()
        }

        Log.i(TAG, "WebDAV server configuration cleared")
    }

    /**
     * Save sync folder configuration
     *
     * @param config Folder configuration
     */
    fun saveFolderConfig(config: SyncFolderConfig) {
        Log.d(TAG, "Saving folder configuration: ${config.id}")

        val json = gson.toJson(config)
        prefs.edit().putString("${KEY_FOLDER_PREFIX}${config.id}", json).apply()

        // Update folder list
        updateFolderList(config.id, true)

        Log.i(TAG, "Folder configuration saved: ${config.id}")
    }

    /**
     * Get folder configuration by ID
     *
     * @param folderId Folder ID
     * @return Folder configuration or null if not found
     */
    fun getFolderConfig(folderId: String): SyncFolderConfig? {
        val json = prefs.getString("${KEY_FOLDER_PREFIX}$folderId", null)
            ?: return null

        return try {
            gson.fromJson(json, SyncFolderConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load folder configuration: $folderId", e)
            null
        }
    }

    /**
     * Get all folder configurations
     *
     * @return List of folder configurations
     */
    fun getAllFolderConfigs(): List<SyncFolderConfig> {
        val folderIds = getFolderList()
        return folderIds.mapNotNull { getFolderConfig(it) }
    }

    /**
     * Get enabled folder configurations
     *
     * @return List of enabled folder configurations
     */
    fun getEnabledFolderConfigs(): List<SyncFolderConfig> {
        return getAllFolderConfigs().filter { it.enabled }
    }

    /**
     * Delete folder configuration
     *
     * @param folderId Folder ID
     */
    fun deleteFolderConfig(folderId: String) {
        Log.d(TAG, "Deleting folder configuration: $folderId")

        prefs.edit().remove("${KEY_FOLDER_PREFIX}$folderId").apply()

        // Update folder list
        updateFolderList(folderId, false)

        Log.i(TAG, "Folder configuration deleted: $folderId")
    }

    /**
     * Update folder enabled status
     *
     * @param folderId Folder ID
     * @param enabled Enabled status
     */
    fun setFolderEnabled(folderId: String, enabled: Boolean) {
        val config = getFolderConfig(folderId) ?: return

        val updatedConfig = config.copy(enabled = enabled)
        saveFolderConfig(updatedConfig)

        Log.d(TAG, "Folder ${if (enabled) "enabled" else "disabled"}: $folderId")
    }

    /**
     * Generate unique folder ID
     *
     * @return Unique folder ID
     */
    fun generateFolderId(): String {
        return "folder_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    /**
     * Export all configurations to JSON string
     *
     * @return JSON string containing all configurations
     */
    fun exportConfigurations(): String {
        val exportData = mapOf(
            "server" to getServerConfig()?.let { config ->
                mapOf(
                    "serverUrl" to config.serverUrl,
                    "username" to config.username,
                    "authType" to config.authType.name,
                    "connectTimeout" to config.connectTimeoutMs,
                    "readTimeout" to config.readTimeoutMs
                )
            },
            "folders" to getAllFolderConfigs()
        )

        return gson.toJson(exportData)
    }

    /**
     * Import configurations from JSON string
     *
     * @param json JSON string containing configurations
     * @return true if import successful
     */
    fun importConfigurations(json: String): Boolean {
        return try {
            val importData = gson.fromJson(json, Map::class.java) as Map<String, Any>

            // Import server config (without password for security)
            @Suppress("UNCHECKED_CAST")
            val serverData = importData["server"] as? Map<String, Any>
            if (serverData != null) {
                // Don't import password, user needs to re-enter it
                prefs.edit().apply {
                    putString(KEY_SERVER_URL, serverData["serverUrl"] as? String)
                    putString(KEY_USERNAME, serverData["username"] as? String)
                    putString(KEY_AUTH_TYPE, serverData["authType"] as? String)
                    putInt(KEY_CONNECT_TIMEOUT, (serverData["connectTimeout"] as? Double)?.toInt() ?: 30000)
                    putInt(KEY_READ_TIMEOUT, (serverData["readTimeout"] as? Double)?.toInt() ?: 60000)
                    apply()
                }
            }

            // Import folder configs
            @Suppress("UNCHECKED_CAST")
            val foldersData = importData["folders"] as? List<Map<String, Any>>
            foldersData?.forEach { folderData ->
                val folderJson = gson.toJson(folderData)
                val folderConfig = gson.fromJson(folderJson, SyncFolderConfig::class.java)
                saveFolderConfig(folderConfig)
            }

            Log.i(TAG, "Configurations imported successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import configurations", e)
            false
        }
    }

    /**
     * Clear all configurations
     */
    fun clearAll() {
        Log.d(TAG, "Clearing all configurations")

        prefs.edit().clear().apply()

        Log.i(TAG, "All configurations cleared")
    }

    /**
     * Register preference change listener
     *
     * @param listener Preference change listener
     */
    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Unregister preference change listener
     *
     * @param listener Preference change listener
     */
    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Encrypt password using AES-GCM
     *
     * @param password Plain text password
     * @return Encrypted password (Base64 encoded)
     */
    private fun encryptPassword(password: String): String {
        try {
            // Generate or retrieve encryption key
            val key = getOrCreateEncryptionKey()

            // Generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // Encrypt password
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)

            val encryptedBytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

            // Combine IV and encrypted data
            val combined = iv + encryptedBytes

            // Return Base64 encoded result
            return android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt password", e)
            // Fallback: return plain text (not recommended)
            return password
        }
    }

    /**
     * Decrypt password using AES-GCM
     *
     * @param encryptedPassword Encrypted password (Base64 encoded)
     * @return Plain text password
     */
    private fun decryptPassword(encryptedPassword: String): String {
        try {
            // Decode Base64
            val combined = android.util.Base64.decode(encryptedPassword, android.util.Base64.DEFAULT)

            // Extract IV and encrypted data
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encryptedBytes = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            // Get encryption key
            val key = getOrCreateEncryptionKey()

            // Decrypt password
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)

            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt password", e)
            // Fallback: return encrypted as-is (might be plain text)
            return encryptedPassword
        }
    }

    /**
     * Get or create encryption key
     *
     * @return Secret key for encryption
     */
    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyPref = "encryption_key"

        // Try to get existing key
        val existingKey = prefs.getString(keyPref, null)
        if (existingKey != null) {
            val keyBytes = android.util.Base64.decode(existingKey, android.util.Base64.DEFAULT)
            return SecretKeySpec(keyBytes, KEY_ALGORITHM)
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM)
        keyGenerator.init(KEY_SIZE, SecureRandom())
        val key = keyGenerator.generateKey()

        // Store key
        val keyBytes = key.encoded
        val keyBase64 = android.util.Base64.encodeToString(keyBytes, android.util.Base64.DEFAULT)
        prefs.edit().putString(keyPref, keyBase64).apply()

        return key
    }

    /**
     * Get list of folder IDs
     *
     * @return Set of folder IDs
     */
    private fun getFolderList(): Set<String> {
        val json = prefs.getString(KEY_FOLDER_CONFIGS, null)
            ?: return emptySet()

        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load folder list", e)
            emptySet()
        }
    }

    /**
     * Update folder list
     *
     * @param folderId Folder ID
     * @param add true to add, false to remove
     */
    private fun updateFolderList(folderId: String, add: Boolean) {
        val folderList = getFolderList().toMutableSet()

        if (add) {
            folderList.add(folderId)
        } else {
            folderList.remove(folderId)
        }

        val json = gson.toJson(folderList)
        prefs.edit().putString(KEY_FOLDER_CONFIGS, json).apply()
    }
}
