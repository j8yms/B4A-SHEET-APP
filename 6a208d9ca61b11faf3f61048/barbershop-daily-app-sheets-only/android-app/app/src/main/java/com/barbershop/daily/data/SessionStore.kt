package com.barbershop.daily.data

import android.content.Context

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
    private const val KEY_REMEMBERED_PIN = "remembered_pin"

    fun load(context: Context): Session? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = sp.getString(KEY_TOKEN, null) ?: return null
        val name = sp.getString(KEY_NAME, "") ?: ""
        val role = sp.getString(KEY_ROLE, "staff") ?: "staff"
        return Session(token = token, name = name, role = role)
    }

    fun save(context: Context, session: Session) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_NAME, session.name)
            .putString(KEY_ROLE, session.role)
            .apply()
    }

    fun clearSession(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_NAME)
            .remove(KEY_ROLE)
            .apply()
    }

    /** Keeps remembered credentials so "easy login" still works after signing out. */
    fun saveRemembered(context: Context, name: String, pin: String, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_REMEMBER_ENABLED, enabled)
            .putString(KEY_REMEMBERED_NAME, name)
            .putString(KEY_REMEMBERED_PIN, pin)
            .apply()
    }

    fun clearRemembered(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_REMEMBER_ENABLED, false)
            .remove(KEY_REMEMBERED_NAME)
            .remove(KEY_REMEMBERED_PIN)
            .apply()
    }

    fun isRememberEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_REMEMBER_ENABLED, false)
    }

    /** Returns (name, pin) if the user opted in to remember their PIN. */
    fun loadRemembered(context: Context): Pair<String, String>? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_REMEMBER_ENABLED, false)) return null
        val name = sp.getString(KEY_REMEMBERED_NAME, null) ?: return null
        val pin = sp.getString(KEY_REMEMBERED_PIN, null) ?: return null
        return name to pin
    }
}
