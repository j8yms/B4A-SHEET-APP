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

    fun clear(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
    }
}

