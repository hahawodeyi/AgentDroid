package com.appia.ai.appia

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AppiaConfig(
    val serverUrl: String = "",
    val userId: String = "",
    val authToken: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && userId.isNotBlank() && authToken.isNotBlank()
}

class AppiaConfigStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): AppiaConfig = AppiaConfig(
        serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
        userId = prefs.getString(KEY_USER_ID, "") ?: "",
        authToken = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
    )

    fun save(config: AppiaConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, normalizeServerUrl(config.serverUrl))
            .putString(KEY_USER_ID, config.userId.trim())
            .putString(KEY_AUTH_TOKEN, config.authToken.trim())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "appia_secure_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTH_TOKEN = "auth_token"

        internal fun normalizeServerUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return url
        }
    }
}
