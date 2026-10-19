package com.barbershop.daily.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore
import javax.crypto.KeyGenerator

data class Session(
    val token: String,
    val name: String,
    val role: String
)

object SessionStore {
    private const val PREFS = "barbershop_daily_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_NAME = "name"
    private const val KEY_ROLE = "role"
    private const val KEY_REMEMBER_ENABLED = "remember_enabled"
    private const val KEY_REMEMBERED_NAME = "remembered_name"
    // NOTE: PIN is NOT stored. Only name is remembered for convenience.
    // Biometric authentication is used for "easy login" instead of PIN storage.

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(context: Context): Session? {
        val sp = getEncryptedPrefs(context)
        val token = sp.getString(KEY_TOKEN, null) ?: return null
        val name = sp.getString(KEY_NAME, "") ?: ""
        val role = sp.getString(KEY_ROLE, "staff") ?: "staff"
        return Session(token = token, name = name, role = role)
    }

    fun save(context: Context, session: Session) {
        val sp = getEncryptedPrefs(context)
        sp.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_NAME, session.name)
            .putString(KEY_ROLE, session.role)
            .apply()
    }

    fun clearSession(context: Context) {
        val sp = getEncryptedPrefs(context)
        sp.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_NAME)
            .remove(KEY_ROLE)
            .apply()
    }

    /** Stores only the user's name for biometric login convenience. PIN is NEVER stored. */
    fun saveRemembered(context: Context, name: String, enabled: Boolean) {
        val sp = getEncryptedPrefs(context)
        sp.edit()
            .putBoolean(KEY_REMEMBER_ENABLED, enabled)
            .putString(KEY_REMEMBERED_NAME, if (enabled) name else "")
            .apply()
    }

    fun clearRemembered(context: Context) {
        val sp = getEncryptedPrefs(context)
        sp.edit()
            .putBoolean(KEY_REMEMBER_ENABLED, false)
            .remove(KEY_REMEMBERED_NAME)
            .apply()
    }

    fun isRememberEnabled(context: Context): Boolean {
        val sp = getEncryptedPrefs(context)
        return sp.getBoolean(KEY_REMEMBER_ENABLED, false)
    }

    /** Returns the remembered name if user opted in. PIN is never returned. */
    fun loadRememberedName(context: Context): String? {
        val sp = getEncryptedPrefs(context)
        if (!sp.getBoolean(KEY_REMEMBER_ENABLED, false)) return null
        return sp.getString(KEY_REMEMBERED_NAME, null)
    }
}
