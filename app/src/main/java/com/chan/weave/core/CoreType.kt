package com.chan.weave.core

/**
 * Supported proxy core engines.
 *
 * Add a new entry here + a [ProxyCore] implementation + one line in
 * [CoreRegistry] and the new core is live. No other changes needed.
 */
enum class CoreType(val displayName: String) {
    XRAY("Xray"),
    SING_BOX("sing-box");

    companion object {
        fun fromName(name: String): CoreType =
            entries.firstOrNull { it.name == name } ?: XRAY
    }
}
