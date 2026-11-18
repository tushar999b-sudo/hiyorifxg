package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hiyori_settings")

data class HiyoriSettings(
    val useGeminiApi: Boolean = false,
    val hasGeminiApiKey: Boolean = false,
    val maskedGeminiKey: String = "",
    val cachedGeminiModel: String = "",
    val useLocalModel: Boolean = false,
    val laptopAddress: String = "",
    val localModelName: String = DEFAULT_LOCAL_MODEL,
    val isLiveVoiceEnabled: Boolean = false,
    val hasPorcupineKey: Boolean = false,
    val maskedPorcupineKey: String = "",
    val whisperModelDownloaded: Boolean = false,
    val darkTheme: Boolean? = null,
    val maxAgentSteps: Int = 12,
    val confirmDeleteFiles: Boolean = true,
    val confirmCriticalSettings: Boolean = true,
    val confirmNewContacts: Boolean = true,
    val confirmedRecipients: Set<String> = emptySet()
) {
    companion object {
        const val DEFAULT_LOCAL_MODEL = "qwen2.5:7b"
        val LOCAL_MODEL_OPTIONS = listOf(
            "qwen2.5:7b",
            "qwen2.5-coder:7b",
            "dolphin3:8b"
        )
    }
}

interface SettingsBackend {
    val settingsFlow: Flow<HiyoriSettings>
    fun getGeminiApiKey(): String
    fun saveGeminiApiKey(apiKey: String)
    fun clearGeminiApiKey()
    fun getLaptopAddress(): String
    fun saveLaptopAddress(address: String)
    fun getPorcupineAccessKey(): String
    fun savePorcupineAccessKey(accessKey: String)
    fun clearPorcupineAccessKey()
    suspend fun update(transform: (HiyoriSettings) -> HiyoriSettings)
}

class InMemorySettingsBackend(initial: HiyoriSettings = HiyoriSettings()) : SettingsBackend {
    private var geminiKey: String = ""
    private var laptopAddr: String = initial.laptopAddress
    private var porcupineKey: String = ""
    private val _flow = MutableStateFlow(initial)
    override val settingsFlow: Flow<HiyoriSettings> = _flow.asStateFlow()

    override fun getGeminiApiKey(): String = geminiKey

    override fun saveGeminiApiKey(apiKey: String) {
        geminiKey = apiKey.trim()
        _flow.value = _flow.value.copy(
            hasGeminiApiKey = geminiKey.isNotBlank(),
            maskedGeminiKey = maskApiKey(geminiKey),
            cachedGeminiModel = ""
        )
    }

    override fun clearGeminiApiKey() {
        geminiKey = ""
        _flow.value = _flow.value.copy(
            hasGeminiApiKey = false,
            maskedGeminiKey = "",
            cachedGeminiModel = ""
        )
    }

    override fun getLaptopAddress(): String = laptopAddr

    override fun saveLaptopAddress(address: String) {
        laptopAddr = address.trim()
        _flow.value = _flow.value.copy(laptopAddress = laptopAddr)
    }

    override fun getPorcupineAccessKey(): String = porcupineKey

    override fun savePorcupineAccessKey(accessKey: String) {
        porcupineKey = accessKey.trim()
        _flow.value = _flow.value.copy(
            hasPorcupineKey = porcupineKey.isNotBlank(),
            maskedPorcupineKey = maskApiKey(porcupineKey)
        )
    }

    override fun clearPorcupineAccessKey() {
        porcupineKey = ""
        _flow.value = _flow.value.copy(
            hasPorcupineKey = false,
            maskedPorcupineKey = ""
        )
    }

    override suspend fun update(transform: (HiyoriSettings) -> HiyoriSettings) {
        _flow.value = transform(_flow.value)
    }
}

