package com.chan.ciancore.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Routing preferences.
 *
 * Modes:
 * - GLOBAL: everything through the proxy.
 * - BYPASS_CN: mainland China domains/IPs go direct (needs geo files).
 * - CUSTOM: user-defined rules.
 */
object RoutePrefs {

    private const val FILE = "routing"
    private const val KEY_MODE = "mode"
    private const val KEY_CUSTOM_RULES = "custom_rules"

    enum class Mode(val displayName: String) {
        GLOBAL("全局代理"),
        BYPASS_CN("绕过大陆"),
        CUSTOM("自定义规则")
    }

    /** One custom rule: match [kind]:[value], send to [action]. */
    data class CustomRule(
        val kind: String, // "domain" or "ip"
        val value: String,
        val action: String // "proxy", "direct", "block"
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("kind", kind)
            .put("value", value)
            .put("action", action)

        companion object {
            fun fromJson(o: JSONObject) = CustomRule(
                kind = o.optString("kind", "domain"),
                value = o.optString("value"),
                action = o.optString("action", "proxy")
            )
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getMode(context: Context): Mode =
        runCatching {
            Mode.valueOf(prefs(context).getString(KEY_MODE, Mode.GLOBAL.name) ?: Mode.GLOBAL.name)
        }.getOrDefault(Mode.GLOBAL)

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit { putString(KEY_MODE, mode.name) }
    }

    fun getCustomRules(context: Context): List<CustomRule> {
        val raw = prefs(context).getString(KEY_CUSTOM_RULES, "[]").orEmpty()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> CustomRule.fromJson(arr.getJSONObject(i)) }
                .filter { it.value.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun setCustomRules(context: Context, rules: List<CustomRule>) {
        val arr = JSONArray()
        rules.filter { it.value.isNotBlank() }.forEach { arr.put(it.toJson()) }
        prefs(context).edit { putString(KEY_CUSTOM_RULES, arr.toString()) }
    }
}
