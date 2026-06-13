package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePreferences {
    private const val SECURE_PREFS_FILE = "wtr_secure_settings"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    
    private var encryptedPrefs: SharedPreferences? = null

    @Synchronized
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        if (encryptedPrefs != null) {
            return encryptedPrefs!!
        }
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            encryptedPrefs = EncryptedSharedPreferences.create(
                SECURE_PREFS_FILE,
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return encryptedPrefs!!
        } catch (e: Exception) {
            WtrLogManager.log(context, "Custom EncryptedSharedPreferences init failed safely: ${e.message}. Using private fallback.")
            e.printStackTrace()
            return context.getSharedPreferences("wtr_secure_fallback_settings", Context.MODE_PRIVATE)
        }
    }

    fun getGeminiApiKey(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        var key = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (key.isEmpty()) {
            val legacyPrefs = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
            val legacyKey = legacyPrefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
            if (legacyKey.isNotEmpty()) {
                setGeminiApiKey(context, legacyKey)
                legacyPrefs.edit().remove(KEY_GEMINI_API_KEY).apply()
                key = legacyKey
                WtrLogManager.log(context, "Successful secure migration of Gemini API key to EncryptedSharedPreferences.")
            }
        }
        return key
    }

    fun setGeminiApiKey(context: Context, key: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
    }
}
