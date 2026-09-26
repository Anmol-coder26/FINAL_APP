package com.guardian.app.protect.advanced

import android.content.Context

object TrustedContactManager {
    private const val PREFS = "guardian_trusted_contact"

    data class TrustedContact(
        val name: String,
        val phone: String,
        val enabled: Boolean
    )

    fun save(context: Context, contact: TrustedContact) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", contact.name)
            .putString("phone", contact.phone)
            .putBoolean("enabled", contact.enabled)
            .apply()
    }

    fun load(context: Context): TrustedContact? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val phone = p.getString("phone", null) ?: return null
        return TrustedContact(
            name = p.getString("name", "") ?: "",
            phone = phone,
            enabled = p.getBoolean("enabled", true)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
