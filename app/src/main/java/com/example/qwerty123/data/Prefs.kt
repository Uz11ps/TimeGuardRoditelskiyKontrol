package com.example.qwerty123.data

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "timeguard_prefs"
    private const val KEY_ROLE = "user_role" // "parent", "child", null
    private const val KEY_FAMILY_ID = "family_id"
    private const val KEY_PIN = "parent_pin"

    fun getRole(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ROLE, null)
    }

    fun saveRole(context: Context, role: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ROLE, role).apply()
    }

    fun getFamilyId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_FAMILY_ID, null)
    }

    fun saveFamilyId(context: Context, familyId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_FAMILY_ID, familyId).apply()
    }

    fun getPin(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PIN, null)
    }

    fun savePin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_PIN, pin).apply()
    }
}

