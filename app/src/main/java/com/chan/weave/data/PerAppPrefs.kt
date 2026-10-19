package com.chan.weave.data

import android.content.Context
import androidx.core.content.edit

/**
 * Per-app VPN routing preferences.
 *
 * Modes:
 * - ALL: every app goes through the tunnel (default).
 * - BLOCKLIST: listed apps bypass the VPN.
 * - ALLOWLIST: only listed apps go through the VPN.
 */
object PerAppPrefs {

    private const val FILE = "per_app"
    private const val KEY_MODE = "mode"
    private const val KEY_PACKAGES = "packages"

    enum class Mode { ALL, BLOCKLIST, ALLOWLIST }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getMode(context: Context): Mode =
        runCatching {
            Mode.valueOf(prefs(context).getString(KEY_MODE, Mode.ALL.name) ?: Mode.ALL.name)
        }.getOrDefault(Mode.ALL)

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit { putString(KEY_MODE, mode.name) }
    }

    fun getPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PACKAGES, emptySet()).orEmpty()

    fun setPackages(context: Context, packages: Set<String>) {
        prefs(context).edit { putStringSet(KEY_PACKAGES, packages) }
    }

    fun togglePackage(context: Context, packageName: String, enabled: Boolean) {
        val current = getPackages(context).toMutableSet()
        if (enabled) current.add(packageName) else current.remove(packageName)
        setPackages(context, current)
    }
}
