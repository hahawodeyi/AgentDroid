package com.appia.ai.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.ModelConfigList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "agentdroid_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _configs = MutableStateFlow(loadConfigs())
    val configs: StateFlow<List<ModelConfig>> = _configs

    private val _activeConfigId = MutableStateFlow(prefs.getString(KEY_ACTIVE_ID, "") ?: "")
    val activeConfigId: StateFlow<String> = _activeConfigId

    fun saveConfigs(list: List<ModelConfig>) {
        val configList = ModelConfigList(list.toMutableList())
        prefs.edit().putString(KEY_CONFIGS, json.encodeToString(configList)).apply()
        _configs.value = list
    }

    fun saveActiveConfig(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
        _activeConfigId.value = id
    }

    fun getActiveConfig(): ModelConfig? {
        val id = _activeConfigId.value
        return _configs.value.firstOrNull { it.providerId == id }
    }

    private fun loadConfigs(): List<ModelConfig> {
        val raw = prefs.getString(KEY_CONFIGS, null)
        return if (raw != null) {
            try {
                json.decodeFromString<ModelConfigList>(raw).configs.toList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    companion object {
        private const val KEY_CONFIGS = "model_configs"
        private const val KEY_ACTIVE_ID = "active_config_id"
    }
}
