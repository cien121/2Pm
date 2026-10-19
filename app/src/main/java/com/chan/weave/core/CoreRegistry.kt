package com.chan.weave.core

import android.content.Context
import com.chan.weave.core.xray.XrayCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pluggable core registry — single core (Xray) now; sing-box removed.
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
