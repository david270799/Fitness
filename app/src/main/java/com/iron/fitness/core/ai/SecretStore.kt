package com.iron.fitness.core.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Секреты (ключ Gemini) — в EncryptedSharedPreferences, ключ шифрования — в Android Keystore.
 * В бэкап не попадают.
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences? by lazy { open() }

    private val _hasKey = MutableStateFlow(false)
    val hasGeminiKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    init {
        _hasKey.value = !geminiKey().isNullOrBlank()
    }

    private fun create(): SharedPreferences {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Если хранилище повреждено (например, после переустановки), создаём заново — ключ придётся ввести снова. */
    private fun open(): SharedPreferences? = runCatching { create() }.recoverCatching {
        context.deleteSharedPreferences(FILE)
        create()
    }.getOrNull()

    fun geminiKey(): String? = runCatching { prefs?.getString(KEY_GEMINI, null) }.getOrNull()

    fun setGeminiKey(key: String?) {
        val p = prefs ?: return
        val clean = key?.trim()?.takeIf { it.isNotEmpty() }
        p.edit().apply {
            if (clean == null) remove(KEY_GEMINI) else putString(KEY_GEMINI, clean)
        }.apply()
        _hasKey.value = clean != null
    }

    companion object {
        private const val FILE = "iron_secrets"
        private const val KEY_GEMINI = "gemini_api_key"
    }
}
