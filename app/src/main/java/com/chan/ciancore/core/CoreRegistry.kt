package com.chan.ciancore.core

import android.content.Context
import com.chan.ciancore.core.singbox.SingBoxCore
import com.chan.ciancore.core.xray.XrayCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pluggable core registry — the only place that knows which concrete
 * cores exist.
 *
 * To add a third core:
 *  1. Add an entry to [CoreType]
 *  2. Implement [ProxyCore]
 *  3. Register it in [registerBuiltins] below
 * That's it. UI, VPN service and database code stay untouched.
 */
object CoreRegistry {

    private val cores = mutableMapOf<CoreType, ProxyCore>()

    private val _activeType = MutableStateFlow(CoreType.XRAY)
    val activeType: StateFlow<CoreType> = _activeType.asStateFlow()

    fun initialize(context: Context) {
        registerBuiltins(context)
    }

    private fun registerBuiltins(context: Context) {
        register(XrayCore(context))
        register(SingBoxCore(context))
    }

    fun register(core: ProxyCore) {
        cores[core.type] = core
    }

    fun get(type: CoreType): ProxyCore =
        cores[type] ?: throw IllegalArgumentException("Core not registered: $type")

    fun getOrNull(type: CoreType): ProxyCore? = cores[type]

    fun all(): List<ProxyCore> = CoreType.entries.mapNotNull { cores[it] }

    /** The core currently selected for new connections. */
    var active: ProxyCore
        get() = get(_activeType.value)
        set(value) {
            _activeType.value = value.type
        }

    fun setActive(type: CoreType) {
        _activeType.value = type
    }
}