class AndroidSettingsBackend(private val context: Context) : SettingsBackend {
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                "hiyori_encrypted_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.applicationContext.getSharedPreferences("hiyori_encrypted_fallback", Context.MODE_PRIVATE)
        }
    }

    override val settingsFlow: Flow<HiyoriSettings>
        get() = context.applicationContext.settingsDataStore.data.map { prefs ->
            mapPrefsToSettings(prefs)
        }

    private fun mapPrefsToSettings(prefs: Preferences): HiyoriSettings {
        val rawGemini = getGeminiApiKey()
        val rawAddr = getLaptopAddress()
        val rawPorcupine = getPorcupineAccessKey()
        val storedLocalModel = prefs[KEY_LOCAL_MODEL_NAME]?.trim().orEmpty()
        val validLocalModel = if (storedLocalModel in HiyoriSettings.LOCAL_MODEL_OPTIONS) {
            storedLocalModel
        } else {
            HiyoriSettings.DEFAULT_LOCAL_MODEL
        }

        return HiyoriSettings(
            useGeminiApi = prefs[KEY_USE_GEMINI_API] ?: false,
            hasGeminiApiKey = rawGemini.isNotBlank(),
            maskedGeminiKey = maskApiKey(rawGemini),
            cachedGeminiModel = prefs[KEY_CACHED_GEMINI_MODEL]?.trim().orEmpty(),
            useLocalModel = prefs[KEY_USE_LOCAL_MODEL] ?: false,
            laptopAddress = rawAddr,
            localModelName = validLocalModel,
            isLiveVoiceEnabled = prefs[KEY_LIVE_VOICE_ENABLED] ?: false,
            hasPorcupineKey = rawPorcupine.isNotBlank(),
            maskedPorcupineKey = maskApiKey(rawPorcupine),
            whisperModelDownloaded = prefs[KEY_WHISPER_MODEL_DOWNLOADED] ?: false,
            darkTheme = prefs[KEY_DARK_THEME],
            maxAgentSteps = prefs[KEY_MAX_STEPS] ?: 12,
            confirmDeleteFiles = prefs[KEY_CONFIRM_DELETE_FILES] ?: true,
            confirmCriticalSettings = prefs[KEY_CONFIRM_CRITICAL_SETTINGS] ?: true,
            confirmNewContacts = prefs[KEY_CONFIRM_NEW_CONTACTS] ?: true,
            confirmedRecipients = prefs[KEY_CONFIRMED_RECIPIENTS] ?: emptySet()
        )
    }

    override fun getGeminiApiKey(): String = encryptedPrefs.getString(KEY_GEMINI_API_KEY, "") ?: ""

    override fun saveGeminiApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    override fun clearGeminiApiKey() {
        encryptedPrefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    override fun getLaptopAddress(): String = encryptedPrefs.getString(KEY_LAPTOP_ADDRESS, "") ?: ""

    override fun saveLaptopAddress(address: String) {
        val cleaned = address.trim()
        if (cleaned.isEmpty()) {
            encryptedPrefs.edit().remove(KEY_LAPTOP_ADDRESS).apply()
        } else {
            encryptedPrefs.edit().putString(KEY_LAPTOP_ADDRESS, cleaned).apply()
        }
    }

    override fun getPorcupineAccessKey(): String = encryptedPrefs.getString(KEY_PORCUPINE_ACCESS_KEY, "") ?: ""

    override fun savePorcupineAccessKey(accessKey: String) {
        encryptedPrefs.edit().putString(KEY_PORCUPINE_ACCESS_KEY, accessKey.trim()).apply()
    }

    override fun clearPorcupineAccessKey() {
        encryptedPrefs.edit().remove(KEY_PORCUPINE_ACCESS_KEY).apply()
    }

    override suspend fun update(transform: (HiyoriSettings) -> HiyoriSettings) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            val current = mapPrefsToSettings(prefs)
            val next = transform(current)
            prefs[KEY_USE_GEMINI_API] = next.useGeminiApi
            if (next.cachedGeminiModel.isBlank()) {
                prefs.remove(KEY_CACHED_GEMINI_MODEL)
            } else {
                prefs[KEY_CACHED_GEMINI_MODEL] = next.cachedGeminiModel
            }
            prefs[KEY_USE_LOCAL_MODEL] = next.useLocalModel
            prefs[KEY_LOCAL_MODEL_NAME] = next.localModelName
            prefs[KEY_LIVE_VOICE_ENABLED] = next.isLiveVoiceEnabled
            prefs[KEY_WHISPER_MODEL_DOWNLOADED] = next.whisperModelDownloaded
            if (next.darkTheme != null) {
                prefs[KEY_DARK_THEME] = next.darkTheme
            }
            prefs[KEY_MAX_STEPS] = next.maxAgentSteps
            prefs[KEY_CONFIRM_DELETE_FILES] = next.confirmDeleteFiles
            prefs[KEY_CONFIRM_CRITICAL_SETTINGS] = next.confirmCriticalSettings
            prefs[KEY_CONFIRM_NEW_CONTACTS] = next.confirmNewContacts
            prefs[KEY_CONFIRMED_RECIPIENTS] = next.confirmedRecipients
        }
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_LAPTOP_ADDRESS = "laptop_address"
        private const val KEY_PORCUPINE_ACCESS_KEY = "porcupine_access_key"

        private val KEY_USE_GEMINI_API = booleanPreferencesKey("use_gemini_api")
        private val KEY_CACHED_GEMINI_MODEL = stringPreferencesKey("cached_gemini_model")
        private val KEY_USE_LOCAL_MODEL = booleanPreferencesKey("use_local_model")
        private val KEY_LOCAL_MODEL_NAME = stringPreferencesKey("local_model_name")
        private val KEY_LIVE_VOICE_ENABLED = booleanPreferencesKey("live_voice_enabled")
        private val KEY_WHISPER_MODEL_DOWNLOADED = booleanPreferencesKey("whisper_model_downloaded")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_MAX_STEPS = intPreferencesKey("max_agent_steps")
        private val KEY_CONFIRM_DELETE_FILES = booleanPreferencesKey("confirm_delete_files")
        private val KEY_CONFIRM_CRITICAL_SETTINGS = booleanPreferencesKey("confirm_critical_settings")
        private val KEY_CONFIRM_NEW_CONTACTS = booleanPreferencesKey("confirm_new_contacts")
        private val KEY_CONFIRMED_RECIPIENTS = stringSetPreferencesKey("confirmed_recipients")
    }
}

fun maskApiKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.length <= 8) return "...${trimmed.takeLast(4)}"
    return "${trimmed.take(4)}...${trimmed.takeLast(4)}"
}

class SettingsStore(
    private val backend: SettingsBackend
) {
    constructor(context: Context) : this(AndroidSettingsBackend(context))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(HiyoriSettings())
    val state: StateFlow<HiyoriSettings> = _state.asStateFlow()

    init {
        scope.launch {
            backend.settingsFlow.collect { mapped ->
                _state.value = mapped
            }
        }
    }

    fun getGeminiApiKey(): String = backend.getGeminiApiKey()

    fun saveGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        backend.saveGeminiApiKey(trimmed)
        _state.value = _state.value.copy(
            hasGeminiApiKey = trimmed.isNotBlank(),
            maskedGeminiKey = maskApiKey(trimmed),
            cachedGeminiModel = ""
        )
        scope.launch {
            backend.update { it.copy(cachedGeminiModel = "") }
        }
    }

    fun clearGeminiApiKey() {
        backend.clearGeminiApiKey()
        _state.value = _state.value.copy(
            hasGeminiApiKey = false,
            maskedGeminiKey = "",
            cachedGeminiModel = ""
        )
        scope.launch {
            backend.update { it.copy(cachedGeminiModel = "") }
        }
    }

    fun getLaptopAddress(): String = backend.getLaptopAddress()

    fun saveLaptopAddress(address: String) {
        val trimmed = address.trim()
        backend.saveLaptopAddress(trimmed)
        _state.value = _state.value.copy(laptopAddress = trimmed)
    }

    fun getPorcupineAccessKey(): String = backend.getPorcupineAccessKey()

    fun savePorcupineAccessKey(accessKey: String) {
        val trimmed = accessKey.trim()
        backend.savePorcupineAccessKey(trimmed)
        _state.value = _state.value.copy(
            hasPorcupineKey = trimmed.isNotBlank(),
            maskedPorcupineKey = maskApiKey(trimmed)
        )
    }

    fun clearPorcupineAccessKey() {
        backend.clearPorcupineAccessKey()
        _state.value = _state.value.copy(
            hasPorcupineKey = false,
            maskedPorcupineKey = ""
        )
    }

    suspend fun setUseGeminiApi(enabled: Boolean) = updateState { it.copy(useGeminiApi = enabled) }

    suspend fun setCachedGeminiModel(modelId: String) {
        val cleaned = modelId.trim()
        updateState { it.copy(cachedGeminiModel = cleaned) }
    }

    suspend fun clearCachedGeminiModel() {
        updateState { it.copy(cachedGeminiModel = "") }
    }

    suspend fun setUseLocalModel(enabled: Boolean) = updateState { it.copy(useLocalModel = enabled) }

    suspend fun setLocalModelName(modelName: String) {
        val valid = if (modelName in HiyoriSettings.LOCAL_MODEL_OPTIONS) {
            modelName
        } else {
            HiyoriSettings.DEFAULT_LOCAL_MODEL
        }
        updateState { it.copy(localModelName = valid) }
    }

    suspend fun setLiveVoiceEnabled(enabled: Boolean) = updateState { it.copy(isLiveVoiceEnabled = enabled) }

    suspend fun setWhisperModelDownloaded(downloaded: Boolean) = updateState { it.copy(whisperModelDownloaded = downloaded) }

    private suspend fun updateState(transform: (HiyoriSettings) -> HiyoriSettings) {
        _state.value = transform(_state.value)
        backend.update(transform)
    }

    suspend fun setDarkTheme(isDark: Boolean) = updateState { it.copy(darkTheme = isDark) }

    suspend fun setMaxAgentSteps(steps: Int) = updateState { it.copy(maxAgentSteps = steps.coerceIn(1, 30)) }

    suspend fun setConfirmDeleteFiles(enabled: Boolean) = updateState { it.copy(confirmDeleteFiles = enabled) }

    suspend fun setConfirmCriticalSettings(enabled: Boolean) = updateState { it.copy(confirmCriticalSettings = enabled) }

    suspend fun setConfirmNewContacts(enabled: Boolean) = updateState { it.copy(confirmNewContacts = enabled) }

    suspend fun markRecipientConfirmed(recipient: String) {
        val norm = recipient.trim().lowercase()
        if (norm.isBlank()) return
        updateState { it.copy(confirmedRecipients = it.confirmedRecipients + norm) }
    }

    fun isRecipientConfirmed(recipient: String): Boolean {
        val norm = recipient.trim().lowercase()
        return norm in _state.value.confirmedRecipients
    }
}
